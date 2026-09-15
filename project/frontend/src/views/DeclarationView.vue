<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { api, errorMessage } from "../api";

const props = defineProps<{ workId: string }>();

interface Item {
  tool: string;
  version: string;
  stage: string;
  purpose: string;
  prompt: string;
  verification: string;
  adoption: string;
  exceedsPolicy: boolean;
  registeredAt: string;
}
interface Content {
  kind: "AI_USED" | "NOT_USED";
  title: string;
  student: { loginNo: string; name: string };
  assignment: { title: string; deadline: string };
  submittedAt: string;
  late: boolean;
  policy: { versionNo: number; tier: string; lastModifiedAt: string };
  items: Item[];
  exceededCount: number;
  pledge?: string;
}

const raw = ref<{
  contentJson: string;
  contentHash: string;
  generatedAt: string;
} | null>(null);
const error = ref("");
const content = computed<Content | null>(() =>
  raw.value ? (JSON.parse(raw.value.contentJson) as Content) : null,
);
const TIER: Record<string, string> = {
  FORBID: "禁止",
  DECLARE: "需声明",
  ENCOURAGE: "鼓励",
};

onMounted(async () => {
  try {
    const { data } = await api.get(`/works/${props.workId}/declaration`);
    raw.value = data;
  } catch (e) {
    error.value = errorMessage(e);
  }
});
</script>

<template>
  <p><a href="javascript:history.back()">← 返回</a></p>
  <p class="error" v-if="error">{{ error }}</p>
  <div class="card" v-if="content">
    <h2>{{ content.title }}</h2>
    <p class="muted">
      {{ content.student.name }}（{{ content.student.loginNo }}）·
      {{ content.assignment.title }} · 提交于
      {{ content.submittedAt.replace("T", " ").slice(0, 19) }}
      <span v-if="content.late" class="tag">逾期</span>
    </p>
    <p class="muted">
      对照规则：第 {{ content.policy.versionNo }} 版（{{
        TIER[content.policy.tier]
      }}），规则最后修改于
      {{ content.policy.lastModifiedAt.replace("T", " ").slice(0, 19) }}
    </p>
    <p v-if="content.pledge">{{ content.pledge }}</p>
    <table v-if="content.items.length">
      <thead>
        <tr>
          <th>工具</th>
          <th>版本</th>
          <th>使用环节</th>
          <th>用途</th>
          <th>提示词</th>
          <th>核对方式</th>
          <th>采用方式</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(it, i) in content.items" :key="i">
          <td>{{ it.tool }}</td>
          <td>{{ it.version }}</td>
          <td>{{ it.stage }}</td>
          <td>{{ it.purpose }}</td>
          <td class="muted">
            {{
              it.prompt.length > 60 ? it.prompt.slice(0, 60) + "…" : it.prompt
            }}
          </td>
          <td>{{ it.verification }}</td>
          <td>{{ it.adoption }}</td>
          <td>
            <span v-if="it.exceedsPolicy" class="tag">超出本课允许场景</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="muted" v-if="raw">
      声明哈希 {{ raw.contentHash.slice(0, 16) }}… · 提交后只读
    </p>
  </div>
</template>
