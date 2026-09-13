import { useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import { Activity, Bell, BookOpen, CalendarDays, ChevronDown, ChevronLeft, ChevronRight, CircleDollarSign, Download, GraduationCap, Menu, Moon, Plus, Search, Sun, Users, UserRoundPlus } from 'lucide-react'
import toast from 'react-hot-toast'
import Sidebar from './components/Sidebar.jsx'
import MetricCard from './components/MetricCard.jsx'
import EnrollmentChart from './components/EnrollmentChart.jsx'
import RecentStudents from './components/RecentStudents.jsx'

const activity = [
  { title: 'New student enrollment', detail: 'Ava Thompson · Grade 10', time: '12 min ago', tone: 'purple', icon: UserRoundPlus },
  { title: 'Fee payment received', detail: 'Invoice #INV-2024-0312', time: '38 min ago', tone: 'green', icon: CircleDollarSign },
  { title: 'Attendance report ready', detail: 'Grade 9 · Period 3', time: '1 hour ago', tone: 'blue', icon: CalendarDays },
]

function Topbar({ query, setQuery, dark, setDark, setMobileOpen }) {
  return (
    <header className="topbar">
      <div className="topbar-left">
        <button className="icon-button mobile-menu" onClick={() => setMobileOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
        <div className="breadcrumbs"><span>Pages</span><span className="crumb-slash">/</span><strong>Dashboard</strong></div>
      </div>
      <div className="topbar-actions">
        <label className="global-search"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search anything..." /><kbd>⌘ K</kbd></label>
        <button className="icon-button theme-button" onClick={() => setDark(!dark)} aria-label="Toggle color theme">{dark ? <Sun size={18} /> : <Moon size={18} />}</button>
        <button className="notification-button" aria-label="Notifications" onClick={() => toast('You’re all caught up!')}><Bell size={19} /><i /></button>
        <span className="topbar-divider" />
        <button className="profile-button"><span className="profile-avatar">JD</span><span className="profile-label"><strong>Jordan Davis</strong><small>School Admin</small></span><ChevronDown size={15} /></button>
      </div>
    </header>
  )
}

function AttendancePanel() {
  return (
    <section className="panel attendance-panel">
      <div className="panel-heading"><div><h2>Today’s attendance</h2><p>Thursday, October 24, 2024</p></div><button className="icon-button compact"><MoreHorizontalIcon /></button></div>
      <div className="attendance-summary"><div className="attendance-ring"><span><strong>94.8%</strong><small>Present</small></span></div><div className="attendance-numbers"><div><i className="legend-dot present-dot" /><span>Present</span><strong>1,284</strong></div><div><i className="legend-dot absent-dot" /><span>Absent</span><strong>56</strong></div><div><i className="legend-dot late-dot" /><span>Late</span><strong>18</strong></div></div></div>
      <div className="attendance-foot"><span><Activity size={14} /> 2.4% higher than last week</span><button onClick={() => toast('Attendance report is being prepared')}>Full report <ChevronRight size={14} /></button></div>
    </section>
  )
}

function MoreHorizontalIcon() {
  return <span className="more-dots">···</span>
}

function App() {
  const [active, setActive] = useState('Dashboard')
  const [query, setQuery] = useState('')
  const [dark, setDark] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const dateLabel = useMemo(() => new Intl.DateTimeFormat('en', { month: 'long', day: 'numeric', year: 'numeric' }).format(new Date()), [])

  const addStudent = () => toast.success('New student workflow opened')
  const exportReport = () => toast.success('Report export is being prepared')

  return (
    <div className={`app-shell ${dark ? 'dark-theme' : ''}`}>
      <Sidebar active={active} setActive={setActive} open={mobileOpen} onClose={() => setMobileOpen(false)} />
      <main className="main-area">
        <Topbar query={query} setQuery={setQuery} dark={dark} setDark={setDark} setMobileOpen={setMobileOpen} />
        <div className="page-content">
          <motion.div className="welcome-row" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35 }}>
            <div><div className="eyebrow"><span className="welcome-sparkle">✳</span> YOUR CAMPUS AT A GLANCE</div><h1>Good morning, Jordan <span>✦</span></h1><p>Here’s what’s happening at your school today.</p></div>
            <div className="welcome-actions"><span className="date-label"><CalendarDays size={15} /> {dateLabel}</span><button className="button-secondary" onClick={exportReport}><Download size={16} /> Export</button><button className="button-primary" onClick={addStudent}><Plus size={17} /> Add student</button></div>
          </motion.div>

          <section className="metrics-grid" aria-label="School metrics">
            <MetricCard title="Total students" value="2,847" delta="+12.8%" note="vs last semester" icon={GraduationCap} tone="metric-purple" />
            <MetricCard title="Teaching staff" value="184" delta="+4.2%" note="vs last semester" icon={Users} tone="metric-blue" />
            <MetricCard title="Attendance rate" value="94.8%" delta="+2.4%" note="vs last week" icon={CalendarDays} tone="metric-green" />
            <MetricCard title="Fees collected" value="$284,560" delta="−3.1%" note="vs last month" icon={CircleDollarSign} tone="metric-orange" negative />
          </section>

          <div className="middle-grid">
            <section className="panel enrollment-panel">
              <div className="panel-heading chart-heading"><div><h2>Student enrollment</h2><p>Enrollment growth throughout the year</p></div><button className="period-button">This year <ChevronDown size={14} /></button></div>
              <div className="chart-legend"><span><i className="legend-line current-line" /> This year</span><span><i className="legend-line previous-line" /> Last year</span><strong>2,847 <small>total enrolled</small></strong></div>
              <EnrollmentChart />
            </section>
            <AttendancePanel />
          </div>

          <div className="bottom-grid">
            <section className="panel students-panel">
              <div className="panel-heading"><div><h2>Recently enrolled</h2><p>Keep up with your newest students</p></div><button className="icon-button compact"><MoreHorizontalIcon /></button></div>
              <RecentStudents query={query} />
            </section>
            <section className="panel activity-panel">
              <div className="panel-heading"><div><h2>Recent activity</h2><p>Latest updates from your campus</p></div><button className="icon-button compact"><MoreHorizontalIcon /></button></div>
              <div className="activity-list">
                {activity.map(({ title, detail, time, tone, icon: Icon }) => <div className="activity-item" key={title}><span className={`activity-icon ${tone}`}><Icon size={16} /></span><span className="activity-copy"><strong>{title}</strong><small>{detail}</small></span><time>{time}</time></div>)}
              </div>
              <button className="activity-link" onClick={() => toast('Showing all campus activity')}>View all activity <ChevronRight size={15} /></button>
            </section>
          </div>
          <footer className="page-footer"><span>© 2024 CampusOS</span><span><span className="footer-dot" /> All data is up to date <button><ChevronLeft size={14} /><ChevronRight size={14} /></button></span></footer>
        </div>
      </main>
    </div>
  )
}

export default App
