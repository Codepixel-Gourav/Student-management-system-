const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || '/api'
).replace(/\/+$/, '')

function requireTenantId(tenantId) {
  if (!tenantId) throw new Error('Set VITE_TENANT_ID to use student management.')
  return tenantId
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  })

  if (!response.ok) {
    const contentType = response.headers.get('content-type') || ''
    const payload = contentType.includes('json')
      ? await response.json()
      : await response.text()
    const message = typeof payload === 'object'
      ? payload.detail || payload.message || payload.title
      : payload
    throw new Error(message || `Request failed (HTTP ${response.status}).`)
  }

  if (response.status === 204) return null
  return response.json()
}

export async function getStudents({
  tenantId,
  page = 0,
  size = 20,
  sortBy = 'createdAt',
  direction = 'DESC',
  search = '',
  signal,
}) {
  requireTenantId(tenantId)
  const params = new URLSearchParams({
    tenantId,
    page: String(page),
    size: String(size),
    sortBy,
    direction,
    search,
  })
  const result = await request(`/students?${params}`, { signal })
  if (!Array.isArray(result.content)) throw new Error('The students API returned an unexpected response.')
  return result
}

export async function createStudent(student) {
  requireTenantId(student.tenantId)
  return request('/students', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(student),
  })
}

export async function updateStudent(tenantId, id, student) {
  const params = new URLSearchParams({ tenantId: requireTenantId(tenantId) })
  return request(`/students/${encodeURIComponent(id)}?${params}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(student),
  })
}

export async function deleteStudent(tenantId, id) {
  const params = new URLSearchParams({ tenantId: requireTenantId(tenantId) })
  return request(`/students/${encodeURIComponent(id)}?${params}`, { method: 'DELETE' })
}

export async function getDashboardSummary(tenantId, signal) {
  const params = new URLSearchParams({ tenantId: requireTenantId(tenantId) })
  return request(`/dashboard/summary?${params}`, { signal })
}
