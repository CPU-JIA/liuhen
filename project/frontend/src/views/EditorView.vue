<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { api, errorMessage } from "../api";

const props = defineProps<{ assignmentId: string }>();
const router = useRouter();

interface PolicyView {
  versionId: number;
  versionNo: number;
  tier: "FORBID" | "DECLARE" | "ENCOURAGE";
  gradingNote: string | null;
  scenes: { name: string; allowed: boolean }[];
  acked: boolean;
  updatedAt: string;
}
interface Tool {
  id: number;
  name: string;
}
interface Registration {
  id: number;
  toolId: number | null;
  toolNameCustom: string | null;
  toolVersion: string | null;
  stage: string;
  purpose: string;
  adoption: string;
  status: "ACTIVE" | "VOIDED";
  createdAt: string;
}

const AUTOSAVE_MS = 5000;
const RETRY_MS = 10000;
const TIER: Record<string, string> = {
  FORBID: "禁止使用 AI",
  DECLARE: "可以使用，但需要声明",
  ENCOURAGE: "鼓励使用，需要声明",
};
const STAGES = [
  ["RESEARCH", "查资料"],
  ["OUTLINE", "生成大纲"],
  ["BODY", "生成正文"],
  ["CODE", "生成代码框架"],
  ["DATA", "生成数据"],
  ["POLISH", "改语法"],
  ["OTHER", "其他"],
];
const ADOPTIONS = [
  ["DIRECT", "直接采用"],
  ["MODIFIED", "修改后采用"],
  ["REFERENCE", "仅作参考"],
  ["NOT_USED", "未采用"],
];
const VERIFICATIONS = [
  ["", "未填写"],
  ["SOURCE_CHECK", "核对原文"],
  ["RUN_TEST", "运行验证"],
  ["TEXTBOOK", "与教材比对"],
  ["NONE", "未核对"],
];
const SOURCES = [
  ["OWN_DOC", "自己的其他文档"],
  ["AI_TOOL", "AI 工具"],
  ["WEB", "网络资料"],
  ["OTHER", "其他"],
];

const policy = ref<PolicyView | null>(null);
const gateOpen = ref(false);
const workId = ref<number | null>(null);
const text = ref("");
const status = ref<"DRAFT" | "SUBMITTED">("DRAFT");
const savedAt = ref("");
const saveState = ref<"idle" | "dirty" | "saving" | "offline">("idle");
const error = ref("");
const editor = ref<HTMLTextAreaElement | null>(null);

const tools = ref<Tool[]>([]);
const registrations = ref<Registration[]>([]);
const activeRegs = computed(() =>
  registrations.value.filter((r) => r.status === "ACTIVE"),
);

const pasteDialog = ref<{ pasteId: number; text: string } | null>(null);
const regForm = ref<{
  open: boolean;
  forPasteId: number | null;
  toolId: number | "";
  toolNameCustom: string;
  toolVersion: string;
  stage: string;
  purpose: string;
  adoption: string;
  promptText: string;
  outputText: string;
  verification: string;
}>(emptyForm());
const pendingPastes = ref<
  { id: number; charCount: number; occurredAt: string }[]
>([]);
const declareNotUsed = ref(false);

let saveTimer: ReturnType<typeof setTimeout> | null = null;
let retryTimer: ReturnType<typeof setInterval> | null = null;

function emptyForm() {
  return {
    open: false,
    forPasteId: null as number | null,
    toolId: "" as number | "",
    toolNameCustom: "",
    toolVersion: "",
    stage: "RESEARCH",
    purpose: "",
    adoption: "REFERENCE",
    promptText: "",
    outputText: "",
    verification: "",
  };
}

function cacheKey() {
  return `liuhen.pending.${workId.value}`;
}

function codePoints(s: string) {
  return Array.from(s).length;
}

// ---------------------------------------------------------------- 进入

async function enter() {
  const { data } = await api.post(`/assignments/${props.assignmentId}/work`);
  workId.value = data.workId;
  status.value = data.status;
  text.value = data.text;
  savedAt.value = data.lastSavedAt;
  // 断网期间没传上去的内容优先（AC-WRK-01-3）
  const cached = localStorage.getItem(cacheKey());
  if (cached !== null && cached !== text.value) {
    text.value = cached;
    saveState.value = "dirty";
    scheduleSave();
  }
  await Promise.all([loadRegistrations(), loadTools()]);
}

