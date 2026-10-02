import { useCallback, useEffect, useMemo, useState } from 'react'
import { Activity, BookOpen, CalendarDays, Download, GraduationCap, Menu, Moon, Plus, Search, Sun, Users } from 'lucide-react'
import toast from 'react-hot-toast'
import Sidebar from './components/Sidebar.jsx'
import MetricCard from './components/MetricCard.jsx'
import EnrollmentChart from './components/EnrollmentChart.jsx'
import RecentStudents from './components/RecentStudents.jsx'
import StudentsPage from './components/StudentsPage.jsx'
import LoginPage from './components/LoginPage.jsx'
import AcademicPage from './components/AcademicPage.jsx'
import StaffPage from './components/StaffPage.jsx'
import AttendancePage from './components/AttendancePage.jsx'
import FinancePage from './components/FinancePage.jsx'
import { clearSession, getSession } from './services/api.js'
import { getDashboardSummary, getStudents } from './services/studentService.js'
import { attendanceApi } from './services/attendanceService.js'

function academicSectionFromPath() {
  const segment = window.location.pathname.split('/').filter(Boolean).at(-1)
  return ['periods', 'courses', 'sections'].includes(segment) ? segment : 'periods'
}

function Topbar({ active, query, setQuery, dark, setDark, setMobileOpen, session, onLogout }) {
  return (
    <header className="topbar">
      <div className="topbar-left">
        <button className="icon-button mobile-menu" onClick={() => setMobileOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
        <div className="breadcrumbs"><span>Pages</span><span className="crumb-slash">/</span><strong>{active}</strong></div>
      </div>
      <div className="topbar-actions">
        <label className="global-search"><Search size={17} /><input maxLength="100" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search students..." /></label>
        <button className="icon-button theme-button" onClick={() => setDark(!dark)} aria-label="Toggle color theme">{dark ? <Sun size={18} /> : <Moon size={18} />}</button>
        <span className="topbar-divider" />
        <span className="profile-static"><span className="profile-avatar">{session.email?.[0]?.toUpperCase() ?? 'U'}</span><span className="profile-label"><strong>{session.email}</strong><small>{session.roles?.join(', ')}</small></span><button className="button-secondary" onClick={onLogout}>Sign out</button></span>
      </div>
    </header>
  )
}

function Dashboard({ query, navigate }) {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [todaySessions, setTodaySessions] = useState(null)
  const [attendanceLoadError, setAttendanceLoadError] = useState(false)

  const loadSummary = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      setSummary(await getDashboardSummary(signal))
    } catch (loadError) {
      if (loadError.name !== 'AbortError') setError(loadError.message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    loadSummary(controller.signal)
    return () => controller.abort()
  }, [loadSummary])

  useEffect(() => {
    const controller = new AbortController()
    attendanceApi.sessions(controller.signal)
      .then((sessions) => {
        const today = new Date().toLocaleDateString()
        setTodaySessions(sessions.filter((session) => new Date(session.startsAt).toLocaleDateString() === today).length)
      })
      .catch((loadError) => {
        if (loadError.name !== 'AbortError') setAttendanceLoadError(true)
      })
    return () => controller.abort()
  }, [])

  const chartData = useMemo(() => {
    const year = new Date().getFullYear()
    const grouped = new Map((summary?.monthlyEnrollments ?? []).map((row) => [`${row.year}-${row.month}`, row.count]))
    return Array.from({ length: 12 }, (_, index) => ({
      month: new Intl.DateTimeFormat('en', { month: 'short' }).format(new Date(year, index, 1)),
      current: grouped.get(`${year}-${index + 1}`) ?? 0,
      previous: grouped.get(`${year - 1}-${index + 1}`) ?? 0,
    }))
  }, [summary])

  const exportStudents = async () => {
    try {
      const firstPage = await getStudents({ page: 0, size: 100 })
      const pages = []
      for (let page = 1; page < Math.ceil(firstPage.totalElements / 100); page += 1) {
        pages.push(await getStudents({ page, size: 100 }))
      }
      const students = [firstPage, ...pages].flatMap((result) => result.content)
      const quote = (value) => {
        const text = String(value ?? '')
        const safeText = /^[=+\-@]/.test(text) ? `'${text}` : text
        return `"${safeText.replaceAll('"', '""')}"`
      }
      const rows = [
        ['Enrollment No', 'First Name', 'Last Name', 'Email', 'Department', 'Status'],
        ...students.map((student) => [
          student.enrollmentNo, student.firstName, student.lastName,
          student.email, student.department, student.admissionStatus,
        ]),
      ]
      const csv = rows.map((row) => row.map(quote).join(',')).join('\r\n')
      const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = 'students.csv'
      link.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 0)
      toast.success(`Exported ${students.length} students.`)
    } catch (exportError) {
      toast.error(exportError.message)
    }
  }

  const dateLabel = useMemo(
    () => new Intl.DateTimeFormat('en', { month: 'long', day: 'numeric', year: 'numeric' }).format(new Date()),
    [],
  )
  const currentYear = new Date().getFullYear()
  const yearTotal = summary?.monthlyEnrollments
    .filter((row) => row.year === currentYear)
    .reduce((total, row) => total + row.count, 0) ?? 0

  return (
    <div className="page-content">
      <div className="welcome-row">
        <div><div className="eyebrow">YOUR CAMPUS AT A GLANCE</div><h1>Student dashboard</h1><p>Live student records from your school workspace.</p></div>
        <div className="welcome-actions">
          <span className="date-label"><CalendarDays size={15} /> {dateLabel}</span>
          <button className="button-secondary" onClick={exportStudents}><Download size={16} /> Export students</button>
          <button className="button-primary" onClick={() => navigate('Students')}><Plus size={17} /> Add student</button>
        </div>
      </div>

      {error && <div className="records-error" role="alert">{error}<button className="button-secondary" onClick={() => loadSummary()}>Retry</button></div>}
      <section className="metrics-grid" aria-label="Student metrics">
        <MetricCard title="Total students" value={loading ? '…' : (summary?.totalStudents ?? '—')} note="All student records" icon={GraduationCap} tone="metric-purple" />
        <MetricCard title="Enrolled" value={loading ? '…' : (summary?.enrolledStudents ?? '—')} note="Admission status: enrolled" icon={Users} tone="metric-blue" />
        <MetricCard title="New applications" value={loading ? '…' : (summary?.pendingAdmissions ?? '—')} note="Admission status: applied" icon={BookOpen} tone="metric-green" />
      </section>

      <div className="middle-grid">
        <section className="panel enrollment-panel">
          <div className="panel-heading chart-heading"><div><h2>Student enrollments</h2><p>New records created by month</p></div><span className="period-label">{currentYear}</span></div>
          <div className="chart-legend"><span><i className="legend-line current-line" /> {currentYear}</span><span><i className="legend-line previous-line" /> {currentYear - 1}</span><strong>{yearTotal.toLocaleString()} <small>this year</small></strong></div>
          <EnrollmentChart data={chartData} />
        </section>
        <section className="panel attendance-panel">
          <div className="panel-heading"><div><h2>Attendance</h2><p>Today’s scheduled class sessions</p></div><Activity size={18} /></div>
          <div className="module-placeholder"><CalendarDays size={28} /><strong>{attendanceLoadError ? 'Schedule unavailable' : todaySessions === null ? 'Loading schedule…' : `${todaySessions} session${todaySessions === 1 ? '' : 's'} today`}</strong><button className="text-action" onClick={() => navigate('Attendance')}>Open attendance</button></div>
        </section>
      </div>

      <div className="bottom-grid">
        <section className="panel students-panel">
          <div className="panel-heading"><div><h2>Recently added students</h2><p>Latest records from the student database</p></div><button className="text-action" onClick={() => navigate('Students')}>Manage students</button></div>
          <RecentStudents query={query} onViewAll={() => navigate('Students')} />
        </section>
        <section className="panel activity-panel">
          <div className="panel-heading"><div><h2>Recent activity</h2><p>System activity</p></div></div>
          <div className="module-placeholder"><Activity size={25} /><strong>Activity feed unavailable</strong><span>Activity tracking has not been implemented for this application.</span></div>
        </section>
      </div>
      <footer className="page-footer"><span>Student Management System</span><span><span className="footer-dot" /> {error ? 'Dashboard data unavailable' : 'Connected to student API'}</span></footer>
    </div>
  )
}

