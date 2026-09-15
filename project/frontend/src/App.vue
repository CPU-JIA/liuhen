<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { TOKEN_KEY, USER_KEY, currentUser } from "./api";

const router = useRouter();
const me = computed(() => currentUser());
const roleLabel: Record<string, string> = {
  STUDENT: "学生",
  TEACHER: "教师",
  REVIEWER: "复核员",
  AFFAIRS: "教务",
  ADMIN: "管理员",
};

function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  router.push({ name: "login" });
}
</script>

<template>
  <header class="top">
    <router-link class="brand" :to="{ name: 'courses' }">留痕</router-link>
    <div class="row" v-if="me">
      <span class="muted">{{ me.name }} · {{ roleLabel[me.role] }}</span>
      <router-link :to="{ name: 'password' }">改密码</router-link>
      <button @click="logout">退出</button>
    </div>
  </header>
  <main>
    <router-view :key="$route.fullPath" />
  </main>
</template>
