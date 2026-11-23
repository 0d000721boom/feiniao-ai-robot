import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildCustomerServiceChatUrl,
  consumeCustomerServiceStream,
} from '../src/utils/customerServiceStream.js'

const textResponse = (chunks, contentType = 'text/plain;charset=UTF-8') => {
  const encoder = new TextEncoder()
  const body = new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    },
  })

  return new Response(body, {
    headers: { 'Content-Type': contentType },
  })
}

test('buildCustomerServiceChatUrl safely encodes the user message', () => {
  const url = buildCustomerServiceChatUrl('京东 & 天猫?', '/api')

  assert.equal(
    url,
    '/api/customer-service/chat/completion?message=%E4%BA%AC%E4%B8%9C%20%26%20%E5%A4%A9%E7%8C%AB%3F',
  )
})

test('consumeCustomerServiceStream emits plain text chunks in order', async () => {
  const chunks = []
  const response = textResponse(['你好，', '我是飞鸟智能客服'])

  const result = await consumeCustomerServiceStream(response, (chunk) => {
    chunks.push(chunk)
  })

  assert.deepEqual(chunks, ['你好，', '我是飞鸟智能客服'])
  assert.equal(result, '你好，我是飞鸟智能客服')
})

test('consumeCustomerServiceStream extracts text from SSE data frames', async () => {
  const chunks = []
  const response = textResponse(
    [
      'data: {"v":"欢迎"}\n\n',
      'data: {"v":"咨询"}\n\n',
      'event: close\n\n',
    ],
    'text/event-stream;charset=UTF-8',
  )

  const result = await consumeCustomerServiceStream(response, (chunk) => {
    chunks.push(chunk)
  })

  assert.deepEqual(chunks, ['欢迎', '咨询'])
  assert.equal(result, '欢迎咨询')
})
