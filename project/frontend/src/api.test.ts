import { beforeEach, describe, expect, it } from "vitest";
import { TOKEN_KEY, USER_KEY, api, currentUser, errorMessage } from "./api";
import { router } from "./router";

describe("errorMessage：后端错误体与网络错误的文案", () => {
  it("有后端 message 时原样显示", () => {
    expect(
      errorMessage({ response: { data: { message: "已提交的作业不能再修改" } } }),
    ).toBe("已提交的作业不能再修改");
  });

  it("AC-WRK-01-3 没有响应且是网络错误时提示稍后自动重试", () => {
    expect(errorMessage({ message: "Network Error" })).toBe(
      "网络不可用，稍后自动重试",
    );
  });

  it("其他未知错误统一为操作失败", () => {
    expect(errorMessage(new Error("boom"))).toBe("操作失败");
    expect(errorMessage({})).toBe("操作失败");
  });
});

describe("currentUser：从 localStorage 读登录用户", () => {
  beforeEach(() => localStorage.clear());

  it("没有登录信息时返回 null", () => {
    expect(currentUser()).toBeNull();
  });

  it("AC-ACC-04-2 有登录信息时解析出角色与首次改密标记", () => {
    localStorage.setItem(
      USER_KEY,
      JSON.stringify({
        id: 1,
        loginNo: "24020110",
        name: "张三",
        role: "STUDENT",
        mustChangePassword: true,
      }),
    );
    expect(currentUser()).toMatchObject({
      role: "STUDENT",
      mustChangePassword: true,
    });
  });
});

describe("401 拦截器", () => {
  beforeEach(() => localStorage.clear());

  it("收到 401 时清掉令牌与用户信息并回到登录页", async () => {
    localStorage.setItem(TOKEN_KEY, "t");
    localStorage.setItem(USER_KEY, "{}");
    const handlers = (
      api.interceptors.response as unknown as {
        handlers: { rejected: (err: unknown) => Promise<never> }[];
      }
    ).handlers;
    await expect(
      handlers[0].rejected({ response: { status: 401 } }),
    ).rejects.toBeDefined();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(USER_KEY)).toBeNull();
    await router.isReady();
    expect(router.currentRoute.value.name).toBe("login");
  });
});
