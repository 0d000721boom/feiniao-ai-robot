<template>
  <div class="flex h-screen flex-col overflow-hidden bg-[#f8fbff]">
    <header class="z-20 flex-shrink-0 border-b border-blue-100/80 bg-white/95 backdrop-blur">
      <div class="mx-auto flex h-16 max-w-5xl items-center px-4">
        <a-tooltip placement="bottom">
          <template #title>
            <span>返回首页</span>
          </template>
          <button class="header-icon-button" aria-label="返回首页" @click="jumpHomePage">
            <SvgIcon name="back" customCss="w-5 h-5 text-gray-500" />
          </button>
        </a-tooltip>

        <div class="min-w-0 flex-1 text-center">
          <div class="flex items-center justify-center gap-2">
            <div class="flex h-8 w-8 items-center justify-center rounded-full border border-blue-100 bg-blue-50">
              <SvgIcon name="customer-service-logo" customCss="w-5 h-5" />
            </div>
            <div class="truncate text-base font-semibold text-gray-800">飞鸟 AI 智能客服</div>
          </div>
          <div class="mt-0.5 flex items-center justify-center gap-1.5 text-[11px] text-gray-400">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald-400"></span>
            基于知识库智能解答
          </div>
        </div>

        <a-tooltip placement="bottom">
          <template #title>
            <span>知识库设置</span>
          </template>
          <button class="header-icon-button" aria-label="打开知识库设置" @click="openSettingsDrawer">
            <SvgIcon name="settings" customCss="w-5 h-5 text-gray-500" />
          </button>
        </a-tooltip>
      </div>
    </header>

    <main ref="chatContainer" class="chat-scrollbar min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-3xl px-4 pb-12 pt-8">
        <template v-for="chat in chatList" :key="chat.id">
          <div v-if="chat.role === 'user'" class="mb-6 flex justify-end message-enter">
            <div class="question-container">
              <p>{{ chat.content }}</p>
            </div>
          </div>

          <div v-else class="mb-6 flex message-enter">
            <div class="mr-3 flex-shrink-0">
              <div class="flex h-9 w-9 items-center justify-center rounded-full border border-blue-100 bg-blue-50 shadow-sm">
                <SvgIcon name="customer-service-logo" customCss="w-5 h-5" />
              </div>
            </div>
            <div class="max-w-[calc(100%-48px)] pt-1">
              <LoadingDots v-if="chat.loading && !chat.content" />
              <StreamMarkdownRender v-if="chat.content" :content="chat.content" />
            </div>
          </div>
        </template>

        <div v-if="chatList.length === 1" class="mt-8 grid gap-3 sm:grid-cols-3">
          <button v-for="question in quickQuestions" :key="question" class="quick-question" @click="askQuestion(question)">
            {{ question }}
          </button>
        </div>
      </div>
    </main>

    <footer class="z-10 flex-shrink-0 bg-gradient-to-t from-[#f8fbff] via-[#f8fbff] to-transparent pb-4 pt-6">
      <ChatInputBox
        v-model="chatMessage"
        containerClass="mx-auto w-full max-w-3xl"
        placeholder="向飞鸟 AI 智能客服询问"
        :show-model-dropdown="false"
        :show-network-search="false"
        :disabled="isGenerating"
        @send-message="sendMessage"
      />
      <p class="mt-2 text-center text-[11px] text-gray-400">
        {{ isGenerating ? '正在结合知识库生成回答…' : '内容由 AI 生成，请仔细甄别' }}
      </p>
    </footer>
  </div>

  <a-drawer
    :open="settingsDrawerOpen"
    width="min(960px, 88vw)"
    title="客服知识库文件管理"
    placement="right"
    @close="onSettingsDrawerClose"
  >
    <div class="mb-5 rounded-2xl border border-blue-100 bg-blue-50/60 px-5 py-4">
      <div class="text-sm font-medium text-gray-800">Markdown 知识库</div>
      <div class="mt-1 text-xs leading-5 text-gray-500">
        上传 .md 文件后，系统会自动切分并向量化。处理完成后，客服会基于这些内容回答用户问题。
      </div>
    </div>

    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div class="text-sm text-gray-500">
        共 <span class="font-semibold text-gray-800">{{ total }}</span> 个文件
      </div>
      <div class="flex gap-2">
        <a-button :icon="h(ReloadOutlined)" :loading="fileLoading" @click="loadFiles(current)">
          刷新
        </a-button>
        <a-button type="primary" :icon="h(UploadOutlined)" :loading="uploadLoading" @click="triggerFileUpload">
          上传 Markdown
        </a-button>
      </div>
    </div>

    <input
      ref="fileInput"
      class="hidden"
      type="file"
      accept=".md,.markdown,text/markdown"
      @change="handleFileSelect"
    />

    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="fileLoading"
      :pagination="pagination"
      :scroll="{ x: 900 }"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusMeta(record.status).color">{{ statusMeta(record.status).label }}</a-tag>
        </template>
        <template v-else-if="column.key === 'createTime' || column.key === 'updateTime'">
          {{ formatDateTime(record[column.dataIndex]) }}
        </template>
        <template v-else-if="column.key === 'remark'">
          <span class="text-gray-500">{{ record.remark || '暂无备注' }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button size="small" @click="showEditMarkdownModal(record)">编辑备注</a-button>
          <a-divider type="vertical" />
          <a-tooltip :title="canDelete(record) ? '' : '处理中或待处理文件暂时无法删除'">
            <a-button
              size="small"
              danger
              :disabled="!canDelete(record)"
              @click="showDeleteMarkdownModal(record)"
            >
              删除
            </a-button>
          </a-tooltip>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="还没有上传知识库文件">
          <a-button type="primary" @click="triggerFileUpload">上传第一个文件</a-button>
        </a-empty>
      </template>
    </a-table>
  </a-drawer>

  <a-modal
    v-model:open="deleteModalOpen"
    width="420px"
    centered
    title="永久删除问答文件"
    ok-text="确认删除"
    ok-type="danger"
    cancel-text="取消"
    :confirm-loading="deleteLoading"
    @ok="handleDeleteMarkdown"
  >
    <p class="text-gray-600">
      确定删除“{{ selectedRecord.originalFileName }}”吗？文件及其向量化数据都会被永久移除。
    </p>
  </a-modal>

  <a-modal
    v-model:open="editModalOpen"
    width="680px"
    centered
    title="编辑问答文件"
    ok-text="保存"
    cancel-text="取消"
    :confirm-loading="editLoading"
    @ok="handleEditMarkdown"
  >
    <a-form class="mt-6" layout="vertical">
      <div class="grid gap-x-4 sm:grid-cols-2">
        <a-form-item label="文件 ID">
          <a-input :value="selectedRecord.id" disabled />
        </a-form-item>
        <a-form-item label="文件名称">
          <a-input :value="selectedRecord.originalFileName" disabled />
        </a-form-item>
        <a-form-item label="文件大小">
          <a-input :value="selectedRecord.fileSize || '-'" disabled />
        </a-form-item>
        <a-form-item label="处理状态">
          <a-tag :color="statusMeta(selectedRecord.status).color">
            {{ statusMeta(selectedRecord.status).label }}
          </a-tag>
        </a-form-item>
        <a-form-item label="创建时间">
          <a-input :value="formatDateTime(selectedRecord.createTime)" disabled />
        </a-form-item>
        <a-form-item label="更新时间">
          <a-input :value="formatDateTime(selectedRecord.updateTime)" disabled />
        </a-form-item>
      </div>
      <a-form-item label="备注">
        <a-textarea
          v-model:value="selectedRecord.remark"
          placeholder="补充文件用途、适用业务或更新说明"
          :auto-size="{ minRows: 4, maxRows: 8 }"
          show-count
          :maxlength="500"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { computed, h, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'

import ChatInputBox from '@/components/ChatInputBox.vue'
import LoadingDots from '@/components/LoadingDots.vue'
import StreamMarkdownRender from '@/components/StreamMarkdownRender.vue'
import SvgIcon from '@/components/SvgIcon.vue'
import {
  deleteMarkdownFile,
  findMarkdownFilePageList,
  updateMarkdownFile,
  uploadMarkdownFile,
} from '@/api/customerService'
import {
  buildCustomerServiceChatUrl,
  consumeCustomerServiceStream,
} from '@/utils/customerServiceStream'
import { normalizeMarkdownFilePage } from '@/utils/markdownFilePage'

const router = useRouter()
const chatContainer = ref(null)
const chatMessage = ref('')
const isGenerating = ref(false)
const isDisposed = ref(false)

let messageSequence = 0
let activeController = null

const createMessage = (role, content, extra = {}) => ({
  id: ++messageSequence,
  role,
  content,
  loading: false,
  ...extra,
})

const chatList = ref([
  createMessage(
    'assistant',
    '你好，我是 **飞鸟 AI 智能客服**。你可以咨询产品使用、常见问题，也可以让我根据已上传的知识库文件为你解答。',
  ),
])

const quickQuestions = [
  '如何开始使用飞鸟 AI？',
  '知识库支持哪些文件？',
  '回答不准确时怎么办？',
]

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

const jumpHomePage = () => {
  router.push({ name: 'Index' })
}

const askQuestion = (question) => {
  chatMessage.value = question
  sendMessage()
}

const sendMessage = async () => {
  const userMessage = chatMessage.value.trim()
  if (!userMessage || isGenerating.value) return

  chatList.value.push(createMessage('user', userMessage))
  const assistantMessage = createMessage('assistant', '', { loading: true })
  chatList.value.push(assistantMessage)
  chatMessage.value = ''
  isGenerating.value = true
  await scrollToBottom()

  const controller = new AbortController()
  activeController = controller

  try {
    const response = await fetch(buildCustomerServiceChatUrl(userMessage), {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream, text/plain, text/html',
      },
      signal: controller.signal,
    })

    await consumeCustomerServiceStream(response, (chunk) => {
      assistantMessage.loading = false
      assistantMessage.content += chunk
      scrollToBottom()
    })

    if (!assistantMessage.content) {
      assistantMessage.content = '暂时没有获取到回复，请稍后重试。'
    }
  } catch (error) {
    if (error.name !== 'AbortError' && !isDisposed.value) {
      assistantMessage.content = getRequestErrorMessage(error)
    }
  } finally {
    assistantMessage.loading = false
    isGenerating.value = false
    activeController = null
    await scrollToBottom()
  }
}

const getRequestErrorMessage = (error) => {
  const responseMessage = error?.response?.data?.msg || error?.response?.data?.Msg
  return responseMessage || error?.message || '抱歉，客服服务暂时不可用，请稍后重试。'
}

const settingsDrawerOpen = ref(false)
const fileInput = ref(null)
const dataSource = ref([])
const fileLoading = ref(false)
const uploadLoading = ref(false)
const editLoading = ref(false)
const deleteLoading = ref(false)
const current = ref(1)
const pageSize = ref(10)
const total = ref(0)
const deleteModalOpen = ref(false)
const editModalOpen = ref(false)

const emptyRecord = () => ({
  id: null,
  originalFileName: '',
  fileSize: '',
  status: null,
  createTime: '',
  updateTime: '',
  remark: '',
})

const selectedRecord = reactive(emptyRecord())

const columns = [
  { title: '文件名称', dataIndex: 'originalFileName', key: 'originalFileName', width: 240 },
  { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 170 },
  { title: '备注', dataIndex: 'remark', key: 'remark', width: 180 },
  { title: '操作', key: 'action', fixed: 'right', width: 170 },
]

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: false,
  showTotal: (count) => `共 ${count} 条`,
}))

