import { useCallback, useEffect, useMemo, useState } from 'react'
import { CalendarPlus, Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { attendanceApi } from '../services/attendanceService.js'
import { sectionsApi, coursesApi } from '../services/academicService.js'
import { listStaff } from '../services/staffService.js'
import { getStudents } from '../services/studentService.js'

const ATTENDANCE_STATES = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED']
const LEAVE_STATES = ['PENDING', 'TEACHER_APPROVED', 'APPROVED', 'REJECTED', 'CANCELLED']
const isAdminRole = (session) => session.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))
const emptySession = () => ({ classSectionId: '', courseId: '', teacherUserId: '', startsAt: '', endsAt: '' })

export default function AttendancePage({ session }) {
  const canManage = isAdminRole(session)
  const [tab, setTab] = useState('sessions')
  const [sessions, setSessions] = useState([])
  const [records, setRecords] = useState([])
  const [eligibleStudents, setEligibleStudents] = useState([])
  const [leaveRequests, setLeaveRequests] = useState([])
  const [sections, setSections] = useState([])
  const [courses, setCourses] = useState([])
  const [teachers, setTeachers] = useState([])
  const [students, setStudents] = useState([])
  const [selectedSession, setSelectedSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modal, setModal] = useState('')
  const [editing, setEditing] = useState(null)
  const [sessionForm, setSessionForm] = useState(emptySession())
  const [leaveForm, setLeaveForm] = useState({ studentId: '', startsOn: '', endsOn: '', reason: '', status: 'PENDING' })
  const [recordForm, setRecordForm] = useState({ studentId: '', status: 'PRESENT' })
  const [saving, setSaving] = useState(false)

  const load = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const [sessionRows, leaveRows, studentPage] = await Promise.all([
        attendanceApi.sessions(signal),
        canManage ? attendanceApi.leaveRequests(signal) : Promise.resolve([]),
        getStudents({ page: 0, size: 100, sortBy: 'lastName', direction: 'ASC', signal }),
      ])
      setSessions(sessionRows)
      setLeaveRequests(leaveRows)
      setStudents(studentPage.content)
      const [sectionRows, courseRows, teacherRows] = await Promise.all([
        sectionsApi.list(signal),
        canManage ? coursesApi.list(signal) : Promise.resolve([]),
        canManage ? listStaff(signal) : Promise.resolve([]),
      ])
      setSections(sectionRows)
      setCourses(courseRows)
      setTeachers(teacherRows)
    } catch (loadError) {
      if (loadError.name !== 'AbortError') setError(loadError.message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [canManage])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const selectedSection = useMemo(() => sections.find((section) => section.id === selectedSession?.classSectionId), [sections, selectedSession])
  const nameOfStudent = (id) => {
    const student = [...eligibleStudents, ...students].find((row) => row.id === id)
    return student ? `${student.firstName} ${student.lastName}` : id
  }
  const sectionName = (id) => sections.find((section) => section.id === id)?.name ?? id

  const viewRecords = async (row) => {
    setSelectedSession(row)
    try {
      const [sessionRecords, eligible] = await Promise.all([
        attendanceApi.records(row.id),
        attendanceApi.eligibleStudents(row.id),
      ])
      setRecords(sessionRecords)
      setEligibleStudents(eligible)
      setRecordForm({ studentId: '', status: 'PRESENT' })
    } catch (loadError) {
      toast.error(loadError.message)
    }
  }

  const openSession = (row = null) => {
    setEditing(row)
    const asLocal = (value) => {
      if (!value) return ''
      const date = new Date(value)
      return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
    }
    setSessionForm(row ? {
      classSectionId: row.classSectionId,
      courseId: row.courseId ?? '',
      teacherUserId: row.teacherUserId ?? '',
      startsAt: asLocal(row.startsAt),
      endsAt: asLocal(row.endsAt),
    } : emptySession())
    setModal('session')
  }

  const openLeave = (row = null) => {
    setEditing(row)
    setLeaveForm(row ? {
      studentId: row.studentId,
      startsOn: row.startsOn,
      endsOn: row.endsOn,
      reason: row.reason,
      status: row.status,
    } : { studentId: '', startsOn: '', endsOn: '', reason: '', status: 'PENDING' })
    setModal('leave')
  }

  const submitSession = async (event) => {
    event.preventDefault()
    setSaving(true)
    const payload = {
      classSectionId: sessionForm.classSectionId,
      courseId: sessionForm.courseId || null,
      teacherUserId: sessionForm.teacherUserId || null,
      startsAt: new Date(sessionForm.startsAt).toISOString(),
      endsAt: new Date(sessionForm.endsAt).toISOString(),
    }
    try {
      if (editing) await attendanceApi.updateSession(editing.id, payload)
      else await attendanceApi.createSession(payload)
      toast.success(`Attendance session ${editing ? 'updated' : 'created'}.`)
      setModal('')
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const submitLeave = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      const payload = { ...leaveForm }
      if (editing) await attendanceApi.updateLeaveRequest(editing.id, payload)
      else await attendanceApi.createLeaveRequest(payload)
      toast.success(`Leave request ${editing ? 'updated' : 'created'}.`)
      setModal('')
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const markAttendance = async (event) => {
    event.preventDefault()
    if (!selectedSession) return
    try {
      await attendanceApi.createRecord(selectedSession.id, recordForm)
      toast.success('Attendance marked.')
      setRecords(await attendanceApi.records(selectedSession.id))
      setRecordForm({ studentId: '', status: 'PRESENT' })
    } catch (saveError) {
      toast.error(saveError.message)
    }
  }

  const changeRecordStatus = async (record, status) => {
    try {
      const updated = await attendanceApi.updateRecord(record.id, status)
      setRecords((current) => current.map((row) => row.id === updated.id ? updated : row))
    } catch (saveError) {
      toast.error(saveError.message)
    }
  }

  const remove = async (kind, row) => {
    const label = kind === 'session' ? 'attendance session' : kind === 'leave' ? 'leave request' : 'attendance record'
    if (!window.confirm(`Delete this ${label}?`)) return
    try {
      if (kind === 'session') {
        await attendanceApi.deleteSession(row.id)
        if (selectedSession?.id === row.id) { setSelectedSession(null); setRecords([]) }
      } else if (kind === 'leave') await attendanceApi.deleteLeaveRequest(row.id)
      else await attendanceApi.deleteRecord(row.id)
      toast.success(`${label[0].toUpperCase()}${label.slice(1)} deleted.`)
      if (kind === 'record' && selectedSession) setRecords(await attendanceApi.records(selectedSession.id))
      else await load()
    } catch (deleteError) {
      toast.error(deleteError.message)
    }
  }

  return (
    <div className="page-content">
      <div className="welcome-row"><div><div className="eyebrow">SCHOOL MANAGEMENT</div><h1>Attendance</h1><p>Manage class sessions, attendance records, and leave requests.</p></div>
        {canManage && <button className="button-primary" onClick={() => tab === 'sessions' ? openSession() : openLeave()}><Plus size={16} /> Add {tab === 'sessions' ? 'session' : 'leave request'}</button>}
      </div>
      <div className="module-tabs" role="tablist" aria-label="Attendance sections">
        <button role="tab" aria-selected={tab === 'sessions'} className={`module-tab ${tab === 'sessions' ? 'active' : ''}`} onClick={() => setTab('sessions')}>Sessions & records</button>
        {canManage && <button role="tab" aria-selected={tab === 'leave'} className={`module-tab ${tab === 'leave' ? 'active' : ''}`} onClick={() => setTab('leave')}>Leave requests</button>}
      </div>
      {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => load()}>Retry</button></div>}
      {tab === 'sessions' && <>
        <section className="panel records-panel">
          <div className="panel-heading"><div><h2>Class sessions</h2><p>Select a session to review or mark attendance.</p></div><CalendarPlus size={18} /></div>
          <div className="table-scroll records-table-scroll"><table className="student-table">
            <thead><tr><th>SECTION</th><th>STARTS</th><th>ENDS</th><th>TEACHER</th><th>RECORDS</th>{canManage && <th>ACTIONS</th>}</tr></thead>
            <tbody>
              {loading && <tr><td colSpan={canManage ? 6 : 5} className="empty-state">Loading sessions…</td></tr>}
              {!loading && !error && sessions.map((row) => <tr key={row.id} className={selectedSession?.id === row.id ? 'selected-record-row' : ''}>
                <td>{sectionName(row.classSectionId)}</td><td>{new Date(row.startsAt).toLocaleString()}</td><td>{new Date(row.endsAt).toLocaleString()}</td><td>{teachers.find((teacher) => teacher.id === row.teacherUserId)?.displayName ?? (row.teacherUserId === session.userId ? session.email : row.teacherUserId ? 'Assigned teacher' : '—')}</td>
                <td><button className="text-action" onClick={() => viewRecords(row)}>View / mark</button></td>
                {canManage && <td className="record-actions"><button className="icon-button" onClick={() => openSession(row)} aria-label="Edit session"><Pencil size={16} /></button><button className="icon-button" onClick={() => remove('session', row)} aria-label="Delete session"><Trash2 size={16} /></button></td>}
              </tr>)}
              {!loading && !error && sessions.length === 0 && <tr><td colSpan={canManage ? 6 : 5} className="empty-state">No attendance sessions found.</td></tr>}
            </tbody>
          </table></div>
        </section>
        {selectedSession && <section className="panel records-panel attendance-records-panel">
          <div className="panel-heading"><div><h2>Attendance records</h2><p>{sectionName(selectedSession.classSectionId)} · {new Date(selectedSession.startsAt).toLocaleString()}</p></div><button className="icon-button" onClick={() => setSelectedSession(null)} aria-label="Close attendance records"><X size={17} /></button></div>
          {(canManage || session.roles?.includes('TEACHER')) && <form className="inline-record-form" onSubmit={markAttendance}>
            <label>Student<select required value={recordForm.studentId} onChange={(event) => setRecordForm({ ...recordForm, studentId: event.target.value })}><option value="">Select student</option>{eligibleStudents.filter((student) => !records.some((record) => record.studentId === student.id)).map((student) => <option key={student.id} value={student.id}>{student.firstName} {student.lastName} · {student.enrollmentNo}</option>)}</select></label>
            <label>Status<select value={recordForm.status} onChange={(event) => setRecordForm({ ...recordForm, status: event.target.value })}>{ATTENDANCE_STATES.map((status) => <option key={status}>{status}</option>)}</select></label>
            <button className="button-primary"><Check size={15} /> Mark attendance</button>
          </form>}
          <div className="table-scroll records-table-scroll"><table className="student-table"><thead><tr><th>STUDENT</th><th>STATUS</th><th>MARKED AT</th>{canManage && <th>ACTIONS</th>}</tr></thead><tbody>
            {records.map((record) => <tr key={record.id}><td>{nameOfStudent(record.studentId)}</td><td>{canManage || session.roles?.includes('TEACHER') ? <select aria-label={`Attendance status for ${nameOfStudent(record.studentId)}`} value={record.status} onChange={(event) => changeRecordStatus(record, event.target.value)}>{ATTENDANCE_STATES.map((status) => <option key={status}>{status}</option>)}</select> : record.status}</td><td>{record.markedAt ? new Date(record.markedAt).toLocaleString() : '—'}</td>{canManage && <td><button className="icon-button" onClick={() => remove('record', record)} aria-label="Delete attendance record"><Trash2 size={16} /></button></td>}</tr>)}
            {records.length === 0 && <tr><td colSpan={canManage ? 4 : 3} className="empty-state">No records marked for this session.</td></tr>}
          </tbody></table></div>
          {selectedSection && <p className="form-hint">Only students actively enrolled in {selectedSection.name} are selectable. Edit a marked record to change its attendance status.</p>}
        </section>}
      </>}
      {tab === 'leave' && canManage && <section className="panel records-panel"><div className="table-scroll records-table-scroll"><table className="student-table">
        <thead><tr><th>STUDENT</th><th>DATES</th><th>REASON</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody>
          {leaveRequests.map((row) => <tr key={row.id}><td>{nameOfStudent(row.studentId)}</td><td>{row.startsOn} – {row.endsOn}</td><td>{row.reason}</td><td>{row.status}</td><td className="record-actions"><button className="icon-button" onClick={() => openLeave(row)} aria-label="Edit leave request"><Pencil size={16} /></button><button className="icon-button" onClick={() => remove('leave', row)} aria-label="Delete leave request"><Trash2 size={16} /></button></td></tr>)}
          {!loading && !error && leaveRequests.length === 0 && <tr><td colSpan="5" className="empty-state">No leave requests found.</td></tr>}
        </tbody></table></div></section>}

      {modal && <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setModal('') }}>
        <section className="student-modal" role="dialog" aria-modal="true" aria-labelledby="attendance-form-title">
          <div className="modal-heading"><div><h2 id="attendance-form-title">{modal === 'session' ? `${editing ? 'Edit' : 'Create'} attendance session` : `${editing ? 'Edit' : 'Create'} leave request`}</h2><p>Fields marked * are required.</p></div><button className="icon-button" onClick={() => setModal('')} aria-label="Close"><X size={18} /></button></div>
          {modal === 'session' ? <form onSubmit={submitSession}><div className="student-form-grid">
            <label>Class section *<select required value={sessionForm.classSectionId} onChange={(event) => setSessionForm({ ...sessionForm, classSectionId: event.target.value })}><option value="">Select section</option>{sections.map((row) => <option key={row.id} value={row.id}>{row.name}</option>)}</select></label>
            <label>Course<select value={sessionForm.courseId} onChange={(event) => setSessionForm({ ...sessionForm, courseId: event.target.value })}><option value="">None</option>{courses.map((row) => <option key={row.id} value={row.id}>{row.code} · {row.name}</option>)}</select></label>
            <label>Teacher<select value={sessionForm.teacherUserId} onChange={(event) => setSessionForm({ ...sessionForm, teacherUserId: event.target.value })}><option value="">Unassigned</option>{teachers.map((row) => <option key={row.id} value={row.id}>{row.displayName}</option>)}</select></label>
            <label>Starts at *<input required type="datetime-local" value={sessionForm.startsAt} onChange={(event) => setSessionForm({ ...sessionForm, startsAt: event.target.value })} /></label>
            <label>Ends at *<input required type="datetime-local" value={sessionForm.endsAt} onChange={(event) => setSessionForm({ ...sessionForm, endsAt: event.target.value })} /></label>
          </div><div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setModal('')} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving}>{saving ? 'Saving…' : 'Save session'}</button></div></form> : <form onSubmit={submitLeave}><div className="student-form-grid">
            <label>Student *<select required value={leaveForm.studentId} onChange={(event) => setLeaveForm({ ...leaveForm, studentId: event.target.value })}><option value="">Select student</option>{students.map((row) => <option key={row.id} value={row.id}>{row.firstName} {row.lastName} · {row.enrollmentNo}</option>)}</select></label>
            <label>Starts on *<input required type="date" value={leaveForm.startsOn} onChange={(event) => setLeaveForm({ ...leaveForm, startsOn: event.target.value })} /></label>
            <label>Ends on *<input required type="date" value={leaveForm.endsOn} onChange={(event) => setLeaveForm({ ...leaveForm, endsOn: event.target.value })} /></label>
            <label>Status<select value={leaveForm.status} onChange={(event) => setLeaveForm({ ...leaveForm, status: event.target.value })}>{(editing ? LEAVE_STATES : ['PENDING']).map((status) => <option key={status}>{status}</option>)}</select></label>
            <label className="wide-field">Reason *<textarea required maxLength="4000" value={leaveForm.reason} onChange={(event) => setLeaveForm({ ...leaveForm, reason: event.target.value })} /></label>
          </div><div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setModal('')} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving}>{saving ? 'Saving…' : 'Save request'}</button></div></form>}
        </section>
      </div>}
    </div>
  )
}
