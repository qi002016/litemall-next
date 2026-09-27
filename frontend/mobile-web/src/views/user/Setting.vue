<template>
  <div class="setting">
    <van-nav-bar title="设置" left-arrow @click-left="$router.back()" />
    <van-cell-group>
      <van-cell title="修改昵称" is-link center @click="nicknameDialog = true" />
      <van-cell title="绑定手机号" is-link center @click="phoneDialog = true" />
      <van-cell title="修改密码" is-link center @click="passwordDialog = true" />
    </van-cell-group>
    <div style="margin: 16px">
      <van-button block type="danger" round @click="onLogout">退出登录</van-button>
    </div>

    <!-- 修改昵称 -->
    <van-dialog v-model:show="nicknameDialog" title="修改昵称" show-cancel-button @confirm="onSaveNickname">
      <van-field v-model="nicknameForm.nickname" placeholder="请输入新昵称" />
    </van-dialog>

    <!-- 绑定手机号 -->
    <van-dialog v-model:show="phoneDialog" title="绑定手机号" show-cancel-button @confirm="onSavePhone">
      <van-field v-model="phoneForm.mobile" type="tel" maxlength="11" placeholder="请输入手机号" />
    </van-dialog>

    <!-- 修改密码 -->
    <van-dialog v-model:show="passwordDialog" title="修改密码" show-cancel-button @confirm="onSavePassword">
      <van-field v-model="pwdForm.oldPassword" type="password" placeholder="请输入原密码" />
      <van-field v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码" />
    </van-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showFailToast } from 'vant'
import { useUserStore } from '@/store/user'
import { userInfo, updateUserInfo, updatePassword } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const nicknameDialog = ref(false)
const phoneDialog = ref(false)
const passwordDialog = ref(false)

const nicknameForm = reactive({ nickname: '' })
const phoneForm = reactive({ mobile: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

const onSaveNickname = async () => {
  if (!nicknameForm.nickname.trim()) {
    showFailToast('昵称不能为空')
    return Promise.reject()
  }
  try {
    const info = await updateUserInfo({ nickname: nicknameForm.nickname.trim() })
    userStore.setUserInfo(info || {})
    showSuccessToast('昵称已更新')
    nicknameForm.nickname = ''
  } catch (e) {
    showFailToast(e.message || '更新失败')
    return Promise.reject()
  }
}

const onSavePhone = async () => {
  if (!/^1\d{10}$/.test(phoneForm.mobile)) {
    showFailToast('请输入正确的手机号')
    return Promise.reject()
  }
  // 后端暂无独立绑定手机号接口，先提示
  showFailToast('手机号绑定功能暂未开通')
  return Promise.reject()
}

const onSavePassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    showFailToast('请填写完整')
    return Promise.reject()
  }
  try {
    await updatePassword({ ...pwdForm })
    showSuccessToast('密码已修改')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } catch (e) {
    showFailToast(e.message || '修改失败')
    return Promise.reject()
  }
}

const onLogout = async () => {
  await showConfirmDialog({ title: '提示', message: '确定退出登录？' })
  userStore.logout()
  showSuccessToast('已退出')
  router.replace('/login')
}
</script>
