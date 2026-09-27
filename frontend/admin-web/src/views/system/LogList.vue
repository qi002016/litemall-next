<template>
  <div>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="管理员">
        <el-input v-model="query.admin" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
    </el-form>

    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="admin" label="管理员" width="120" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="action" label="操作" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="说明" show-overflow-tooltip />
      <el-table-column prop="addTime" label="时间" width="180" />
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
import { logPage } from '@/api/system'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, admin: '' })

const load = async () => {
  loading.value = true
  try {
    const data = (await logPage(query)) || {}
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const reload = () => { query.pageNum = 1; load() }
const onPageChange = (p) => { query.pageNum = p; load() }
onMounted(load)
</script>
