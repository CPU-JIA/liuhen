import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
  type Mock,
} from "vitest";
import EditorView from "./EditorView.vue";
import { api } from "../api";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));

// api.ts 会经 router.ts 用到 createRouter，所以只桩 useRouter，其余保留真实现
vi.mock("vue-router", async () => {
  const actual = await vi.importActual<typeof import("vue-router")>("vue-router");
  return { ...actual, useRouter: () => ({ push }) };
});

vi.mock("../api", async () => {
  const actual = await vi.importActual<typeof import("../api")>("../api");
  return {
    ...actual,
    api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  };
});

const mocked = api as unknown as {
  get: Mock;
  post: Mock;
  put: Mock;
  delete: Mock;
};

const ASSIGNMENT = "5";
const WORK_ID = 7;
const CACHE_KEY = `liuhen.pending.${WORK_ID}`;

interface Server {
  policyStatus: 200 | 204;
  acked: boolean;
  workStatus: "DRAFT" | "SUBMITTED";
  text: string;
}

/** 按 URL 派发桩响应，模拟后端。 */
function serve(server: Partial<Server> = {}) {
  const s: Server = {
    policyStatus: 200,
    acked: true,
    workStatus: "DRAFT",
    text: "",
    ...server,
  };
  mocked.get.mockImplementation((url: string) => {
    if (url === `/assignments/${ASSIGNMENT}/policy`) {
      return s.policyStatus === 204
        ? Promise.resolve({ status: 204, data: "" })
        : Promise.resolve({
            status: 200,
            data: {
              versionId: 1,
              versionNo: 3,
              tier: "DECLARE",
              gradingNote: null,
              scenes: [
                { name: "查资料", allowed: true },
                { name: "生成正文", allowed: false },
              ],
              acked: s.acked,
              updatedAt: "2026-09-18T09:00:00",
            },
          });
    }
    if (url === `/works/${WORK_ID}/registrations`)
      return Promise.resolve({ data: [] });
    if (url === "/ai-tools")
      return Promise.resolve({ data: [{ id: 1, name: "ChatGPT" }] });
    return Promise.reject(new Error("未桩的 GET " + url));
  });
  mocked.post.mockImplementation((url: string) => {
    if (url === `/assignments/${ASSIGNMENT}/policy/ack`)
      return Promise.resolve({ data: { ok: true } });
    if (url === `/assignments/${ASSIGNMENT}/work`)
      return Promise.resolve({
        data: {
          workId: WORK_ID,
          status: s.workStatus,
          text: s.text,
          charCount: s.text.length,
          lastSavedAt: "",
        },
      });
    return Promise.reject(new Error("未桩的 POST " + url));
  });
  mocked.put.mockResolvedValue({
    data: { savedAt: "2026-09-18T10:20:30", charCount: 0 },
  });
}

function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

let wrapper: VueWrapper | null = null;

async function open(server: Partial<Server> = {}) {
  serve(server);
  wrapper = mount(EditorView, {
    props: { assignmentId: ASSIGNMENT },
    global: { stubs: { RouterLink: true } },
    attachTo: document.body,
  });
  await flushPromises();
  return wrapper;
}

function label(w: VueWrapper) {
  return w.find(".card .muted").text();
}

function textarea(w: VueWrapper) {
  return w.find("textarea.editor");
}

function paste(w: VueWrapper, pasted: string) {
  // jsdom 没有 ClipboardEvent，用普通 Event 挂一个 clipboardData 顶上
  const ev = new Event("paste", { bubbles: true, cancelable: true });
  Object.defineProperty(ev, "clipboardData", {
    value: { getData: () => pasted },
  });
  textarea(w).element.dispatchEvent(ev);
}

function putCalls() {
  return mocked.put.mock.calls.filter((c) => c[0] === `/works/${WORK_ID}/text`);
}

beforeEach(() => {
  localStorage.clear();
  vi.useFakeTimers({
    toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval"],
  });
  mocked.get.mockReset();
  mocked.post.mockReset();
  mocked.put.mockReset();
  mocked.delete.mockReset();
  push.mockReset();
});

afterEach(() => {
  wrapper?.unmount();
  wrapper = null;
  vi.useRealTimers();
});

