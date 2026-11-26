import axios from '@/axios'

export function findMarkdownFilePageList(current, size) {
  return axios.post('/customer-service/md/list', { current, size })
}

export function uploadMarkdownFile(formData) {
  return axios.post('/customer-service/md/upload', formData)
}

export function checkFile(fileMd5) {
  return axios.post('/customer-service/file/check', { fileMd5 })
}

export function uploadFileChunk(formData, onUploadProgress) {
  return axios.post('/customer-service/file/chunk/upload', formData, {
    timeout: 60_000,
    onUploadProgress,
  })
}

export function mergeFile(fileMd5) {
  return axios.post('/customer-service/file/merge', { fileMd5 }, {
    timeout: 0,
  })
}

export function deleteMarkdownFile(id) {
  return axios.post('/customer-service/md/delete', { id })
}

export function updateMarkdownFile(id, remark) {
  return axios.post('/customer-service/md/update', { id, remark })
}
