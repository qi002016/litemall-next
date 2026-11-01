<!-- 收货地址：对齐 prototype/assets/app.js address（3023-3138） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">{{ pick ? '选择收货地址' : '收货地址' }}</div>
      <div class="right" @click="openForm(null)">新增</div>
    </div>

    <div class="c-body">
      <div v-if="loading" class="loading-more">加载中…</div>
      <template v-else>
        <div v-if="pick" class="tiny dim" style="margin-bottom: 8px">点击地址可带回结算页</div>

        <div v-for="a in list" :key="a.id" class="card pad" style="margin-bottom: 10px">
          <div class="row top" style="gap: 10px">
            <div style="color: var(--brand)"><AppIcon name="location" :size="19" /></div>
            <div class="grow" :style="pick ? 'cursor:pointer' : ''" @click="pickAddress(a)">
              <div class="small bold">
                {{ a.name }} <span class="dim">{{ a.tel }}</span>
                <span v-if="a.isDefault" class="tag">默认</span>
              </div>
              <div class="tiny dim mt6">{{ addrText(a) }}</div>
            </div>
          </div>
          <div class="row mt8" style="justify-content: flex-end; gap: 8px; border-top: 1px solid var(--line); padding-top: 8px">
            <button v-if="!a.isDefault" type="button" class="btn sm ghost" @click="setDefault(a)">设为默认</button>
            <button type="button" class="btn sm ghost" @click="openForm(a)">编辑</button>
            <button type="button" class="btn sm ghost" @click="remove(a)">删除</button>
          </div>
        </div>

        <EmptyState v-if="!list.length" icon="location" title="还没有收货地址" hint="添加地址后才能在结算页提交订单" />
        <button type="button" class="btn primary block mt12" @click="openForm(null)">+ 新增收货地址</button>
        <div class="tiny dim center" style="padding: 12px 0">地址保存于本机后端，仅当前账号可见</div>
      </template>
    </div>

    <BottomSheet v-if="formOpen" :title="editId ? '编辑地址' : '新增地址'" :actions="formActions" @close="formOpen = false">
      <div class="field">
        <div class="label">收货人</div>
        <input v-model="form.name" class="input" maxlength="20" placeholder="姓名" />
      </div>
      <div class="field">
        <div class="label">手机号</div>
        <input v-model="form.tel" class="input" maxlength="11" inputmode="numeric" placeholder="11 位手机号" />
      </div>
      <div class="field">
        <div class="label">省 / 市 / 区</div>
        <div class="row" style="gap: 6px">
          <input v-model="form.province" class="input" style="flex: 1" placeholder="省" />
          <input v-model="form.city" class="input" style="flex: 1" placeholder="市" />
          <input v-model="form.county" class="input" style="flex: 1" placeholder="区" />
        </div>
      </div>
      <div class="field">
        <div class="label">详细地址</div>
        <input v-model="form.addressDetail" class="input" placeholder="街道、门牌号、楼层" />
      </div>
      <div class="row between" style="padding: 4px 0">
        <span class="small">设为默认地址</span>
        <span class="cbx" :class="{ on: form.isDefault }" @click="form.isDefault = !form.isDefault" />
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import { addressCreate, addressDelete, addressList, addressSetDefault, addressUpdate } from '@/api/user'
import { toast } from '@/utils/toast'
import { confirm } from '@/utils/confirm'

const route = useRoute()
const router = useRouter()

const pick = computed(() => String(route.query.pick || '') === '1')

const loading = ref(true)
const list = ref([])

const formOpen = ref(false)
const editId = ref('')
const form = reactive({ name: '', tel: '', province: '', city: '', county: '', addressDetail: '', isDefault: false })

function addrText(a) {
  return [a.province, a.city, a.county, a.addressDetail].filter(Boolean).join(' ')
}

function payload() {
  return {
    name: form.name.trim(),
    tel: form.tel.trim(),
    province: form.province.trim() || '演示省',
    city: form.city.trim() || '演示市',
    county: form.county.trim() || '演示区',
    addressDetail: form.addressDetail.trim(),
    isDefault: !!form.isDefault,
  }
}

async function load() {
  loading.value = true
  try {
    const data = await addressList()
    list.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openForm(a) {
  editId.value = a ? String(a.id) : ''
  form.name = a?.name || ''
  form.tel = a?.tel || ''
  form.province = a?.province || ''
  form.city = a?.city || ''
  form.county = a?.county || ''
  form.addressDetail = a?.addressDetail || ''
  form.isDefault = a ? !!a.isDefault : !list.value.length
  formOpen.value = true
}

async function save() {
  if (!form.name.trim()) return toast('请填写收货人', 'err'), false
  const tel = form.tel.replace(/\s/g, '')
  if (!/^\d{6,11}$/.test(tel)) return toast('请填写正确的手机号', 'err'), false
  if (!form.addressDetail.trim()) return toast('请填写详细地址', 'err'), false
  try {
    const body = payload()
    if (editId.value) await addressUpdate(editId.value, body)
    else await addressCreate(body)
    toast(editId.value ? '地址已更新' : '地址已添加', 'ok')
    formOpen.value = false
    await load()
  } catch {
    return false
  }
}

const formActions = [
  { text: '取消' },
  { text: '保存', type: 'primary', onClick: save },
]

async function setDefault(a) {
  try {
    await addressSetDefault(a.id)
    toast('已设为默认地址', 'ok')
    await load()
  } catch {
    /* 已 toast */
  }
}

async function remove(a) {
  const yes = await confirm({ title: '删除地址', body: `删除「${a.name} ${a.tel}」这条地址？删除后不可恢复。`, okText: '删除', danger: true })
  if (!yes) return
  try {
    await addressDelete(a.id, payloadOf(a))
    toast('地址已删除', 'ok')
    await load()
  } catch {
    /* 已 toast */
  }
}

function payloadOf(a) {
  return {
    name: a.name,
    tel: a.tel,
    province: a.province || '演示省',
    city: a.city || '演示市',
    county: a.county || '演示区',
    addressDetail: a.addressDetail,
    isDefault: !!a.isDefault,
  }
}

function pickAddress(a) {
  if (!pick.value) return
  sessionStorage.setItem('hm_pick_address', String(a.id))
  router.back()
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/me')
}

onMounted(load)
</script>