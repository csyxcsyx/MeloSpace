import axios from "axios";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "@/api/http";
import { useUiStore } from "@/stores/ui";

describe("http cancellation", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
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
