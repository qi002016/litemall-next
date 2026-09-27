import request from '@/utils/request'

// 文件上传 POST /app/storage/upload  (multipart/form-data)
// file: File 对象，返回 { url, name, size }
export const uploadFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/app/storage/upload',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
