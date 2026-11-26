export function calculateFileMd5(file, onProgress = () => {}, signal) {
  return new Promise((resolve, reject) => {
    const worker = new Worker(
      new URL('../workers/fileMd5.worker.js', import.meta.url),
      { type: 'module' },
    )

    const cleanup = () => {
      worker.terminate()
      signal?.removeEventListener('abort', handleAbort)
    }
    const handleAbort = () => {
      cleanup()
      reject(new DOMException('MD5 calculation aborted', 'AbortError'))
    }

    worker.onmessage = ({ data }) => {
      if (data.type === 'progress') {
        onProgress(data.percent)
      } else if (data.type === 'done') {
        cleanup()
        resolve(data.md5)
      } else if (data.type === 'error') {
        cleanup()
        reject(new Error(data.message))
      }
    }
    worker.onerror = (event) => {
      cleanup()
      reject(new Error(event.message || 'MD5 worker failed'))
    }

    if (signal?.aborted) {
      handleAbort()
      return
    }
    signal?.addEventListener('abort', handleAbort, { once: true })
    worker.postMessage({ file, sliceSize: 2 * 1024 * 1024 })
  })
}
