const DEFAULT_BASE_URL = 'http://localhost:8080/api'
const BASE_URL = normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL || DEFAULT_BASE_URL)
const DEMO_STORAGE_KEY = 'ourspace-demo-state'
const LOVE_START_DATE = '2026-02-23'
const NEXT_MEETING_DATE = '2026-07-03'
const PET_NAME = '芋圆'
const PET_BIRTHDAY = '2026-01-17'

type MockResult = { handled: true; data: unknown } | { handled: false }
type ApiBody<T> = { success: boolean; data: T; error?: string }
type ApiRequestOptions = Omit<UniApp.RequestOptions, 'url'> & { url?: string }

class ApiRequestError extends Error {
  readonly statusCode?: number
  readonly shouldUseMock: boolean

  constructor(message: string, options: { statusCode?: number; shouldUseMock?: boolean } = {}) {
    super(message)
    this.name = 'ApiRequestError'
    this.statusCode = options.statusCode
    this.shouldUseMock = Boolean(options.shouldUseMock)
  }
}

const ERROR_MESSAGES: Record<string, string> = {
  PAIR_REQUIRED: '请先完成配对',
  ALREADY_HAS_COUPLE_OR_INVITE: '你已经有配对空间或待绑定邀请',
  ALREADY_PAIRED: '你已经完成配对',
  CANNOT_BIND_OWN_INVITE: '不能绑定自己创建的配对密语',
  COUPLE_FULL: '这个配对空间已经满员',
  INVITE_NOT_FOUND: '没有找到这串配对密语',
  INVITE_EXPIRED: '这串配对密语已经过期',
  ONLY_REQUESTER_CAN_CANCEL: '只有申请人可以撤销解绑',
  UNBIND_NOT_PENDING: '当前没有待确认的解绑申请',
  MODULE_DISABLED: '这个模块还没有开启',
  MODULE_UNSUPPORTED: '暂时不支持这个模块',
  PET_NOT_FOUND: '没有找到这只宠物',
  PET_EVENT_NOT_FOUND: '没有找到这条宠物动态',
  FILE_NOT_FOUND: '没有找到这个文件',
  FILE_EMPTY: '请选择一个有效文件',
  FILE_INVALID_NAME: '文件名包含暂不支持的字符',
  ALBUM_NOT_FOUND: '没有找到这条相册记录',
  ANNIVERSARY_NOT_FOUND: '没有找到这个纪念日',
  DIARY_NOT_FOUND: '没有找到这篇日记',
  DIARY_NOT_OWNER: '只能编辑自己的日记',
  DIARY_PRIVATE: '这篇日记暂时不能评论',
  INVALID_DIARY_VISIBILITY: '请选择正确的日记可见范围',
  WISH_NOT_FOUND: '没有找到这个愿望',
  FUTURE_LETTER_NOT_FOUND: '没有找到这封未来信',
  FUTURE_LETTER_LOCKED: '还没到打开的时候',
  FUTURE_LETTER_OPEN_AT_PAST: '请选择未来的打开时间',
  FUTURE_LETTER_OPEN_AT_REQUIRED: '请选择打开时间',
  INVALID_RECIPIENT_MODE: '请选择正确的收信方式',
  STATUS_NOT_FOUND: '没有找到这条此刻',
  STATUS_NOT_OWNER: '只能撤回自己发布的此刻',
  VALIDATION_FAILED: '请检查填写内容',
  SERVER_ERROR: '服务暂时开了小差',
  REQUEST_FAILED: '请求失败，请稍后再试'
}

export function getErrorCode(error: unknown) {
  return error instanceof Error ? error.message : String(error || '')
}

export function getErrorMessage(error: unknown, fallback = '操作失败，请稍后再试') {
  const code = getErrorCode(error)
  if (!code) return fallback
  if (ERROR_MESSAGES[code]) return ERROR_MESSAGES[code]
  if (code.startsWith('HTTP_')) return '服务连接异常，请稍后再试'
  return code
}

export function toDisplayMediaUrl(item: any) {
  const url = item?.local_url || item?.localUrl || item?.signed_url || item?.signedUrl || ''
  if (url) return normalizeMediaUrl(url)
  const objectKey = item?.object_key || item?.objectKey || ''
  return isDisplayableMediaUrl(objectKey) ? normalizeMediaUrl(objectKey) : ''
}

export async function enrichSignedFileUrls<T extends Record<string, any>>(items: T[]) {
  return Promise.all(items.map(async item => {
    if (toDisplayMediaUrl(item)) return item
    const fileId = item.file_id || item.fileId
    if (!fileId) return item
    try {
      const signed = await request<{ url: string }>(`/files/${fileId}/signed-url`)
      return {
        ...item,
        local_url: normalizeMediaUrl(signed.url)
      }
    } catch {
      return item
    }
  }))
}

export async function uploadMediaFile(filePath: string, options: { name?: string; size?: number; mimeType?: string } = {}) {
  const token = getToken()
  try {
    const response = await uni.uploadFile({
      url: `${BASE_URL}/files/upload`,
      filePath,
      name: 'file',
      formData: {
        originalName: options.name || filePath
      },
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
    })
    return parseUploadResponse<{ id: number; objectKey?: string; url?: string }>(response)
  } catch {
    return request<{ id: number }>('/files', {
      method: 'POST',
      data: {
        objectKey: filePath,
        originalName: options.name || filePath,
        mimeType: options.mimeType || inferMimeType(options.name || filePath),
        sizeBytes: options.size || 0
      }
    })
  }
}

export function getToken() {
  return uni.getStorageSync('token') as string | undefined
}

export function setToken(token: string) {
  uni.setStorageSync('token', token)
}

export function clearToken() {
  uni.removeStorageSync('token')
}

export function resetDemoState() {
  uni.removeStorageSync(DEMO_STORAGE_KEY)
}

