/** 与后端 cn.liuhen.common.ForbiddenWords 保持一致。构建时脚本扫描全部 .vue/.ts 文案。 */
export const FORBIDDEN_WORDS = [
  "疑似",
  "作弊",
  "风险",
  "AI 率",
  "AI率",
  "时长",
];

export function scanForbidden(text: string): string[] {
  return FORBIDDEN_WORDS.filter((w) => text.includes(w));
}
