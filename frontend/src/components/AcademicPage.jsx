import { useCallback, useEffect, useState } from 'react'
import { Pencil, Plus, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { campusesApi, coursesApi, periodsApi, sectionsApi } from '../services/academicService.js'

const RESOURCES = {
  periods: {
    title: 'Academic periods',
    api: periodsApi,
    fields: [
      { name: 'name', label: 'Period name', required: true, maxLength: 100 },
      { name: 'startsOn', label: 'Starts on', type: 'date', required: true },
      { name: 'endsOn', label: 'Ends on', type: 'date', required: true },
      { name: 'status', label: 'Status', type: 'select', options: ['PLANNED', 'ACTIVE', 'CLOSED'] },
    ],
    columns: [['name', 'Name'], ['startsOn', 'Starts'], ['endsOn', 'Ends'], ['status', 'Status']],
    empty: { name: '', startsOn: '', endsOn: '', status: 'PLANNED' },
  },
  courses: {
    title: 'Courses',
    api: coursesApi,
    fields: [
      { name: 'code', label: 'Course code', required: true, maxLength: 40 },
      { name: 'name', label: 'Course name', required: true, maxLength: 180 },
      { name: 'credits', label: 'Credits', type: 'number', required: true, min: '0', step: '0.1' },
    ],
    columns: [['code', 'Code'], ['name', 'Course'], ['credits', 'Credits']],
    empty: { code: '', name: '', credits: '0' },
  },
  sections: {
    title: 'Class sections',
    api: sectionsApi,
    fields: [
      { name: 'name', label: 'Section name', required: true, maxLength: 100 },
      { name: 'gradeLevel', label: 'Grade level', required: true, maxLength: 40 },
      { name: 'room', label: 'Room', maxLength: 80 },
      { name: 'campusId', label: 'Campus', type: 'campus', required: true },
      { name: 'academicPeriodId', label: 'Academic period', type: 'period', required: true },
    ],
    columns: [['name', 'Section'], ['gradeLevel', 'Grade'], ['room', 'Room'], ['campusId', 'Campus'], ['academicPeriodId', 'Period']],
    empty: { name: '', gradeLevel: '', room: '', campusId: '', academicPeriodId: '' },
  },
  campuses: {
    title: 'Campuses',
    api: campusesApi,
    fields: [
      { name: 'name', label: 'Campus name', required: true, maxLength: 180 },
      { name: 'timezone', label: 'Timezone', required: true, maxLength: 80 },
    ],
    columns: [['name', 'Campus'], ['timezone', 'Timezone']],
    empty: { name: '', timezone: 'UTC' },
  },
}

function displayValue(field, value, campuses, periods) {
  if (field === 'campusId') return campuses.find((campus) => campus.id === value)?.name ?? value
  if (field === 'academicPeriodId') return periods.find((period) => period.id === value)?.name ?? value
  return value || '—'
}

export default function AcademicPage({ section = 'periods', session, onNavigate }) {
  const [activeSection, setActiveSection] = useState(section)
  const config = RESOURCES[activeSection]
  const [records, setRecords] = useState([])
  const [campuses, setCampuses] = useState([])
  const [periods, setPeriods] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(config.empty)
  const [saving, setSaving] = useState(false)
  const canManage = session.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))

  const load = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const result = await config.api.list(signal)
      setRecords(result)
      if (activeSection === 'sections') {
        const [campusList, periodList] = await Promise.all([
          campusesApi.list(signal),
          periodsApi.list(signal),
        ])
        setCampuses(campusList)
        setPeriods(periodList)
      }
    } catch (loadError) {
      if (loadError.name !== 'AbortError') setError(loadError.message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [config, activeSection])

  useEffect(() => {
    setActiveSection(section)
  }, [section])

  useEffect(() => {
    const handlePopState = () => {
      const pathSection = window.location.pathname.split('/').filter(Boolean).at(-1)
      if (RESOURCES[pathSection]) setActiveSection(pathSection)
      else if (window.location.pathname.startsWith('/academics')) setActiveSection('periods')
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const openNew = () => {
    setEditing(false)
    setForm({ ...config.empty })
  }

  const openEdit = (record) => {
    setEditing(record)
    setForm(Object.fromEntries(config.fields.map(({ name }) => [name, record[name] ?? ''])))
  }

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    const payload = { ...form }
    if (activeSection === 'courses') payload.credits = Number(payload.credits)
    if (activeSection === 'sections' && !payload.room) payload.room = null
    try {
      if (editing) {
        await config.api.update(editing.id, payload)
        toast.success(`${config.title.slice(0, -1)} updated.`)
      } else {
        await config.api.create(payload)
        toast.success(`${config.title.slice(0, -1)} created.`)
      }
      setEditing(null)
      await load()
    } catch (saveError) {
      toast.error(saveError.message)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (record) => {
    if (!window.confirm(`Delete this ${config.title.toLowerCase().replace(/s$/, '')}?`)) return
    try {
      await config.api.remove(record.id)
      toast.success('Record deleted.')
      await load()
    } catch (deleteError) {
      toast.error(deleteError.message)
    }
  }

  const relationOptions = (type) => type === 'campus' ? campuses : periods

  return (
    <div className="page-content">
      <div className="welcome-row">
        <div><div className="eyebrow">SCHOOL MANAGEMENT</div><h1>{config.title}</h1><p>Manage records for your authenticated school workspace.</p></div>
        {canManage && <button className="button-primary" onClick={openNew}><Plus size={16} /> Add {config.title.replace(/s$/, '').toLowerCase()}</button>}
      </div>
      {section !== 'campuses' && <div className="module-tabs" role="tablist" aria-label="Academic sections">
        {[['periods', 'Academic periods'], ['courses', 'Courses'], ['sections', 'Class sections'], ['enrollments', 'Enrollments']].map(([key, label]) => (
          <button key={key} role="tab" aria-selected={activeSection === key} className={activeSection === key ? 'module-tab active' : 'module-tab'} onClick={() => {
            if (key === 'enrollments') {
              onNavigate('Enrollments')
              return
            }
            onNavigate(`Academic:${key}`)
          }}>{label}</button>
        ))}
      </div>}
      <section className="panel records-panel">
        {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => load()}>Retry</button></div>}
        <div className="table-scroll records-table-scroll">
          <table className="student-table">
            <thead><tr>{config.columns.map(([, label]) => <th key={label}>{label.toUpperCase()}</th>)}{canManage && <th>ACTIONS</th>}</tr></thead>
            <tbody>
              {loading && <tr><td colSpan={config.columns.length + Number(canManage)} className="empty-state">Loading…</td></tr>}
              {!loading && !error && records.map((record) => (
                <tr key={record.id}>
                  {config.columns.map(([field]) => <td key={field}>{displayValue(field, record[field], campuses, periods)}</td>)}
                  {canManage && <td className="record-actions"><button className="icon-button" onClick={() => openEdit(record)} aria-label={`Edit ${config.title} record`}><Pencil size={16} /></button><button className="icon-button" onClick={() => remove(record)} aria-label={`Delete ${config.title} record`}><Trash2 size={16} /></button></td>}
                </tr>
              ))}
              {!loading && !error && records.length === 0 && <tr><td colSpan={config.columns.length + Number(canManage)} className="empty-state">No records found.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      {editing !== null && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setEditing(null) }}>
          <section className="student-modal" role="dialog" aria-modal="true" aria-labelledby="resource-form-title">
            <div className="modal-heading"><div><h2 id="resource-form-title">{editing ? 'Edit' : 'Add'} {config.title.toLowerCase().replace(/s$/, '')}</h2><p>Enter the record details.</p></div><button className="icon-button" onClick={() => !saving && setEditing(null)} aria-label="Close"><X size={18} /></button></div>
            <form onSubmit={submit}>
              <div className="student-form-grid">
                {config.fields.map((field) => (
                  <label key={field.name}>{field.label}{field.type === 'select' || field.type === 'campus' || field.type === 'period' ? (
                    <select required={field.required} value={form[field.name] ?? ''} onChange={(event) => setForm({ ...form, [field.name]: event.target.value })}>
                      <option value="">Select {field.label.toLowerCase()}</option>
                      {(field.options ?? relationOptions(field.type)).map((option) => (
                        <option key={field.options ? option : option.id} value={field.options ? option : option.id}>
                          {field.options ? option : option.name}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input required={field.required} maxLength={field.maxLength} type={field.type ?? 'text'} min={field.min} step={field.step} value={form[field.name] ?? ''} onChange={(event) => setForm({ ...form, [field.name]: event.target.value })} />
                  )}</label>
                ))}
              </div>
              <div className="modal-actions"><button type="button" className="button-secondary" onClick={() => setEditing(null)} disabled={saving}>Cancel</button><button className="button-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button></div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}
