<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { api, errorMessage, USER_KEY, currentUser } from "../api";

const router = useRouter();
const oldPassword = ref("");
const newPassword = ref("");
const confirm = ref("");
const error = ref("");
const me = currentUser();

async function submit() {
  error.value = "";
  if (newPassword.value.length < 8)
    return void (error.value = "新密码至少 8 位");
  if (newPassword.value !== confirm.value)
    return void (error.value = "两次输入不一致");
  try {
    await api.post("/auth/password", {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    });
    if (me)
      localStorage.setItem(
        USER_KEY,
        JSON.stringify({ ...me, mustChangePassword: false }),
      );
    router.push({ name: "courses" });
  } catch (e) {
    error.value = errorMessage(e);
  }
}
</script>

<template>
  <div class="card" style="max-width: 420px; margin: 60px auto">
    <h2>修改密码</h2>
    <p class="muted" v-if="me?.mustChangePassword">
      这是首次登录，请先设置新密码再进入系统。
    </p>
    <form @submit.prevent="submit">
      <p>
        <input
          v-model="oldPassword"
          type="password"
          placeholder="当前密码"
          required
          style="width: 100%"
        />
      </p>
      <p>
        <input
          v-model="newPassword"
          type="password"
          placeholder="新密码（至少 8 位）"
          required
          style="width: 100%"
        />
      </p>
      <p>
        <input
          v-model="confirm"
          type="password"
          placeholder="再输一次"
          required
          style="width: 100%"
        />
      </p>
      <p class="error" v-if="error">{{ error }}</p>
      <button class="primary" type="submit">保存</button>
    </form>
  </div>
</template>
