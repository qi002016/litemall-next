<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>账号管理</h2>
          <span class="dim small">禁用后该账号旧会话立即失效；不能禁用自己，也不能禁用最后一个启用账号</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增管理员</el-button>
      </div>

      <div class="toolbar mt10">
        <el-input
          v-model="state.username"
          placeholder="账号名"
          clearable
          style="width: 200px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <div v-if="errorTip" class="alert err mt10">
        <div class="between">
          <span>{{ errorTip }}</span>
          <el-button link size="small" @click="errorTip = ''">关闭</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="暂无管理员">
        <el-table-column prop="id" label="ID" width="80">
          <template #default="{ row }">
            <span class="small dim">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账号" min-width="200">
          <template #default="{ row }">
            <div class="row">
              <el-avatar v-if="row.avatar" :src="row.avatar" :size="22" />
              <span class="bold">{{ row.username }}</span>
              <span v-if="isSelf(row)" class="tag info">当前登录</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :text="accountStatusMeta(row).text" :tone="accountStatusMeta(row).tone" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="small dim">{{ fmtTime(row.addTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 0 || String(row.status) === '0'"
              content="启用账号"
              placement="top"
            >
              <el-button link type="success" size="small" :loading="togglingId === String(row.id)" @click="onToggle(row, 1)">
                启用
              </el-button>
            </el-tooltip>
            <el-tooltip v-else :content="isSelf(row) ? '不能禁用自己' : '禁用账号（旧会话立即失效）'" placement="top">
              <span>
                <el-button
                  link
                  type="warning"
                  size="small"
                  :disabled="isSelf(row)"
                  :loading="togglingId === String(row.id)"
                  @click="onToggle(row, 0)"
                >
                  禁用
                </el-button>
              </span>
            </el-tooltip>
            <el-button link type="primary" size="small" @click="openPwd(row)">重置密码</el-button>
            <el-button link type="danger" size="small" :disabled="isSelf(row)" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增管理员" width="460px" :close-on-click-modal="false">
      <div v-if="formError" class="alert err">{{ formError }}</div>
      <el-form :model="form" label-width="90px" @submit.prevent>
        <el-form-item label="账号" required>
          <el-input v-model="form.username" maxlength="32" placeholder="登录账号，不可重复" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="form.password" type="password" show-password maxlength="32" placeholder="6–32 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="重置密码" width="440px" :close-on-click-modal="false">
      <div v-if="formError" class="alert err">{{ formError }}</div>
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="账号">
          <span>{{ pwdRow?.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwd" type="password" show-password maxlength="32" placeholder="6–32 位" />
        </el-form-item>
      </el-form>
      <div class="tiny dim">重置后该账号旧会话立即失效，需要用新密码重新登录。</div>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { accountCreate, accountDelete, accountPage, accountResetPwd, accountStatus } from '@/api/system'
import { accountStatusMeta } from '@/constants/dict'
import { fmtTime } from '@/utils/format'
import { useListQuery } from '@/utils/useListQuery'
import { useUserStore } from '@/store/user'
import StatusTag from '@/components/StatusTag.vue'

const userStore = useUserStore()
const meId = computed(() => (userStore.userInfo?.id == null ? '' : String(userStore.userInfo.id)))

const { state, reset } = useListQuery({ pageNum: 1, pageSize: 10, username: '' }, { onSync: () => load() })

const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorTip = ref('')
const togglingId = ref('')

const dialogVisible = ref(false)
const pwdVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = reactive({ username: '', password: '' })
const pwd = ref('')
const pwdRow = ref(null)

const isSelf = (row) => !!meId.value && String(row.id) === meId.value

const load = async () => {
  loading.value = true
  try {
    const data = await accountPage(
      { pageNum: state.pageNum, pageSize: state.pageSize, username: state.username || undefined },
      { silent: true },
    )
    list.value = data?.list || []
    total.value = Number(data?.total || 0)
    errorTip.value = ''
  } catch (err) {
    list.value = []
    total.value = 0
    errorTip.value = err?.message || '账号列表加载失败'
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
  form.username = ''
  form.password = ''
  formError.value = ''
  dialogVisible.value = true
}

const onSave = async () => {
  formError.value = ''
  const username = form.username.trim()
  if (!username) {
    formError.value = '请填写账号'
    return
  }
  if (!form.password || form.password.length < 6 || form.password.length > 32) {
    formError.value = '密码长度需为 6-32 位'
    return
  }
  submitting.value = true
  try {
    await accountCreate({ username, password: form.password }, { silent: true })
    ElMessage.success('管理员已创建')
    dialogVisible.value = false
    load()
  } catch (err) {
    // 409 ADMIN_USERNAME_DUPLICATED「账号名已存在」
    formError.value = err?.message || '创建失败'
  } finally {
    submitting.value = false
  }
}

const openPwd = (row) => {
  pwdRow.value = row
  pwd.value = ''
  formError.value = ''
  pwdVisible.value = true
}

const onPwd = async () => {
  formError.value = ''
  if (!pwd.value || pwd.value.length < 6 || pwd.value.length > 32) {
    formError.value = '密码长度需为 6-32 位'
    return
  }
  submitting.value = true
  try {
    await accountResetPwd({ id: pwdRow.value.id, password: pwd.value }, { silent: true })
    ElMessage.success('密码已重置，旧会话已失效')
    pwdVisible.value = false
  } catch (err) {
    formError.value = err?.message || '重置失败'
  } finally {
    submitting.value = false
  }
}

const onToggle = async (row, target) => {
  const label = target === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${label}账号「${row.username}」？${target === 0 ? '禁用后该账号旧会话会立即失效。' : ''}`,
      `${label}确认`,
      { type: 'warning' },
    )
  } catch {
    return
  }
  togglingId.value = String(row.id)
  errorTip.value = ''
  try {
    await accountStatus({ id: row.id, status: target }, { silent: true })
    ElMessage.success(`账号已${label}`)
    load()
  } catch (err) {
    // 403 ADMIN_SELF_FORBIDDEN / 409 ADMIN_LAST_ENABLED / 404
    errorTip.value = err?.message || `${label}失败`
  } finally {
    togglingId.value = ''
  }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除管理员「${row.username}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  errorTip.value = ''
  try {
    await accountDelete({ id: row.id }, { silent: true })
    ElMessage.success('已删除')
    load()
  } catch (err) {
    errorTip.value = err?.message || '删除失败'
  }
}

onMounted(load)
</script>