describe("规则页闸门", () => {
  it("AC-RULE-02-1 未确认规则时先显示规则页，编辑器不出现", async () => {
    const w = await open({ acked: false });
    expect(w.find(".gate").exists()).toBe(true);
    expect(w.text()).toContain("本作业的 AI 使用规则（第 3 版）");
    expect(w.text()).toContain("可以使用，但需要声明");
    expect(textarea(w).exists()).toBe(false);
    expect(mocked.post).not.toHaveBeenCalled();
  });

  it("AC-RULE-02-2 点我已阅读后记录确认并进入编辑器", async () => {
    const w = await open({ acked: false });
    await w.find(".gate button.primary").trigger("click");
    await flushPromises();
    expect(mocked.post).toHaveBeenCalledWith(
      `/assignments/${ASSIGNMENT}/policy/ack`,
    );
    expect(mocked.post).toHaveBeenCalledWith(`/assignments/${ASSIGNMENT}/work`);
    expect(w.find(".gate").exists()).toBe(false);
    expect(textarea(w).exists()).toBe(true);
  });

  it("没有配置规则（204）时直接进入编辑器", async () => {
    const w = await open({ policyStatus: 204 });
    expect(textarea(w).exists()).toBe(true);
    expect(mocked.post).not.toHaveBeenCalledWith(
      `/assignments/${ASSIGNMENT}/policy/ack`,
    );
    expect(mocked.post).toHaveBeenCalledWith(`/assignments/${ASSIGNMENT}/work`);
  });
});

describe("自动保存状态机", () => {
  it("AC-WRK-01-1 停止输入 5 秒后自动保存并显示已保存时间", async () => {
    const w = await open();
    expect(label(w)).toBe("尚未保存");
    await textarea(w).setValue("第一段。");
    expect(label(w)).toBe("有未保存的修改，停止输入 5 秒后自动保存");
    expect(localStorage.getItem(CACHE_KEY)).toBe("第一段。");
    expect(putCalls()).toHaveLength(0);

    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(putCalls()).toEqual([[`/works/${WORK_ID}/text`, { text: "第一段。" }]]);
    expect(label(w)).toBe("已保存 10:20:30");
    expect(localStorage.getItem(CACHE_KEY)).toBeNull();
  });

  it("5 秒内连续输入只保存一次，发送的是最新文本", async () => {
    const w = await open();
    await textarea(w).setValue("a");
    await vi.advanceTimersByTimeAsync(3000);
    await textarea(w).setValue("ab");
    await vi.advanceTimersByTimeAsync(3000);
    expect(putCalls()).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(putCalls()).toEqual([[`/works/${WORK_ID}/text`, { text: "ab" }]]);
  });

  it("AC-WRK-01-3 断网时内容留在本地，恢复后每 10 秒补传", async () => {
    const w = await open();
    mocked.put.mockRejectedValueOnce({ message: "Network Error" });
    await textarea(w).setValue("断网前写的");
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(putCalls()).toHaveLength(1);
    expect(label(w)).toBe("网络不可用，内容已在本地保留，恢复后自动补传");
    expect(localStorage.getItem(CACHE_KEY)).toBe("断网前写的");

    await vi.advanceTimersByTimeAsync(10000);
    await flushPromises();
    expect(putCalls()).toHaveLength(2);
    expect(putCalls()[1]).toEqual([`/works/${WORK_ID}/text`, { text: "断网前写的" }]);
    expect(label(w)).toBe("已保存 10:20:30");
    expect(localStorage.getItem(CACHE_KEY)).toBeNull();

    await vi.advanceTimersByTimeAsync(20000);
    expect(putCalls()).toHaveLength(2);
  });

  it("业务错误只提示不重试", async () => {
    const w = await open();
    mocked.put.mockRejectedValueOnce({
      response: { status: 409, data: { message: "已提交的作业不能再修改" } },
    });
    await textarea(w).setValue("x");
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(label(w)).toBe("有未保存的修改，停止输入 5 秒后自动保存");
    expect(w.find(".error").text()).toBe("已提交的作业不能再修改");
    await vi.advanceTimersByTimeAsync(30000);
    expect(putCalls()).toHaveLength(1);
  });

  it("AC-WRK-01-2 重新打开时本地未传的内容优先于服务端", async () => {
    localStorage.setItem(CACHE_KEY, "本地更新的内容");
    const w = await open({ text: "服务端旧内容" });
    expect((textarea(w).element as HTMLTextAreaElement).value).toBe(
      "本地更新的内容",
    );
    expect(label(w)).toBe("有未保存的修改，停止输入 5 秒后自动保存");
    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(putCalls()).toEqual([
      [`/works/${WORK_ID}/text`, { text: "本地更新的内容" }],
    ]);
  });

  it("AC-DECL-01-3 已提交后编辑器只读且不再保存", async () => {
    const w = await open({ workStatus: "SUBMITTED", text: "已交的稿" });
    expect(textarea(w).attributes("readonly")).toBeDefined();
    expect(label(w)).toBe("已提交，只读");
    await textarea(w).setValue("已交的稿又改");
    await vi.advanceTimersByTimeAsync(10000);
    expect(putCalls()).toHaveLength(0);
    expect(localStorage.getItem(CACHE_KEY)).toBeNull();
  });

  it("保存请求在途时的新输入不能被成功回调抹掉（审查发现）", async () => {
    const w = await open();
    const inFlight = deferred<{ data: { savedAt: string } }>();
    mocked.put.mockReturnValueOnce(inFlight.promise);
    await textarea(w).setValue("第一版");
    await vi.advanceTimersByTimeAsync(5000);
    expect(putCalls()).toHaveLength(1);
    expect(label(w)).toBe("保存中…");

    await textarea(w).setValue("第一版加新内容");
    inFlight.resolve({ data: { savedAt: "2026-09-18T10:20:30" } });
    await flushPromises();
    expect(label(w)).toBe("有未保存的修改，停止输入 5 秒后自动保存");
    expect(localStorage.getItem(CACHE_KEY)).toBe("第一版加新内容");

    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();
    expect(putCalls()).toHaveLength(2);
    expect(putCalls()[1]).toEqual([
      `/works/${WORK_ID}/text`,
      { text: "第一版加新内容" },
    ]);
    expect(label(w)).toBe("已保存 10:20:30");
    expect(localStorage.getItem(CACHE_KEY)).toBeNull();
  });
});

