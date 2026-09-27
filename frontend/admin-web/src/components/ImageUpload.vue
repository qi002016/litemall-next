<template>
  <el-upload
    :show-file-list="false"
    :http-request="customUpload"
    accept="image/*"
  >
    <img v-if="modelValue" :src="modelValue" class="preview" />
    <el-button v-else type="primary" plain>点击上传</el-button>
  </el-upload>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { uploadFile } from '@/api/upload'

defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

const customUpload = async (option) => {
  try {
    const res = await uploadFile(option.file)
    emit('update:modelValue', res.url)
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error('上传失败')
  }
}
</script>

<style scoped>
.preview {
  width: 120px;
  height: 80px;
  object-fit: cover;
  border: 1px solid #eee;
  border-radius: 4px;
}
</style>
