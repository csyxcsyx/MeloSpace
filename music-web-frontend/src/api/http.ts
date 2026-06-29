import axios, { AxiosError } from "axios";
import type { ApiResponse } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";

export const http = axios.create({
  baseURL: "",
  timeout: 15000
});

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
  (error: AxiosError<ApiResponse<null>>) => {
    const auth = useAuthStore();
    const ui = useUiStore();
    const status = error.response?.status;
    const message = error.response?.data?.message || error.message || "网络请求失败";

    if (status === 401) {
      auth.clearSession();
      ui.toast("请先登录后继续操作");
    } else if (status === 403) {
      ui.toast("当前账号没有权限执行该操作");
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
