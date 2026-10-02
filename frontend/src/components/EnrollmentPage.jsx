import { useCallback, useEffect, useState } from 'react'
import { Pencil, Plus, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { enrollmentsApi, sectionsApi } from '../services/academicService.js'
import { getStudents } from '../services/studentService.js'

const STATUSES = ['ACTIVE', 'COMPLETED', 'WITHDRAWN']
const EMPTY = { studentId: '', classSectionId: '', enrolledOn: '', status: 'ACTIVE' }

export default function EnrollmentPage({ session, onNavigate }) {
  const [enrollments, setEnrollments] = useState([])
  const [students, setStudents] = useState([])
  const [sections, setSections] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [saving, setSaving] = useState(false)
  const canManage = session.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))

  const load = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const [rows, sectionRows, studentPage] = await Promise.all([
        enrollmentsApi.list(signal),
        sectionsApi.list(signal),
        getStudents({ page: 0, size: 100, sortBy: 'lastName', direction: 'ASC', signal }),
      ])
      setEnrollments(rows)
      setSections(sectionRows)
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

  const openNew = () => {
    setEditing(null)
    setForm({ ...EMPTY })
    setModalOpen(true)
  }
  const openEdit = (row) => {
    setEditing(row)
    setForm({ studentId: row.studentId, classSectionId: row.classSectionId, enrolledOn: row.enrolledOn, status: row.status })
    setModalOpen(true)
  }
  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      const payload = { ...form, enrolledOn: form.enrolledOn || null }
      if (editing) await enrollmentsApi.update(editing.id, payload)
      else await enrollmentsApi.create(payload)
      toast.success(`Enrollment ${editing ? 'updated' : 'created'}.`)
      setModalOpen(false)
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }
  const remove = async (row) => {
    if (!window.confirm(`Remove ${row.firstName} ${row.lastName} from ${row.sectionName}?`)) return
    try {
      await enrollmentsApi.remove(row.id)
      toast.success('Enrollment removed.')
      await load()
    } catch (deleteError) {
      toast.error(deleteError.message)
    }
  }

  return (
    <div className="page-content">
      <div className="welcome-row"><div><div className="eyebrow">ACADEMIC SETUP</div><h1>Enrollments</h1><p>Assign students to campus class sections for the academic period.</p></div>
        {canManage && <button className="button-primary" onClick={openNew}><Plus size={16} /> Add enrollment</button>}
      </div>
      <div className="module-tabs" role="tablist" aria-label="Academic sections">
        {[['periods', 'Academic periods'], ['courses', 'Courses'], ['sections', 'Class sections'], ['enrollments', 'Enrollments']].map(([path, label]) => (
          <button key={path} role="tab" aria-selected={path === 'enrollments'} className={`module-tab ${path === 'enrollments' ? 'active' : ''}`} onClick={() => onNavigate(path === 'enrollments' ? 'Enrollments' : `Academic:${path}`)}>{label}</button>
        ))}
      </div>
      <section className="panel records-panel">
        {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => load()}>Retry</button></div>}
        <div className="table-scroll records-table-scroll"><table className="student-table">
          <thead><tr><th>STUDENT</th><th>ENROLLMENT NO.</th><th>SECTION</th><th>ENROLLED ON</th><th>STATUS</th>{canManage && <th>ACTIONS</th>}</tr></thead><tbody>
            {loading && <tr><td colSpan={canManage ? 6 : 5} className="empty-state">Loading enrollments…</td></tr>}
            {!loading && !error && enrollments.map((row) => <tr key={row.id}>
              <td>{row.firstName} {row.lastName}</td><td>{row.enrollmentNo}</td><td>{row.sectionName}</td><td>{row.enrolledOn}</td><td>{row.status}</td>
              {canManage && <td className="record-actions"><button className="icon-button" onClick={() => openEdit(row)} aria-label={`Edit enrollment for ${row.firstName} ${row.lastName}`}><Pencil size={16} /></button><button className="icon-button" onClick={() => remove(row)} aria-label={`Remove enrollment for ${row.firstName} ${row.lastName}`}><Trash2 size={16} /></button></td>}
            </tr>)}
            {!loading && !error && enrollments.length === 0 && <tr><td colSpan={canManage ? 6 : 5} className="empty-state">No enrollments found. Add an enrollment before marking attendance.</td></tr>}
          </tbody></table></div>
      </section>
      {modalOpen && <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setModalOpen(false) }}>
        <section className="student-modal" role="dialog" aria-modal="true" aria-labelledby="enrollment-title">
          <div className="modal-heading"><div><h2 id="enrollment-title">{editing ? 'Edit enrollment' : 'Add enrollment'}</h2><p>Student and section must belong to the same campus.</p></div><button className="icon-button" onClick={() => !saving && setModalOpen(false)} disabled={saving} aria-label="Close"><X size={18} /></button></div>
          <form onSubmit={submit}><div className="student-form-grid">
            <label>Student *<select required value={form.studentId} onChange={(event) => setForm({ ...form, studentId: event.target.value })}><option value="">Select student</option>{students.map((row) => <option key={row.id} value={row.id}>{row.firstName} {row.lastName} · {row.enrollmentNo}</option>)}</select></label>
            <label>Class section *<select required value={form.classSectionId} onChange={(event) => setForm({ ...form, classSectionId: event.target.value })}><option value="">Select section</option>{sections.map((row) => <option key={row.id} value={row.id}>{row.name} · {row.gradeLevel}</option>)}</select></label>
            <label>Enrolled on<input type="date" value={form.enrolledOn} onChange={(event) => setForm({ ...form, enrolledOn: event.target.value })} /></label>
            <label>Status<select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>{STATUSES.map((status) => <option key={status}>{status}</option>)}</select></label>
          </div><div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setModalOpen(false)} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving || students.length === 0 || sections.length === 0}>{saving ? 'Saving…' : 'Save enrollment'}</button></div></form>
        </section>
      </div>}
    </div>
  )
}
