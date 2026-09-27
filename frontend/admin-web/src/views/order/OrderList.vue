<template>
  <div>
    <el-form :inline="true">
      <el-form-item label="订单状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="orderSn" label="订单编号" width="200" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="orderAmount" label="金额" width="100" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag>{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="addTime" label="下单时间" width="180" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button v-if="row.status === 'undelivered'" size="small" type="primary" @click="onShip(row)">发货</el-button>
          <el-button v-if="row.status === 'refund'" size="small" type="warning" @click="onRefund(row)">退款审核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="onPageChange"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { orderPage, orderShip, orderRefund } from '@/api/order'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })
const statusOptions = [
  { label: '待付款', value: 'unpaid' },
  { label: '待发货', value: 'undelivered' },
  { label: '待收货', value: 'shipped' },
  { label: '已完成', value: 'confirmed' },
  { label: '退款', value: 'refund' },
]

const statusText = (s) => statusOptions.find((o) => o.value === s)?.label || s

const load = async () => {
  loading.value = true
  try {
    const data = (await orderPage(query)) || {}
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const reload = () => {
  query.pageNum = 1
  load()
}
const onPageChange = (p) => {
  query.pageNum = p
  load()
}
const onShip = async (row) => {
  await orderShip({ orderId: row.id || row.orderId })
  ElMessage.success('已发货')
  load()
}
const onRefund = async (row) => {
  await orderRefund({ orderId: row.id || row.orderId })
  ElMessage.success('已处理退款')
  load()
}

onMounted(load)
</script>
