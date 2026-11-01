<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>品牌管理</h2>
          <span class="dim small">品牌名全局唯一；仍有商品使用的品牌不能删除</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建品牌</el-button>
      </div>

      <div class="toolbar mt10">
        <el-input
          v-model="state.keyword"
          placeholder="品牌名关键字"
          clearable
          style="width: 200px"
          :maxlength="50"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="state.sortBy" style="width: 150px" @change="reload">
          <el-option label="按排序号" value="sortOrder" />
          <el-option label="按创建时间" value="addTime" />
          <el-option label="按品牌名" value="name" />
        </el-select>
        <el-select v-model="state.isAsc" style="width: 110px" @change="reload">
          <el-option label="升序" value="asc" />
          <el-option label="降序" value="desc" />
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

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="暂无品牌">
        <el-table-column label="品牌" min-width="200">
          <template #default="{ row }">
            <div class="row">
              <el-image
                v-if="row.picUrl"
                :src="row.picUrl"
                fit="cover"
                class="thumb"
                :preview-src-list="[row.picUrl]"
                preview-teleported
              />
              <span v-else class="thumb thumb-empty tiny dim">无图</span>
              <div>
                <div class="bold">{{ row.name }}</div>
                <div class="tiny dim ellipsis" style="max-width: 220px">{{ row.desc || '—' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品数" width="90" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.goodsCount ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最低价" width="110" align="right">
          <template #default="{ row }">
            <span class="price">{{ money(row.floorPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80" align="center">
          <template #default="{ row }">{{ row.sortOrder ?? 0 }}</template>
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
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
      :title="form.id ? '编辑品牌' : '新建品牌'"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-if="formError" class="alert err">{{ formError }}</div>
      <el-form :model="form" label-width="90px" @submit.prevent>
        <el-form-item label="品牌名称" required>
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="1–64 字，不可与已有品牌重名" />
        </el-form-item>
        <el-form-item label="最低价">
          <el-input-number
            v-model="form.floorPriceYuan"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
          />
          <span class="tiny dim ml8">单位：元（提交时换算为整数分）</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :precision="0" controls-position="right" />
          <span class="tiny dim ml8">值越小越靠前</span>
        </el-form-item>
        <el-form-item label="品牌图片">
          <ImageUpload v-model="form.picUrl" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.desc"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="≤255，选填"
          />
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
import { adminBrandCreate, adminBrandDelete, adminBrandPage, adminBrandUpdate } from '@/api/catalog'
import { fen2yuan, fmtTime, money, yuan2fen } from '@/utils/format'
import { useListQuery } from '@/utils/useListQuery'
import ImageUpload from '@/components/ImageUpload.vue'

const { state, reset } = useListQuery(
  { pageNum: 1, pageSize: 10, keyword: '', sortBy: 'sortOrder', isAsc: 'asc' },
  { onSync: () => load() },
)

const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorTip = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = reactive({
  id: null,
  name: '',
  desc: '',
  picUrl: '',
  sortOrder: 0,
  floorPriceYuan: 0,
  updateTime: '',
})

const load = async () => {
  loading.value = true
  try {
    const data = await adminBrandPage(
      {
        pageNum: state.pageNum,
        pageSize: state.pageSize,
        keyword: state.keyword || undefined,
        sortBy: state.sortBy || undefined,
        isAsc: state.isAsc !== 'desc',
      },
      { silent: true },
    )
    list.value = data?.list || []
    total.value = Number(data?.total || 0)
    errorTip.value = ''
  } catch (err) {
    list.value = []
    total.value = 0
    errorTip.value = err?.message || '品牌列表加载失败'
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
  form.name = ''
  form.desc = ''
  form.picUrl = ''
  form.sortOrder = 0
  form.floorPriceYuan = 0
  form.updateTime = ''
  formError.value = ''
  dialogVisible.value = true
}

const openEdit = (row) => {
  form.id = row.id
  form.name = row.name || ''
  form.desc = row.desc || ''
  form.picUrl = row.picUrl || ''
  form.sortOrder = Number(row.sortOrder || 0)
  form.floorPriceYuan = Number(fen2yuan(row.floorPrice, 0))
  form.updateTime = row.updateTime || ''
  formError.value = ''
  dialogVisible.value = true
}

const submitForm = async () => {
  formError.value = ''
  const name = form.name.trim()
  if (!name) {
    formError.value = '请填写品牌名称'
    return
  }
  const floorPrice = yuan2fen(form.floorPriceYuan)
  if (Number.isNaN(floorPrice)) {
    formError.value = '最低价格式不正确'
    return
  }
  const payload = {
    name,
    sortOrder: Number(form.sortOrder) || 0,
    floorPrice,
    picUrl: form.picUrl || undefined,
    desc: form.desc.trim() || undefined,
  }
  submitting.value = true
  try {
    if (form.id) {
      await adminBrandUpdate({ id: form.id, ...payload, updateTime: form.updateTime || undefined }, { silent: true })
      ElMessage.success('品牌已保存')
    } else {
      await adminBrandCreate(payload, { silent: true })
      ElMessage.success('品牌已创建')
    }
    dialogVisible.value = false
    load()
  } catch (err) {
    // 409 BRAND_NAME_DUPLICATED「品牌名已存在」/ 409 CONCURRENT_MODIFIED 原样展示
    formError.value = err?.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除品牌「${row.name}」？品牌下若仍有商品，删除会被拒绝。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  errorTip.value = ''
  try {
    await adminBrandDelete({ id: row.id }, { silent: true })
    ElMessage.success('品牌已删除')
    load()
  } catch (err) {
    // 409 BRAND_IN_USE ⇒「请先移走商品」
    errorTip.value = err?.message || '删除失败'
  }
}

onMounted(load)
</script>

<style scoped>
.thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1px solid var(--line);
  flex: none;
}
.thumb-empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}
.ml8 {
  margin-left: 8px;
}
</style>