import fs from "node:fs";
import path from "node:path";

// 与 src/forbidden-words.ts 同一清单；脚本独立不引 TS，方便 CI 直接跑
const WORDS = ["疑似", "作弊", "风险", "AI 率", "AI率", "时长"];
const ALLOW_FILES = new Set(["forbidden-words.ts"]);

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p, out);
    // 测试文件不是用户会看到的文案，而且禁用词清单自己的测试必须写这些词，所以不扫
    else if (
      /\.(vue|ts)$/.test(entry.name) &&
      !/\.test\.ts$/.test(entry.name) &&
      !ALLOW_FILES.has(entry.name)
    )
      out.push(p);
  }
  return out;
}

const hits = [];
for (const file of walk(path.resolve("src"))) {
  const lines = fs.readFileSync(file, "utf8").split("\n");
  lines.forEach((line, i) => {
    for (const w of WORDS)
      if (line.includes(w))
        hits.push(`${path.relative(".", file)}:${i + 1} 含「${w}」`);
  });
}
if (hits.length) {
  console.error("前端文案出现禁用词：\n" + hits.join("\n"));
  process.exit(1);
}
console.log("禁用词扫描通过");
