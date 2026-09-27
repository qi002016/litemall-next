<template>
  <div>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="专题标题">
        <el-input v-model="query.title" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button type="success" @click="openDialog()">新增专题</el-button>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <el-image v-if="row.picUrl" :src="row.picUrl" fit="cover" style="width:70px;height:44px" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="160" />
      <el-table-column prop="subtitle" label="子标题" show-overflow-tooltip />
      <el-table-column prop="price" label="最低价" width="90" />
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑专题' : '新增专题'" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="子标题"><el-input v-model="form.subtitle" /></el-form-item>
        <el-form-item label="封面图"><el-input v-model="form.picUrl" /></el-form-item>
        <el-form-item label="最低价"><el-input v-model="form.price" type="number" /></el-form-item>
        <el-form-item label="阅读量"><el-input v-model="form.readCount" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="3" /></el-form-item>
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
import { topicPage, topicCreate, topicUpdate, topicDelete } from '@/api/marketing'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, title: '' })
const emptyForm = () => ({ id: null, title: '', subtitle: '', picUrl: '', price: 0, readCount: '0', content: '' })
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const data = (await topicPage(query)) || {}
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
  if (form.id) await topicUpdate(form)
  else await topicCreate(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除专题「${row.title}」？`, '提示', { type: 'warning' })
  await topicDelete({ id: row.id })
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