async function init() {
  try {
    const res = await api.get<PolicyView>(
      `/assignments/${props.assignmentId}/policy`,
    );
    if (res.status === 200) {
      policy.value = res.data;
      if (!res.data.acked) {
        gateOpen.value = true;
        return;
      }
    }
    await enter();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function ack() {
  await api.post(`/assignments/${props.assignmentId}/policy/ack`);
  gateOpen.value = false;
  await enter();
}

// ---------------------------------------------------------------- 自动保存

function onInput() {
  if (status.value === "SUBMITTED") return;
  saveState.value = "dirty";
  if (workId.value) localStorage.setItem(cacheKey(), text.value);
  scheduleSave();
}

function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(save, AUTOSAVE_MS);
}

async function save() {
  if (!workId.value || saveState.value === "saving") return;
  saveState.value = "saving";
  try {
    const { data } = await api.put(`/works/${workId.value}/text`, {
      text: text.value,
    });
    savedAt.value = data.savedAt;
    saveState.value = "idle";
    localStorage.removeItem(cacheKey());
    if (retryTimer) {
      clearInterval(retryTimer);
      retryTimer = null;
    }
  } catch (e) {
    const status = (e as { response?: { status?: number } }).response?.status;
    if (status) {
      error.value = errorMessage(e);
      saveState.value = "dirty";
    } else {
      // 没有响应 = 网络断了，本地已缓存，定时补传
      saveState.value = "offline";
      if (!retryTimer) retryTimer = setInterval(save, RETRY_MS);
    }
  }
}

const saveLabel = computed(() => {
  if (status.value === "SUBMITTED") return "已提交，只读";
  switch (saveState.value) {
    case "saving":
      return "保存中…";
    case "dirty":
      return "有未保存的修改，停止输入 5 秒后自动保存";
    case "offline":
      return "网络不可用，内容已在本地保留，恢复后自动补传";
    default:
      return savedAt.value
        ? `已保存 ${savedAt.value.replace("T", " ").slice(11, 19)}`
        : "尚未保存";
  }
});

// ---------------------------------------------------------------- 粘贴

async function onPaste(ev: ClipboardEvent) {
  if (!workId.value || status.value === "SUBMITTED") return;
  const pasted = ev.clipboardData?.getData("text/plain") ?? "";
  const len = codePoints(pasted);
  const start = editor.value?.selectionStart ?? 0;
  try {
    const { data } = await api.post(`/works/${workId.value}/pastes`, {
      charCount: len,
      offsetStart: start,
      offsetEnd: start + len,
    });
    if (data.recorded)
      pasteDialog.value = { pasteId: data.pasteId, text: pasted };
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function choosePasteSource(source: string) {
  if (!pasteDialog.value) return;
  const { pasteId, text: pasted } = pasteDialog.value;
  if (source === "AI_TOOL") {
    regForm.value = {
      ...emptyForm(),
      open: true,
      forPasteId: pasteId,
      outputText: pasted,
      adoption: "DIRECT",
    };
    pasteDialog.value = null;
    return;
  }
  await api.put(`/works/pastes/${pasteId}/source`, { source });
  pasteDialog.value = null;
}

function pasteLater() {
  pasteDialog.value = null;
}

// ---------------------------------------------------------------- 登记

async function loadTools() {
  const { data } = await api.get<Tool[]>("/ai-tools");
  tools.value = data;
}

async function loadRegistrations() {
  if (!workId.value) return;
  const { data } = await api.get<Registration[]>(
    `/works/${workId.value}/registrations`,
  );
  registrations.value = data;
}

function openRegistration() {
  regForm.value = { ...emptyForm(), open: true };
}

async function submitRegistration() {
  if (!workId.value) return;
  error.value = "";
  const f = regForm.value;
  try {
    const { data } = await api.post(`/works/${workId.value}/registrations`, {
      toolId: f.toolId === "" ? null : f.toolId,
      toolNameCustom: f.toolNameCustom || null,
      toolVersion: f.toolVersion || null,
      stage: f.stage,
      purpose: f.purpose,
      adoption: f.adoption,
      promptText: f.promptText || null,
      outputText: f.outputText || null,
      verification: f.verification || null,
    });
    if (f.forPasteId)
      await api.put(`/works/pastes/${f.forPasteId}/source`, {
        source: "AI_TOOL",
        registrationId: data.id,
      });
    regForm.value = emptyForm();
    await loadRegistrations();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function voidRegistration(id: number) {
  if (!workId.value) return;
  await api.delete(`/works/${workId.value}/registrations/${id}`);
  await loadRegistrations();
}

function toolLabel(r: Registration) {
  return (
    r.toolNameCustom ??
    tools.value.find((t) => t.id === r.toolId)?.name ??
    "未填写"
  );
}

// ---------------------------------------------------------------- 提交

async function submitWork() {
  if (!workId.value) return;
  error.value = "";
  if (saveState.value !== "idle") await save();
  const { data } = await api.get(`/works/${workId.value}/pastes/pending`);
  pendingPastes.value = data;
  if (data.length) {
    error.value = `还有 ${data.length} 处粘贴没有选择来源，请先补填。`;
    return;
  }
  try {
    await api.post(`/works/${workId.value}/submit`, {
      declareNotUsed: declareNotUsed.value,
    });
    router.push({ name: "declaration", params: { workId: workId.value } });
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function resolvePending(id: number, source: string) {
  if (source === "AI_TOOL") {
    regForm.value = {
      ...emptyForm(),
      open: true,
      forPasteId: id,
      adoption: "DIRECT",
    };
    return;
  }
  await api.put(`/works/pastes/${id}/source`, { source });
  pendingPastes.value = pendingPastes.value.filter((p) => p.id !== id);
}

onMounted(init);
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer);
  if (retryTimer) clearInterval(retryTimer);
});
</script>

<template>
  <div class="gate" v-if="gateOpen && policy">
    <div class="card">
      <h2>本作业的 AI 使用规则（第 {{ policy.versionNo }} 版）</h2>
      <p>
        <strong>{{ TIER[policy.tier] }}</strong>
      </p>
      <p v-if="policy.scenes.length">
        允许的场景：<span
          v-for="s in policy.scenes.filter((x) => x.allowed)"
          :key="s.name"
          class="tag"
          style="margin-right: 6px"
          >{{ s.name }}</span
        >
        <template v-if="policy.scenes.some((x) => !x.allowed)">
          <br />不允许的场景：<span
            v-for="s in policy.scenes.filter((x) => !x.allowed)"
            :key="s.name"
            class="tag"
            style="margin-right: 6px"
            >{{ s.name }}</span
          >
        </template>
      </p>
      <p v-if="policy.gradingNote">{{ policy.gradingNote }}</p>
      <p class="muted">
        规则更新于
        {{
          policy.updatedAt.replace("T", " ").slice(0, 16)
        }}。确认后进入编辑器；老师修改规则后会再次显示。
      </p>
      <button class="primary" @click="ack">我已阅读</button>
    </div>
  </div>

  <p><a href="javascript:history.back()">← 返回</a></p>
  <p class="error" v-if="error">{{ error }}</p>

  <div class="card" v-if="workId">
    <div class="row" style="justify-content: space-between">
      <span class="muted">{{ saveLabel }}</span>
      <span class="row">
        <router-link :to="{ name: 'timeline', params: { workId } }"
          >我的时间线</router-link
        >
        <button @click="openRegistration" :disabled="status === 'SUBMITTED'">
          登记一次 AI 使用
        </button>
      </span>
    </div>
    <textarea
      ref="editor"
      class="editor"
      v-model="text"
      :readonly="status === 'SUBMITTED'"
      maxlength="50000"
      placeholder="在这里写作业。停止输入 5 秒自动保存，系统会自动留存版本。"
      @input="onInput"
      @paste="onPaste"
    ></textarea>
    <p class="muted">{{ codePoints(text) }} / 50000 字符</p>
  </div>

  <div class="card" v-if="workId">
    <h3>AI 使用登记（{{ activeRegs.length }} 条有效）</h3>
    <p class="muted" v-if="!registrations.length">
      还没有登记。用了 AI 就点右上角"登记一次 AI 使用"，几下点选完成。
    </p>
    <table v-else>
      <thead>
        <tr>
          <th>时间</th>
          <th>工具</th>
          <th>环节</th>
          <th>用途</th>
          <th>采用方式</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="r in registrations"
          :key="r.id"
          :class="{ muted: r.status === 'VOIDED' }"
        >
          <td>{{ r.createdAt.replace("T", " ").slice(5, 16) }}</td>
          <td>{{ toolLabel(r) }}</td>
          <td>{{ STAGES.find((s) => s[0] === r.stage)?.[1] }}</td>
          <td>{{ r.purpose }}</td>
          <td>{{ ADOPTIONS.find((a) => a[0] === r.adoption)?.[1] }}</td>
          <td>
            <span v-if="r.status === 'VOIDED'">已作废</span>
            <button
              v-else-if="status !== 'SUBMITTED'"
              @click="voidRegistration(r.id)"
            >
              作废
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="card" v-if="workId && status !== 'SUBMITTED'">
    <h3>提交</h3>
    <p class="muted">
      提交时自动生成《AI
      使用声明》；没有任何登记时需要勾选未使用承诺。提交后作业与声明只读。
    </p>
    <p v-if="!activeRegs.length">
      <label
        ><input type="checkbox" v-model="declareNotUsed" />
        本人承诺本次作业未使用任何生成式 AI 工具</label
      >
    </p>
    <div v-if="pendingPastes.length">
      <p>待补填来源的粘贴：</p>
      <ul>
        <li v-for="p in pendingPastes" :key="p.id" class="row">
          <span
            >{{ p.occurredAt.replace("T", " ").slice(5, 16) }} 粘贴
            {{ p.charCount }} 字符</span
          >
          <button
            v-for="s in SOURCES"
            :key="s[0]"
            @click="resolvePending(p.id, s[0])"
          >
            {{ s[1] }}
          </button>
        </li>
      </ul>
    </div>
    <button class="primary" @click="submitWork">提交作业</button>
  </div>

  <div class="gate" v-if="pasteDialog">
    <div class="card">
      <h3>
        刚粘贴了 {{ codePoints(pasteDialog.text) }} 字符，这段内容来自哪里？
      </h3>
      <p class="row">
        <button
          v-for="s in SOURCES"
          :key="s[0]"
          class="primary"
          @click="choosePasteSource(s[0])"
        >
          {{ s[1] }}
        </button>
        <button @click="pasteLater">稍后</button>
      </p>
      <p class="muted">
        选"稍后"会记为来源待定，提交前需要补填。选"AI
        工具"会直接打开登记表并预填这段内容。
      </p>
    </div>
  </div>

  <div class="gate" v-if="regForm.open">
    <div class="card">
      <h3>登记一次 AI 使用</h3>
      <form @submit.prevent="submitRegistration">
        <p class="row">
          <select v-model="regForm.toolId">
            <option value="">选择工具</option>
            <option v-for="t in tools" :key="t.id" :value="t.id">
              {{ t.name }}
            </option>
          </select>
          <input
            v-model="regForm.toolNameCustom"
            placeholder="或输入工具名"
            maxlength="50"
          />
          <input
            v-model="regForm.toolVersion"
            placeholder="版本（可选）"
            maxlength="50"
          />
        </p>
        <p class="row">
          <select v-model="regForm.stage">
            <option v-for="s in STAGES" :key="s[0]" :value="s[0]">
              {{ s[1] }}
            </option>
          </select>
          <select v-model="regForm.adoption">
            <option v-for="a in ADOPTIONS" :key="a[0]" :value="a[0]">
              {{ a[1] }}
            </option>
          </select>
          <select v-model="regForm.verification">
            <option v-for="v in VERIFICATIONS" :key="v[0]" :value="v[0]">
              核对方式：{{ v[1] }}
            </option>
          </select>
        </p>
        <p>
          <input
            v-model="regForm.purpose"
            placeholder="用途，一句话"
            maxlength="200"
            required
            style="width: 100%"
          />
        </p>
        <p>
          <textarea
            v-model="regForm.promptText"
            placeholder="提示词（可选，10000 字符内）"
            rows="3"
            style="width: 100%"
          ></textarea>
        </p>
        <p>
          <textarea
            v-model="regForm.outputText"
            placeholder="AI 输出（可选，10000 字符内）"
            rows="3"
            style="width: 100%"
          ></textarea>
        </p>
        <p class="row">
          <button class="primary" type="submit">保存登记</button>
          <button type="button" @click="regForm = emptyForm()">取消</button>
        </p>
      </form>
    </div>
  </div>
</template>
