import { beforeEach, describe, expect, it } from "vitest";
import { TOKEN_KEY, USER_KEY } from "./api";
import { router } from "./router";

function loginAs(mustChangePassword: boolean) {
  localStorage.setItem(TOKEN_KEY, "t");
  localStorage.setItem(
    USER_KEY,
    JSON.stringify({
      id: 1,
      loginNo: "24020110",
      name: "张三",
      role: "STUDENT",
      mustChangePassword,
    }),
  );
}

describe("路由守卫", () => {
  beforeEach(() => localStorage.clear());

  it("未登录访问课程页被送到登录页", async () => {
    await router.push({ name: "courses" });
    await router.isReady();
    expect(router.currentRoute.value.name).toBe("login");
  });

  it("AC-ACC-04-2 首次登录未改密时任何页面都先去改密页", async () => {
    loginAs(true);
    await router.push({ name: "courses" });
    expect(router.currentRoute.value.name).toBe("password");
  });

  it("已登录且不需改密时正常放行", async () => {
    loginAs(false);
    await router.push({ name: "assignments", params: { courseId: "3" } });
    expect(router.currentRoute.value.name).toBe("assignments");
    expect(router.currentRoute.value.params.courseId).toBe("3");
  });
});
