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
};

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
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
    const message = error.response?.data?.message || error.message || "网络请求失败";

    if (status === 401 && url.startsWith("/api/auth/")) {
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
