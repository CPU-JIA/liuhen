import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi, type Mock } from "vitest";
import TimelineView from "./TimelineView.vue";
import { api } from "../api";

vi.mock("../api", async () => {
  const actual = await vi.importActual<typeof import("../api")>("../api");
  return { ...actual, api: { get: vi.fn() } };
});

const mocked = api as unknown as { get: Mock };

// 每条用例都自己设桩响应，不在 beforeEach 里 mockReset：vitest 3 的 mockReset 会让第一条用例的
// 拒绝值变成未处理的 rejection（彩排时踩到，记在实验 7 报告）
describe("时间线页", () => {
  it("AC-PERM-01-1 没有权限时页面提示无权限，而不是显示空态", async () => {
    mocked.get.mockRejectedValue({
      response: { status: 403, data: { code: 403, message: "没有权限" } },
    });
    const w = mount(TimelineView, { props: { workId: "9" } });
    await flushPromises();
    expect(w.text()).toContain("没有权限");
    expect(w.text()).not.toContain("还没有记录");
  });

  it("AC-WRK-05-3 新作业没有记录时只显示空态一句话", async () => {
    mocked.get.mockResolvedValue({ data: [] });
    const w = mount(TimelineView, { props: { workId: "9" } });
    await flushPromises();
    expect(w.text()).toContain("还没有记录，开始写作后自动生成");
    expect(w.find("p.error").exists()).toBe(false);
  });

  it("AC-TCH-01-3 只有版本、粘贴、登记三类条目，默认选中最后两版对比", async () => {
    mocked.get.mockResolvedValue({
      data: [
        {
          type: "SNAPSHOT",
          at: "2026-09-18T10:00:00",
          data: { seqNo: 1, trigger: "TIME", charCount: 100, charDelta: 100 },
        },
        {
          type: "PASTE",
          at: "2026-09-18T10:01:00",
          data: { charCount: 120, source: "PENDING" },
        },
        {
          type: "REGISTRATION",
          at: "2026-09-18T10:02:00",
          data: {
            stage: "改语法",
            adoption: "修改后采用",
            toolId: 1,
            status: "ACTIVE",
          },
        },
        {
          type: "SNAPSHOT",
          at: "2026-09-18T10:12:00",
          data: {
            seqNo: 2,
            trigger: "EDIT_VOLUME",
            charCount: 700,
            charDelta: 600,
          },
        },
      ],
    });
    const w = mount(TimelineView, { props: { workId: "9" } });
    await flushPromises();
    const tags = w.findAll("ul.timeline .tag").map((t) => t.text());
    expect(tags).toEqual(["版本", "粘贴", "登记", "版本"]);
    expect(w.text()).toContain("来源：来源待定");
    const selects = w.findAll("select");
    expect((selects[0].element as HTMLSelectElement).value).toBe("1");
    expect((selects[1].element as HTMLSelectElement).value).toBe("2");
  });
});