const statusMeta = (status) => {
  const statusMap = {
    0: { label: '待处理', color: 'default' },
    1: { label: '向量化中', color: 'processing' },
    2: { label: '已完成', color: 'success' },
    3: { label: '失败', color: 'error' },
  }
  return statusMap[status] || { label: '未知', color: 'default' }
}

const canDelete = (record) => record.status === 2 || record.status === 3

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const getBusinessMessage = (response, fallback) => {
  return response?.data?.msg || response?.data?.Msg || fallback
}

const loadFiles = async (pageNo = 1) => {
  if (fileLoading.value) return

  fileLoading.value = true
  try {
    const response = await findMarkdownFilePageList(pageNo, pageSize.value)
    if (response.data?.success === false) {
      message.warning(getBusinessMessage(response, '加载文件列表失败'))
      return
    }

    const page = normalizeMarkdownFilePage(response.data)
    dataSource.value = page.records || []
    current.value = Number(page.pageNum || pageNo)
    pageSize.value = Number(page.pageSize || pageSize.value)
    total.value = Number(page.total || 0)
  } catch (error) {
    message.error(getRequestErrorMessage(error) || '加载文件列表失败')
  } finally {
    fileLoading.value = false
  }
}

const openSettingsDrawer = () => {
  settingsDrawerOpen.value = true
  loadFiles(current.value)
}

