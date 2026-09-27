<template>
  <div class="address">
    <van-nav-bar title="收货地址" left-arrow @click-left="$router.back()" />
    <van-empty v-if="!list.length" description="暂无收货地址" />
    <van-cell
      v-for="a in list"
      :key="a.id"
      :center="true"
      @click="openDialog(a)"
    >
      <div>
        <p class="line">
          <span class="name">{{ a.name }}</span>
          <span class="mobile">{{ a.tel }}</span>
          <van-tag v-if="a.isDefault" type="primary">默认</van-tag>
        </p>
        <p class="detail">{{ a.province }}{{ a.city }}{{ a.county }}{{ a.addressDetail }}</p>
      </div>
      <template #right-icon>
        <van-button size="mini" type="danger" plain @click.stop="onDelete(a)">删除</van-button>
      </template>
    </van-cell>

    <div class="footer">
      <van-button type="primary" block round @click="openDialog()">新增收货地址</van-button>
    </div>

    <van-popup v-model:show="show" position="bottom" :style="{ height: '60%' }">
      <van-form @submit="onSubmit">
        <van-cell-group inset style="margin-top: 12px">
          <van-field
            v-model="form.name"
            label="收货人"
            placeholder="姓名"
            :rules="[{ required: true, message: '请填写收货人' }]"
          />
          <van-field
            v-model="form.tel"
            label="手机号"
            type="tel"
            maxlength="11"
            placeholder="11 位手机号"
            :rules="[{ required: true, message: '请填写手机号' }]"
          />
          <van-field v-model="form.province" label="省份" placeholder="如：湖北省" />
          <van-field v-model="form.city" label="城市" placeholder="如：宜昌市" />
          <van-field v-model="form.county" label="区/县" placeholder="如：西陵区" />
          <van-field v-model="form.addressDetail" label="详细地址" placeholder="街道、门牌号" />
          <van-cell title="设为默认地址">
            <template #right-icon>
              <van-switch v-model="form.isDefault" />
            </template>
          </van-cell>
        </van-cell-group>
        <div style="margin: 16px">
          <van-button round block type="primary" native-type="submit" :loading="loading">保存</van-button>
        </div>
      </van-form>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { showSuccessToast, showConfirmDialog } from 'vant'
import { addressList, addressCreate, addressUpdate, addressDelete } from '@/api/user'

const list = ref([])
const show = ref(false)
const loading = ref(false)
const editingId = ref(null)

const form = reactive({
  name: '',
  tel: '',
  province: '',
  city: '',
  county: '',
  addressDetail: '',
  isDefault: false,
})

const load = async () => {
  list.value = (await addressList()) || []
}
onMounted(load)

const openDialog = (a) => {
  if (a) {
    editingId.value = a.id
    Object.assign(form, {
      name: a.name,
      tel: a.tel,
      province: a.province,
      city: a.city,
      county: a.county,
      addressDetail: a.addressDetail,
      isDefault: !!a.isDefault,
    })
  } else {
    editingId.value = null
    Object.assign(form, {
      name: '',
      tel: '',
      province: '',
      city: '',
      county: '',
      addressDetail: '',
      isDefault: false,
    })
  }
  show.value = true
}

const onSubmit = async () => {
  loading.value = true
  try {
    if (editingId.value) {
      await addressUpdate(editingId.value, form)
    } else {
      await addressCreate(form)
    }
    showSuccessToast('保存成功')
    show.value = false
    load()
  } finally {
    loading.value = false
  }
}

const onDelete = async (a) => {
  await showConfirmDialog({ title: '提示', message: '确定删除该地址？' })
  await addressDelete(a.id)
  showSuccessToast('已删除')
  load()
}
</script>

<style scoped>
.line {
  margin: 0;
}
.name {
  font-weight: bold;
  margin-right: 8px;
}
.mobile {
  color: #666;
  margin-right: 8px;
}
.detail {
  margin: 4px 0 0;
  color: #999;
  font-size: 13px;
}
.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  background: #fff;
}
</style>
