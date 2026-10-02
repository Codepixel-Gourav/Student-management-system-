import { useCallback, useEffect, useState } from 'react'
import { KeyRound, Pencil, Plus, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { createStaff, deleteStaff, listStaff, setStaffPassword, updateStaff } from '../services/staffService.js'

const EMPTY = { email: '', displayName: '', password: '', status: 'ACTIVE' }
const STATUSES = ['ACTIVE', 'INVITED', 'LOCKED', 'DISABLED']

export default function StaffPage({ session }) {
  const [staff, setStaff] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [mode, setMode] = useState('create')
  const [saving, setSaving] = useState(false)
  const isAdmin = session.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))

  const load = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      setStaff(await listStaff(signal))
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

  const openCreate = () => {
    setMode('create')
    setSelected(null)
    setForm({ ...EMPTY })
    setModalOpen(true)
  }

  const openEdit = (person) => {
    setMode('edit')
    setSelected(person)
    setForm({ email: person.email, displayName: person.displayName, password: '', status: person.status })
    setModalOpen(true)
  }

  const openPassword = (person) => {
    setMode('password')
    setSelected(person)
    setForm({ ...EMPTY })
    setModalOpen(true)
  }

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      if (mode === 'create') {
        await createStaff({ email: form.email, displayName: form.displayName, password: form.password })
        toast.success('Teacher account created.')
      } else if (mode === 'edit') {
        await updateStaff(selected.id, { email: form.email, displayName: form.displayName, status: form.status })
        toast.success('Teacher account updated.')
      } else {
        await setStaffPassword(selected.id, form.password)
        toast.success('Teacher password changed.')
      }
      setSelected(null)
      setModalOpen(false)
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (person) => {
    if (!window.confirm(`Delete teacher account for ${person.displayName}?`)) return
    try {
      await deleteStaff(person.id)
      toast.success('Teacher account deleted.')
      await load()
    } catch (deleteError) {
      toast.error(deleteError.message)
    }
  }

  return (
    <div className="page-content">
      <div className="welcome-row"><div><div className="eyebrow">PEOPLE</div><h1>Teachers</h1><p>Manage teacher accounts and access.</p></div>{isAdmin && <button className="button-primary" onClick={openCreate}><Plus size={16} /> Add teacher</button>}</div>
      <section className="panel records-panel">
        {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => load()}>Retry</button></div>}
        <div className="table-scroll records-table-scroll"><table className="student-table">
          <thead><tr><th>NAME</th><th>EMAIL</th><th>STATUS</th><th>CREATED</th>{isAdmin && <th>ACTIONS</th>}</tr></thead>
          <tbody>
            {loading && <tr><td colSpan={isAdmin ? 5 : 4} className="empty-state">Loading teachers…</td></tr>}
            {!loading && !error && staff.map((person) => <tr key={person.id}>
              <td>{person.displayName}</td><td>{person.email}</td><td>{person.status}</td>
              <td>{person.createdAt ? new Date(person.createdAt).toLocaleDateString() : '—'}</td>
              {isAdmin && <td className="record-actions">
                <button className="icon-button" onClick={() => openEdit(person)} aria-label={`Edit ${person.displayName}`}><Pencil size={16} /></button>
                <button className="icon-button" onClick={() => openPassword(person)} aria-label={`Change password for ${person.displayName}`}><KeyRound size={16} /></button>
                {person.id !== session.userId && <button className="icon-button" onClick={() => remove(person)} aria-label={`Delete ${person.displayName}`}><Trash2 size={16} /></button>}
              </td>}
            </tr>)}
            {!loading && !error && staff.length === 0 && <tr><td colSpan={isAdmin ? 5 : 4} className="empty-state">No teacher accounts found.</td></tr>}
          </tbody>
        </table></div>
      </section>

      {modalOpen && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setModalOpen(false) }}>
          <section className="student-modal" role="dialog" aria-modal="true" aria-labelledby="staff-title">
            <div className="modal-heading"><div><h2 id="staff-title">{mode === 'create' ? 'Add teacher' : mode === 'edit' ? 'Edit teacher' : 'Change password'}</h2><p>{mode === 'password' ? 'Use at least 12 characters.' : 'Teacher account details.'}</p></div><button className="icon-button" onClick={() => !saving && setModalOpen(false)} disabled={saving} aria-label="Close"><X size={18} /></button></div>
            <form onSubmit={submit}><div className="student-form-grid">
              {mode !== 'password' && <>
                <label>Name *<input required maxLength="180" value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} /></label>
                <label>Email *<input required type="email" maxLength="254" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
              </>}
              {(mode === 'create' || mode === 'password') && <label>Password *<input required type="password" minLength="12" maxLength="200" autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>}
              {mode === 'edit' && <label>Status<select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>{STATUSES.map((status) => <option key={status}>{status}</option>)}</select></label>}
            </div><div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setModalOpen(false)} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button></div></form>
          </section>
        </div>
      )}
    </div>
  )
}
