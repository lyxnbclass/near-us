export function isUserCancel(error: any) {
  const message = String(error?.errMsg || error?.message || error || '').toLowerCase()
  return message.includes('cancel') || message.includes('取消')
}
