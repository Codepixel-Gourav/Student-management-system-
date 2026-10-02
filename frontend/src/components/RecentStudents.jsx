import { useEffect, useState } from 'react'
import { ArrowUpRight } from 'lucide-react'
import { getStudents } from '../services/studentService.js'

export default function RecentStudents({ query }) {
  const [students, setStudents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    getStudents({
      tenantId: import.meta.env.VITE_TENANT_ID,
      size: 100,
      sortBy: 'createdAt',
      signal: controller.signal,
    })
      .then(setStudents)
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setError(loadError.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [])

  const filtered = students
    .slice()
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .filter((student) => {
      const name = `${student.firstName} ${student.lastName}`
      return `${name} ${student.email ?? ''} ${student.enrollmentNo} ${student.department ?? ''}`
        .toLowerCase()
        .includes(query.toLowerCase())
    })
    .slice(0, 5)

  return (
    <div className="table-scroll">
      <table className="student-table">
        <thead><tr><th>STUDENT</th><th>STUDENT ID</th><th>PROGRAM</th><th>STATUS</th></tr></thead>
        <tbody>
          {loading && <tr><td colSpan="4" className="empty-state">Loading students…</td></tr>}
          {!loading && error && <tr><td colSpan="4" className="empty-state">{error}</td></tr>}
          {!loading && !error && filtered.map((student) => {
            const name = `${student.firstName} ${student.lastName}`
            const initials = `${student.firstName?.[0] ?? ''}${student.lastName?.[0] ?? ''}`
            const status = student.admissionStatus ?? 'Unknown'

            return (
              <tr key={student.id}>
                <td><div className="student-name"><span className="student-avatar lilac">{initials}</span><span><strong>{name}</strong><small>{student.email || 'No email'}</small></span></div></td>
                <td className="id-cell">{student.enrollmentNo}</td>
                <td>{student.department || '—'}</td>
                <td><span className={`status-pill ${status.toLowerCase()}`}><i />{status}</span></td>
              </tr>
            )
          })}
          {!loading && !error && filtered.length === 0 && (
            <tr><td colSpan="4" className="empty-state">
              {query ? `No students match “${query}”.` : 'No students found.'}
            </td></tr>
          )}
        </tbody>
      </table>
      <button className="view-all">View all students <ArrowUpRight size={15} /></button>
    </div>
  )
}
