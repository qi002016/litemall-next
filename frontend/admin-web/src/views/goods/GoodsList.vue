<template>
  <div>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="商品名称">
        <el-input v-model="query.name" placeholder="请输入" clearable />
      </el-form-item>
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button type="success" @click="openDialog()">新增商品</el-button>
    </el-form>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="商品名称" />
      <el-table-column prop="brief" label="简介" show-overflow-tooltip />
      <el-table-column prop="minPrice" label="价格" width="100" />
      <el-table-column prop="retailOnSale" label="上架" width="80">
        <template #default="{ row }">
          <el-tag :type="row.onSale ? 'success' : 'info'">{{ row.onSale ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品' : '新增商品'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.brief" /></el-form-item>
        <el-form-item label="价格"><el-input v-model="form.minPrice" /></el-form-item>
        <el-form-item label="商品图"><ImageUpload v-model="form.picUrl" /></el-form-item>
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
import { goodsPage, goodsCreate, goodsUpdate, goodsDelete } from '@/api/goods'
import ImageUpload from '@/components/ImageUpload.vue'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, name: '' })
const form = reactive({ id: null, name: '', brief: '', minPrice: 0 })

const load = async () => {
  loading.value = true
  try {
    const data = (await goodsPage(query)) || {}
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

const openDialog = (row) => {
  Object.assign(form, row ? { ...row } : { id: null, name: '', brief: '', minPrice: 0 })
  dialogVisible.value = true
}
const onSave = async () => {
  if (form.id) await goodsUpdate(form)
  else await goodsCreate(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除商品「${row.name}」？`, '提示', { type: 'warning' })
  await goodsDelete({ id: row.id })
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
