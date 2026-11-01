<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>公告管理</h2>
          <span class="dim small">停用后前台不再展示；本期不提供删除</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建公告</el-button>
      </div>

      <div class="toolbar mt10">
        <el-input
          v-model="state.keyword"
          placeholder="标题关键字"
          clearable
          style="width: 220px"
          :maxlength="50"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="state.enabled" style="width: 140px" @change="reload">
          <el-option label="全部状态" value="" />
          <el-option label="已启用" value="true" />
          <el-option label="已停用" value="false" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <div v-if="errorTip" class="alert err mt10">
        <div class="between">
          <span>{{ errorTip }}</span>
          <el-button link size="small" @click="errorTip = ''">关闭</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="暂无公告">
        <el-table-column label="标题" min-width="240">
          <template #default="{ row }">
            <div class="bold">{{ row.title }}</div>
            <div class="tiny dim ellipsis" style="max-width: 420px">{{ row.content || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :text="row.enabled ? '已启用' : '已停用'" :tone="row.enabled ? 'ok' : 'gray'" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="small dim">{{ fmtTime(row.addTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            <span class="small dim">{{ fmtTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.enabled ? 'warning' : 'success'"
              size="small"
              :loading="togglingId === String(row.id)"
              @click="onToggle(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="state.pageNum"
          v-model:page-size="state.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑公告' : '新建公告'"
      width="640px"
      :close-on-click-modal="false"
    >
      <div v-if="formError" class="alert err">{{ formError }}</div>
      <el-form :model="form" label-width="80px" @submit.prevent>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="1–100 字" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="≤2000 字，选填"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { noticeCreate, noticePage, noticeUpdate } from '@/api/marketing'
import { fmtTime } from '@/utils/format'
import { useListQuery } from '@/utils/useListQuery'
import StatusTag from '@/components/StatusTag.vue'

const { state, reset } = useListQuery(
  { pageNum: 1, pageSize: 10, keyword: '', enabled: '' },
  { onSync: () => load() },
)

const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorTip = ref('')
const togglingId = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = reactive({ id: null, title: '', content: '', enabled: true, updateTime: '' })

const load = async () => {
  loading.value = true
  try {
    const data = await noticePage(
      {
        pageNum: state.pageNum,
        pageSize: state.pageSize,
        keyword: state.keyword || undefined,
        enabled: state.enabled === '' ? undefined : state.enabled === 'true',
      },
      { silent: true },
    )
    list.value = data?.list || []
    total.value = Number(data?.total || 0)
    errorTip.value = ''
  } catch (err) {
    list.value = []
    total.value = 0
    errorTip.value = err?.message || '公告列表加载失败'
  } finally {
    loading.value = false
  }
}

const reload = () => {
  state.pageNum = 1
  load()
}

const onSizeChange = () => {
  state.pageNum = 1
  load()
}

const onReset = () => {
  reset()
  state.pageNum = 1
  load()
}

const openCreate = () => {
  form.id = null
  form.title = ''
  form.content = ''
  form.enabled = true
  form.updateTime = ''
  formError.value = ''
  dialogVisible.value = true
}

const openEdit = (row) => {
  form.id = row.id
  form.title = row.title || ''
  form.content = row.content || ''
  form.enabled = row.enabled !== false
  // CAS 必须回传后端原始 updateTime（ISO 串），不能传格式化后的展示值
  form.updateTime = row.updateTime || ''
  formError.value = ''
  dialogVisible.value = true
}

const submitForm = async () => {
  formError.value = ''
  const title = form.title.trim()
  if (!title) {
    formError.value = '请填写公告标题'
    return
  }
  submitting.value = true
  try {
    if (form.id) {
      await noticeUpdate(
        {
          id: form.id,
          title,
          content: form.content.trim(),
          enabled: form.enabled,
          updateTime: form.updateTime || undefined,
        },
        { silent: true },
      )
      ElMessage.success('公告已保存')
    } else {
      await noticeCreate({ title, content: form.content.trim(), enabled: form.enabled }, { silent: true })
      ElMessage.success('公告已创建')
    }
    dialogVisible.value = false
    load()
  } catch (err) {
    // 409 CONCURRENT_MODIFIED「内容已被他人修改，请刷新后重试」
    formError.value = err?.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

const onToggle = async (row) => {
  const next = !row.enabled
  try {
    await ElMessageBox.confirm(
      `确定${next ? '启用' : '停用'}公告「${row.title}」？${next ? '' : '停用后前台立即不再展示。'}`,
      `${next ? '启用' : '停用'}确认`,
      { type: 'warning' },
    )
  } catch {
    return
  }
  togglingId.value = String(row.id)
  errorTip.value = ''
  try {
    await noticeUpdate({ id: row.id, enabled: next, updateTime: row.updateTime || undefined }, { silent: true })
    ElMessage.success(next ? '公告已启用' : '公告已停用')
    load()
  } catch (err) {
    errorTip.value = err?.message || '操作失败'
  } finally {
    togglingId.value = ''
  }
}

onMounted(load)
</script>