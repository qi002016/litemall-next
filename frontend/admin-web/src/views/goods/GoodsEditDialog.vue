<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑商品' : '新建商品'"
    width="980px"
    top="4vh"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div v-loading="loading" class="goods-form">
      <div v-if="loadError" class="alert err">
        <div>
          商品 SKU 数据加载失败（{{ loadError }}）。可继续编辑基础字段；为避免误删，本次保存<b>不会提交 SKU 矩阵</b>。
        </div>
      </div>

      <div class="grid2">
        <div class="field span2">
          <label>商品名称 <span class="danger">*</span></label>
          <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="1–120 字" />
        </div>

        <div class="field span2">
          <label>商品简介</label>
          <el-input v-model="form.brief" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="≤255 字" />
        </div>

        <div class="field">
          <label>分类 <span class="danger">*</span></label>
          <el-select v-model="form.categoryId" filterable placeholder="选择末级分类" style="width: 100%">
            <el-option v-for="c in leafCategories" :key="c.id" :label="c.path" :value="c.id" />
          </el-select>
          <div v-if="isEdit && categoryFallback" class="tiny dim mt6">当前分类：{{ categoryFallback }}</div>
        </div>

        <div class="field">
          <label>品牌</label>
          <el-select v-model="form.brandId" filterable clearable placeholder="选择品牌（可空）" style="width: 100%">
            <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </div>

        <div class="field">
          <label>商品状态</label>
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="草稿" :value="0" />
            <el-option label="在售" :value="1" />
            <el-option label="已下架" :value="2" />
          </el-select>
        </div>

        <div class="field">
          <label>商品主图 <span class="danger">*</span></label>
          <ImageUpload v-model="form.picUrl" />
        </div>

        <div class="field span2">
          <label>商品详情（HTML，选填）</label>
          <el-input v-model="form.detail" type="textarea" :rows="3" maxlength="20000" placeholder="≤20000 字" />
        </div>
      </div>

      <div class="divider" />

      <div class="between">
        <h3 class="section-title">规格与 SKU 矩阵</h3>
        <div class="dim tiny">价格按元填写、提交转分；库存为实际库存，可售由后端按锁定量派生</div>
      </div>

      <div v-if="!isEdit" class="spec-editor mt10">
        <div v-for="(dim, i) in dimensions" :key="i" class="spec-dim">
          <el-input v-model="dim.name" placeholder="规格名，如 颜色" style="width: 180px" maxlength="32" />
          <el-input
            v-model="dim.valuesText"
            placeholder="规格值，用逗号分隔，如 白,黑"
            style="flex: 1"
            @change="markDirty"
          />
          <el-button link type="danger" :disabled="dimensions.length === 1" @click="removeDimension(i)">删除</el-button>
        </div>
        <div class="row mt8">
          <el-button :disabled="dimensions.length >= 2" @click="addDimension">＋ 新增规格维度</el-button>
          <el-button type="primary" plain @click="buildMatrix">生成 SKU 矩阵</el-button>
          <span class="tiny dim">两维规格生成笛卡尔积，每个组合各占矩阵一行；至少保留 1 个 SKU</span>
        </div>
      </div>

      <div v-else class="spec-readonly mt10">
        <template v-if="specDims.length">
          <span v-for="dim in specDims" :key="dim.name" class="tag outline">
            {{ dim.name }}：{{ dim.values.map((v) => v.value).join(' / ') }}
          </span>
          <span class="tiny dim">已有商品的规格项不可修改（原型口径）；可调整每行价格/库存，或新增缺失组合</span>
        </template>
        <span v-else class="tiny dim">该商品没有可读的规格数据</span>
      </div>

      <el-table :data="rows" class="tbl-wrap mt10" empty-text="请先生成 SKU 矩阵">
        <el-table-column label="规格组合" min-width="220">
          <template #default="{ row }">
            <template v-if="isEdit">
              <span class="tag gray">{{ row.specText || '—' }}</span>
            </template>
            <template v-else>{{ row.values.join(' / ') }}</template>
          </template>
        </el-table-column>
        <el-table-column label="价格（元）" width="170">
          <template #default="{ row }">
            <el-input-number v-model="row.priceYuan" :min="0.01" :precision="2" :step="1" controls-position="right" style="width: 140px" />
          </template>
        </el-table-column>
        <el-table-column label="库存" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.number" :min="0" :precision="0" :step="1" controls-position="right" style="width: 120px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="rows.length === 1" @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="isEdit && specDims.length" class="add-row mt10">
        <span class="small">新增组合：</span>
        <el-select
          v-for="(dim, di) in specDims"
          :key="dim.name"
          v-model="newRow.valueIds[di]"
          placeholder="选择{{ dim.name }}"
          style="width: 150px"
        >
          <el-option v-for="v in dim.values" :key="v.valueId" :label="v.value" :value="String(v.valueId)" />
        </el-select>
        <el-button @click="addRow">添加</el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { goodsCreate, goodsDetail, goodsUpdate } from '@/api/goods'