const onSettingsDrawerClose = () => {
  settingsDrawerOpen.value = false
}

const triggerFileUpload = () => {
  fileInput.value?.click()
}

const handleFileSelect = async (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (!/\.(md|markdown)$/i.test(file.name)) {
    message.warning('请选择 Markdown 文件')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  uploadLoading.value = true

  try {
    const response = await uploadMarkdownFile(formData)
    if (!response.data.success) {
      message.warning(getBusinessMessage(response, '上传失败'))
      return
    }

    message.success('上传成功，文件正在进入处理队列')
    await loadFiles(1)
  } catch (error) {
    message.error(getRequestErrorMessage(error) || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

const showDeleteMarkdownModal = (record) => {
  Object.assign(selectedRecord, record)
  deleteModalOpen.value = true
}

const handleDeleteMarkdown = async () => {
  deleteLoading.value = true
  try {
    const response = await deleteMarkdownFile(selectedRecord.id)
    if (!response.data.success) {
      message.warning(getBusinessMessage(response, '删除失败'))
      return
    }

    message.success('删除成功')
    deleteModalOpen.value = false
    await loadFiles(1)
  } catch (error) {
    message.error(getRequestErrorMessage(error) || '删除失败')
  } finally {
    deleteLoading.value = false
  }
}

const showEditMarkdownModal = (record) => {
  Object.assign(selectedRecord, record)
  editModalOpen.value = true
}

const handleEditMarkdown = async () => {
  editLoading.value = true
  try {
    const response = await updateMarkdownFile(selectedRecord.id, selectedRecord.remark || '')
    if (!response.data.success) {
      message.warning(getBusinessMessage(response, '保存失败'))
      return
    }

    message.success('备注已保存')
    editModalOpen.value = false
    await loadFiles(current.value)
  } catch (error) {
    message.error(getRequestErrorMessage(error) || '保存失败')
  } finally {
    editLoading.value = false
  }
}

const handleTableChange = (page) => {
  loadFiles(page.current)
}

onBeforeUnmount(() => {
  isDisposed.value = true
  activeController?.abort()
})
</script>

<style scoped>
.header-icon-button {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #e5e7eb;
  border-radius: 9999px;
  background: #fff;
  cursor: pointer;
  transition: background-color 0.2s, box-shadow 0.2s;
}

.header-icon-button:hover {
  background: #f3f6fb;
  box-shadow: 0 4px 14px rgb(15 23 42 / 8%);
}

.question-container {
  max-width: calc(100% - 48px);
  border-radius: 16px 16px 4px 16px;
  background: #eaf2ff;
  padding: 12px 18px;
  color: #262626;
  font-size: 16px;
  line-height: 28px;
  white-space: pre-wrap;
  word-break: break-word;
}

.quick-question {
  border: 1px solid #dce9ff;
  border-radius: 14px;
  background: rgb(255 255 255 / 80%);
  padding: 12px 14px;
  color: #526174;
  font-size: 13px;
  line-height: 20px;
  text-align: left;
  transition: border-color 0.2s, color 0.2s, box-shadow 0.2s;
}

.quick-question:hover {
  border-color: #a9c6ff;
  color: #365ee8;
  box-shadow: 0 6px 18px rgb(77 107 254 / 10%);
}

.chat-scrollbar {
  scrollbar-color: rgb(148 163 184 / 45%) transparent;
}

.message-enter {
  animation: message-in 0.24s ease-out both;
}

:deep(.ant-drawer-body) {
  padding: 20px 24px 28px;
}

@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 640px) {
  .header-icon-button {
    width: 36px;
    height: 36px;
  }

  .question-container {
    font-size: 15px;
  }
}
</style>
