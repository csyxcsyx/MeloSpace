import axios, { AxiosError } from "axios";
import type { ApiResponse } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";

export const http = axios.create({
  baseURL: "",
  timeout: 15000
});

type RetryableConfig = NonNullable<AxiosError<ApiResponse<null>>["config"]> & {
  __retryCount?: number;
  __retriedWithoutAuth?: boolean;
};

const PUBLIC_CONTENT_GET_PATHS = [
  /^\/api\/songs(?:\/\d+)?$/,
  /^\/api\/artists$/,
  /^\/api\/albums$/,
  /^\/api\/discover\/community$/,
  /^\/api\/search(?:\/.*)?$/,
  /^\/api\/playlists(?:\/\d+)?$/,
  /^\/api\/comments(?:\/\d+\/replies)?$/,
  /^\/api\/users\/\d+(?:\/playlists)?$/
];

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function isPublicContentGet(config: RetryableConfig | undefined) {
  if (config?.method?.toUpperCase() !== "GET") return false;
  const path = String(config.url || "").split("?", 1)[0];
  return PUBLIC_CONTENT_GET_PATHS.some((pattern) => pattern.test(path));
}

function removeAuthorizationHeader(config: RetryableConfig) {
  if (typeof config.headers?.delete === "function") {
    config.headers.delete("Authorization");
    return;
  }
  if (config.headers) {
    delete config.headers.Authorization;
  }
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore();
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>;
    if (payload && typeof payload.code === "number" && payload.code !== 0) {
      throw new Error(payload.message || "请求失败");
    }
    return response;
  },
  async (error: AxiosError<ApiResponse<null>>) => {
    if (axios.isCancel(error) || error.code === "ERR_CANCELED") {
      return Promise.reject(error);
    }

    const config = error.config as RetryableConfig | undefined;
    const method = config?.method?.toUpperCase();
    const status = error.response?.status;
    const canRetry = config
      && (method === "GET" || method === "HEAD")
      && (status == null || status >= 500)
      && (config.__retryCount ?? 0) < 1;
    if (canRetry) {
      config.__retryCount = (config.__retryCount ?? 0) + 1;
      await wait(600);
      return http.request(config);
    }

    const auth = useAuthStore();
    const ui = useUiStore();
    const url = String(error.config?.url || "");
    const message = status === 502 || status === 503 || status === 504
      ? "服务暂时不可用，请稍后重试"
      : error.response?.data?.message || error.message || "网络请求失败";

    if (status === 401 && config && isPublicContentGet(config) && !config.__retriedWithoutAuth) {
      auth.clearSession();
      config.__retriedWithoutAuth = true;
      removeAuthorizationHeader(config);
      return http.request(config);
    } else if (status === 401 && url.startsWith("/api/auth/")) {
      ui.toast(message);
    } else if (status === 401) {
      auth.clearSession();
      ui.toast("请先登录后继续操作");
    } else if (status === 403) {
      ui.toast("当前账号没有权限执行该操作");
    } else if (status === 404 && url.includes("/api/admin/lyrics/lddc") && message === "资源不存在") {
      ui.toast("后端 LDDC 接口未加载，请重启后端服务后再匹配歌词");
    } else {
      ui.toast(message);
    }
    return Promise.reject(error);
  }
);

export async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await request;
  return response.data.data;
}
