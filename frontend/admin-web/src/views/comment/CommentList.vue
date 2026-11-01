<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>评价管理</h2>
          <span class="dim small">每条评价只能有 1 条回复、可修改（BR-CM-03）；已退款订单行不可评价（BR-CM-05）</span>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <StatusTabs v-model="tab" :options="COMMENT_REPLY_TABS" @change="reload" />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="评价内容 / 商品名"
          clearable
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-input v-model="query.goodsId" placeholder="商品 ID" clearable style="width: 140px" @keyup.enter="reload" />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <span class="tiny dim">低分口径：1–3 星（BR-CM-02）</span>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="暂无评价数据">
        <el-table-column label="评分" width="130">
          <template #default="{ row }">
            <el-rate :model-value="Number(row.star) || 0" disabled />
            <div class="tiny dim">{{ row.star }} 星</div>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="110">
          <template #default="{ row }">{{ row.nickname || row.userId || '—' }}</template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="300">
          <template #default="{ row }">
            <div class="cell-text">{{ row.content }}</div>
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
              <span class="tiny dim ml6">{{ row.replyTime || '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.goodsName || '—' }}</div>
            <div v-if="row.orderSn" class="tiny dim mt6">{{ row.orderSn }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="addTime" label="评价时间" width="165" />
        <el-table-column label="回复状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="row.replied ? '已回复' : '待回复'" :tone="row.replied ? 'ok' : 'warn'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openReply(row)">
              {{ row.replied ? '改回复' : '回复' }}
            </el-button>
            <el-button link size="small" @click="toggleStatus(row)">
              {{ row.status === 0 ? '隐藏' : '显示' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="replyVisible" :title="replyForm.commentId ? '回复评价' : '回复评价'" width="520px">
      <div class="alert info">
        回复对买家可见；再次提交会覆盖原回复（BR-CM-03）。
      </div>
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
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { commentPage, commentReply, commentStatus } from '@/api/comment'
import { COMMENT_REPLY_TABS } from '@/constants/dict'
import StatusTabs from '@/components/StatusTabs.vue'
import StatusTag from '@/components/StatusTag.vue'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const tab = ref('')
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', goodsId: '' })

const replyVisible = ref(false)
const replying = ref(false)
const replyForm = reactive({ commentId: null, content: '' })

const params = computed(() => {
  const p = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.keyword) p.keyword = query.keyword.trim()
  if (query.goodsId) p.goodsId = query.goodsId.trim()
  if (tab.value === 'low') p.star = '1,2,3'
  if (tab.value === 'unreplied') p.replied = false
  if (tab.value === 'replied') p.replied = true
  return p
})

const load = async () => {
  loading.value = true
  try {
    const data = (await commentPage(params.value)) || {}
    list.value = data.list || []
    total.value = Number(data.total || 0)
  } catch {
    // 评价端点属 R2（03 §5.7），未部署时保持空态而不是报错刷屏
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  query.pageNum = 1
  load()
}

const resetQuery = () => {
  query.keyword = ''
  query.goodsId = ''
  tab.value = ''
  reload()
}

const onPageChange = (p) => {
  query.pageNum = p
  load()
}
const onSizeChange = (size) => {
  query.pageSize = size
  query.pageNum = 1
  load()
}

const openReply = (row) => {
  replyForm.commentId = row.id
  replyForm.content = row.adminContent || ''
  replyVisible.value = true
}

const submitReply = async () => {
  const content = replyForm.content.trim()
  if (!content) {
    ElMessage.warning('请填写回复内容')
    return
  }
  replying.value = true
  try {
    await commentReply({ commentId: replyForm.commentId, content })
    ElMessage.success('回复已保存')
    replyVisible.value = false
    load()
  } finally {
    replying.value = false
  }
}

const toggleStatus = async (row) => {
  const status = row.status === 0 ? 1 : 0
  await commentStatus({ commentId: row.id, status })
  ElMessage.success(status === 0 ? '评价已显示' : '评价已隐藏')
  load()
}

onMounted(load)
</script>

<style scoped>
.cell-text {
  white-space: pre-wrap;
  line-height: 1.5;
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
</style>