<template>
  <div class="profile">
    <van-nav-bar title="个人信息" left-arrow @click-left="$router.back()" />
    <div class="avatar-wrap">
      <van-image round width="80" height="80" :src="info.avatar || defaultAvatar" />
      <p class="name">{{ info.nickname || info.username }}</p>
    </div>
    <van-cell-group title="基本资料">
      <van-cell title="用户ID" :value="String(info.userId || '-')" />
      <van-cell title="用户名" :value="info.username || '-'" />
      <van-cell title="昵称" :value="info.nickname || '-'" />
      <van-cell title="手机号" :value="info.mobile || '未绑定'" />
      <van-cell title="性别" :value="genderText" />
      <van-cell title="会员等级" :value="'Lv.' + (info.userLevel || 0)" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { userInfo } from '@/api/user'
import { useUserStore } from '@/store/user'

const defaultAvatar = 'https://img.yzcdn.cn/vant/cat.jpeg'
const info = ref({})
const userStore = useUserStore()

const genderText = computed(() => {
  const g = info.value.gender
  return g === 1 ? '男' : g === 2 ? '女' : '未知'
})

onMounted(async () => {
  try {
    info.value = (await userInfo()) || {}
    userStore.setUserInfo(info.value)
  } catch (e) {
    info.value = userStore.userInfo || {}
  }
})
</script>

<style scoped>
.avatar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 28px 0;
  background: #fff;
  margin-bottom: 10px;
}
.avatar-wrap .name {
  margin-top: 10px;
  font-size: 16px;
  font-weight: bold;
}
</style>
