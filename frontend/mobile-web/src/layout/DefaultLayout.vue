<template>
  <div class="layout">
    <div class="layout-body">
      <router-view />
    </div>
    <van-tabbar route>
      <van-tabbar-item to="/home" icon="shop-o">首页</van-tabbar-item>
      <van-tabbar-item to="/category" icon="apps-o">分类</van-tabbar-item>
      <van-tabbar-item to="/cart" icon="cart-o">购物车</van-tabbar-item>
      <van-tabbar-item to="/user" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
</script>

<style scoped>
.layout {
  padding-bottom: 50px;
}
</style>
