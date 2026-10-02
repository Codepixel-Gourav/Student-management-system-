import { apiRequest } from './api.js'

export async function getStudents({
  page = 0,
  size = 20,
  sortBy = 'createdAt',
  direction = 'DESC',
  search = '',
  signal,
}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sortBy,
    direction,
    search,
  })
  const result = await apiRequest(`/students?${params}`, { signal })
  if (!Array.isArray(result.content)) throw new Error('The students API returned an unexpected response.')
  return result
}

export async function createStudent(student) {
  return apiRequest('/students', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(student),
  })
}

export async function updateStudent(id, student) {
  return apiRequest(`/students/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(student),
  })
}

export async function deleteStudent(id) {
  return apiRequest(`/students/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function getDashboardSummary(signal) {
  return apiRequest('/dashboard/summary', { signal })
}
