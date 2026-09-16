#!/usr/bin/env bash
# Sprint 1 冒烟脚本：跑通主线并验证权限与边界。依赖 curl 与 python。
# 用法：BASE=http://127.0.0.1:18080 PWD_SEED=Liuhen@2026 bash scripts/smoke-sprint1.sh
# 需要后端以 dev 配置启动（种子账号 T0001 / J0001），且数据库为空库。
set -u
BASE="${BASE:-http://127.0.0.1:8080}"
SEED="${PWD_SEED:-Liuhen@2026}"
pass=0; fail=0
ok()  { pass=$((pass+1)); echo "  [通过] $1"; }
bad() { fail=$((fail+1)); echo "  [失败] $1 -> $2"; }
# 从 stdin 的 JSON 里取字段，路径形如 token 或 items.0.tool；不使用 eval
jget() { python -c '
import sys, json
d = json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d = d[int(k)] if isinstance(d, list) else d[k]
print(d)' "$1"; }
jlen() { python -c 'import sys, json; print(len(json.load(sys.stdin)))'; }
req() { # method path token [body]。请求体先落文件再发，避免 Windows 下命令行参数经代码页转换把中文弄坏
  local m=$1 p=$2 t=$3 b=${4:-}
  if [ -n "$b" ]; then
    printf '%s' "$b" > /tmp/req-body.json
    curl -s -w '\n%{http_code}' -X "$m" "$BASE$p" -H "Authorization: Bearer $t" -H 'Content-Type: application/json; charset=utf-8' --data-binary @/tmp/req-body.json
  else
    curl -s -w '\n%{http_code}' -X "$m" "$BASE$p" -H "Authorization: Bearer $t"
  fi
}
code() { tail -n1 <<<"$1"; }
body() { sed '$d' <<<"$1"; }

echo "1 教师登录"
r=$(req POST /api/auth/login "" "{\"loginNo\":\"T0001\",\"password\":\"$SEED\"}")
[ "$(code "$r")" = 200 ] && ok "T0001 登录" || bad "T0001 登录" "$(body "$r")"
T=$(body "$r" | jget token)

echo "2 建课、发作业、导名单、配规则"
r=$(req POST /api/courses "$T" '{"name":"软件工程"}'); [ "$(code "$r")" = 200 ] && ok "建课" || bad "建课" "$(body "$r")"
CID=$(body "$r" | jget id); CODE=$(body "$r" | jget joinCode)
DL=$(python -c "import datetime;print((datetime.datetime.now()+datetime.timedelta(days=7)).strftime('%Y-%m-%dT%H:%M:00'))")
r=$(req POST "/api/courses/$CID/assignments" "$T" "{\"title\":\"实验 1 报告\",\"deadline\":\"$DL\"}"); [ "$(code "$r")" = 200 ] && ok "发作业" || bad "发作业" "$(body "$r")"
AID=$(body "$r" | jget id)
r=$(req POST "/api/courses/$CID/assignments" "$T" '{"title":"过去","deadline":"2020-01-01T00:00:00"}'); [ "$(code "$r")" = 400 ] && ok "截止时间早于现在被拒 AC-ACC-01-3" || bad "截止早于现在" "$(code "$r")"
printf '学号,姓名\n24020110,周浩然\n24020111,杨子航\n' > /tmp/roster.csv
r=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/courses/$CID/roster" -H "Authorization: Bearer $T" -F "file=@/tmp/roster.csv"); [ "$(code "$r")" = 200 ] && ok "导入名单 $(body "$r" | jget imported) 人" || bad "导入名单" "$(body "$r")"
printf '学号,姓名\n24020110,周浩然\n24020110,重复\n' > /tmp/dup.csv
r=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/courses/$CID/roster" -H "Authorization: Bearer $T" -F "file=@/tmp/dup.csv"); [ "$(code "$r")" = 400 ] && ok "重复学号整份拒绝 AC-ACC-04-3" || bad "重复学号" "$(code "$r")"
r=$(req PUT "/api/courses/$CID/policy" "$T" '{"tier":"DECLARE","gradingNote":"如实声明不扣分","scenes":[{"name":"改语法","allowed":true},{"name":"生成大纲","allowed":true},{"name":"生成正文","allowed":false}]}')
[ "$(code "$r")" = 200 ] && ok "发布规则 v$(body "$r" | jget versionNo)" || bad "发布规则" "$(body "$r")"
r=$(req PUT "/api/courses/$CID/policy" "$T" '{"gradingNote":"x"}'); [ "$(code "$r")" = 400 ] && ok "不选档位被拒 AC-RULE-01-4" || bad "不选档位" "$(code "$r")"

echo "3 学生首次登录改密、进课程、看规则、确认"
r=$(req POST /api/auth/login "" '{"loginNo":"24020110","password":"020110"}'); [ "$(code "$r")" = 200 ] && ok "学生初始密码登录" || bad "学生登录" "$(body "$r")"
S=$(body "$r" | jget token); MUST=$(body "$r" | jget mustChangePassword)
[ "$MUST" = "True" ] && ok "首次登录要求改密 AC-ACC-04-2" || bad "首次改密标记" "$MUST"
r=$(req POST /api/auth/password "$S" '{"oldPassword":"020110","newPassword":"Student@2026"}'); [ "$(code "$r")" = 200 ] && ok "改密" || bad "改密" "$(body "$r")"
r=$(req POST /api/auth/login "" '{"loginNo":"24020110","password":"Student@2026"}'); [ "$(code "$r")" = 200 ] && [ "$(body "$r" | jget mustChangePassword)" = "False" ] && ok "新密码能登录且不再要求改密 AC-ACC-04-2" || bad "新密码登录" "$(body "$r")"
S=$(body "$r" | jget token)
r=$(req POST /api/auth/login "" '{"loginNo":"24020110","password":"020110"}'); [ "$(code "$r")" = 400 ] && ok "旧密码已失效" || bad "旧密码仍可登录" "$(code "$r")"
r=$(req POST /api/courses/join "$S" "{\"joinCode\":\"$CODE\"}"); [ "$(code "$r")" = 409 ] && ok "名单导入后再输码提示已在课程中 AC-ACC-02-3" || bad "重复加入" "$(code "$r")"
r=$(req POST /api/courses/join "$S" "{\"joinCode\":\"$(echo "$CODE" | tr 'A-Z' 'a-z')\"}"); [ "$(code "$r")" = 409 ] && ok "课程码不区分大小写 AC-ACC-02-4" || bad "大小写" "$(code "$r")"
r=$(req POST "/api/assignments/$AID/work" "$S"); [ "$(code "$r")" = 409 ] && ok "未确认规则不能进编辑器 AC-RULE-02-3" || bad "未确认进编辑" "$(code "$r")"
r=$(req GET "/api/assignments/$AID/policy" "$S"); [ "$(code "$r")" = 200 ] && ok "规则页数据 tier=$(body "$r" | jget tier) acked=$(body "$r" | jget acked)" || bad "规则页" "$(code "$r")"
r=$(req POST "/api/assignments/$AID/policy/ack" "$S"); [ "$(code "$r")" = 200 ] && ok "确认阅读 AC-RULE-02-2" || bad "确认阅读" "$(code "$r")"
r=$(req POST "/api/assignments/$AID/work" "$S"); [ "$(code "$r")" = 200 ] && ok "进入作业稿" || bad "进入作业" "$(body "$r")"
WID=$(body "$r" | jget workId)

echo "4 写作、快照、粘贴、登记"
r=$(req PUT "/api/works/$WID/text" "$S" '{"text":"第一段。"}'); [ "$(code "$r")" = 200 ] && ok "首次保存 snapshot=$(body "$r" | jget snapshotCreated)" || bad "保存" "$(body "$r")"
LONG=$(python -c "print('第二段内容。'*120)")
r=$(req PUT "/api/works/$WID/text" "$S" "{\"text\":\"第一段。$LONG\"}"); [ "$(body "$r" | jget snapshotCreated)" = "True" ] && ok "改动超 500 字符触发快照 AC-WRK-02-2" || bad "改动量触发" "$(body "$r")"
python -c "import json; print(json.dumps({'text': '字'*50001}, ensure_ascii=False))" > /tmp/big.json
r=$(curl -s -w '\n%{http_code}' -X PUT "$BASE/api/works/$WID/text" -H "Authorization: Bearer $S" -H 'Content-Type: application/json' --data-binary @/tmp/big.json); [ "$(code "$r")" = 400 ] && ok "超 50000 字符被拒 AC-WRK-01-4" || bad "上限" "$(code "$r")"
r=$(req POST "/api/works/$WID/pastes" "$S" '{"charCount":80,"offsetStart":0,"offsetEnd":80}'); [ "$(body "$r" | jget recorded)" = "False" ] && ok "80 字符粘贴不记录 AC-WRK-03-2" || bad "短粘贴" "$(body "$r")"
r=$(req POST "/api/works/$WID/pastes" "$S" '{"charCount":300,"offsetStart":4,"offsetEnd":304}'); [ "$(body "$r" | jget source)" = "PENDING" ] && ok "300 字符粘贴记录为 PENDING AC-WRK-03-4" || bad "长粘贴" "$(body "$r")"
PID=$(body "$r" | jget pasteId)
r=$(req POST "/api/works/$WID/submit" "$S" '{"declareNotUsed":false}'); [ "$(code "$r")" = 409 ] && ok "有待定粘贴不能提交 AC-DECL-01-2" || bad "待定阻断" "$(code "$r")"
r=$(req POST "/api/works/$WID/registrations" "$S" '{"toolId":1,"stage":"BODY","purpose":"让它写了第二段","adoption":"MODIFIED","outputText":"第二段内容。"}'); [ "$(code "$r")" = 200 ] && ok "登记（生成正文，规则未允许）" || bad "登记" "$(body "$r")"
RID=$(body "$r" | jget id)
r=$(req POST "/api/works/$WID/registrations" "$S" '{"stage":"BODY","purpose":"x","adoption":"MODIFIED"}'); [ "$(code "$r")" = 400 ] && ok "无工具名被拒" || bad "无工具" "$(code "$r")"
r=$(req PUT "/api/works/pastes/$PID/source" "$S" "{\"source\":\"AI_TOOL\",\"registrationId\":$RID}"); [ "$(code "$r")" = 200 ] && ok "粘贴来源补填为 AI 工具" || bad "补填" "$(body "$r")"
r=$(req PUT "/api/works/$WID/registrations/$RID" "$S" '{"toolId":1,"stage":"POLISH","purpose":"改语法","adoption":"MODIFIED"}'); [ "$(code "$r")" = 200 ] && ok "修改登记 = 新增并作废旧条 AC-REG-02-1" || bad "修改登记" "$(body "$r")"
r=$(req GET "/api/works/$WID/registrations" "$S"); N=$(body "$r" | jlen); [ "$N" = 2 ] && ok "登记列表 2 条（1 作废 1 有效）" || bad "登记列表" "$N"

echo "5 提交与声明"
r=$(req POST "/api/works/$WID/submit" "$S" '{"declareNotUsed":false}'); [ "$(code "$r")" = 200 ] && ok "提交生成声明 kind=$(body "$r" | jget kind) late=$(body "$r" | jget late)" || bad "提交" "$(body "$r")"
r=$(req PUT "/api/works/$WID/text" "$S" '{"text":"改"}'); [ "$(code "$r")" = 409 ] && ok "提交后不能再改 AC-DECL-01-3" || bad "提交后修改" "$(code "$r")"
r=$(req GET "/api/works/$WID/declaration" "$S")
if [ "$(code "$r")" = 200 ]; then
  ITEMS=$(body "$r" | jget contentJson | python -c 'import sys,json; d=json.loads(sys.stdin.read()); print(len(d["items"]), "exceeded", d["exceededCount"])')
  ok "学生看声明，条目 $ITEMS"
else bad "看声明" "$(code "$r")"; fi

echo "6 时间线与差异"
r=$(req GET "/api/works/$WID/timeline" "$S"); [ "$(code "$r")" = 200 ] && ok "学生时间线 $(body "$r" | jlen) 条" || bad "学生时间线" "$(code "$r")"
r2=$(req GET "/api/works/$WID/timeline" "$T"); [ "$(body "$r" | jlen)" = "$(body "$r2" | jlen)" ] && ok "教师与学生时间线条数一致 AC-WRK-05-2" || bad "两端一致" "$(body "$r" | jlen) vs $(body "$r2" | jlen)"
r=$(req GET "/api/works/$WID/diff?from=1&to=2" "$T"); [ "$(code "$r")" = 200 ] && ok "两版差异 $(body "$r" | jlen) 行" || bad "差异" "$(body "$r")"
r=$(req GET "/api/works/$WID/diff?from=1&to=1" "$T"); [ "$(code "$r")" = 400 ] && ok "同版本对比被拒 AC-TCH-02-3" || bad "同版本" "$(code "$r")"
r=$(req GET "/api/works/$WID/snapshots/2" "$T"); [ "$(code "$r")" = 200 ] && ok "还原版本 2 全文 $(body "$r" | jget text | wc -m) 字符" || bad "还原" "$(code "$r")"

echo "7 权限"
r=$(req POST /api/auth/login "" '{"loginNo":"24020111","password":"020111"}'); S2=$(body "$r" | jget token)
r=$(req GET "/api/works/$WID/timeline" "$S2"); [ "$(code "$r")" = 403 ] && ok "学生看别人 403 AC-PERM-01-1" || bad "学生越权" "$(code "$r")"
r=$(req POST /api/auth/login "" "{\"loginNo\":\"J0001\",\"password\":\"$SEED\"}"); J=$(body "$r" | jget token)
r=$(req GET "/api/works/$WID/timeline" "$J"); [ "$(code "$r")" = 403 ] && ok "教务看正文 403 AC-PERM-01-4" || bad "教务越权" "$(code "$r")"
r=$(req GET "/api/works/$WID/timeline" ""); [ "$(code "$r")" = 401 ] && ok "未登录 401" || bad "未登录" "$(code "$r")"
r=$(req POST /api/courses "$S" '{"name":"学生建课"}'); [ "$(code "$r")" = 403 ] && ok "学生建课 403" || bad "学生建课" "$(code "$r")"

echo "8 锁定"
for i in 1 2 3 4 5; do req POST /api/auth/login "" '{"loginNo":"24020111","password":"wrong"}' >/dev/null; done
r=$(req POST /api/auth/login "" '{"loginNo":"24020111","password":"020111"}'); body "$r" | grep -q "锁定" && ok "5 次错误后锁定 AC-ACC-04-4" || bad "锁定" "$(body "$r")"

echo; echo "通过 $pass，失败 $fail"
[ "$fail" = 0 ]
