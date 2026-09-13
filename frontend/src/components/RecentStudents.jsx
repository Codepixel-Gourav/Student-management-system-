import { ArrowUpRight, MoreHorizontal } from 'lucide-react'

const students = [
  { name: 'Olivia Rhye', email: 'olivia.rhye@northwood.edu', initials: 'OR', color: 'lilac', id: 'STU-2024-0842', course: 'Computer Science', status: 'Active' },
  { name: 'Phoenix Baker', email: 'phoenix.baker@northwood.edu', initials: 'PB', color: 'peach', id: 'STU-2024-0839', course: 'Business Admin', status: 'Active' },
  { name: 'Lana Steiner', email: 'lana.steiner@northwood.edu', initials: 'LS', color: 'mint', id: 'STU-2024-0834', course: 'Visual Arts', status: 'Pending' },
  { name: 'Demi Wilkinson', email: 'demi.wilkinson@northwood.edu', initials: 'DW', color: 'blue', id: 'STU-2024-0828', course: 'Engineering', status: 'Active' },
]

export default function RecentStudents({ query }) {
  const filtered = students.filter((student) => `${student.name} ${student.email} ${student.id} ${student.course}`.toLowerCase().includes(query.toLowerCase()))
  return (
    <div className="table-scroll">
      <table className="student-table">
        <thead><tr><th>STUDENT</th><th>STUDENT ID</th><th>PROGRAM</th><th>STATUS</th><th aria-label="Actions" /></tr></thead>
        <tbody>
          {filtered.map((student) => (
            <tr key={student.id}>
              <td><div className="student-name"><span className={`student-avatar ${student.color}`}>{student.initials}</span><span><strong>{student.name}</strong><small>{student.email}</small></span></div></td>
              <td className="id-cell">{student.id}</td>
              <td>{student.course}</td>
              <td><span className={`status-pill ${student.status.toLowerCase()}`}><i />{student.status}</span></td>
              <td><button className="table-more" aria-label={`Actions for ${student.name}`}><MoreHorizontal size={18} /></button></td>
            </tr>
          ))}
          {filtered.length === 0 && <tr><td colSpan="5" className="empty-state">No students match “{query}”.</td></tr>}
        </tbody>
      </table>
      <button className="view-all">View all students <ArrowUpRight size={15} /></button>
    </div>
  )
}
