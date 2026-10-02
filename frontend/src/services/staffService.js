import { apiRequest } from './api.js'

export const listStaff = (signal) => apiRequest('/staff', { signal })

export const createStaff = (staff) => apiRequest('/staff', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(staff),
})

export const updateStaff = (id, staff) => apiRequest(`/staff/${encodeURIComponent(id)}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(staff),
})

export const setStaffPassword = (id, password) => apiRequest(`/staff/${encodeURIComponent(id)}/password`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ password }),
})

export const deleteStaff = (id) => apiRequest(`/staff/${encodeURIComponent(id)}`, { method: 'DELETE' })
