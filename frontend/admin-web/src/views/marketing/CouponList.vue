<template>
  <div>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="优惠券名称">
        <el-input v-model="query.name" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button type="success" @click="openDialog()">新增优惠券</el-button>
    </el-form>

    <el-table :data="list" border v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="tag" label="标签" width="100" />
      <el-table-column prop="discount" label="优惠金额" width="90" />
      <el-table-column prop="min" label="最低消费" width="90" />
      <el-table-column prop="total" label="总量" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '可用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑优惠券' : '新增优惠券'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tag" /></el-form-item>
        <el-form-item label="介绍"><el-input v-model="form.desc" /></el-form-item>
        <el-form-item label="优惠金额"><el-input v-model="form.discount" type="number" /></el-form-item>
        <el-form-item label="最低消费"><el-input v-model="form.min" type="number" /></el-form-item>
        <el-form-item label="总量(0无限)"><el-input v-model="form.total" type="number" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { couponPage, couponCreate, couponUpdate, couponDelete } from '@/api/marketing'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, name: '' })
const emptyForm = () => ({ id: null, name: '', tag: '', desc: '', discount: 0, min: 0, total: 0 })
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const data = (await couponPage(query)) || {}
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const reload = () => { query.pageNum = 1; load() }
const onPageChange = (p) => { query.pageNum = p; load() }

const openDialog = (row) => {
  Object.assign(form, row ? { ...row } : emptyForm())
  dialogVisible.value = true
}
const onSave = async () => {
  if (form.id) await couponUpdate(form)
  else await couponCreate(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除优惠券「${row.name}」？`, '提示', { type: 'warning' })
  await couponDelete({ id: row.id })
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
