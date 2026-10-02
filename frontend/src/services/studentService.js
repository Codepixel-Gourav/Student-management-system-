const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
).replace(/\/+$/, '')

export async function getStudents({ tenantId, page = 0, size = 20, sortBy = 'lastName', signal }) {
  if (!tenantId) {
    throw new Error('Set VITE_TENANT_ID to load students for your tenant.')
  }

  const params = new URLSearchParams({
    tenantId,
    page: String(page),
    size: String(size),
    sortBy,
  })
  const response = await fetch(`${API_BASE_URL}/students?${params}`, { signal })

  if (!response.ok) {
    const detail = await response.text()
    throw new Error(detail || `Unable to load students (HTTP ${response.status}).`)
  }

  const result = await response.json()
  if (!Array.isArray(result.content)) {
    throw new Error('The students API returned an unexpected response.')
  }

  return result.content
}
