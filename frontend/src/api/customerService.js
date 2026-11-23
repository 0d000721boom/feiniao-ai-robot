import axios from '@/axios'

export function findMarkdownFilePageList(current, size) {
  return axios.post('/customer-service/md/list', { current, size })
}

export function uploadMarkdownFile(formData) {
  return axios.post('/customer-service/md/upload', formData)
}

export function deleteMarkdownFile(id) {
  return axios.post('/customer-service/md/delete', { id })
}

export function updateMarkdownFile(id, remark) {
  return axios.post('/customer-service/md/update', { id, remark })
}
