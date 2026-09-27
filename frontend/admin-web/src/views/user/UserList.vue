<template>
  <div>
    <el-form :inline="true">
      <el-form-item label="用户名">
        <el-input v-model="query.username" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
    </el-form>

    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="mobile" label="手机号" width="140" />
      <el-table-column prop="userLevel" label="等级" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '冻结' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" :type="row.status === 0 ? 'warning' : 'success'" @click="onToggle(row)">
            {{ row.status === 0 ? '冻结' : '解冻' }}
          </el-button>
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
import { userPage, userStatus } from '@/api/user'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, username: '' })

const load = async () => {
  loading.value = true
  try {
    const data = (await userPage(query)) || {}
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
const onToggle = async (row) => {
  await userStatus({ id: row.id, status: row.status === 0 ? 1 : 0 })
  ElMessage.success('操作成功')
  load()
}

onMounted(load)
</script>
