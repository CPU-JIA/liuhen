<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage } from "../api";

const props = defineProps<{ workId: string }>();

interface Item {
  type: "SNAPSHOT" | "PASTE" | "REGISTRATION";
  at: string;
  data: Record<string, string | number | boolean>;
}
interface LineChange {
  op: "EQUAL" | "INSERT" | "DELETE";
  line: string;
}

const items = ref<Item[]>([]);
const from = ref<number | null>(null);
const to = ref<number | null>(null);
const diff = ref<LineChange[]>([]);
const fullText = ref("");
const fullSeq = ref<number | null>(null);
const error = ref("");

const SOURCE: Record<string, string> = {
  OWN_DOC: "自己的其他文档",
  AI_TOOL: "AI 工具",
  WEB: "网络资料",
  OTHER: "其他",
  PENDING: "来源待定",
};
const TRIGGER: Record<string, string> = {
  TIME: "按时间",
  EDIT_VOLUME: "按改动量",
  IMPORT: "导入",
  SUBMIT: "提交",
};

function snapshots() {
  return items.value.filter((i) => i.type === "SNAPSHOT");
}

function fmt(at: string) {
  return at.replace("T", " ").slice(0, 19);
}

function describe(i: Item): string {
  const d = i.data;
  if (i.type === "SNAPSHOT") {
    const delta = Number(d.charDelta);
    return `版本 ${d.seqNo}（${TRIGGER[String(d.trigger)] ?? d.trigger}），${d.charCount} 字符，相比上一版 ${delta >= 0 ? "+" : ""}${delta}`;
  }
  if (i.type === "PASTE")
    return `粘贴 ${d.charCount} 字符，来源：${SOURCE[String(d.source)] ?? d.source}`;
  const tool = d.toolNameCustom || `工具 ${d.toolId}`;
  return `登记：${d.stage} · ${d.adoption} · ${tool}${d.status === "VOIDED" ? "（已作废）" : ""}`;
}

// 加载失败要显示出来：没权限的人打开这一页看到的应是"没有权限"，
// 而不是空态文案"还没有记录"（AC-PERM-01-1；评审彩排发现）
const loadError = ref("");

async function load() {
  loadError.value = "";
  try {
    const { data } = await api.get<Item[]>(`/works/${props.workId}/timeline`);
    items.value = data;
  } catch (e) {
    loadError.value = errorMessage(e);
    return;
  }
  const snaps = snapshots();
  if (snaps.length >= 2) {
    from.value = Number(snaps[snaps.length - 2].data.seqNo);
    to.value = Number(snaps[snaps.length - 1].data.seqNo);
  }
}

async function compare() {
  error.value = "";
  if (from.value === null || to.value === null) return;
  try {
    const { data } = await api.get<LineChange[]>(
      `/works/${props.workId}/diff`,
      { params: { from: from.value, to: to.value } },
    );
    diff.value = data;
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function showFull(seq: number) {
  const { data } = await api.get<{ seqNo: number; text: string }>(
    `/works/${props.workId}/snapshots/${seq}`,
  );
  fullSeq.value = seq;
  fullText.value = data.text;
}

onMounted(load);
</script>

<template>
  <p><a href="javascript:history.back()">← 返回</a></p>
  <div class="card">
    <h3>时间线</h3>
    <p class="muted">
      只有版本、粘贴、登记三类条目，按时间排列。学生本人与教师看到的内容一致。
    </p>
    <p class="error" v-if="loadError">{{ loadError }}</p>
    <p class="muted" v-else-if="!items.length">
      还没有记录，开始写作后自动生成。
    </p>
    <ul class="timeline">
      <li v-for="(i, idx) in items" :key="idx">
        <span class="muted">{{ fmt(i.at) }}</span>
        <span class="tag">{{
          i.type === "SNAPSHOT" ? "版本" : i.type === "PASTE" ? "粘贴" : "登记"
        }}</span>
        <span>
          {{ describe(i) }}
          <a
            v-if="i.type === 'SNAPSHOT'"
            href="#"
            @click.prevent="showFull(Number(i.data.seqNo))"
          >
            查看全文</a
          >
        </span>
      </li>
    </ul>
  </div>

  <div class="card" v-if="snapshots().length >= 2">
    <h3>两版对比</h3>
    <p class="row">
      <select v-model.number="from">
        <option
          v-for="s in snapshots()"
          :key="'f' + s.data.seqNo"
          :value="s.data.seqNo"
        >
          版本 {{ s.data.seqNo }}
        </option>
      </select>
      <span>→</span>
      <select v-model.number="to">
        <option
          v-for="s in snapshots()"
          :key="'t' + s.data.seqNo"
          :value="s.data.seqNo"
        >
          版本 {{ s.data.seqNo }}
        </option>
      </select>
      <button class="primary" @click="compare">对比</button>
    </p>
    <p class="error" v-if="error">{{ error }}</p>
    <div class="diff" v-if="diff.length">
      <pre v-for="(l, i) in diff" :key="i" :class="l.op"
        >{{ l.op === "INSERT" ? "+ " : l.op === "DELETE" ? "- " : "  "
        }}{{ l.line }}</pre>
    </div>
  </div>

  <div class="card" v-if="fullSeq !== null">
    <h3>版本 {{ fullSeq }} 全文</h3>
    <pre style="white-space: pre-wrap; font-family: inherit">{{
      fullText
    }}</pre>
  </div>
</template>
