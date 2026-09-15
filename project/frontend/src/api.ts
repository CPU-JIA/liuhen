import axios from "axios";
import { router } from "./router";

export const TOKEN_KEY = "liuhen.token";
export const USER_KEY = "liuhen.user";

export interface Me {
  id: number;
  loginNo: string;
  name: string;
  role: "STUDENT" | "TEACHER" | "REVIEWER" | "AFFAIRS" | "ADMIN";
  mustChangePassword: boolean;
}

export const api = axios.create({ baseURL: "/api", timeout: 15000 });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      if (router.currentRoute.value.name !== "login")
        router.push({ name: "login" });
    }
    return Promise.reject(err);
  },
);

/** 后端统一错误体是 {code, message}；网络错误给一句人话。 */
export function errorMessage(err: unknown): string {
  const e = err as {
    response?: { data?: { message?: string } };
    message?: string;
  };
  return (
    e.response?.data?.message ??
    (e.message?.includes("Network") ? "网络不可用，稍后自动重试" : "操作失败")
  );
}

export function currentUser(): Me | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as Me) : null;
}
