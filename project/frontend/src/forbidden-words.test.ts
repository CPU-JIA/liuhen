import { describe, expect, it } from "vitest";
import { FORBIDDEN_WORDS, scanForbidden } from "./forbidden-words";

describe("禁用词扫描（AC-TCH-03-1、AC-TCH-03-3）", () => {
  it("命中时按清单顺序返回出现过的词", () => {
    expect(scanForbidden("疑似作弊？AI 率 87%")).toEqual([
      "疑似",
      "作弊",
      "AI 率",
    ]);
  });

  it("没有禁用词时返回空数组", () => {
    expect(scanForbidden("这份作业有 12 个版本，最后一版在截止前提交")).toEqual(
      [],
    );
  });

  it("清单与后端 ForbiddenWords.WORDS 一致", () => {
    expect(FORBIDDEN_WORDS).toEqual([
      "疑似",
      "作弊",
      "风险",
      "AI 率",
      "AI率",
      "时长",
    ]);
  });
});
