import { useCallback, useEffect, useState } from 'react'
import { Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { createStudent, deleteStudent, getStudents, updateStudent } from '../services/studentService.js'
import { campusesApi } from '../services/academicService.js'

const PAGE_SIZE = 10
const EMPTY_FORM = {
  enrollmentNo: '',
  firstName: '',
  lastName: '',
  email: '',
  department: '',
  enrollmentYear: new Date().getFullYear(),
  dateOfBirth: '',
}
const STATUSES = ['APPLIED', 'UNDER_REVIEW', 'ADMITTED', 'ENROLLED', 'REJECTED', 'WITHDRAWN']

function toForm(student) {
  return {
    enrollmentNo: student.enrollmentNo ?? '',
    firstName: student.firstName ?? '',
    lastName: student.lastName ?? '',
    email: student.email ?? '',
    department: student.department ?? '',
    enrollmentYear: student.enrollmentYear ?? '',
    dateOfBirth: student.dateOfBirth ?? '',
    admissionStatus: student.admissionStatus ?? 'APPLIED',
  }
}

export default function StudentsPage({ query, setQuery, session }) {
  const [campuses, setCampuses] = useState([])
  const [students, setStudents] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const canManage = session.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))

  const loadStudents = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const result = await getStudents({ page, size: PAGE_SIZE, search: query, signal })
      setStudents(result.content)
      setTotal(result.totalElements)
    } catch (loadError) {
      if (loadError.name !== 'AbortError') setError(loadError.message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [page, query])

  useEffect(() => {
    const controller = new AbortController()
    loadStudents(controller.signal)
    return () => controller.abort()
  }, [loadStudents])

  useEffect(() => {
    const controller = new AbortController()
    campusesApi.list(controller.signal).then(setCampuses).catch((loadError) => {
      if (loadError.name !== 'AbortError') toast.error(loadError.message)
    })
    return () => controller.abort()
  }, [])

  const visibleStudents = students

  const openCreate = () => {
    setEditing(false)
    setForm({ ...EMPTY_FORM, enrollmentYear: new Date().getFullYear(), admissionStatus: 'APPLIED' })
  }

  const openEdit = (student) => {
    setEditing(student)
    setForm(toForm(student))
  }

  const closeForm = () => {
    if (!saving) setEditing(null)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setSaving(true)
    const { campusId, ...studentFields } = form
    const payload = {
      ...studentFields,
      enrollmentYear: studentFields.enrollmentYear ? Number(studentFields.enrollmentYear) : null,
      dateOfBirth: studentFields.dateOfBirth || null,
      email: studentFields.email.trim() || null,
      department: studentFields.department.trim() || null,
    }

    try {
      if (editing && editing !== false) {
        await updateStudent(editing.id, { ...payload, admissionStatus: form.admissionStatus })
        toast.success('Student updated.')
      } else {
        if (!campusId) throw new Error('Select a campus before adding students.')
        await createStudent({ ...payload, campusId })
        toast.success('Student added.')
      }
      setEditing(null)
      await loadStudents()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (student) => {
    if (!window.confirm(`Delete ${student.firstName} ${student.lastName}? This cannot be undone.`)) return
    try {
      await deleteStudent(student.id)
      toast.success('Student deleted.')
      if (students.length === 1 && page > 0) setPage((current) => current - 1)
      else await loadStudents()
    } catch (deleteError) {
      toast.error(deleteError.message)
    }
  }

  return (
    <div className="page-content">
      <div className="welcome-row">
        <div>
          <div className="eyebrow">STUDENT RECORDS</div>
          <h1>Students</h1>
          <p>Manage admissions and student records.</p>
        </div>
        {canManage && <button className="button-primary" onClick={openCreate}><Plus size={16} /> Add student</button>}
      </div>

      <section className="panel records-panel">
        <div className="records-toolbar">
          <strong>{total} students</strong>
          <label className="records-search"><Search size={16} /><input maxLength="100" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0) }} placeholder="Search all students..." /></label>
        </div>
        {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => loadStudents()}>Retry</button></div>}
        <div className="table-scroll records-table-scroll">
          <table className="student-table">
            <thead><tr><th>STUDENT</th><th>ENROLLMENT NO.</th><th>PROGRAM</th><th>STATUS</th><th>ACTIONS</th></tr></thead>
            <tbody>
              {loading && <tr><td colSpan="5" className="empty-state">Loading students…</td></tr>}
              {!loading && !error && visibleStudents.map((student) => (
                <tr key={student.id}>
                  <td><div className="student-name"><span className="student-avatar lilac">{student.firstName?.[0]}{student.lastName?.[0]}</span><span><strong>{student.firstName} {student.lastName}</strong><small>{student.email || 'No email'}</small></span></div></td>
                  <td className="id-cell">{student.enrollmentNo}</td>
                  <td>{student.department || '—'}</td>
                  <td><span className={`status-pill ${student.admissionStatus?.toLowerCase()}`}><i />{student.admissionStatus?.replaceAll('_', ' ')}</span></td>
                  <td className="record-actions">
                    {canManage && <button className="icon-button" onClick={() => openEdit(student)} aria-label={`Edit ${student.firstName} ${student.lastName}`}><Pencil size={16} /></button>}
                    {canManage && <button className="icon-button" onClick={() => handleDelete(student)} aria-label={`Delete ${student.firstName} ${student.lastName}`}><Trash2 size={16} /></button>}
                  </td>
                </tr>
              ))}
              {!loading && !error && visibleStudents.length === 0 && <tr><td colSpan="5" className="empty-state">{query ? 'No matching students found.' : 'No students found.'}</td></tr>}
            </tbody>
          </table>
        </div>
        <div className="records-pagination">
          <span>Page {page + 1} of {Math.max(1, Math.ceil(total / PAGE_SIZE))}</span>
          <div>
            <button className="button-secondary" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)}>Previous</button>
            <button className="button-secondary" disabled={(page + 1) * PAGE_SIZE >= total || loading} onClick={() => setPage((current) => current + 1)}>Next</button>
          </div>
        </div>
      </section>

      {editing !== null && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) closeForm() }}>
          <section className="student-modal" role="dialog" aria-modal="true" aria-labelledby="student-form-title">
            <div className="modal-heading">
              <div><h2 id="student-form-title">{editing ? 'Edit student' : 'Add student'}</h2><p>Fields marked * are required.</p></div>
              <button className="icon-button" onClick={closeForm} aria-label="Close dialog"><X size={18} /></button>
            </div>
            <form onSubmit={handleSubmit}>
              <div className="student-form-grid">
                <label>Enrollment number *<input required maxLength="60" value={form.enrollmentNo} onChange={(event) => setForm({ ...form, enrollmentNo: event.target.value })} /></label>
                <label>First name *<input required maxLength="100" value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} /></label>
                <label>Last name *<input required maxLength="100" value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} /></label>
                <label>Email<input type="email" maxLength="254" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
                <label>Department<input maxLength="120" value={form.department} onChange={(event) => setForm({ ...form, department: event.target.value })} /></label>
                <label>Enrollment year<input type="number" min="1900" max="2200" value={form.enrollmentYear} onChange={(event) => setForm({ ...form, enrollmentYear: event.target.value })} /></label>
                <label>Date of birth<input type="date" value={form.dateOfBirth} onChange={(event) => setForm({ ...form, dateOfBirth: event.target.value })} /></label>
                {!editing && <label>Campus *<select required value={form.campusId ?? ''} onChange={(event) => setForm({ ...form, campusId: event.target.value })}><option value="">Select campus</option>{campuses.map((campus) => <option key={campus.id} value={campus.id}>{campus.name}</option>)}</select></label>}
                {editing && <label>Admission status<select value={form.admissionStatus} onChange={(event) => setForm({ ...form, admissionStatus: event.target.value })}>{STATUSES.map((status) => <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>)}</select></label>}
              </div>
              <div className="modal-actions">
                <button type="button" className="button-secondary" onClick={closeForm} disabled={saving}>Cancel</button>
                <button type="submit" className="button-primary" disabled={saving || !canManage || (!editing && campuses.length === 0)}>{saving ? 'Saving…' : editing ? 'Save changes' : 'Create student'}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}
