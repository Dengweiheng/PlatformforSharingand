<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { loginUser, registerUser, resetPassword, sendCode } from '@/api/userCenterApi'

const router = useRouter()
const authMode = ref('login') // 'login', 'register', 'forgot'
const loading = ref(false)
const waitSecs = ref(0)
const notice = reactive({ type: '', text: '' })

const loginForm = reactive({
  account: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  password: '',
  email: '',
  phone: '',
})

const forgotForm = reactive({
  account: '',
  captcha: '',
  newPassword: '',
  confirmPassword: '',
})

function setNotice(type, text) {
  notice.type = type
  notice.text = text
  setTimeout(() => {
    notice.text = ''
  }, 3000)
}

function saveUserCache(user) {
  const safeUser = {
    id: user?.id,
    username: user?.username,
    email: user?.email,
    phone: user?.phone,
    role: user?.role,
    status: user?.status,
  }
  localStorage.setItem('user-center-current-user', JSON.stringify(safeUser))
}

async function handleLogin() {
  if (!loginForm.account || !loginForm.password) {
    setNotice('error', '请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const user = await loginUser(loginForm)
    saveUserCache(user)
    setNotice('success', '登录成功，正在跳转...')
    setTimeout(() => {
      if (user.role === 'ADMIN') {
        router.push('/admin-center')
      } else {
        router.push('/user-center')
      }
    }, 1000)
  } catch (error) {
    setNotice('error', error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.password) {
    setNotice('error', '请填写必要的名称和密码')
    return
  }
  loading.value = true
  try {
    const user = await registerUser(registerForm)
    setNotice('success', '注册成功，请登录')
    authMode.value = 'login'
    loginForm.account = user.username
  } catch (error) {
    setNotice('error', error.message || '注册失败')
  } finally {
    loading.value = false
  }
}

async function handleSendCode() {
  if (!forgotForm.account) {
    setNotice('error', '请先输入账号')
    return
  }
  try {
    await sendCode(forgotForm.account)
    setNotice('success', '验证码已发送')
    waitSecs.value = 60
    const timer = setInterval(() => {
      waitSecs.value--
      if (waitSecs.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (error) {
    setNotice('error', error.message || '获取验证码失败')
  }
}

async function handleResetPassword() {
  if (!forgotForm.account || !forgotForm.captcha || !forgotForm.newPassword) {
    setNotice('error', '请填全账号、验证码和新密码')
    return
  }
  if (forgotForm.newPassword !== forgotForm.confirmPassword) {
    setNotice('error', '两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword({
      account: forgotForm.account,
      captcha: forgotForm.captcha,
      newPassword: forgotForm.newPassword,
    })
    setNotice('success', '密码重置成功，请使用新密码登录')
    authMode.value = 'login'
    loginForm.account = forgotForm.account
  } catch (error) {
    setNotice('error', error.message || '密码重置失败')
  } finally {
    loading.value = false
  }
}

function toggleMode(mode) {
  authMode.value = mode
  notice.text = ''
}
</script>

<template>
  <div class="auth-page">
    <!-- Animated background layers -->
    <div class="bg-blur">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-info">
          <div class="brand">
            <h1>个性化学习平台</h1>
          </div>
          <p class="slogan">探索知识，分享智慧，开启你的数字化学习之旅。</p>
          <div class="illustration">
            <!-- Simple decorative elements -->
            <div class="circle"></div>
            <div class="sq-1"></div>
            <div class="sq-2"></div>
          </div>
        </div>

        <div class="auth-form-side">
          <div class="form-header">
            <template v-if="authMode === 'login'">
              <h2>欢迎回来</h2>
              <p>登录以访问你的个性化推荐</p>
            </template>
            <template v-else-if="authMode === 'register'">
              <h2>创建账号</h2>
              <p>加入我们的共享资源社区</p>
            </template>
            <template v-else>
              <h2>密码找回</h2>
              <p>通过验证信息重置你的密码</p>
            </template>
          </div>

          <transition name="fade-slide" mode="out-in">
            <div :key="authMode" class="form-body">
              <div v-if="notice.text" class="notice-bar" :class="notice.type">
                {{ notice.text }}
              </div>

              <!-- Login Form -->
              <div v-if="authMode === 'login'" class="inputs">
                <div class="input-group">
                  <label>账号</label>
                  <input v-model="loginForm.account" type="text" placeholder="用户名/邮箱/手机号" @keyup.enter="handleLogin" />
                </div>
                <div class="input-group">
                  <label>密码</label>
                  <input v-model="loginForm.password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
                  <div class="forgot-link">
                    <a @click="toggleMode('forgot')">忘记密码？</a>
                  </div>
                </div>
                <button class="submit-btn" :disabled="loading" @click="handleLogin">
                  {{ loading ? '登录中...' : '登录' }}
                </button>
              </div>

              <!-- Register Form -->
              <div v-else-if="authMode === 'register'" class="inputs">
                <div class="input-group">
                  <label>用户名</label>
                  <input v-model="registerForm.username" type="text" placeholder="设置你的用户名" />
                </div>
                <div class="input-group">
                  <label>密码</label>
                  <input v-model="registerForm.password" type="password" placeholder="设置复杂密码更安全" />
                </div>
                <div class="input-group">
                  <label>邮箱</label>
                  <input v-model="registerForm.email" type="email" placeholder="支持邮箱注册" />
                </div>
                <div class="input-group">
                  <label>手机号</label>
                  <input v-model="registerForm.phone" type="text" placeholder="支持手机号注册" />
                </div>
                <button class="submit-btn" :disabled="loading" @click="handleRegister">
                  {{ loading ? '注册中...' : '注册' }}
                </button>
              </div>

              <!-- Forgot Password Form -->
              <div v-else class="inputs">
                <div class="input-group">
                  <label>找回账号</label>
                  <input v-model="forgotForm.account" type="text" placeholder="用户名/邮箱/手机号" />
                </div>
                <div class="input-group">
                  <label>验证码</label>
                  <div class="captcha-row">
                    <input v-model="forgotForm.captcha" type="text" placeholder="6位数字" />
                    <button class="captcha-btn" :disabled="waitSecs > 0" @click="handleSendCode">
                      {{ waitSecs > 0 ? `${waitSecs}s` : '获取验证码' }}
                    </button>
                  </div>
                </div>
                <div class="input-group">
                  <label>新密码</label>
                  <input v-model="forgotForm.newPassword" type="password" placeholder="请输入新密码" />
                </div>
                <div class="input-group">
                  <label>确认密码</label>
                  <input v-model="forgotForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
                </div>
                <button class="submit-btn" :disabled="loading" @click="handleResetPassword">
                  {{ loading ? '重置密码中...' : '确认重置' }}
                </button>
              </div>
            </div>
          </transition>

          <div class="form-footer">
            <p v-if="authMode === 'login'">还没有账号？ <a @click="toggleMode('register')">立即注册</a></p>
            <p v-else>已有账号？ <a @click="toggleMode('login')">去登录</a></p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  overflow: hidden;
  position: relative;
}

/* Glassmorphic Background Blobs */
.bg-blur {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  z-index: 0;
  filter: blur(80px);
}

.blob {
  position: absolute;
  border-radius: 50%;
  opacity: 0.6;
}

.blob-1 {
  width: 400px;
  height: 400px;
  background: #3b82f6;
  top: -100px;
  left: -100px;
  animation: float 15s infinite alternate;
}

.blob-2 {
  width: 350px;
  height: 350px;
  background: #8b5cf6;
  bottom: -50px;
  right: -50px;
  animation: float 20s infinite alternate-reverse;
}

.blob-3 {
  width: 300px;
  height: 300px;
  background: #f59e0b;
  top: 40%;
  left: 60%;
  animation: float 12s infinite alternate;
}

@keyframes float {
  from { transform: translate(0, 0); }
  to { transform: translate(40px, 60px); }
}

.auth-container {
  width: 100%;
  max-width: 1000px;
  padding: 20px;
  position: relative;
  z-index: 1;
}

.auth-card {
  display: flex;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 32px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  min-height: 600px;
}

.auth-info {
  flex: 1;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  padding: 60px;
  color: white;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
}

.brand {
  position: relative;
  z-index: 2;
}

.logo {
  width: 50px;
  height: 50px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  font-size: 20px;
  margin-bottom: 20px;
}

.auth-info h1 {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 12px;
}

.slogan {
  font-size: 16px;
  color: #94a3b8;
  max-width: 300px;
  line-height: 1.6;
}

.illustration {
  position: absolute;
  bottom: -50px;
  right: -50px;
  width: 300px;
  height: 300px;
  z-index: 1;
}

.circle {
  width: 200px;
  height: 200px;
  border: 30px solid rgba(59, 130, 246, 0.1);
  border-radius: 50%;
  position: absolute;
  bottom: 60px;
  right: 60px;
}

.sq-1, .sq-2 {
  position: absolute;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.2), transparent);
  border-radius: 20px;
  transform: rotate(45deg);
}

.sq-1 { width: 150px; height: 150px; top: 0; right: 20px; }
.sq-2 { width: 100px; height: 100px; bottom: 100px; right: 180px; }

.auth-form-side {
  width: 450px;
  background: rgba(255, 255, 255, 0.85);
  padding: 60px;
  display: flex;
  flex-direction: column;
}

.form-header {
  margin-bottom: 40px;
}

.form-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}

.form-header p {
  color: #64748b;
  font-size: 14px;
}

.notice-bar {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 13px;
  margin-bottom: 20px;
  text-align: center;
}

.notice-bar.success { background: #dcfce7; color: #15803d; }
.notice-bar.error { background: #fee2e2; color: #b91c1c; }

.inputs {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.input-row {
  display: flex;
  gap: 15px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.input-group label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.forgot-link {
  text-align: right;
  margin-top: 4px;
}

.forgot-link a {
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
  transition: color 0.3s;
}

.forgot-link a:hover {
  color: #3b82f6;
  text-decoration: underline;
}

.input-group input {
  padding: 14px 16px;
  border-radius: 12px;
  border: 2px solid #e2e8f0;
  background: white;
  font-size: 14px;
  transition: all 0.3s ease;
}

.input-group input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1);
}

.captcha-row {
  display: flex;
  gap: 10px;
}

.captcha-btn {
  padding: 0 15px;
  border-radius: 12px;
  border: 2px solid #e2e8f0;
  background: #f8fafc;
  color: #3b82f6;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.3s;
}

.captcha-btn:hover:not(:disabled) {
  background: #eff6ff;
  border-color: #3b82f6;
}

.captcha-btn:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.submit-btn {
  margin-top: 10px;
  padding: 16px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: white;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 10px 15px -3px rgba(59, 130, 246, 0.3);
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 25px -5px rgba(59, 130, 246, 0.4);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.form-footer {
  margin-top: auto;
  text-align: center;
}

.form-footer p {
  color: #64748b;
  font-size: 14px;
}

.form-footer a {
  color: #3b82f6;
  font-weight: 600;
  cursor: pointer;
}

.form-footer a:hover {
  text-decoration: underline;
}

/* Animations */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

@media (max-width: 900px) {
  .auth-info { display: none; }
  .auth-card { min-width: auto; width: 100%; border-radius: 24px; }
  .auth-form-side { width: 100%; padding: 40px; }
}
</style>
