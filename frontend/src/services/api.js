const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')
const TOKEN_KEY = 'sms.access-token'

export function getAccessToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function saveSession(session) {
  sessionStorage.setItem(TOKEN_KEY, session.accessToken)
  sessionStorage.setItem('sms.expires-at', session.expiresAt)
  sessionStorage.setItem('sms.user', JSON.stringify({
    userId: session.userId,
    tenantId: session.tenantId,
    email: session.email,
    roles: session.roles,
  }))
}

export function getSession() {
  const expiresAt = sessionStorage.getItem('sms.expires-at')
  const token = getAccessToken()
  const expiryTime = Date.parse(expiresAt || '')
  if (!token || !Number.isFinite(expiryTime) || expiryTime <= Date.now()) {
    clearSession()
    return null
  }
  try {
    const user = JSON.parse(sessionStorage.getItem('sms.user') || '{}')
    if (!user.userId || !user.tenantId || !user.email || !Array.isArray(user.roles)) {
      clearSession()
      return null
    }
    return { ...user, accessToken: token, expiresAt }
  } catch {
    clearSession()
    return null
  }
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem('sms.expires-at')
  sessionStorage.removeItem('sms.user')
}

export async function apiRequest(path, { anonymous = false, ...options } = {}) {
  const token = anonymous ? null : getAccessToken()
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  if (response.status === 401 && token) {
    clearSession()
    window.dispatchEvent(new Event('sms:unauthorized'))
  }
  if (!response.ok) {
    const contentType = response.headers.get('content-type') || ''
    const payload = contentType.includes('json') ? await response.json() : await response.text()
    const message = typeof payload === 'object'
      ? payload.detail || payload.message || payload.title
      : payload
    throw new Error(message || `Request failed (HTTP ${response.status}).`)
  }
  if (response.status === 204) return null
  const body = await response.text()
  if (!body) return null
  return response.headers.get('content-type')?.includes('json') ? JSON.parse(body) : body
}

export async function login({ tenantSlug, email, password }) {
  const response = await apiRequest('/auth/login', {
    method: 'POST',
    anonymous: true,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenantSlug, email, password }),
  })
  saveSession(response)
  return getSession()
}
