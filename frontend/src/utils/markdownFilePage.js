export function normalizeMarkdownFilePage(responseData) {
  if (Array.isArray(responseData?.records)) {
    return responseData
  }

  return responseData?.data ?? responseData
}