import { adminBrandPage, adminCategoryTree } from '@/api/catalog'
import { fen2yuan, yuan2fen } from '@/utils/format'
import ImageUpload from '@/components/ImageUpload.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  goods: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const skuBlocked = ref(false)
const isEdit = computed(() => Boolean(props.goods?.id))

const form = reactive({
  id: null,
  name: '',
  brief: '',
  picUrl: '',
  categoryId: '',
  brandId: '',
  status: 1,
  detail: '',
  updateTime: '',
})

const dimensions = ref([{ name: '', valuesText: '' }])
const specDims = ref([])
const rows = ref([])
const newRow = reactive({ valueIds: [] })
const categories = ref([])
const brands = ref([])
const categoryFallback = ref('')

const leafCategories = computed(() => {
  const out = []
  const walk = (nodes, prefix) => {
    for (const n of nodes || []) {
      const path = prefix ? `${prefix} / ${n.name}` : n.name
      if (!n.children || !n.children.length) out.push({ id: String(n.id), path })
      else walk(n.children, path)
    }
  }
  walk(categories.value, '')
  return out
})

const parseValues = (text) =>
  String(text || '')
    .split(/[,，/、]/)
    .map((s) => s.trim())
    .filter(Boolean)

const reset = () => {
  form.id = null
  form.name = ''
  form.brief = ''
  form.picUrl = ''
  form.categoryId = ''
  form.brandId = ''
  form.status = 1
  form.detail = ''
  form.updateTime = ''
  dimensions.value = [{ name: '', valuesText: '' }]
  specDims.value = []
  rows.value = []
  newRow.valueIds = []
  loadError.value = ''
  skuBlocked.value = false
  categoryFallback.value = ''
}

const onOpen = async () => {
  reset()
  loading.value = true
  try {
    // 必须用 admin 端点：C 端 /app/goods/category 只返回「自身或子分类下存在在售商品」的分类，
    // 后台刚建好、还没有商品的分类（验收 §6.2 第 1→2 步）会选不到。
    const [tree, brandPage] = await Promise.allSettled([
      adminCategoryTree({ withGoodsCount: false }),
      adminBrandPage({ pageNum: 1, pageSize: 100 }),
    ])
    if (tree.status === 'fulfilled') categories.value = tree.value || []
    if (brandPage.status === 'fulfilled') brands.value = brandPage.value?.list || []

    if (isEdit.value) await loadGoods()
  } finally {
    loading.value = false
  }
}

const loadGoods = async () => {
  const g = props.goods
  form.id = g.id
  form.name = g.name || ''
  form.brief = g.brief || ''
  form.picUrl = g.picUrl || ''
  form.categoryId = g.categoryId != null ? String(g.categoryId) : ''
  form.categoryFallback = g.categoryName || ''
  form.brandId = g.brandId != null ? String(g.brandId) : ''
  form.status = Number(g.status ?? (g.onSale ? 1 : 2))
  form.detail = g.detail || ''
  form.updateTime = g.updateTime || ''

  try {
    // 商品编辑所需 specs/skus 来自 C 端详情（08 CI-32）；下架/草稿商品会 404 GOODS_OFF_SALE
    const detail = await goodsDetail(g.id)
    form.detail = detail?.detail || form.detail
    form.categoryId = detail?.categoryId != null ? String(detail.categoryId) : form.categoryId
    form.brandId = detail?.brandId != null ? String(detail.brandId) : form.brandId
    form.picUrl = detail?.picUrl || form.picUrl

    // CI-32 实际返回 key 为 specifications（每维 {name, values:[{value,valueId,picUrl}]}），兼容旧写法 specs
    const dims = detail?.specifications || detail?.specs
    specDims.value = Array.isArray(dims) ? dims : []
    const skus = Array.isArray(detail?.skus) ? detail.skus : []
    rows.value = skus.map((sku) => ({
      id: sku.id,
      valueIds: (sku.specValueIds || []).map((v) => String(v)),
      specText: sku.specText || resolveSpecText(sku.specValueIds || []),
      priceYuan: Number(fen2yuan(sku.price, '0')),
      number: Number(sku.number ?? 0),
    }))
    newRow.valueIds = specDims.value.map(() => '')
    if (!rows.value.length) skuBlocked.value = true
  } catch (e) {
    loadError.value = e?.message || '商品详情不可读'
    skuBlocked.value = true
  }
}

const resolveSpecText = (valueIds) =>
  valueIds
    .map((id) => {
      for (const dim of specDims.value) {
        const hit = dim.values?.find((v) => String(v.valueId) === String(id))
        if (hit) return hit.value
      }
      return id
    })
    .join(' / ')

