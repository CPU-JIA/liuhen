<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, currentUser } from "../api";

const props = defineProps<{ courseId: string }>();

interface Assignment {
  id: number;
  title: string;
  deadline: string;
}
interface WorkSummary {
  workId: number;
  studentId: number;
  studentName: string;
  status: string;
  charCount: number;
  lastSavedAt: string | null;
  submittedAt: string | null;
  late: boolean;
}

const me = currentUser();
const isTeacher = me?.role === "TEACHER";
const assignments = ref<Assignment[]>([]);
const worksOf = ref<Record<number, WorkSummary[]>>({});
const error = ref("");
const notice = ref("");

const title = ref("");
const deadline = ref("");

const PRESET_SCENES = [
  "查资料",
  "改语法",
  "生成大纲",
  "生成正文",
  "生成代码框架",
  "生成数据",
];
const tier = ref<"FORBID" | "DECLARE" | "ENCOURAGE">("DECLARE");
const gradingNote = ref("如实声明不扣分；直接采用未消化的内容才影响评分。");
const allowed = ref<Record<string, boolean>>({
  查资料: true,
  改语法: true,
  生成大纲: true,
});
const customScene = ref("");

const rosterFile = ref<File | null>(null);

async function load() {
  const { data } = await api.get<Assignment[]>(
    `/courses/${props.courseId}/assignments`,
  );
  assignments.value = data;
}

async function createAssignment() {
  error.value = "";
  try {
    await api.post(`/courses/${props.courseId}/assignments`, {
      title: title.value,
      deadline: deadline.value + ":00",
    });
    title.value = "";
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

function addScene() {
  const s = customScene.value.trim();
  if (!s) return;
  if (s.length > 30) return void (error.value = "场景名不超过 30 字符");
  allowed.value[s] = true;
  customScene.value = "";
}

async function publishPolicy() {
  error.value = "";
  const scenes = Object.entries(allowed.value).map(([name, ok]) => ({
    name,
    allowed: ok,
  }));
  try {
    const { data } = await api.put(`/courses/${props.courseId}/policy`, {
      tier: tier.value,
      gradingNote: gradingNote.value,
      scenes,
    });
    notice.value = `规则已发布，当前第 ${data.versionNo} 版。学生下次进入作业会重新看到规则页。`;
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function uploadRoster() {
  if (!rosterFile.value) return;
  error.value = "";
  const form = new FormData();
  form.append("file", rosterFile.value);
  try {
    const { data } = await api.post(`/courses/${props.courseId}/roster`, form);
    notice.value = `已导入 ${data.imported} 名学生，初始密码为学号后 6 位。`;
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function showWorks(a: Assignment) {
  const { data } = await api.get<WorkSummary[]>(`/assignments/${a.id}/works`);
  worksOf.value = { ...worksOf.value, [a.id]: data };
}

onMounted(load);
</script>

<template>
  <p><router-link :to="{ name: 'courses' }">← 我的课程</router-link></p>
  <p class="error" v-if="error">{{ error }}</p>
  <p class="muted" v-if="notice">{{ notice }}</p>

  <template v-if="isTeacher">
    <div class="card">
      <h3>AI 使用规则</h3>
      <p class="row">
        <label><input type="radio" value="FORBID" v-model="tier" /> 禁止</label>
        <label
          ><input type="radio" value="DECLARE" v-model="tier" /> 需声明</label
        >
        <label
          ><input type="radio" value="ENCOURAGE" v-model="tier" /> 鼓励</label
        >
      </p>
      <p class="row">
        <label v-for="s in PRESET_SCENES" :key="s"
          ><input type="checkbox" v-model="allowed[s]" /> {{ s }}</label
        >
        <template v-for="(_, s) in allowed" :key="'c' + s">
          <label v-if="!PRESET_SCENES.includes(String(s))"
            ><input type="checkbox" v-model="allowed[s]" /> {{ s }}</label
          >
        </template>
      </p>
      <p class="row">
        <input
          v-model="customScene"
          placeholder="自定义场景，30 字符内"
          maxlength="30"
        />
        <button type="button" @click="addScene">添加场景</button>
      </p>
      <p>
        <input
          v-model="gradingNote"
          maxlength="200"
          placeholder="评分态度，200 字符内"
          style="width: 100%"
        />
      </p>
      <button class="primary" @click="publishPolicy">发布为新版本</button>
      <span class="muted">
        每次发布都是新版本，旧版本保留；已确认旧版本的学生会重新看到规则页。</span
      >
    </div>

    <div class="card">
      <h3>导入学生名单</h3>
      <p class="muted">
        两列：学号、姓名。支持 csv 与 xlsx。重复学号整份拒绝。
      </p>
      <p class="row">
        <input
          type="file"
          accept=".csv,.xlsx"
          @change="
            rosterFile = ($event.target as HTMLInputElement).files?.[0] ?? null
          "
        />
        <button @click="uploadRoster" :disabled="!rosterFile">导入</button>
      </p>
    </div>

    <div class="card">
      <h3>发布作业</h3>
      <form class="row" @submit.prevent="createAssignment">
        <input v-model="title" placeholder="作业名" required />
        <input v-model="deadline" type="datetime-local" required />
        <button class="primary" type="submit">发布</button>
      </form>
    </div>
  </template>

  <div class="card">
    <h3>作业</h3>
    <p class="muted" v-if="!assignments.length">还没有作业。</p>
    <div
      v-for="a in assignments"
      :key="a.id"
      style="padding: 8px 0; border-bottom: 1px solid var(--line)"
    >
      <div class="row">
        <strong>{{ a.title }}</strong>
        <span class="muted"
          >截止 {{ a.deadline.replace("T", " ").slice(0, 16) }}</span
        >
        <router-link
          v-if="!isTeacher"
          :to="{ name: 'editor', params: { assignmentId: a.id } }"
          >进入作业</router-link
        >
        <button v-else @click="showWorks(a)">查看全班</button>
      </div>
      <table v-if="worksOf[a.id]" style="margin-top: 8px">
        <thead>
          <tr>
            <th>学生</th>
            <th>状态</th>
            <th>字符数</th>
            <th>最后保存</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="w in worksOf[a.id]" :key="w.workId">
            <td>{{ w.studentName }}</td>
            <td>
              {{
                w.status === "SUBMITTED"
                  ? w.late
                    ? "已提交（逾期）"
                    : "已提交"
                  : "写作中"
              }}
            </td>
            <td>{{ w.charCount }}</td>
            <td>{{ (w.lastSavedAt ?? "").replace("T", " ").slice(0, 19) }}</td>
            <td>
              <router-link
                :to="{ name: 'timeline', params: { workId: w.workId } }"
                >时间线</router-link
              >
              <span v-if="w.status === 'SUBMITTED'">
                ·
                <router-link
                  :to="{ name: 'declaration', params: { workId: w.workId } }"
                  >声明</router-link
                ></span
              >
            </td>
          </tr>
          <tr v-if="!worksOf[a.id].length">
            <td colspan="5" class="muted">学生尚未开始。</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
