import { useCallback, useEffect, useMemo, useState } from 'react'
import { CreditCard, Pencil, Plus, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { financeApi } from '../services/financeService.js'
import { getStudents } from '../services/studentService.js'

const EMPTY_INVOICE = { studentId: '', invoiceNo: '', currency: 'USD', subtotal: '', penalty: '0', dueOn: '' }
function paymentTransitions(status) {
  if (status === 'INITIATED') return ['PENDING', 'SUCCEEDED', 'FAILED']
  if (status === 'PENDING') return ['SUCCEEDED', 'FAILED']
  if (status === 'SUCCEEDED') return ['REFUNDED']
  return []
}

export default function FinancePage() {
  const [tab, setTab] = useState('invoices')
  const [invoices, setInvoices] = useState([])
  const [payments, setPayments] = useState([])
  const [students, setStudents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modal, setModal] = useState('')
  const [editing, setEditing] = useState(null)
  const [invoiceForm, setInvoiceForm] = useState(EMPTY_INVOICE)
  const [paymentForm, setPaymentForm] = useState({ invoiceId: '', amount: '', currency: 'USD', status: 'PENDING', providerReference: '' })
  const [saving, setSaving] = useState(false)

  const load = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const [invoiceRows, paymentRows, studentPage] = await Promise.all([
        financeApi.invoices(signal),
        financeApi.payments(signal),
        getStudents({ page: 0, size: 100, sortBy: 'lastName', direction: 'ASC', signal }),
      ])
      setInvoices(invoiceRows)
      setPayments(paymentRows)
      setStudents(studentPage.content)
    } catch (loadError) {
      if (loadError.name !== 'AbortError') setError(loadError.message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const studentName = (id) => {
    const row = students.find((student) => student.id === id)
    return row ? `${row.firstName} ${row.lastName}` : id
  }
  const invoiceById = useMemo(() => new Map(invoices.map((row) => [row.id, row])), [invoices])

  const openInvoice = (row = null) => {
    setEditing(row)
    setInvoiceForm(row ? {
      studentId: row.studentId,
      invoiceNo: row.invoiceNo,
      currency: row.currency,
      subtotal: String(row.subtotal),
      penalty: String(row.penalty),
      dueOn: row.dueOn,
    } : { ...EMPTY_INVOICE })
    setModal('invoice')
  }

  const openPayment = (invoice = null) => {
    setEditing(null)
    setPaymentForm({
      invoiceId: invoice?.id ?? '',
      amount: '',
      currency: invoice?.currency ?? 'USD',
      status: 'PENDING',
      providerReference: '',
    })
    setModal('payment')
  }

  const openPaymentEdit = (payment) => {
    setEditing(payment)
    setPaymentForm({
      invoiceId: payment.invoiceId,
      amount: String(payment.amount),
      currency: payment.currency,
      status: paymentTransitions(payment.status)[0] ?? payment.status,
      providerReference: payment.providerReference ?? '',
    })
    setModal('payment-edit')
  }

  const submitInvoice = async (event) => {
    event.preventDefault()
    setSaving(true)
    const payload = {
      ...invoiceForm,
      subtotal: Number(invoiceForm.subtotal),
      penalty: Number(invoiceForm.penalty),
    }
    try {
      if (editing) await financeApi.updateInvoice(editing.id, payload)
      else await financeApi.createInvoice(payload)
      toast.success(`Invoice ${editing ? 'updated' : 'created'}.`)
      setModal('')
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const submitPayment = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      if (modal === 'payment-edit') {
        await financeApi.updatePayment(editing.id, {
          status: paymentForm.status,
          providerReference: paymentForm.providerReference || null,
        })
        toast.success('Payment status updated.')
      } else {
        await financeApi.createPayment(paymentForm.invoiceId, {
          amount: Number(paymentForm.amount),
          currency: paymentForm.currency,
          status: paymentForm.status,
          providerReference: paymentForm.providerReference || null,
          idempotencyKey: crypto.randomUUID(),
        })
        toast.success('Payment recorded.')
      }
      setModal('')
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (kind, row) => {
    const label = kind === 'invoice' ? 'invoice' : 'payment'
    if (!window.confirm(`Delete this ${label}?`)) return
    try {
      if (kind === 'invoice') await financeApi.deleteInvoice(row.id)
      else await financeApi.deletePayment(row.id)
      toast.success(`${label[0].toUpperCase()}${label.slice(1)} deleted.`)
      await load()
    } catch (deleteError) {
      toast.error(deleteError.message)
    }
  }

  const voidInvoice = async (row) => {
    if (!window.confirm(`Void invoice ${row.invoiceNo}? This action cannot be undone.`)) return
    try {
      await financeApi.voidInvoice(row.id)
      toast.success('Invoice voided.')
      await load()
    } catch (voidError) {
      toast.error(voidError.message)
    }
  }

  const chooseInvoice = (invoiceId) => {
    const invoice = invoiceById.get(invoiceId)
    setPaymentForm({ ...paymentForm, invoiceId, currency: invoice?.currency ?? 'USD' })
  }
  const formatMoney = (amount, currency) => new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(Number(amount))

  return (
    <div className="page-content">
      <div className="welcome-row"><div><div className="eyebrow">SCHOOL MANAGEMENT</div><h1>Fees & billing</h1><p>Manage invoices, payment records, and balances.</p></div>
        <button className="button-primary" onClick={() => tab === 'invoices' ? openInvoice() : openPayment()}><Plus size={16} /> Add {tab === 'invoices' ? 'invoice' : 'payment'}</button>
      </div>
      <div className="module-tabs" role="tablist" aria-label="Finance sections">
        <button role="tab" aria-selected={tab === 'invoices'} className={`module-tab ${tab === 'invoices' ? 'active' : ''}`} onClick={() => setTab('invoices')}>Invoices</button>
        <button role="tab" aria-selected={tab === 'payments'} className={`module-tab ${tab === 'payments' ? 'active' : ''}`} onClick={() => setTab('payments')}>Payments</button>
      </div>
      {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => load()}>Retry</button></div>}
      {tab === 'invoices' ? <section className="panel records-panel"><div className="table-scroll records-table-scroll"><table className="student-table">
        <thead><tr><th>INVOICE</th><th>STUDENT</th><th>TOTAL</th><th>DUE DATE</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody>
          {loading && <tr><td colSpan="6" className="empty-state">Loading invoices…</td></tr>}
          {!loading && !error && invoices.map((row) => <tr key={row.id}><td>{row.invoiceNo}</td><td>{studentName(row.studentId)}</td><td>{formatMoney(row.total, row.currency)}</td><td>{row.dueOn}</td><td>{row.status}</td><td className="record-actions">
            {row.status !== 'VOID' && !payments.some((payment) => payment.invoiceId === row.id) && <button className="icon-button" onClick={() => openInvoice(row)} aria-label={`Edit invoice ${row.invoiceNo}`}><Pencil size={16} /></button>}
            {row.status !== 'VOID' && <button className="text-action" onClick={() => openPayment(row)}>Record payment</button>}
            {row.status !== 'VOID' && <button className="text-action" onClick={() => voidInvoice(row)}>Void</button>}
            {!payments.some((payment) => payment.invoiceId === row.id) && <button className="icon-button" onClick={() => remove('invoice', row)} aria-label={`Delete invoice ${row.invoiceNo}`}><Trash2 size={16} /></button>}
          </td></tr>)}
          {!loading && !error && invoices.length === 0 && <tr><td colSpan="6" className="empty-state">No invoices found.</td></tr>}
        </tbody></table></div></section> : <section className="panel records-panel"><div className="table-scroll records-table-scroll"><table className="student-table">
          <thead><tr><th>INVOICE</th><th>AMOUNT</th><th>PROVIDER</th><th>REFERENCE</th><th>STATUS</th><th>PAID AT</th><th>ACTIONS</th></tr></thead><tbody>
            {loading && <tr><td colSpan="7" className="empty-state">Loading payments…</td></tr>}
            {!loading && !error && payments.map((row) => <tr key={row.id}><td>{invoiceById.get(row.invoiceId)?.invoiceNo ?? row.invoiceId}</td><td>{formatMoney(row.amount, row.currency)}</td><td>{row.provider}</td><td>{row.providerReference || '—'}</td><td>{row.status}</td><td>{row.paidAt ? new Date(row.paidAt).toLocaleString() : '—'}</td><td className="record-actions">
              {paymentTransitions(row.status).length > 0 && <button className="icon-button" onClick={() => openPaymentEdit(row)} aria-label="Update payment status"><Pencil size={16} /></button>}
              {['INITIATED', 'PENDING', 'FAILED'].includes(row.status) && row.provider === 'MANUAL' && <button className="icon-button" onClick={() => remove('payment', row)} aria-label="Delete payment"><Trash2 size={16} /></button>}
            </td></tr>)}
            {!loading && !error && payments.length === 0 && <tr><td colSpan="7" className="empty-state">No payments found.</td></tr>}
          </tbody></table></div></section>}

      {modal && <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setModal('') }}>
        <section className="student-modal" role="dialog" aria-modal="true" aria-labelledby="finance-form-title">
          <div className="modal-heading"><div><h2 id="finance-form-title">{modal === 'invoice' ? `${editing ? 'Edit' : 'Create'} invoice` : modal === 'payment-edit' ? 'Update payment status' : 'Record payment'}</h2><p>Transactions are recorded manually; no payment provider is connected.</p></div><button className="icon-button" onClick={() => !saving && setModal('')} disabled={saving} aria-label="Close"><X size={18} /></button></div>
          {modal === 'invoice' ? <form onSubmit={submitInvoice}><div className="student-form-grid">
            <label>Student *<select required value={invoiceForm.studentId} onChange={(event) => setInvoiceForm({ ...invoiceForm, studentId: event.target.value })}><option value="">Select student</option>{students.map((row) => <option key={row.id} value={row.id}>{row.firstName} {row.lastName} · {row.enrollmentNo}</option>)}</select></label>
            <label>Invoice number *<input required maxLength="60" value={invoiceForm.invoiceNo} onChange={(event) => setInvoiceForm({ ...invoiceForm, invoiceNo: event.target.value })} /></label>
            <label>Currency *<input required pattern="[A-Z]{3}" maxLength="3" value={invoiceForm.currency} onChange={(event) => setInvoiceForm({ ...invoiceForm, currency: event.target.value.toUpperCase() })} /></label>
            <label>Subtotal *<input required type="number" min="0" step="0.01" value={invoiceForm.subtotal} onChange={(event) => setInvoiceForm({ ...invoiceForm, subtotal: event.target.value })} /></label>
            <label>Penalty *<input required type="number" min="0" step="0.01" value={invoiceForm.penalty} onChange={(event) => setInvoiceForm({ ...invoiceForm, penalty: event.target.value })} /></label>
            <label>Due on *<input required type="date" value={invoiceForm.dueOn} onChange={(event) => setInvoiceForm({ ...invoiceForm, dueOn: event.target.value })} /></label>
          </div><div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setModal('')} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving}>{saving ? 'Saving…' : 'Save invoice'}</button></div></form> : <form onSubmit={submitPayment}><div className="student-form-grid">
            {modal !== 'payment-edit' && <label>Invoice *<select required value={paymentForm.invoiceId} onChange={(event) => chooseInvoice(event.target.value)}><option value="">Select invoice</option>{invoices.filter((row) => row.status !== 'VOID').map((row) => <option key={row.id} value={row.id}>{row.invoiceNo} · {studentName(row.studentId)} · {formatMoney(row.total, row.currency)}</option>)}</select></label>}
            {modal !== 'payment-edit' && <label>Amount *<input required type="number" min="0.01" step="0.01" value={paymentForm.amount} onChange={(event) => setPaymentForm({ ...paymentForm, amount: event.target.value })} /></label>}
            {modal !== 'payment-edit' && <label>Status<select value={paymentForm.status} onChange={(event) => setPaymentForm({ ...paymentForm, status: event.target.value })}>{['INITIATED', 'PENDING', 'SUCCEEDED', 'FAILED'].map((status) => <option key={status}>{status}</option>)}</select></label>}
            {modal === 'payment-edit' && <label>Status<select value={paymentForm.status} onChange={(event) => setPaymentForm({ ...paymentForm, status: event.target.value })}>{paymentTransitions(editing.status).map((status) => <option key={status}>{status}</option>)}</select></label>}
            <label>Provider reference<input maxLength="180" value={paymentForm.providerReference} onChange={(event) => setPaymentForm({ ...paymentForm, providerReference: event.target.value })} /></label>
            {modal !== 'payment-edit' && <p className="form-hint">A unique idempotency key is generated when this payment is saved. Currency follows the selected invoice ({paymentForm.currency}).</p>}
          </div><div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setModal('')} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving || (modal === 'payment-edit' && paymentTransitions(editing.status).length === 0)}><CreditCard size={15} /> {saving ? 'Saving…' : modal === 'payment-edit' ? 'Update payment' : 'Save payment'}</button></div></form>}
        </section>
      </div>}
    </div>
  )
}
