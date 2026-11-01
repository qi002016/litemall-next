<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>分类管理</h2>
          <span class="dim small">两级分类（一级 / 二级）；删除前需先移走分类下的商品</span>
        </div>
        <div class="row">
          <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateRoot">新建一级分类</el-button>
        </div>
      </div>

      <div v-if="errorTip" class="alert err mt10">
        <div class="between">
          <span>{{ errorTip }}</span>
          <el-button link size="small" @click="errorTip = ''">关闭</el-button>
        </div>
      </div>

      <el-table
        :key="treeKey"
        v-loading="loading"
        :data="tree"
        class="tbl-wrap"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        empty-text="暂无分类，可先新建一级分类"
      >
        <el-table-column label="分类名称" min-width="260">
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
              <span class="bold">{{ row.name }}</span>
              <span class="tag outline">{{ row.level === '2' ? '二级' : '一级' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品数" width="100" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.goodsCount ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="90" align="center">
          <template #default="{ row }">{{ row.sortOrder ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="图标 / 图片" width="150">
          <template #default="{ row }">
            <span v-if="row.picUrl || row.iconUrl" class="tiny dim ellipsis">{{ row.iconUrl || row.picUrl }}</span>
            <span v-else class="tiny dim">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.level !== '2'"
              link
              type="primary"
              size="small"
              @click="openCreateChild(row)"
            >
              新增子分类
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false">
      <div v-if="formError" class="alert err">{{ formError }}</div>
      <el-form :model="form" label-width="90px" @submit.prevent>
        <el-form-item label="上级分类">
          <span v-if="form.mode === 'edit'" class="small">{{ form.parentName || '—' }}</span>
          <el-input v-else :model-value="form.parentName" disabled />
          <span v-if="form.mode === 'edit'" class="tiny dim ml8">本期不支持调整上级分类</span>
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="form.name" maxlength="32" show-word-limit placeholder="1–32 字，同级不可重名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :precision="0" controls-position="right" />
          <span class="tiny dim ml8">值越小越靠前</span>
        </el-form-item>
        <el-form-item label="分类图片">
          <ImageUpload v-model="form.picUrl" />
        </el-form-item>
        <el-form-item label="图标地址">
          <el-input v-model="form.iconUrl" maxlength="255" placeholder="≤255，选填" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="form.keywords" maxlength="255" placeholder="搜索用关键字，选填" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.desc"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="≤255，选填"
          />
        </el-form-item>
      </el-form>
      <div class="tiny dim">图片 / 图标 / 关键字 / 描述留空表示不修改（后端按「未传」处理）。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  adminCategoryCreate,
  adminCategoryDelete,
  adminCategoryTree,
  adminCategoryUpdate,
} from '@/api/catalog'
import ImageUpload from '@/components/ImageUpload.vue'

const tree = ref([])
const treeKey = ref(0)
const loading = ref(false)
const errorTip = ref('')

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = reactive({
  mode: 'create-root',
  id: null,
  pid: '0',
  parentName: '',
  name: '',
  sortOrder: 0,
  picUrl: '',
  iconUrl: '',
  keywords: '',
  desc: '',
  updateTime: '',
})

const dialogTitle = computed(() =>
  form.mode === 'edit' ? '编辑分类' : form.mode === 'create-child' ? '新建子分类' : '新建一级分类',
)

const load = async () => {
  loading.value = true
  try {
    const data = await adminCategoryTree({ withGoodsCount: true }, { silent: true })
    tree.value = Array.isArray(data) ? data : []
    // 数据后到：强制重建表格，保证 default-expand-all 生效
    treeKey.value += 1
    errorTip.value = ''
  } catch (err) {
    tree.value = []
    errorTip.value = err?.message || '分类加载失败'
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.mode = 'create-root'
  form.id = null
  form.pid = '0'
  form.parentName = ''
  form.name = ''
  form.sortOrder = 0
  form.picUrl = ''
  form.iconUrl = ''
  form.keywords = ''
  form.desc = ''
  form.updateTime = ''
  formError.value = ''
}

const openCreateRoot = () => {
  resetForm()
  form.mode = 'create-root'
  form.parentName = '（顶层）'
  dialogVisible.value = true
}

const openCreateChild = (row) => {
  resetForm()
  form.mode = 'create-child'
  form.pid = String(row.id)
  form.parentName = row.name
  dialogVisible.value = true
}

const findName = (id) => {
  for (const node of tree.value) {
    if (String(node.id) === String(id)) return node.name
    for (const child of node.children || []) {
      if (String(child.id) === String(id)) return `${node.name} / ${child.name}`
    }
  }
  return ''
}

const openEdit = (row) => {
  resetForm()
  form.mode = 'edit'
  form.id = row.id
  form.pid = row.pid == null ? '0' : String(row.pid)
  form.parentName = form.pid === '0' ? '（顶层）' : findName(form.pid)
  form.name = row.name || ''
  form.sortOrder = Number(row.sortOrder || 0)
  form.picUrl = row.picUrl || ''
  form.iconUrl = row.iconUrl || ''
  form.updateTime = row.updateTime || ''
  dialogVisible.value = true
}

const submitForm = async () => {
  formError.value = ''
  const name = form.name.trim()
  if (!name) {
    formError.value = '请填写分类名称'
    return
  }
  const payload = {
    name,
    sortOrder: Number(form.sortOrder) || 0,
    picUrl: form.picUrl || undefined,
    iconUrl: form.iconUrl.trim() || undefined,
    keywords: form.keywords.trim() || undefined,
    desc: form.desc.trim() || undefined,
  }
  submitting.value = true
  try {
    if (form.mode === 'edit') {
      // pid 冻结：不传，避免触发 400「本期不支持调整上级分类」
      await adminCategoryUpdate(
        { id: form.id, ...payload, updateTime: form.updateTime || undefined },
        { silent: true },
      )
      ElMessage.success('分类已保存')
    } else {
      await adminCategoryCreate({ pid: form.pid, ...payload }, { silent: true })
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    load()
  } catch (err) {
    // 409 同级重名 / 404 父分类不存在 / 400 三级分类：后端文案原样展示
    formError.value = err?.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

const onDelete = async (row) => {
  const extra = row.level === '2' ? '' : '该分类下若仍有子分类或商品，删除会被拒绝。'
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」？${extra}`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  errorTip.value = ''
  try {
    await adminCategoryDelete({ id: row.id }, { silent: true })
    ElMessage.success('分类已删除')
    load()
  } catch (err) {
    // 409 CATEGORY_IN_USE ⇒「请先移走商品」；409 CATEGORY_HAS_CHILDREN ⇒ 子分类提示
    errorTip.value = err?.message || '删除失败'
  }
}

onMounted(load)
</script>

<style scoped>
.thumb {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid var(--line);
}
.ml8 {
  margin-left: 8px;
}
</style>