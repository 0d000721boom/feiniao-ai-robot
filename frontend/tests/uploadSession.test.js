import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createChunkBlob,
  createUploadProgress,
} from '../src/utils/chunkUpload.js'

test('upload progress separates hashing, uploading, and merging', () => {
  const progress = createUploadProgress()
  progress.setStage('hashing', 40)
  progress.setStage('uploading', 25, 1, 4)
  progress.setStage('merging')

  assert.deepEqual(progress.snapshot(), {
    stage: 'merging',
    percent: 100,
    completedChunks: 1,
    totalChunks: 4,
  })
})

test('chunk blob uses zero-based offsets and clamps the final slice', async () => {
  const source = new Blob(['abcdefghij'])
  const chunk = createChunkBlob(source, 2, 4)

  assert.equal(await chunk.text(), 'ij')
})