export async function request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const token = getToken()
  try {
    const response = await uni.request({
      url: `${BASE_URL}${path}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: 2500,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      }
    })
    return parseResponse<T>(response)
  } catch (error) {
    if (error instanceof ApiRequestError && !error.shouldUseMock) {
      throw error
    }
    const mocked = mockRequest(path, options)
    if (mocked.handled) {
      return mocked.data as T
    }
    throw error
  }
}

function parseResponse<T>(response: UniApp.RequestSuccessCallbackResult): T {
  const statusCode = Number(response.statusCode || 0)
  const body = response.data as ApiBody<T> | undefined
  const message = body?.error || `HTTP_${statusCode || 'REQUEST_FAILED'}`

  if (!statusCode) {
    throw new ApiRequestError('REQUEST_FAILED', { shouldUseMock: true })
  }

  if (statusCode < 200 || statusCode >= 300) {
    throw new ApiRequestError(message, { statusCode })
  }

  if (!body?.success) {
    throw new ApiRequestError(message || 'REQUEST_FAILED', { statusCode })
  }

  return body.data
}

function parseUploadResponse<T>(response: UniApp.UploadFileSuccessCallbackResult): T {
  const data = typeof response.data === 'string' ? JSON.parse(response.data || '{}') : response.data
  return parseResponse<T>({
    statusCode: response.statusCode,
    data
  } as UniApp.RequestSuccessCallbackResult)
}

function normalizeBaseUrl(value: string) {
  return String(value || DEFAULT_BASE_URL).replace(/\/+$/, '')
}

function normalizeMediaUrl(url: string) {
  if (!url) return ''
  if (url.startsWith('/')) {
    try {
      return `${new URL(BASE_URL).origin}${url}`
    } catch {
      return url
    }
  }
  if (isDisplayableMediaUrl(url)) return url
  return ''
}

function isDisplayableMediaUrl(url: string) {
  return /^(https?:|data:|blob:|file:|wxfile:|\/)/i.test(url)
}

function inferMimeType(name: string) {
  const lower = name.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.gif')) return 'image/gif'
  return 'image/jpeg'
}

function mockRequest(path: string, options: ApiRequestOptions): MockResult {
  const method = (options.method || 'GET').toUpperCase()
  const state = getDemoState()

  if (path === '/auth/mock-login' && method === 'POST') {
    const body = options.data as any
    state.user = {
      id: 1,
      phone: body?.phone || '13800000001',
      nickname: body?.nickname || '我'
    }
    saveDemoState(state)
    return handled({ token: 'demo-token', userId: state.user.id })
  }

  if (path === '/couples/me' && method === 'GET') {
    return handled(state.paired
      ? {
          paired: true,
          couple: {
            id: 1,
            paired_at: state.pairedAt || LOVE_START_DATE,
            anniversary_start_date: LOVE_START_DATE,
            status: state.coupleStatus || 'active',
            unbind_requested_by: state.unbindRequestedBy || null,
            unbind_requested_at: state.unbindRequestedAt || null
          },
          members: [
            { id: 1, nickname: state.user?.nickname || '我' },
            { id: 2, nickname: '对方' }
          ]
        }
      : { paired: false })
  }

  if (path === '/home' && method === 'GET') {
    if (!state.paired) {
      throw new Error('PAIR_REQUIRED')
    }
    const anniversaries = state.anniversaries || defaultDemoState().anniversaries
    const notifications = state.notifications || defaultDemoState().notifications
    const statuses = state.statuses || defaultDemoState().statuses
    const wishes = state.wishes || defaultDemoState().wishes
    const albums = state.albums || defaultDemoState().albums
    const futureLetters = state.futureLetters || defaultDemoState().futureLetters
    const dailyTopic = state.dailyTopic || defaultDemoState().dailyTopic
    const nextAnniversaries = anniversaries
      .map((item: any) => ({
        ...item,
        days_left: daysLeft(item.event_date)
      }))
      .sort((a: any, b: any) => (a.days_left ?? 9999) - (b.days_left ?? 9999))
    const unlockedLetters = futureLetters.filter((item: any) => new Date(item.open_at).getTime() <= Date.now())
    const completedWishes = wishes.filter((item: any) => item.completed).length
    return handled({
      togetherDays: daysSince(LOVE_START_DATE),
      partnerStatus: statuses.find((item: any) => item.nickname === '对方') || statuses[0] || null,
      anniversaries: nextAnniversaries.slice(0, 3),
      enabledModules: state.petEnabled ? [{ module_key: 'pet' }] : [],
      unreadCount: notifications.filter((item: any) => !item.read_at).length,
      latestStatus: statuses[0] || null,
      latestAlbum: albums[0] || null,
      wishProgress: {
        total: wishes.length,
        completed: completedWishes,
        next: wishes.find((item: any) => !item.completed) || wishes[0] || null
      },
      dailyTopic: {
        question: dailyTopic?.topic?.question || '今晚想和对方聊点什么？',
        unlocked: Boolean(dailyTopic?.unlocked),
        answeredByMe: Boolean(dailyTopic?.answeredByMe)
      },
      futureLetterCount: futureLetters.length,
      unlockedFutureLetterCount: unlockedLetters.length
    })
  }

  if (path === '/couples/invite' && method === 'POST') {
    state.inviteCode = 'NEAR2026'
    state.inviteExpiresAt = new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString()
    saveDemoState(state)
    return handled({ inviteCode: state.inviteCode, expiresAt: state.inviteExpiresAt, coupleId: 1 })
  }

  if (path === '/couples/bind' && method === 'POST') {
    const body = options.data as any
    const submittedCode = String(body?.inviteCode || '').trim().toUpperCase()
    const expectedCode = state.inviteCode || 'NEAR2026'
    if (!submittedCode || submittedCode !== expectedCode) {
      throw new Error('INVITE_NOT_FOUND')
    }
    if (state.inviteExpiresAt && new Date(state.inviteExpiresAt).getTime() <= Date.now()) {
      throw new Error('INVITE_EXPIRED')
    }
    state.paired = true
    state.pairedAt = LOVE_START_DATE
    state.coupleStatus = 'active'
    state.unbindRequestedBy = null
    state.unbindRequestedAt = null
    saveDemoState(state)
    return handled({ coupleId: 1 })
  }

  if (path === '/couples/unbind-request' && method === 'POST') {
    state.coupleStatus = 'unbind_pending'
    state.unbindRequestedBy = state.user?.id || 1
    state.unbindRequestedAt = new Date().toISOString()
    saveDemoState(state)
    return handled({ coupleId: 1, status: 'unbind_pending' })
  }

  if (path === '/couples/unbind-cancel' && method === 'POST') {
    state.coupleStatus = 'active'
    state.unbindRequestedBy = null
    state.unbindRequestedAt = null
    saveDemoState(state)
    return handled({ coupleId: 1, status: 'active' })
  }

  if (path === '/couples/unbind-confirm' && method === 'POST') {
    state.paired = false
    state.coupleStatus = null
    state.pairedAt = null
    state.unbindRequestedBy = null
    state.unbindRequestedAt = null
    saveDemoState(state)
    return handled({ coupleId: 1, status: 'unbound' })
  }

  if (path === '/modules' && method === 'GET') {
    return handled(state.petEnabled ? [{ module_key: 'pet', enabled: true }] : [])
  }

  if (path === '/modules/pet' && method === 'PUT') {
    state.petEnabled = Boolean((options.data as any)?.enabled)
    saveDemoState(state)
    return handled({ moduleKey: 'pet', enabled: state.petEnabled })
  }

  if (path === '/modules/pet/profiles' && method === 'GET') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    state.petProfiles = state.petProfiles || []
    return handled(state.petProfiles)
  }

  if (path === '/modules/pet/profiles' && method === 'POST') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    const body = options.data as any
    state.petProfiles = state.petProfiles || []
    const profile = {
      id: Date.now(),
      name: body?.name || '小朋友',
      breed: body?.breed || '',
      birthday: body?.birthday || null,
      avatar_file_id: body?.avatarFileId || null,
      created_by: state.user?.id || 1,
      created_at: new Date().toISOString()
    }
    state.petProfiles.unshift(profile)
    saveDemoState(state)
    return handled({ id: profile.id })
  }

  if (path.startsWith('/modules/pet/profiles/') && method === 'PUT') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    const id = Number(path.split('/')[4])
    const body = options.data as any
    let updated = false
    state.petProfiles = (state.petProfiles || []).map((item: any) => {
      if (item.id !== id) return item
      updated = true
      return {
        ...item,
        name: body?.name || item.name,
        breed: body?.breed ?? item.breed,
        birthday: body?.birthday ?? item.birthday,
        updated_at: new Date().toISOString()
      }
    })
    state.petEvents = (state.petEvents || []).map((item: any) => item.pet_id === id
      ? { ...item, pet_name: body?.name || item.pet_name }
      : item)
    if (!updated) throw new Error('PET_NOT_FOUND')
    saveDemoState(state)
    return handled({ id })
  }

  if (path.startsWith('/modules/pet/profiles/') && method === 'DELETE') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    const id = Number(path.split('/')[4])
    const before = (state.petProfiles || []).length
    state.petProfiles = (state.petProfiles || []).filter((item: any) => item.id !== id)
    if (state.petProfiles.length === before) throw new Error('PET_NOT_FOUND')
    state.petEvents = (state.petEvents || []).filter((item: any) => item.pet_id !== id)
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path === '/modules/pet/events' && method === 'GET') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    const petId = queryNumber(path, 'petId')
    const events = state.petEvents || []
    return handled(petId ? events.filter((item: any) => item.pet_id === petId) : events)
  }

  if (path === '/modules/pet/events' && method === 'POST') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    const body = options.data as any
    const pet = (state.petProfiles || []).find((item: any) => item.id === body?.petId)
    if (!pet) throw new Error('PET_NOT_FOUND')
    const file = (state.files || []).find((item: any) => item.id === body?.fileId)
    state.petEvents = state.petEvents || []
    const event = {
      id: Date.now(),
      pet_id: body?.petId,
      pet_name: pet.name,
      event_type: body?.eventType || '日常',
      content: body?.content || '',
      mood: body?.mood || '',
      file_id: body?.fileId || null,
      object_key: body?.localUrl || file?.object_key || '',
      local_url: body?.localUrl || file?.object_key || '',
      mime_type: file?.mime_type || null,
      created_by: state.user?.id || 1,
      created_at: new Date().toISOString()
    }
    state.petEvents.unshift(event)
    saveDemoState(state)
    return handled({ id: event.id })
  }

  if (path.startsWith('/modules/pet/events/') && method === 'DELETE') {
    if (!state.petEnabled) throw new Error('MODULE_DISABLED')
    const id = Number(path.split('/')[4])
    const before = (state.petEvents || []).length
    state.petEvents = (state.petEvents || []).filter((item: any) => item.id !== id)
    if (state.petEvents.length === before) throw new Error('PET_EVENT_NOT_FOUND')
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path === '/files' && method === 'POST') {
    const body = options.data as any
    state.files = state.files || []
    const file = {
      id: Date.now(),
      object_key: body?.objectKey || '',
      original_name: body?.originalName || 'memory.jpg',
      mime_type: body?.mimeType || 'image/jpeg',
      size_bytes: body?.sizeBytes || 0,
      created_at: new Date().toISOString()
    }
    state.files.unshift(file)
    saveDemoState(state)
    return handled({ id: file.id })
  }

  if (path.startsWith('/files/') && path.endsWith('/signed-url') && method === 'GET') {
    const id = Number(path.split('/')[2])
    const file = (state.files || []).find((item: any) => item.id === id)
    if (!file) {
      throw new Error('FILE_NOT_FOUND')
    }
    return handled({ url: file.object_key, ttlSeconds: 900 })
  }

  if (path === '/albums' && method === 'GET') {
    return handled(state.albums || [])
  }

  if (path === '/albums' && method === 'POST') {
    const body = options.data as any
    const file = (state.files || []).find((item: any) => item.id === body?.fileId)
    state.albums = state.albums || []
    const album = {
      id: Date.now(),
      file_id: body?.fileId,
      caption: body?.caption || '',
      scene_type: body?.sceneType || 'couple_album',
      scene_ref_id: body?.sceneRefId || null,
      object_key: body?.localUrl || file?.object_key || '',
      local_url: body?.localUrl || file?.object_key || '',
      mime_type: file?.mime_type || 'image/jpeg',
      created_at: new Date().toISOString(),
      creator_name: state.user?.nickname || '我'
    }
    state.albums.unshift(album)
    saveDemoState(state)
    return handled({ id: album.id })
  }

  if (path.startsWith('/albums/') && method === 'PUT') {
    const id = Number(path.split('/')[2])
    const body = options.data as any
    let updated = false
    state.albums = (state.albums || []).map((item: any) => {
      if (item.id !== id) return item
      updated = true
      return {
        ...item,
        caption: body?.caption ?? item.caption,
        updated_at: new Date().toISOString()
      }
    })
    if (!updated) throw new Error('ALBUM_NOT_FOUND')
    saveDemoState(state)
    return handled({ id })
  }

  if (path.startsWith('/albums/') && method === 'DELETE') {
    const id = Number(path.split('/')[2])
    const before = (state.albums || []).length
    state.albums = (state.albums || []).filter((item: any) => item.id !== id)
    if (state.albums.length === before) throw new Error('ALBUM_NOT_FOUND')
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path === '/diaries' && method === 'GET') {
    state.diaries = state.diaries || []
    return handled(state.diaries.map((item: any) => ({
      id: item.id,
      owner_user_id: item.owner_user_id,
      title: item.title,
      visibility: item.visibility,
      created_at: item.created_at,
      updated_at: item.updated_at
    })))
  }

  if (path === '/diaries' && method === 'POST') {
    const body = options.data as any
    state.diaries = state.diaries || []
    const diary = {
      id: Date.now(),
      owner_user_id: state.user?.id || 1,
      title: body?.title || '今天想保存的一小段',
      content: body?.content || '',
      visibility: body?.visibility || 'private',
      comments: [],
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    }
    state.diaries.unshift(diary)
    saveDemoState(state)
    return handled({ id: diary.id })
  }

  if (path.startsWith('/diaries/') && method === 'PUT') {
    const id = Number(path.split('/')[2])
    const body = options.data as any
    let updated = false
    state.diaries = (state.diaries || []).map((item: any) => {
      if (item.id !== id) return item
      updated = true
      return {
        ...item,
        title: body?.title || item.title,
        content: body?.content || item.content,
        visibility: body?.visibility || item.visibility,
        updated_at: new Date().toISOString()
      }
    })
    if (!updated) throw new Error('DIARY_NOT_FOUND')
    saveDemoState(state)
    return handled({ id })
  }

  if (path.startsWith('/diaries/') && method === 'DELETE') {
    const id = Number(path.split('/')[2])
    const before = (state.diaries || []).length
    state.diaries = (state.diaries || []).filter((item: any) => item.id !== id)
    if (state.diaries.length === before) throw new Error('DIARY_NOT_FOUND')
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path.startsWith('/diaries/') && path.endsWith('/comments') && method === 'GET') {
    const id = Number(path.split('/')[2])
    const diary = (state.diaries || []).find((item: any) => item.id === id)
    if (!diary) throw new Error('DIARY_NOT_FOUND')
    if (diary.visibility !== 'shared') throw new Error('DIARY_PRIVATE')
    return handled(diary.comments || [])
  }

  if (path.startsWith('/diaries/') && path.endsWith('/comments') && method === 'POST') {
    const id = Number(path.split('/')[2])
    const body = options.data as any
    const diary = (state.diaries || []).find((item: any) => item.id === id)
    if (!diary) throw new Error('DIARY_NOT_FOUND')
    if (diary.visibility !== 'shared') throw new Error('DIARY_PRIVATE')
    diary.comments = diary.comments || []
    diary.comments.push({
      id: Date.now(),
      created_by: state.user?.id || 1,
      nickname: state.user?.nickname || '我',
      content: body?.content || '',
      created_at: new Date().toISOString()
    })
    saveDemoState(state)
    return handled({ id: diary.comments[diary.comments.length - 1].id })
  }

  if (path.startsWith('/diaries/') && method === 'GET') {
    const id = Number(path.split('/')[2])
    const diary = (state.diaries || []).find((item: any) => item.id === id)
    if (!diary) throw new Error('DIARY_NOT_FOUND')
    return handled(diary)
  }

  if (path === '/anniversaries' && method === 'GET') {
    state.anniversaries = state.anniversaries || []
    return handled(state.anniversaries)
  }

  if (path === '/anniversaries' && method === 'POST') {
    const body = options.data as any
    state.anniversaries = state.anniversaries || []
    const anniversary = {
      id: Date.now(),
      title: body?.title || '我们的纪念日',
      event_date: body?.eventDate || new Date().toISOString().slice(0, 10),
      event_type: body?.eventType || 'custom',
      remind_time: body?.remindTime || null,
      card_theme: body?.cardTheme || 'warm',
      created_at: new Date().toISOString()
    }
    state.anniversaries.unshift(anniversary)
    saveDemoState(state)
    return handled({ id: anniversary.id })
  }

  if (path.startsWith('/anniversaries/') && method === 'PUT') {
    const id = Number(path.split('/')[2])
    const body = options.data as any
    state.anniversaries = (state.anniversaries || []).map((item: any) => item.id === id
      ? {
          ...item,
          title: body?.title || item.title,
          event_date: body?.eventDate || item.event_date,
          event_type: body?.eventType || item.event_type || 'custom',
          remind_time: body?.remindTime || null,
          card_theme: body?.cardTheme || item.card_theme || 'warm',
          updated_at: new Date().toISOString()
        }
      : item)
    saveDemoState(state)
    return handled({ id })
  }

  if (path.startsWith('/anniversaries/') && method === 'DELETE') {
    const id = Number(path.split('/')[2])
    state.anniversaries = (state.anniversaries || []).filter((item: any) => item.id !== id)
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path === '/interactions/affection-cards' && method === 'GET') {
    return handled(state.affectionCards || [])
  }

  if (path === '/interactions/affection-cards' && method === 'POST') {
    const body = options.data as any
    state.affectionCards = state.affectionCards || []
    state.affectionCards.unshift({
      id: Date.now(),
      title: body?.title || '给你的一点心意',
      amount: body?.amount || null,
      message: body?.message || '',
      nickname: state.user?.nickname || '我',
      created_at: new Date().toISOString()
    })
    saveDemoState(state)
    return handled({ id: state.affectionCards[0].id })
  }

  if (path === '/interactions/wishes' && method === 'GET') {
    return handled(state.wishes || [])
  }

  if (path === '/interactions/wishes' && method === 'POST') {
    const body = options.data as any
    state.wishes = state.wishes || []
    state.wishes.unshift({
      id: Date.now(),
      title: body?.title || '一起做一件小事',
      note: body?.note || '',
      completed: false,
      nickname: state.user?.nickname || '我',
      created_at: new Date().toISOString()
    })
    saveDemoState(state)
    return handled({ id: state.wishes[0].id })
  }

  if (path.startsWith('/interactions/wishes/') && path.endsWith('/complete') && method === 'POST') {
    const id = Number(path.split('/')[3])
    state.wishes = (state.wishes || []).map((item: any) => item.id === id
      ? { ...item, completed: true, completed_at: new Date().toISOString() }
      : item)
    saveDemoState(state)
    return handled({ id, completed: true })
  }

  if (path.startsWith('/interactions/wishes/') && path.endsWith('/reopen') && method === 'POST') {
    const id = Number(path.split('/')[3])
    state.wishes = (state.wishes || []).map((item: any) => item.id === id
      ? { ...item, completed: false, completed_at: null, completed_by: null, updated_at: new Date().toISOString() }
      : item)
    saveDemoState(state)
    return handled({ id, completed: false })
  }

  if (path.startsWith('/interactions/wishes/') && method === 'PUT') {
    const id = Number(path.split('/')[3])
    const body = options.data as any
    let updated = false
    state.wishes = (state.wishes || []).map((item: any) => {
      if (item.id !== id) return item
      updated = true
      return {
        ...item,
        title: body?.title || item.title,
        note: body?.note ?? item.note,
        updated_at: new Date().toISOString()
      }
    })
    if (!updated) throw new Error('WISH_NOT_FOUND')
    saveDemoState(state)
    return handled({ id })
  }

  if (path.startsWith('/interactions/wishes/') && method === 'DELETE') {
    const id = Number(path.split('/')[3])
    const before = (state.wishes || []).length
    state.wishes = (state.wishes || []).filter((item: any) => item.id !== id)
    if (state.wishes.length === before) throw new Error('WISH_NOT_FOUND')
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path === '/interactions/daily-topic' && method === 'GET') {
    state.dailyTopic = state.dailyTopic || {
      topic: {
        id: 1,
        question: '如果今晚能把一小时留给对方，你想怎么用？',
        topic_date: new Date().toISOString().slice(0, 10)
      },
      answeredByMe: false,
      unlocked: false,
      answers: []
    }
    saveDemoState(state)
    return handled(state.dailyTopic)
  }

  if (path === '/interactions/daily-topic/answer' && method === 'POST') {
    const body = options.data as any
    state.dailyTopic = state.dailyTopic || {
      topic: { id: 1, question: '如果今晚能把一小时留给对方，你想怎么用？' },
      answers: []
    }
    const mine = {
      user_id: state.user?.id || 1,
      nickname: state.user?.nickname || '我',
      answer: body?.answer || '',
      created_at: new Date().toISOString()
    }
    const partner = {
      user_id: 2,
      nickname: '对方',
      answer: '想一起散步，然后在路灯下多抱一会儿。',
      created_at: new Date().toISOString()
    }
    state.dailyTopic.answeredByMe = true
    state.dailyTopic.unlocked = true
    state.dailyTopic.answers = [mine, partner]
    saveDemoState(state)
    return handled({ answered: true })
  }

  if (path === '/future-letters' && method === 'GET') {
    state.futureLetters = state.futureLetters || []
    return handled(state.futureLetters.map((item: any) => ({
      id: item.id,
      title: item.title,
      open_at: item.open_at,
      created_at: item.created_at,
      recipient_user_id: item.recipient_user_id ?? 2,
      sender_name: item.sender_name || state.user?.nickname || '我',
      unlocked: new Date(item.open_at).getTime() <= Date.now()
    })))
  }

  if (path === '/future-letters' && method === 'POST') {
    const body = options.data as any
    state.futureLetters = state.futureLetters || []
    state.futureLetters.unshift({
      id: Date.now(),
      title: body?.title || '写给未来的你',
      content: body?.content || '',
      open_at: body?.openAt || new Date().toISOString(),
      recipient_user_id: body?.recipientMode === 'both' ? null : 2,
      created_at: new Date().toISOString(),
      sender_name: state.user?.nickname || '我'
    })
    saveDemoState(state)
    return handled({ id: state.futureLetters[0].id })
  }

  if (path.startsWith('/future-letters/') && method === 'GET') {
    const id = Number(path.split('/')[2])
    const letter = (state.futureLetters || []).find((item: any) => item.id === id)
    if (!letter) throw new Error('FUTURE_LETTER_NOT_FOUND')
    if (new Date(letter.open_at).getTime() > Date.now()) throw new Error('FUTURE_LETTER_LOCKED')
    return handled(letter)
  }

  if (path === '/memories/today' && method === 'GET') {
    return handled({
      date: new Date().toISOString().slice(0, 10),
      albums: [
        {
          id: 1,
          caption: '一年前的今天，我们说下次要一起看海。',
          creator_name: '对方',
          mime_type: 'image/jpeg',
          object_key: '',
          local_url: '',
          years_ago: 1,
          created_at: new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString()
        },
        {
          id: 2,
          caption: '那天回家的路很慢，风也很温柔。',
          creator_name: state.user?.nickname || '我',
          mime_type: 'image/jpeg',
          object_key: '',
          local_url: '',
          years_ago: 2,
          created_at: new Date(Date.now() - 730 * 24 * 60 * 60 * 1000).toISOString()
        }
      ],
      diaries: [
        {
          id: 1,
          title: '那天没有说出口的话',
          visibility: 'shared',
          years_ago: 1,
          created_at: new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString()
        }
      ]
    })
  }

  if (path === '/privacy/export' && method === 'GET') {
    return handled({
      exportedAt: new Date().toISOString(),
      user: state.user || { id: 1, nickname: '我', phone: '13800000001', status: 'active' },
      couple: state.paired ? { id: 1, status: state.coupleStatus || 'active', paired_at: state.pairedAt || LOVE_START_DATE, anniversary_start_date: LOVE_START_DATE } : null,
      albums: state.albums || [],
      statuses: state.statuses || [],
      diaries: (state.diaries || []).map((item: any) => ({
        id: item.id,
        title: item.title,
        visibility: item.visibility,
        created_at: item.created_at
      })),
      anniversaries: state.anniversaries || [],
      affectionCards: state.affectionCards || [],
      wishes: state.wishes || [],
      futureLetters: (state.futureLetters || []).map((item: any) => ({
        id: item.id,
        title: item.title,
        open_at: item.open_at,
        created_at: item.created_at
      }))
    })
  }

  if (path === '/privacy/deletion-request' && method === 'POST') {
    state.privacyRequests = state.privacyRequests || []
    const deletionRequest = {
      id: Date.now(),
      request_type: 'account_deletion',
      status: 'pending',
      requested_at: new Date().toISOString(),
      scheduled_delete_at: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      note: '演示模式：账号将在冷静期后删除'
    }
    state.privacyRequests.unshift(deletionRequest)
    saveDemoState(state)
    return handled({ status: 'pending', scheduledDeleteAt: deletionRequest.scheduled_delete_at })
  }

  if (path === '/privacy/deletion-request/cancel' && method === 'POST') {
    let cancelled = 0
    state.privacyRequests = (state.privacyRequests || []).map((item: any) => {
      if (item.request_type !== 'account_deletion' || item.status !== 'pending') return item
      cancelled += 1
      return {
        ...item,
        status: 'cancelled',
        completed_at: new Date().toISOString(),
        note: '演示模式：已在冷静期内撤销注销申请'
      }
    })
    if (state.user) {
      state.user.status = 'active'
    }
    saveDemoState(state)
    return handled({ cancelled })
  }

  if (path === '/privacy/requests' && method === 'GET') {
    return handled(state.privacyRequests || [])
  }

  if (path === '/statuses' && method === 'GET') {
    state.statuses = state.statuses || [
      { id: 1, content: '刚刚路过一家甜品店，忽然想和你一起进去。', nickname: '我', mood_tag: '在想你', reactions: '' },
      { id: 2, content: '今天的风有点软，适合靠近一点。', nickname: '对方', mood_tag: '想被抱一下', reactions: '抱抱' }
    ]
    return handled(state.statuses)
  }

  if (path === '/statuses' && method === 'POST') {
    const body = options.data as any
    const file = (state.files || []).find((item: any) => item.id === body?.fileId)
    state.statuses = state.statuses || []
    state.notifications = state.notifications || []
    state.statuses.unshift({
      id: Date.now(),
      content: body?.content || '我在这里，轻轻想你一下。',
      nickname: state.user?.nickname || '我',
      mood_tag: body?.moodTag || '在想你',
      file_id: body?.fileId || null,
      object_key: body?.localUrl || file?.object_key || '',
      local_url: body?.localUrl || file?.object_key || '',
      mime_type: file?.mime_type || null,
      reactions: '',
      created_at: new Date().toISOString()
    })
    state.notifications.unshift({
      id: Date.now() + 1,
      type: 'status.created',
      title: '新的此刻',
      body: body?.content || '',
      read_at: null,
      created_at: new Date().toISOString()
    })
    saveDemoState(state)
    return handled({ id: state.statuses[0].id })
  }

  if (path.startsWith('/statuses/') && path.endsWith('/reactions') && method === 'POST') {
    const id = Number(path.split('/')[2])
    const body = options.data as any
    const reactionKey = body?.reactionKey || '抱抱'
    state.statuses = (state.statuses || []).map((item: any) => item.id === id
      ? {
          ...item,
          reactions: reactionList(item.reactions).includes(reactionKey)
            ? item.reactions
            : item.reactions ? `${item.reactions},${reactionKey}` : reactionKey
        }
      : item)
    state.notifications = state.notifications || []
    if (!(state.notifications || []).some((item: any) => item.type === 'status.reacted' && item.body === reactionKey && !item.read_at)) {
      state.notifications.unshift({
        id: Date.now(),
        type: 'status.reacted',
        title: 'TA 回应了你的此刻',
        body: reactionKey,
        read_at: null,
        created_at: new Date().toISOString()
      })
    }
    saveDemoState(state)
    return handled({ id, reactionKey })
  }

  if (path.startsWith('/statuses/') && method === 'DELETE') {
    const id = Number(path.split('/')[2])
    const before = (state.statuses || []).length
    state.statuses = (state.statuses || []).filter((item: any) => item.id !== id)
    if (state.statuses.length === before) throw new Error('STATUS_NOT_FOUND')
    saveDemoState(state)
    return handled({ id, deleted: true })
  }

  if (path === '/notifications' && method === 'GET') {
    state.notifications = state.notifications || [
      { id: 1, type: 'status.reacted', title: 'TA 回应了你的此刻', body: '抱抱', read_at: null, created_at: new Date().toISOString() },
      { id: 2, type: 'affection.created', title: '收到一张心意卡片', body: '给你点了一杯热奶茶', read_at: new Date().toISOString(), created_at: new Date().toISOString() }
    ]
    return handled(state.notifications)
  }

  if (path === '/notifications/read-all' && method === 'POST') {
    const now = new Date().toISOString()
    let updated = 0
    state.notifications = (state.notifications || []).map((item: any) => {
      if (item.read_at) return item
      updated += 1
      return { ...item, read_at: now }
    })
    saveDemoState(state)
    return handled({ updated })
  }

  if (path.startsWith('/notifications/') && path.endsWith('/read') && method === 'POST') {
    const id = Number(path.split('/')[2])
    state.notifications = (state.notifications || []).map((item: any) => item.id === id
      ? { ...item, read_at: new Date().toISOString() }
      : item)
    saveDemoState(state)
    return handled({ id, read: true })
  }

  return { handled: false }
}

function handled<T>(data: T): { handled: true; data: T } {
  return { handled: true, data }
}

function daysLeft(dateText: string) {
  if (!dateText) return null
  const normalized = dateText.includes('T') ? dateText.slice(0, 10) : dateText
  const [year, month, day] = normalized.split('-').map(Number)
  if (!year || !month || !day) return null
  const today = new Date()
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const target = new Date(year, month - 1, day)
  let next = new Date(start.getFullYear(), target.getMonth(), target.getDate())
  if (next.getTime() < start.getTime()) {
    next = new Date(start.getFullYear() + 1, target.getMonth(), target.getDate())
  }
  return Math.ceil((next.getTime() - start.getTime()) / 86400000)
}

function daysSince(dateText: string) {
  const [year, month, day] = dateText.split('-').map(Number)
  const today = new Date()
  const start = new Date(year, month - 1, day)
  const current = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const firstDay = new Date(start.getFullYear(), start.getMonth(), start.getDate())
  return Math.max(1, Math.floor((current.getTime() - firstDay.getTime()) / 86400000) + 1)
}

function queryNumber(path: string, key: string) {
  const query = path.split('?')[1]
  if (!query) return null
  const params = new URLSearchParams(query)
  const value = Number(params.get(key))
  return Number.isFinite(value) && value > 0 ? value : null
}

function reactionList(value: string) {
  if (!value) return []
  return String(value).split(',').filter(Boolean)
}

function getDemoState() {
  const stored = uni.getStorageSync(DEMO_STORAGE_KEY)
  if (stored) {
    const migrated = personalizeDemoState(migrateDemoState(stored))
    saveDemoState(migrated)
    return migrated
  }
  return defaultDemoState()
}

function migrateDemoState(state: any) {
  const defaults = defaultDemoState()
  return {
    ...defaults,
    ...state,
    affectionCards: hasReadableList(state.affectionCards) ? state.affectionCards : defaults.affectionCards,
    wishes: hasReadableList(state.wishes) ? state.wishes : defaults.wishes,
    futureLetters: hasReadableList(state.futureLetters) ? state.futureLetters : defaults.futureLetters,
    diaries: hasReadableList(state.diaries) ? state.diaries : defaults.diaries,
    anniversaries: hasReadableList(state.anniversaries) ? state.anniversaries : defaults.anniversaries,
    albums: hasReadableList(state.albums) ? state.albums : defaults.albums,
    statuses: hasReadableList(state.statuses) ? state.statuses : defaults.statuses,
    notifications: hasReadableList(state.notifications) ? state.notifications : defaults.notifications,
    petProfiles: hasReadableList(state.petProfiles) ? state.petProfiles : defaults.petProfiles,
    petEvents: hasReadableList(state.petEvents) ? state.petEvents : defaults.petEvents
  }
}

function personalizeDemoState(state: any) {
  const defaults = defaultDemoState()
  state.pairedAt = LOVE_START_DATE
  state.anniversaries = personalizeAnniversaries(state.anniversaries || defaults.anniversaries)
  state.petProfiles = personalizePetProfiles(state.petProfiles || defaults.petProfiles)
  state.petEvents = personalizePetEvents(state.petEvents || defaults.petEvents)
  return state
}

function personalizeAnniversaries(list: any[]) {
  const withoutSeeds = (list || []).filter((item: any) => item.id !== 301 && item.id !== 302)
  return [
    {
      id: 301,
      title: '在一起纪念日',
      event_date: LOVE_START_DATE,
      event_type: 'love_start',
      remind_time: null,
      card_theme: 'rose',
      created_at: new Date().toISOString()
    },
    {
      id: 302,
      title: '下一次见面',
      event_date: NEXT_MEETING_DATE,
      event_type: 'custom',
      remind_time: null,
      card_theme: 'gold',
      created_at: new Date().toISOString()
    },
    ...withoutSeeds
  ]
}

function personalizePetProfiles(list: any[]) {
  const withoutSeed = (list || []).filter((item: any) => item.id !== 401)
  return [
    {
      id: 401,
      name: PET_NAME,
      breed: '',
      birthday: PET_BIRTHDAY,
      avatar_file_id: null,
      created_by: 1,
      created_at: new Date().toISOString()
    },
    ...withoutSeed
  ]
}

function personalizePetEvents(list: any[]) {
  return (list || []).map((item: any) => item.pet_id === 401
    ? { ...item, pet_name: PET_NAME }
    : item)
}

function hasReadableList(list: any[]) {
  return Array.isArray(list) && !/[\uFFFD\u9346\u93B4\u9427\u95B0\u7EFE\u93C3\u701A\u95C5\u8A1C]/.test(JSON.stringify(list))
}

function defaultDemoState() {
  const now = new Date()
  return {
    user: null,
    paired: false,
    inviteCode: '',
    inviteExpiresAt: '',
    petEnabled: false,
    dailyTopic: {
      topic: {
        id: 1,
        question: '如果今晚能把一小时留给对方，你想怎么用？',
        topic_date: now.toISOString().slice(0, 10)
      },
      answeredByMe: false,
      unlocked: false,
      answers: []
    },
    affectionCards: [
      {
        id: 1,
        title: '给你点了一杯热奶茶',
        amount: 18,
        message: '怕你今天加班太晚，甜一点会好一点。',
        nickname: '对方',
        created_at: now.toISOString()
      }
    ],
    wishes: [
      {
        id: 1,
        title: '一起去看一场夜场电影',
        note: '散场后慢慢走回家',
        completed: false,
        nickname: '我'
      }
    ],
    futureLetters: [
      {
        id: 1,
        title: '下次见面时打开',
        content: '希望那天我们不要急着赶路，先好好抱一会儿。',
        open_at: new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString(),
        recipient_user_id: 2,
        created_at: now.toISOString(),
        sender_name: '我'
      },
      {
        id: 2,
        title: '今晚可以读的一封信',
        content: '如果今天很累，就把这句话当作我在你耳边说：辛苦了，已经很好了。',
        open_at: new Date(Date.now() - 1000 * 60).toISOString(),
        recipient_user_id: null,
        created_at: now.toISOString(),
        sender_name: '对方'
      }
    ],
    files: [
      {
        id: 101,
        object_key: '',
        original_name: 'first-memory.jpg',
        mime_type: 'image/jpeg',
        size_bytes: 0,
        created_at: now.toISOString()
      }
    ],
    diaries: [
      {
        id: 201,
        owner_user_id: 1,
        title: '今晚留给我们的一页',
        content: '今天没有特别盛大的事，只是突然觉得，能把一点点心情放在同一个地方，也很好。',
        visibility: 'shared',
        comments: [
          {
            id: 1,
            created_by: 2,
            nickname: '对方',
            content: '我也想把今天留在这里。',
            created_at: now.toISOString()
          }
        ],
        created_at: now.toISOString(),
        updated_at: now.toISOString()
      },
      {
        id: 202,
        owner_user_id: 1,
        title: '只写给自己的片刻',
        content: '有些话先放在心里，等更合适的时候再慢慢告诉你。',
        visibility: 'private',
        comments: [],
        created_at: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
        updated_at: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString()
      }
    ],
    anniversaries: [
      {
        id: 301,
        title: '在一起纪念日',
        event_date: LOVE_START_DATE,
        event_type: 'love_start',
        remind_time: null,
        card_theme: 'rose',
        created_at: now.toISOString()
      },
      {
        id: 302,
        title: '下一次见面',
        event_date: NEXT_MEETING_DATE,
        event_type: 'custom',
        remind_time: null,
        card_theme: 'gold',
        created_at: now.toISOString()
      }
    ],
    albums: [
      {
        id: 101,
        file_id: 101,
        caption: '先把这里留给你们的第一张照片。',
        scene_type: 'couple_album',
        scene_ref_id: null,
        object_key: '',
        local_url: '',
        mime_type: 'image/jpeg',
        created_at: now.toISOString(),
        creator_name: '近处'
      }
    ],
    statuses: [
      { id: 1, content: '刚刚路过一家甜品店，忽然想和你一起进去。', nickname: '我', mood_tag: '在想你', reactions: '' },
      { id: 2, content: '今天的风有点软，适合靠近一点。', nickname: '对方', mood_tag: '想被抱一下', reactions: '抱抱' }
    ],
    notifications: [
      { id: 1, type: 'status.reacted', title: 'TA 回应了你的此刻', body: '抱抱', read_at: null, created_at: now.toISOString() },
      { id: 2, type: 'affection.created', title: '收到一张心意卡片', body: '给你点了一杯热奶茶', read_at: now.toISOString(), created_at: now.toISOString() }
    ],
    petProfiles: [
      {
        id: 401,
        name: PET_NAME,
        breed: '',
        birthday: PET_BIRTHDAY,
        avatar_file_id: null,
        created_by: 1,
        created_at: now.toISOString()
      }
    ],
    petEvents: [
      {
        id: 501,
        pet_id: 401,
        pet_name: PET_NAME,
        event_type: '喂食',
        content: '今天很给面子，把罐头吃得干干净净。',
        mood: '满足',
        file_id: null,
        created_by: 1,
        created_at: now.toISOString()
      },
      {
        id: 502,
        pet_id: 401,
        pet_name: PET_NAME,
        event_type: '日常',
        content: '在窗边晒太阳，像一个小小的暖炉。',
        mood: '慵懒',
        file_id: null,
        created_by: 2,
        created_at: new Date(Date.now() - 1000 * 60 * 90).toISOString()
      }
    ]
  }
}

function saveDemoState(state: any) {
  uni.setStorageSync(DEMO_STORAGE_KEY, state)
}
