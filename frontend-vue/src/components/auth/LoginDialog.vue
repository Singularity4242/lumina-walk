<template>
  <el-dialog v-model="visible" title="手机号登录" width="420px" @close="resetForm">
    <p class="purchase-notice">演示登录：验证码由系统模拟生成，不会发送真实短信。</p>
    <el-form label-position="top">
      <el-form-item label="手机号" :error="phoneError">
        <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" @input="phoneError = ''" />
      </el-form-item>
      <el-form-item label="验证码">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="请输入验证码" @keyup.enter="submitLogin" />
          <el-button :loading="sendingCode" :disabled="countdown > 0 || sendingCode" @click="sendCode">
            {{ countdown > 0 ? `${countdown}s 后重试` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="userStore.closeLoginDialog()">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitLogin">登录</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();
const submitting = ref(false);
const sendingCode = ref(false);
const countdown = ref(0);
const timer = ref(null);
const phoneError = ref('');
// 与后端 RegexPatterns.PHONE_REGEX 保持一致。
const PHONE_REGEX = /^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\d{8}$/;
const form = reactive({
  phone: '',
  code: ''
});

const visible = computed({
  get: () => userStore.loginDialogVisible,
  set: (value) => {
    if (!value) {
      userStore.closeLoginDialog();
    }
  }
});

onBeforeUnmount(() => {
  if (timer.value) {
    clearInterval(timer.value);
  }
});

async function sendCode() {
  if (!validatePhone() || sendingCode.value || countdown.value > 0) return;
  sendingCode.value = true;
  try {
    await userStore.sendCode(form.phone);
    ElMessage.success('验证码已发送，请查看后端日志中的模拟验证码');
    countdown.value = 60;
    timer.value = setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        clearInterval(timer.value);
        timer.value = null;
      }
    }, 1000);
  } catch (error) {
    ElMessage.error(error?.message || '发送验证码失败，请稍后重试');
  } finally {
    sendingCode.value = false;
  }
}

async function submitLogin() {
  if (!validatePhone()) return;
  if (!form.code) {
    ElMessage.warning('请输入验证码');
    return;
  }
  submitting.value = true;
  try {
    await userStore.login({ phone: form.phone, code: form.code });
    ElMessage.success('登录成功');
    resetForm();
  } catch (error) {
    ElMessage.error(error?.message || '登录失败');
  } finally {
    submitting.value = false;
  }
}

function validatePhone() {
  if (!form.phone) {
    phoneError.value = '请输入手机号';
    return false;
  }
  if (!PHONE_REGEX.test(form.phone)) {
    phoneError.value = '手机号格式错误，请输入有效的11位手机号';
    return false;
  }
  phoneError.value = '';
  return true;
}

function resetForm() {
  form.phone = '';
  form.code = '';
  phoneError.value = '';
}
</script>

<style scoped lang="scss">
.code-row {
  display: grid;
  grid-template-columns: 1fr 124px;
  gap: 10px;
  width: 100%;
}
</style>

