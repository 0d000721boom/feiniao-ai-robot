import test from 'node:test'
import assert from 'node:assert/strict'

import {
  CHUNK_SIZE,
  calculateUploadPercent,
  getChunkCount,
  getMissingChunkNumbers,
  runWithConcurrency,
  withRetries,
} from '../src/utils/chunkUpload.js'

test('chunk count handles exact multiples and a final partial chunk', () => {
  assert.equal(getChunkCount(0), 0)
  assert.equal(getChunkCount(CHUNK_SIZE), 1)
  assert.equal(getChunkCount(CHUNK_SIZE + 1), 2)
})

test('missing chunk calculation skips uploaded indexes', () => {
  assert.deepEqual(getMissingChunkNumbers(5, [0, 2, 4]), [1, 3])
})

test('concurrency helper never exceeds its limit', async () => {
  let active = 0
  let maximum = 0
  const settled = []
  await runWithConcurrency([1, 2, 3, 4, 5], 2, async () => {
    active += 1
    maximum = Math.max(maximum, active)
    await new Promise((resolve) => setTimeout(resolve, 2))
    active -= 1
  }, (value) => settled.push(value))

  assert.equal(maximum, 2)
  assert.deepEqual(settled.sort((left, right) => left - right), [1, 2, 3, 4, 5])
})

test('retry helper retries twice before succeeding', async () => {
  let attempts = 0
  const result = await withRetries(async () => {
    attempts += 1
    if (attempts < 3) throw new Error('retry')
    return 'ok'
  })

  assert.equal(result, 'ok')
  assert.equal(attempts, 3)
})

test('upload percent is bounded', () => {
  assert.equal(calculateUploadPercent(0, 0), 100)
  assert.equal(calculateUploadPercent(1, 4), 25)
  assert.equal(calculateUploadPercent(5, 4), 100)
})
