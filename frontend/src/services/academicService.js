import { apiRequest } from './api.js'

function resourceApi(resource) {
  return {
    list: (signal) => apiRequest(`/${resource}`, { signal }),
    create: (value) => apiRequest(`/${resource}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(value),
    }),
    update: (id, value) => apiRequest(`/${resource}/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(value),
    }),
    remove: (id) => apiRequest(`/${resource}/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  }
}

export const periodsApi = resourceApi('academic-periods')
export const coursesApi = resourceApi('courses')
export const sectionsApi = resourceApi('class-sections')
export const campusesApi = resourceApi('settings/campuses')