const addDimension = () => {
  if (dimensions.value.length >= 2) return
  dimensions.value.push({ name: '', valuesText: '' })
}

const removeDimension = (i) => {
  if (dimensions.value.length <= 1) return
  dimensions.value.splice(i, 1)
}

const markDirty = () => {
  /* 值变更后需重新生成矩阵；这里只保留输入，矩阵在「生成」时重建 */
}

const buildMatrix = () => {
  const dims = dimensions.value.filter((d) => d.name.trim())
  const valueLists = dims.map((d) => parseValues(d.valuesText))

  if (!dims.length || valueLists.some((list) => !list.length)) {
    ElMessage.warning('请填写规格名与规格值（至少 1 组）')
    return
  }
  if (dims.length !== dimensions.value.length) {
    ElMessage.warning('有规格维度未填写规格名')
    return
  }

  let combos = [[]]
  for (const list of valueLists) {
    combos = combos.flatMap((base) => list.map((v) => [...base, v]))
  }

  const old = new Map(rows.value.map((r) => [r.values.join('|'), r]))
  rows.value = combos.map((values) => {
    const key = values.join('|')
    const prev = old.get(key)
    return {
      values,
      specText: values.join(' / '),
      priceYuan: prev?.priceYuan ?? 0.01,
      number: prev?.number ?? 0,
    }
  })
}

const removeRow = (i) => {
  if (rows.value.length === 1) return
  rows.value.splice(i, 1)
}

const addRow = () => {
  if (newRow.valueIds.some((v) => !v)) {
    ElMessage.warning('请为每个规格维度选择一个值')
    return
  }
  const valueIds = newRow.valueIds.map((v) => String(v))
  if (rows.value.some((r) => (r.valueIds || []).join(',') === valueIds.join(','))) {
    ElMessage.warning('该规格组合已存在')
    return
  }
  rows.value.push({
    id: null,
    valueIds,
    specText: resolveSpecText(valueIds),
    priceYuan: 0.01,
    number: 0,
  })
  newRow.valueIds = specDims.value.map(() => '')
}

const validate = () => {
  if (!form.name.trim()) return '请填写商品名称'
  if (!form.picUrl) return '请上传商品主图'
  if (!form.categoryId) return '请选择末级分类'
  // 下架/草稿商品的 SKU 矩阵不可读：仅保存基础字段，不校验也不提交 SKU
  if (isEdit.value && skuBlocked.value) return ''
  if (!rows.value.length) return '请生成至少 1 个 SKU'
  for (const [i, row] of rows.value.entries()) {
    const cents = yuan2fen(row.priceYuan)
    if (!Number.isFinite(cents) || cents < 1) return `第 ${i + 1} 行价格不合法（≥0.01 元）`
    if (!Number.isInteger(Number(row.number)) || Number(row.number) < 0) return `第 ${i + 1} 行库存不合法`
  }
  const keys = rows.value.map((r) => (isEdit.value ? (r.valueIds || []).join(',') : r.values.join('|')))
  if (new Set(keys).size !== keys.length) return '存在重复的规格组合（BR-GOODS-02）'
  return ''
}

const save = async () => {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }

  const base = {
    name: form.name.trim(),
    brief: form.brief || undefined,
    picUrl: form.picUrl,
    categoryId: String(form.categoryId),
    brandId: form.brandId || undefined,
    detail: form.detail || undefined,
    status: Number(form.status),
  }

  const skus = rows.value.map((row) => {
    const item = {
      price: yuan2fen(row.priceYuan),
      number: Number(row.number),
      // 新建：传规格值文本，由服务端翻译为真 id（08 D-02b）；编辑：传真 id
      specValueIds: isEdit.value ? (row.valueIds || []).map(String) : row.values,
    }
    if (isEdit.value && row.id) item.id = row.id
    return item
  })

  const payload = isEdit.value
    ? { ...base, id: form.id, updateTime: form.updateTime || undefined }
    : { ...base, unit: '件', gallery: [form.picUrl], specifications: specificationsPayload(), skus }

  // SKU 详情不可读（下架/草稿）时不提交 skus，避免全量覆盖误删
  if (isEdit.value && !skuBlocked.value) payload.skus = skus

  saving.value = true
  try {
    if (isEdit.value) await goodsUpdate(payload)
    else await goodsCreate(payload)
    ElMessage.success(isEdit.value ? '商品已保存' : '商品已创建')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}

const specificationsPayload = () =>
  dimensions.value.map((d) => ({
    name: d.name.trim(),
    values: parseValues(d.valuesText),
  }))
</script>

<style scoped>
.goods-form {
  max-height: 66vh;
  overflow: auto;
  padding-right: 6px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}
.span2 {
  grid-column: 1 / -1;
}
.spec-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.spec-dim {
  display: flex;
  gap: 8px;
  align-items: center;
}
.spec-readonly {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.add-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
</style>