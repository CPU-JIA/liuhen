<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api, errorMessage, currentUser } from "../api";

interface Course {
  id: number;
  name: string;
  joinCode: string;
  teacherId: number;
  createdAt: string;
}

const me = currentUser();
const courses = ref<Course[]>([]);
const name = ref("");
const joinCode = ref("");
const error = ref("");
const notice = ref("");

async function load() {
  const { data } = await api.get<Course[]>("/courses");
  courses.value = data;
}

async function create() {
  error.value = "";
  try {
    const { data } = await api.post<Course>("/courses", { name: name.value });
    notice.value = `课程「${data.name}」已创建，课程码 ${data.joinCode}`;
    name.value = "";
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function join() {
  error.value = "";
  try {
    const { data } = await api.post<Course>("/courses/join", {
      joinCode: joinCode.value,
    });
    notice.value = `已加入「${data.name}」`;
    joinCode.value = "";
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

onMounted(load);
</script>

<template>
  <div class="card" v-if="me?.role === 'TEACHER' || me?.role === 'ADMIN'">
    <h3>新建课程</h3>
    <form class="row" @submit.prevent="create">
      <input v-model="name" placeholder="课程名" required />
      <button class="primary" type="submit">创建</button>
    </form>
  </div>
  <div class="card" v-if="me?.role === 'STUDENT'">
    <h3>加入课程</h3>
    <form class="row" @submit.prevent="join">
      <input
        v-model="joinCode"
        placeholder="6 位课程码"
        maxlength="6"
        required
      />
      <button class="primary" type="submit">加入</button>
    </form>
  </div>
  <p class="error" v-if="error">{{ error }}</p>
  <p class="muted" v-if="notice">{{ notice }}</p>
  <div class="card">
    <h3>我的课程</h3>
    <p class="muted" v-if="!courses.length">还没有课程。</p>
    <table v-else>
      <thead>
        <tr>
          <th>课程</th>
          <th v-if="me?.role !== 'STUDENT'">课程码</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in courses" :key="c.id">
          <td>{{ c.name }}</td>
          <td v-if="me?.role !== 'STUDENT'">
            <span class="tag">{{ c.joinCode }}</span>
          </td>
          <td>
            <router-link
              :to="{ name: 'assignments', params: { courseId: c.id } }"
              >作业</router-link
            >
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