describe("粘贴检测", () => {
  function servePaste(recorded: boolean) {
    const original = mocked.post.getMockImplementation()!;
    mocked.post.mockImplementation((url: string, body?: unknown) => {
      if (url === `/works/${WORK_ID}/pastes`)
        return Promise.resolve({
          data: recorded
            ? { recorded: true, pasteId: 9 }
            : { recorded: false, threshold: 100 },
        });
      return original(url, body);
    });
  }

  it("AC-WRK-03-1 粘贴超过 100 字符弹出来源选择并记录事件", async () => {
    const w = await open();
    servePaste(true);
    const pasted = "长".repeat(150);
    paste(w, pasted);
    await flushPromises();
    expect(mocked.post).toHaveBeenCalledWith(`/works/${WORK_ID}/pastes`, {
      charCount: 150,
      offsetStart: 0,
      offsetEnd: 150,
    });
    expect(w.text()).toContain("刚粘贴了 150 字符");

    const web = w.findAll(".gate button").find((b) => b.text() === "网络资料")!;
    await web.trigger("click");
    await flushPromises();
    expect(mocked.put).toHaveBeenCalledWith("/works/pastes/9/source", {
      source: "WEB",
    });
    expect(w.text()).not.toContain("刚粘贴了");
  });

  it("AC-WRK-03-3 选 AI 工具打开登记表并把粘贴内容预填到输出", async () => {
    const w = await open();
    servePaste(true);
    const pasted = "这是一段来自模型的输出。".repeat(12);
    paste(w, pasted);
    await flushPromises();
    const ai = w.findAll(".gate button").find((b) => b.text() === "AI 工具")!;
    await ai.trigger("click");
    await flushPromises();
    expect(mocked.put).not.toHaveBeenCalled();
    expect(w.text()).toContain("登记一次 AI 使用");
    const output = w.find('textarea[placeholder^="AI 输出"]');
    expect((output.element as HTMLTextAreaElement).value).toBe(pasted);
  });

  it("AC-WRK-03-5 中英混排 60 个汉字加 50 个字母按 110 字符计", async () => {
    const w = await open();
    servePaste(true);
    paste(w, "汉".repeat(60) + "a".repeat(50));
    await flushPromises();
    expect(mocked.post).toHaveBeenCalledWith(`/works/${WORK_ID}/pastes`, {
      charCount: 110,
      offsetStart: 0,
      offsetEnd: 110,
    });
  });

  it("AC-WRK-03-2 短粘贴后端不记录时不弹出", async () => {
    const w = await open();
    servePaste(false);
    paste(w, "短");
    await flushPromises();
    expect(mocked.post).toHaveBeenCalledWith(`/works/${WORK_ID}/pastes`, {
      charCount: 1,
      offsetStart: 0,
      offsetEnd: 1,
    });
    expect(w.text()).not.toContain("刚粘贴了");
  });
});
