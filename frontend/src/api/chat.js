import axios from "@/axios";

// 新建对话
export function newChat(message) {
    return axios.post("/chat/new", {message})
}

// 查询历史消息分页列表
export function findChatMessagePageList(pageNum, pageSize, chatUuid) {
    return axios.get(`/chat/history/${chatUuid}/messages`, {
    params: { pageNum, pageSize }
  })
}

// 查询历史对话列表
export function findHistoryChatPageList(pageNum, pageSize) {
  return axios.get("/chat/history", {
    params: { pageNum, pageSize }
  })
}

// 删除对话
export function deleteChat(chatUuid) {
  return axios.delete(`/chat/history/${chatUuid}`)
}

// 重命名对话
export function renameChat(chatUuid, title) {
  return axios.patch(`/chat/history/${chatUuid}/title`, {
    title
  })
}
