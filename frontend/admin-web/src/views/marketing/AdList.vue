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
      <el-table-column label="跳转类型" width="90">
        <template #default="{ row }">{{ linkTypeText(row.linkType) }}</template>
      </el-table-column>
      <el-table-column label="目标 ID" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.link || '—' }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序值" width="80" />
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑广告' : '新增广告'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" @submit.prevent>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入广告名称" />
        </el-form-item>
        <el-form-item label="图片地址" prop="url">
          <el-input v-model="form.url" placeholder="https://cdn/.../a.jpg（必填）" />
        </el-form-item>
        <el-form-item label="跳转类型">
          <el-select v-model="form.linkType" style="width: 100%" @change="onLinkTypeChange">
            <el-option v-for="opt in linkTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标 ID" prop="link">
          <el-input
            v-model="form.link"
            :disabled="targetDisabled"
            :placeholder="targetDisabled ? '无跳转无需填写' : '商品/专题 ID（纯数字）'"
            clearable
          />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="0" :precision="0" controls-position="right" />
          <span style="margin-left:8px;color:#909399;font-size:12px">越小越靠前，默认 0</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空不限"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空不限"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="2" />
        </el-form-item>
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adPage, adCreate, adUpdate, adDelete } from '@/api/marketing'

// 与后端 AdDTO.linkType 对齐：0 无跳转 / 1 商品 / 2 专题（BR-CMS-02）
const linkTypeOptions = [
  { value: 0, label: '无跳转' },
  { value: 1, label: '商品' },
  { value: 2, label: '专题' },
]
const linkTypeText = (v) => {
  if (v === null || v === undefined || v === '') return '—'
  return linkTypeOptions.find((o) => o.value === Number(v))?.label ?? '—'
}

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const query = reactive({ pageNum: 1, pageSize: 10, name: '' })
// position=1 是首页轮播（后端 AdServiceImpl.POSITION_HOME_BANNER_VALUE）；表单不暴露但提交必须带上，否则 C 端按 position=1 查不到
const emptyForm = () => ({
  id: null,
  name: '',
  url: '',
  linkType: 0,
  link: '',
  sortOrder: 0,
  position: 1,
  content: '',
  startTime: null,
  endTime: null,
  enabled: true,
})
const form = reactive(emptyForm())

const targetDisabled = computed(() => Number(form.linkType) === 0)

// 图片地址必须是完整 http(s) URL：C 端按 row.url 直接渲染、后端不拼前缀（§7.4 示例为 https 全路径），也避免 /pages/xxx 之类脏值
const validateUrl = (rule, value, callback) => {
  if (!/^https?:\/\/\S+/i.test(String(value ?? '').trim())) {
    callback(new Error('图片地址必填，且需以 http:// 或 https:// 开头'))
    return
  }
  callback()
}
// 与后端 normalizeLink 的 \d+ 校验对齐：linkType=1/2 时目标 ID 必须非空纯数字，避免脏路径入库触发 400 PARAM_INVALID
const validateTargetId = (rule, value, callback) => {
  const t = Number(form.linkType)
  if (t === 1 || t === 2) {
    if (!/^\d+$/.test(String(value ?? '').trim())) {
      callback(new Error('目标 ID 必须是非空数字（商品/专题 ID）'))
      return
    }
  }
  callback()
}
const rules = {
  name: [{ required: true, whitespace: true, message: '请输入广告名称', trigger: 'blur' }],
  url: [{ validator: validateUrl, trigger: 'blur' }],
  link: [{ validator: validateTargetId, trigger: 'blur' }],
}

// 后端 LocalDateTime 可能序列化成带 T 的 ISO 或带毫秒；统一成 yyyy-MM-dd HH:mm:ss 才能被 date-picker 的 value-format 正确回填
const normalizeTime = (v) => (typeof v === 'string' && v ? v.replace('T', ' ').slice(0, 19) : null)

const load = async () => {
  loading.value = true
  try {
    const data = (await adPage(query)) || {}
    // 后端已按 sortOrder ASC, id DESC 排序，前端不再做冲突排序，直接按返回顺序渲染
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}
const reload = () => { query.pageNum = 1; load() }
const onPageChange = (p) => { query.pageNum = p; load() }

const openDialog = (row) => {
  // 先用空表单重置再逐字段回填，避免上次编辑的残留；只取 AdDTO 认识的字段，不把 addTime/deleted 等带进表单
  Object.assign(form, emptyForm(), row ? {
    id: row.id,
    name: row.name ?? '',
    url: row.url ?? '',
    linkType: row.linkType ?? 0,
    link: row.link ?? '',
    sortOrder: row.sortOrder ?? 0,
    position: row.position ?? 1,
    content: row.content ?? '',
    startTime: normalizeTime(row.startTime),
    endTime: normalizeTime(row.endTime),
    enabled: row.enabled ?? true,
  } : {})
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const onLinkTypeChange = (val) => {
  // 无跳转：清掉目标 ID，避免旧的 /pages/xxx 或脏值随提交带入库（后端 linkType=0 也会把 link 置空）
  if (Number(val) === 0) form.link = ''
  nextTick(() => formRef.value?.clearValidate(['link']))
}

const onSave = async () => {
  await formRef.value.validate()
  // 只提交后端 AdDTO 认识的字段：空时间窗归一为 null（后端「空即不限」），link 保持字符串避免 Long 精度丢失
  const payload = {
    id: form.id,
    name: form.name.trim(),
    url: form.url.trim(),
    linkType: Number(form.linkType),
    link: Number(form.linkType) === 0 ? null : String(form.link).trim(),
    sortOrder: Number(form.sortOrder ?? 0),
    position: form.position ?? 1,
    content: form.content ?? '',
    startTime: form.startTime || null,
    endTime: form.endTime || null,
    enabled: Boolean(form.enabled),
  }
  if (form.id) await adUpdate(payload)
  else await adCreate(payload)
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
