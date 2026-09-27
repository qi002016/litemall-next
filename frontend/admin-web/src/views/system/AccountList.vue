<template>
  <div>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="账号">
        <el-input v-model="query.username" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button type="success" @click="openDialog()">新增管理员</el-button>
    </el-form>

    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="账号" width="160" />
      <el-table-column prop="addTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openPwd(row)">重置密码</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增管理员" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="账号"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="form.password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
      <el-form label-width="80px">
        <el-form-item label="新密码"><el-input v-model="pwd" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="onPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { accountPage, accountCreate, accountResetPwd, accountDelete } from '@/api/system'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const pwdVisible = ref(false)
const pwd = ref('')
const pwdRow = ref(null)
const query = reactive({ pageNum: 1, pageSize: 10, username: '' })
const form = reactive({ username: '', password: '' })

const load = async () => {
  loading.value = true
  try {
    const data = (await accountPage(query)) || {}
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const reload = () => { query.pageNum = 1; load() }
const onPageChange = (p) => { query.pageNum = p; load() }

const openDialog = () => {
  form.username = ''
  form.password = ''
  dialogVisible.value = true
}
const onSave = async () => {
  await accountCreate(form)
  ElMessage.success('已创建')
  dialogVisible.value = false
  load()
}
const openPwd = (row) => {
  pwdRow.value = row
  pwd.value = ''
  pwdVisible.value = true
}
const onPwd = async () => {
  await accountResetPwd({ id: pwdRow.value.id, password: pwd.value })
  ElMessage.success('密码已重置')
  pwdVisible.value = false
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除管理员「${row.username}」？`, '提示', { type: 'warning' })
  await accountDelete({ id: row.id })
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
