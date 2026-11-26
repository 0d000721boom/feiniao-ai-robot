import SparkMD5 from 'spark-md5'

self.onmessage = async ({ data }) => {
  const { file, sliceSize = 2 * 1024 * 1024 } = data
  const spark = new SparkMD5.ArrayBuffer()

  try {
    for (let start = 0; start < file.size; start += sliceSize) {
      const end = Math.min(file.size, start + sliceSize)
      spark.append(await file.slice(start, end).arrayBuffer())
      self.postMessage({
        type: 'progress',
        percent: Math.round((end / file.size) * 100),
      })
    }
    self.postMessage({ type: 'done', md5: spark.end() })
  } catch (error) {
    self.postMessage({
      type: 'error',
      message: error instanceof Error ? error.message : String(error),
    })
  }
}
