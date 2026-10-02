import { apiRequest } from './api.js'

const json = (method, value) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(value),
})

export const financeApi = {
  invoices: (signal) => apiRequest('/invoices', { signal }),
  createInvoice: (value) => apiRequest('/invoices', json('POST', value)),
  updateInvoice: (id, value) => apiRequest(`/invoices/${encodeURIComponent(id)}`, json('PUT', value)),
  voidInvoice: (id) => apiRequest(`/invoices/${encodeURIComponent(id)}/void`, { method: 'POST' }),
  deleteInvoice: (id) => apiRequest(`/invoices/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  payments: (signal) => apiRequest('/payments', { signal }),
  createPayment: (invoiceId, value) => apiRequest(`/invoices/${encodeURIComponent(invoiceId)}/payments`, json('POST', value)),
  updatePayment: (id, value) => apiRequest(`/payments/${encodeURIComponent(id)}`, json('PUT', value)),
  deletePayment: (id) => apiRequest(`/payments/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
