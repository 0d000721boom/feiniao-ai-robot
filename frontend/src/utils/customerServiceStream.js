const CUSTOMER_SERVICE_CHAT_PATH = '/customer-service/chat/completion'

export function buildCustomerServiceChatUrl(message, baseUrl = '/api') {
  const normalizedBase = baseUrl.replace(/\/$/, '')
  return `${normalizedBase}${CUSTOMER_SERVICE_CHAT_PATH}?message=${encodeURIComponent(message)}`
}

function extractSseText(event) {
  const data = event
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (!data) {
    return ''
  }

  try {
    const payload = JSON.parse(data)
    return typeof payload === 'string' ? payload : payload.v ?? payload.content ?? ''
  } catch {
    return data
  }
}

export async function consumeCustomerServiceStream(response, onChunk) {
  if (!response.ok) {
    const errorBody = await response.text()
    throw new Error(errorBody || `客服请求失败（HTTP ${response.status}）`)
  }

  if (!response.body) {
    throw new Error('客服接口未返回可读取的响应流')
  }

  const isSse = response.headers.get('content-type')?.includes('text/event-stream')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let fullText = ''
  let eventBuffer = ''

  while (true) {
    const { done, value } = await reader.read()

    if (done) {
      break
    }

    const decoded = decoder.decode(value, { stream: true })

    if (!isSse) {
      if (decoded) {
        fullText += decoded
        onChunk(decoded)
      }
      continue
    }

    eventBuffer += decoded
    const events = eventBuffer.split(/\r?\n\r?\n/)
    eventBuffer = events.pop() ?? ''

    for (const event of events) {
      const text = extractSseText(event)
      if (text) {
        fullText += text
        onChunk(text)
      }
    }
  }

  const tail = decoder.decode()
  if (isSse) {
    eventBuffer += tail
    if (eventBuffer.trim()) {
      const text = extractSseText(eventBuffer)
      if (text) {
        fullText += text
        onChunk(text)
      }
    }
  } else if (tail) {
    fullText += tail
    onChunk(tail)
  }

  return fullText
}
