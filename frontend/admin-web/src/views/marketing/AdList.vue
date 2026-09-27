<template>
  <div>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="广告名称">
        <el-input v-model="query.name" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button type="success" @click="openDialog()">新增广告</el-button>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <el-image v-if="row.url" :src="row.url" fit="cover" style="width:80px;height:46px" />
        </template>
      </el-table-column>
      <el-table-column prop="link" label="跳转链接" show-overflow-tooltip />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑广告' : '新增广告'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="广告图片">
          <ImageUpload v-model="form.url" />
        </el-form-item>
        <el-form-item label="跳转链接"><el-input v-model="form.link" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" /></el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
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
import { adPage, adCreate, adUpdate, adDelete } from '@/api/marketing'
import ImageUpload from '@/components/ImageUpload.vue'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, name: '' })
const emptyForm = () => ({ id: null, name: '', url: '', link: '', content: '', position: 1, enabled: true })
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const data = (await adPage(query)) || {}
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
  if (form.id) await adUpdate(form)
  else await adCreate(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除广告「${row.name}」？`, '提示', { type: 'warning' })
  await adDelete({ id: row.id })
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
