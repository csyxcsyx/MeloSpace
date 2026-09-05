import axios from "axios";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "@/api/http";
import { useUiStore } from "@/stores/ui";
import { useAuthStore } from "@/stores/auth";

describe("http cancellation", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it("并发网关故障只显示一条中文提示，GET 仅重试一次且保留会话", async () => {
    const ui = useUiStore();
    const auth = useAuthStore();
    auth.token = "existing-session";
    const adapter = vi.fn((config) => Promise.reject(new axios.AxiosError(
      "Request failed with status code 502", "ERR_BAD_RESPONSE", config, undefined,
      { status: 502, statusText: "Bad Gateway", headers: {}, config, data: "<html>502</html>" }
    )));
    const requests = Promise.allSettled([
      http.get("/api/songs", { adapter }),
      http.get("/api/albums", { adapter }),
      http.get("/api/artists", { adapter })
    ]);
    await vi.advanceTimersByTimeAsync(600);
    expect((await requests).every((result) => result.status === "rejected")).toBe(true);
    expect(adapter).toHaveBeenCalledTimes(6);
    expect(ui.toasts.map((item) => item.message)).toEqual(["服务暂时不可用，请稍后重试"]);
    expect(auth.token).toBe("existing-session");
    await vi.advanceTimersByTimeAsync(3200);
    expect(ui.toasts).toHaveLength(0);
    ui.toast("服务暂时不可用，请稍后重试");
    expect(ui.toasts).toHaveLength(1);
  });

  it.each([502, 503, 504])("登录遇到 %i 不自动重复提交", async (status) => {
    const adapter = vi.fn((config) => Promise.reject(new axios.AxiosError(
      `Request failed with status code ${status}`, "ERR_BAD_RESPONSE", config, undefined,
      { status, statusText: "Unavailable", headers: {}, config, data: "<html>Error</html>" }
    )));
    await expect(http.post("/api/auth/login", {}, { adapter })).rejects.toMatchObject({
      response: { status }
    });
    expect(adapter).toHaveBeenCalledTimes(1);
    expect(useUiStore().toasts[0]?.message).toBe("服务暂时不可用，请稍后重试");
  });

  it("取消的 GET 请求不会重试或弹出错误 Toast", async () => {
    const ui = useUiStore();
    const toast = vi.spyOn(ui, "toast");
    const adapter = vi.fn((config) =>
      Promise.reject(new axios.CanceledError("canceled", config))
    );

    await expect(http.get("/api/search", { adapter })).rejects.toMatchObject({
      code: "ERR_CANCELED"
    });

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(toast).not.toHaveBeenCalled();
  });
});
