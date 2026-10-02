import { apiRequest } from './api.js'

const json = (method, value) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(value),
})

export const attendanceApi = {
  sessions: (signal) => apiRequest('/attendance/sessions', { signal }),
  createSession: (value) => apiRequest('/attendance/sessions', json('POST', value)),
  updateSession: (id, value) => apiRequest(`/attendance/sessions/${encodeURIComponent(id)}`, json('PUT', value)),
  deleteSession: (id) => apiRequest(`/attendance/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  records: (id, signal) => apiRequest(`/attendance/sessions/${encodeURIComponent(id)}/records`, { signal }),
  eligibleStudents: (id, signal) => apiRequest(`/attendance/sessions/${encodeURIComponent(id)}/students`, { signal }),
  createRecord: (sessionId, value) => apiRequest(`/attendance/sessions/${encodeURIComponent(sessionId)}/records`, json('POST', value)),
  updateRecord: (id, status) => apiRequest(`/attendance/records/${encodeURIComponent(id)}`, json('PUT', { status })),
  deleteRecord: (id) => apiRequest(`/attendance/records/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  leaveRequests: (signal) => apiRequest('/attendance/leave-requests', { signal }),
  createLeaveRequest: (value) => apiRequest('/attendance/leave-requests', json('POST', value)),
  updateLeaveRequest: (id, value) => apiRequest(`/attendance/leave-requests/${encodeURIComponent(id)}`, json('PUT', value)),
  deleteLeaveRequest: (id) => apiRequest(`/attendance/leave-requests/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
