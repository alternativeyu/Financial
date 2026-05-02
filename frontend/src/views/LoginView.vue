<template>
  <div class="login-neo-page">
    <div class="login-neo-grid"></div>
    <div class="login-neo-card">
      <div class="login-neo-left">
        <div class="neo-brand">
          <div class="neo-brand-icon">FT</div>
          <div class="neo-brand-name">FTS Operator</div>
        </div>
        <div class="neo-headline">证券柜台操作员统一入口</div>
      </div>

      <div class="login-neo-right">
        <div class="neo-form-title">操作员登录</div>

        <div class="neo-form-group">
          <label>登录名</label>
          <input
            v-model="form.loginName"
            placeholder="请输入登录名"
            class="neo-input"
            autocomplete="username"
            @keyup.enter="submit"
          />
        </div>

        <div class="neo-form-group">
          <label>密码</label>
          <div class="neo-password-wrap">
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              class="neo-input neo-input-password"
              autocomplete="current-password"
              @keyup.enter="submit"
            />
            <button type="button" class="neo-eye-btn" @click="showPassword = !showPassword">
              {{ showPassword ? "隐藏" : "显示" }}
            </button>
          </div>
        </div>

        <button class="neo-login-btn" @click="submit" :disabled="loading">
          <span v-if="loading" class="neo-spinner"></span>
          <span>{{ loading ? "登录中..." : "登录" }}</span>
        </button>

        <div v-if="message" class="neo-alert">{{ message }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { loginOperator } from "../api";

const router = useRouter();
const message = ref("");
const loading = ref(false);
const showPassword = ref(false);
const form = reactive({
  loginName: "",
  password: ""
});

async function submit() {
  if (loading.value) return;
  loading.value = true;
  try {
    const data = await loginOperator({ loginName: form.loginName, password: form.password });
    localStorage.setItem("op_token", data.token);
    localStorage.setItem("op_name", data.operatorName);
    await router.push("/opening-audit");
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "操作失败";
  } finally {
    loading.value = false;
  }
}
</script>
