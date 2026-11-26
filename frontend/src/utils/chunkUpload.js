export const CHUNK_SIZE = 5 * 1024 * 1024
export const MAX_CONCURRENT_UPLOADS = 3

export function getChunkCount(fileSize) {
  if (!Number.isFinite(fileSize) || fileSize <= 0) return 0
  return Math.ceil(fileSize / CHUNK_SIZE)
}

export function createChunkBlob(file, chunkNumber, chunkSize = CHUNK_SIZE) {
  const start = chunkNumber * chunkSize
  return file.slice(start, Math.min(file.size, start + chunkSize))
}

export function getMissingChunkNumbers(totalChunks, uploadedChunks = []) {
  const uploaded = new Set(uploadedChunks.map(Number))
  return Array.from({ length: Math.max(0, Number(totalChunks) || 0) }, (_, index) => index)
    .filter((index) => !uploaded.has(index))
}

export async function runWithConcurrency(items, limit, worker, onSettled = () => {}) {
  const queue = [...items]
  const workerCount = Math.min(Math.max(1, Number(limit) || 1), queue.length)
  if (workerCount === 0) return

  const runNext = async () => {
    while (queue.length > 0) {
      const item = queue.shift()
      await worker(item)
      onSettled(item)
    }
  }

  await Promise.all(Array.from({ length: workerCount }, runNext))
}

export async function withRetries(task, retries = 2) {
  let lastError
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      return await task()
    } catch (error) {
      lastError = error
    }
  }
  throw lastError
}

export function calculateUploadPercent(completed, total) {
  if (!total) return 100
  return Math.min(100, Math.max(0, Math.round((completed / total) * 100)))
}

export function createUploadProgress() {
  const state = {
    stage: 'idle',
    percent: 0,
    completedChunks: 0,
    totalChunks: 0,
  }

  return {
    setStage(stage, percent, completedChunks, totalChunks) {
      state.stage = stage
      if (stage === 'merging') {
        state.percent = 100
      } else if (percent !== undefined) {
        state.percent = percent
      }
      if (completedChunks !== undefined) state.completedChunks = completedChunks
      if (totalChunks !== undefined) state.totalChunks = totalChunks
    },
    snapshot() {
      return { ...state }
    },
  }
}