function RestrictedPage({ title }) {
  return <div className="page-content"><section className="panel unavailable-panel"><h1>{title}</h1><p>This page is available to school administrators only.</p></section></div>
}

function App() {
  const [session, setSession] = useState(getSession)
  const pageForPath = () => {
    const path = window.location.pathname.replace(/\/+$/, '')
    if (path === '/students') return 'Students'
    if (path.startsWith('/academics')) return 'Academics'
    if (path.startsWith('/settings')) return 'Settings'
    if (path === '/teachers') return 'Teachers'
    if (path === '/attendance') return 'Attendance'
    if (path === '/fees') return 'Fees & billing'
    if (path === '/analytics') return 'Analytics'
    return 'Dashboard'
  }
  const [active, setActive] = useState(pageForPath)
  const [query, setQuery] = useState('')
  const [dark, setDark] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    const handlePopState = () => setActive(pageForPath())
    const handleUnauthorized = () => setSession(null)
    window.addEventListener('popstate', handlePopState)
    window.addEventListener('sms:unauthorized', handleUnauthorized)
    return () => {
      window.removeEventListener('popstate', handlePopState)
      window.removeEventListener('sms:unauthorized', handleUnauthorized)
    }
  }, [])

  useEffect(() => {
    if (!session) return undefined
    const delay = Math.max(0, Date.parse(session.expiresAt) - Date.now())
    const timeout = window.setTimeout(() => {
      clearSession()
      setSession(null)
    }, delay)
    return () => window.clearTimeout(timeout)
  }, [session])

  const handleLogout = () => {
    clearSession()
    setSession(null)
  }

  if (!session) return <LoginPage onLogin={setSession} />
  const isAdmin = session.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))

  const navigate = (page) => {
    const paths = {
      Dashboard: '/',
      Students: '/students',
      Academics: '/academics',
      Settings: '/settings/campuses',
      Teachers: '/teachers',
      Attendance: '/attendance',
      Analytics: '/analytics',
      'Fees & billing': '/fees',
      'Fees': '/fees',
    }
    const path = paths[page]
    if (!path) return
    if (window.location.pathname !== path) window.history.pushState({}, '', path)
    setActive(page)
    setMobileOpen(false)
  }

  return (
    <div className={`app-shell ${dark ? 'dark-theme' : ''}`}>
      <Sidebar active={active} setActive={navigate} open={mobileOpen} onClose={() => setMobileOpen(false)} session={session} />
      <main className="main-area">
        <Topbar active={active} query={query} setQuery={setQuery} dark={dark} setDark={setDark} setMobileOpen={setMobileOpen} session={session} onLogout={handleLogout} />
        {['Dashboard', 'Analytics'].includes(active) && <Dashboard query={query} navigate={navigate} />}
        {active === 'Students' && <StudentsPage query={query} setQuery={setQuery} session={session} />}
        {active === 'Academics' && <AcademicPage section={academicSectionFromPath()} session={session} />}
        {active === 'Settings' && <AcademicPage section="campuses" session={session} />}
        {active === 'Teachers' && (isAdmin ? <StaffPage session={session} /> : <RestrictedPage title="Teachers" />)}
        {active === 'Attendance' && <AttendancePage session={session} />}
        {active === 'Fees & billing' && (isAdmin ? <FinancePage /> : <RestrictedPage title="Fees & billing" />)}
      </main>
    </div>
  )
}

export default App
