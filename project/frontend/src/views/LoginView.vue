<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { api, errorMessage, TOKEN_KEY, USER_KEY } from "../api";

const router = useRouter();
const loginNo = ref("");
const password = ref("");
const error = ref("");
const busy = ref(false);

async function submit() {
  error.value = "";
  busy.value = true;
  try {
    const { data } = await api.post("/auth/login", {
      loginNo: loginNo.value.trim(),
      password: password.value,
    });
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(
      USER_KEY,
      JSON.stringify({
        id: data.userId,
        loginNo: loginNo.value.trim(),
        name: data.name,
        role: data.role,
        mustChangePassword: data.mustChangePassword,
      }),
    );
    router.push(
      data.mustChangePassword ? { name: "password" } : { name: "courses" },
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="card" style="max-width: 420px; margin: 60px auto">
    <h2>登录</h2>
    <p class="muted">
      学生用学号登录，初始密码为学号后 6 位，首次登录需要修改密码。
    </p>
    <form @submit.prevent="submit">
      <p>
        <input
          v-model="loginNo"
          placeholder="学号或工号"
          required
          style="width: 100%"
        />
      </p>
      <p>
        <input
          v-model="password"
          type="password"
          placeholder="密码"
          required
          style="width: 100%"
        />
      </p>
      <p class="error" v-if="error">{{ error }}</p>
      <button class="primary" :disabled="busy" type="submit">登录</button>
    </form>
  </div>
</template>
