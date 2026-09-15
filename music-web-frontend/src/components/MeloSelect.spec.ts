import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import MeloSelect from "@/components/MeloSelect.vue";

const options = [
  { value: 0, label: "全部" },
  { value: 12, label: "十二首" },
  { value: "newest", label: "最近更新" },
  { value: "disabled", label: "不可选择", disabled: true }
];

function mountSelect(modelValue: string | number = 0) {
  return mount(MeloSelect, {
    attachTo: document.body,
    props: { modelValue, options, label: "排序" }
  });
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("MeloSelect", () => {
  it("opens a branded listbox and preserves numeric option values", async () => {
    const wrapper = mountSelect();
    await wrapper.get('[role="combobox"]').trigger("click");

    expect(document.body.querySelector('[role="listbox"]')).not.toBeNull();
    const optionButtons = [...document.body.querySelectorAll<HTMLButtonElement>('[role="option"]')];
    optionButtons[1].click();
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([12]);
    expect(document.body.querySelector('[role="listbox"]')).toBeNull();
  });

  it("supports arrow navigation, selection, and escape", async () => {
    const wrapper = mountSelect("newest");
    const trigger = wrapper.get('[role="combobox"]');
    await trigger.trigger("keydown", { key: "ArrowDown" });
    expect(trigger.attributes("aria-expanded")).toBe("true");
    await trigger.trigger("keydown", { key: "Home" });
    await trigger.trigger("keydown", { key: "Enter" });
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([0]);

    await trigger.trigger("keydown", { key: "ArrowDown" });
    await trigger.trigger("keydown", { key: "Escape" });
    expect(trigger.attributes("aria-expanded")).toBe("false");
  });

  it("does not open when disabled", async () => {
    const wrapper = mount(MeloSelect, {
      props: { modelValue: 0, options, label: "排序", disabled: true }
    });
    await wrapper.get('[role="combobox"]').trigger("click");
    expect(wrapper.get('[role="combobox"]').attributes("aria-expanded")).toBe("false");
  });
});
