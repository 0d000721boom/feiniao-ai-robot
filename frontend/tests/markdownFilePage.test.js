import test from 'node:test'
import assert from 'node:assert/strict'

import { normalizeMarkdownFilePage } from '../src/utils/markdownFilePage.js'

test('normalizeMarkdownFilePage accepts the direct page response used by feiniao', () => {
  const page = normalizeMarkdownFilePage({
    pageNum: 1,
    pageSize: 10,
    total: 1,
    totalPages: 1,
    records: [{ id: 7, originalFileName: 'faq.md', status: 2 }],
  })

  assert.equal(page.pageNum, 1)
  assert.equal(page.records[0].id, 7)
})

test('normalizeMarkdownFilePage accepts a response-wrapped page for compatibility', () => {
  const page = normalizeMarkdownFilePage({
    success: true,
    data: {
      pageNum: 2,
      pageSize: 10,
      total: 11,
      totalPages: 2,
      records: [{ id: 8, originalFileName: 'guide.md', status: 2 }],
    },
  })

  assert.equal(page.pageNum, 2)
  assert.equal(page.records[0].id, 8)
})
