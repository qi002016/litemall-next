<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>评价管理</h2>
          <span class="dim small">每条评价只能有 1 条回复、可修改（BR-CM-03）；隐藏后买家端不再展示</span>
        </div>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>

      <StatusTabs v-model="state.replied" :options="COMMENT_REPLY_TABS" @change="reload" />

      <div class="toolbar">
        <el-input
          v-model="state.keyword"
          placeholder="评价内容 / 商品名"
          clearable
          style="width: 220px"
          :maxlength="50"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-input
          v-model="state.goodsId"
          placeholder="商品 ID"
          clearable
          style="width: 140px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select
          v-model="state.star"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          placeholder="星级（可多选）"
          style="width: 170px"
          @change="reload"
        >
          <el-option v-for="opt in COMMENT_STAR_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="state.status" placeholder="可见状态" clearable style="width: 130px" @change="reload">
          <el-option label="显示中" value="0" />
          <el-option label="已隐藏" value="1" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="评价开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
          :clearable="true"
          @change="reload"
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

      <el-table
        v-loading="loading"
        :data="list"
        class="tbl-wrap"
        :row-class-name="rowClass"
        empty-text="暂无评价数据"
      >
        <el-table-column label="评分" width="130">
          <template #default="{ row }">
            <el-rate :model-value="Number(row.star) || 0" disabled />
            <div class="tiny dim">{{ row.star }} 星</div>
          </template>
        </el-table-column>
        <el-table-column label="可见" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :text="commentStatusMeta(row).text" :tone="commentStatusMeta(row).tone" />
          </template>
        </el-table-column>
        <el-table-column label="用户" width="110">
          <template #default="{ row }">{{ row.nickname || row.userId || '—' }}</template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="300">
          <template #default="{ row }">
            <div class="cell-text">
              <span v-if="String(row.status) === '1'" class="tag danger hidden-flag">已隐藏</span>
              {{ row.content }}
            </div>
            <div v-if="row.picUrls?.length" class="row wrap mt6">
              <el-image
                v-for="(p, i) in row.picUrls"
                :key="i"
                :src="p"
                :preview-src-list="row.picUrls"
                :initial-index="i"
                fit="cover"
                class="pic"
              />
            </div>
            <div v-if="row.adminContent" class="reply mt6">
              <span class="tag outline">商家回复</span>
              <span class="small">{{ row.adminContent }}</span>
              <span class="tiny dim ml6">{{ fmtTime(row.replyTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.goodsName || '—' }}</div>
            <div v-if="row.orderSn" class="tiny dim mt6">{{ row.orderSn }}</div>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="165">
          <template #default="{ row }">
            <span class="small dim">{{ fmtTime(row.addTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="回复状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.replied ? '已回复' : '待回复'" :tone="row.replied ? 'ok' : 'warn'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openReply(row)">
              {{ row.replied ? '改回复' : '回复' }}
            </el-button>
            <el-button
              link
              :type="String(row.status) === '1' ? 'success' : 'warning'"
              size="small"
              :loading="statusLoadingId === String(row.id)"
              @click="toggleStatus(row)"
            >
              {{ String(row.status) === '1' ? '恢复' : '隐藏' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="state.pageNum"
          v-model:page-size="state.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="replyVisible" title="回复评价" width="520px" :close-on-click-modal="false">
      <div class="alert info">回复对买家可见；再次提交会覆盖原回复（BR-CM-03）。</div>
      <div v-if="replyError" class="alert err">{{ replyError }}</div>
      <el-input
        v-model="replyForm.content"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="请输入回复内容（1–500 字）"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replying" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { commentPage, commentReply, commentStatus } from '@/api/comment'
import { COMMENT_REPLY_TABS, COMMENT_STAR_OPTIONS, commentStatusMeta } from '@/constants/dict'
import { fmtTime } from '@/utils/format'
import { useListQuery } from '@/utils/useListQuery'
import StatusTabs from '@/components/StatusTabs.vue'
import StatusTag from '@/components/StatusTag.vue'

const { state, reset } = useListQuery(
  {
    pageNum: 1,
    pageSize: 10,
    keyword: '',
    goodsId: '',
    star: [],
    status: '',
    replied: '',
    dateFrom: '',
    dateTo: '',
  },
  { onSync: () => load() },
)

const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorTip = ref('')
const statusLoadingId = ref('')

const replyVisible = ref(false)
const replying = ref(false)
const replyError = ref('')
const replyForm = reactive({ commentId: null, content: '' })

/** 日期选择器 ↔ URL 里的 dateFrom/dateTo（只存 yyyy-MM-dd，提交时补时分秒） */
const dateRange = computed({
  get: () => (state.dateFrom && state.dateTo ? [state.dateFrom, state.dateTo] : null),
  set: (value) => {
    if (Array.isArray(value) && value.length === 2) {
      state.dateFrom = value[0]
      state.dateTo = value[1]
    } else {
      state.dateFrom = ''
      state.dateTo = ''
    }
  },
})

const params = computed(() => {
  const p = { pageNum: state.pageNum, pageSize: state.pageSize }
  if (state.keyword) p.keyword = state.keyword.trim()
  if (state.goodsId) p.goodsId = state.goodsId.trim()
  if (state.star?.length) p.star = state.star.join(',')
  if (state.status !== '' && state.status != null) p.status = String(state.status)
  if (state.replied === 'unreplied') p.replied = false
  if (state.replied === 'replied') p.replied = true
  // 后端严格按 yyyy-MM-dd HH:mm:ss 解析，必须补时分秒
  if (state.dateFrom) p.dateFrom = `${state.dateFrom} 00:00:00`
  if (state.dateTo) p.dateTo = `${state.dateTo} 23:59:59`
  return p
})

const rowClass = ({ row }) => (String(row.status) === '1' ? 'row-hidden' : '')

const load = async () => {
  loading.value = true
  try {
    const data = await commentPage(params.value, { silent: true })
    list.value = data?.list || []
    total.value = Number(data?.total || 0)
    errorTip.value = ''
  } catch (err) {
    list.value = []
    total.value = 0
    errorTip.value = err?.message || '评价列表加载失败'
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

const openReply = (row) => {
  replyForm.commentId = row.id
  replyForm.content = row.adminContent || ''
  replyError.value = ''
  replyVisible.value = true
}

const submitReply = async () => {
  const content = replyForm.content.trim()
  if (!content) {
    ElMessage.warning('请填写回复内容')
    return
  }
  replying.value = true
  replyError.value = ''
  try {
    await commentReply({ commentId: replyForm.commentId, content }, { silent: true })
    ElMessage.success('回复已保存')
    replyVisible.value = false
    load()
  } catch (err) {
    // 404 RESOURCE_NOT_FOUND / 400 PARAM_INVALID
    replyError.value = err?.message || '回复失败'
  } finally {
    replying.value = false
  }
}

const toggleStatus = async (row) => {
  const hidden = String(row.status) === '1'
  const target = hidden ? 0 : 1
  try {
    await ElMessageBox.confirm(
      hidden ? '确定恢复该评价？恢复后买家端会重新展示。' : '确定隐藏该评价？隐藏后买家端不再展示。',
      hidden ? '恢复确认' : '隐藏确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  statusLoadingId.value = String(row.id)
  errorTip.value = ''
  try {
    await commentStatus({ commentId: row.id, status: target }, { silent: true })
    ElMessage.success(hidden ? '评价已恢复显示' : '评价已隐藏')
    load()
  } catch (err) {
    // 409 COMMENT_STATUS_CONFLICT：状态已被他人改动
    errorTip.value = err?.message || '操作失败'
  } finally {
    statusLoadingId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.cell-text {
  white-space: pre-wrap;
  line-height: 1.5;
}
.hidden-flag {
  margin-right: 6px;
}
.pic {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  border: 1px solid var(--line);
}
.reply {
  background: #fafbfc;
  border-left: 2px solid var(--line-2);
  padding: 6px 8px;
  border-radius: 4px;
}
.ml6 {
  margin-left: 6px;
}
:deep(.row-hidden) {
  color: #9aa4af;
  background: #fcfcfd;
}
</style>