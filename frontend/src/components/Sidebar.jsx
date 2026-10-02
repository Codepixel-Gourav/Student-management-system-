import { BookOpen, CalendarDays, ChartNoAxesCombined, CreditCard, GraduationCap, LayoutDashboard, MessageSquareText, Settings2, Users, X } from 'lucide-react'

const groups = [
  {
    label: 'OVERVIEW',
    links: [{ label: 'Dashboard', icon: LayoutDashboard }, { label: 'Analytics', icon: ChartNoAxesCombined }],
  },
  {
    label: 'MANAGEMENT',
    links: [{ label: 'Students', icon: GraduationCap }, { label: 'Teachers', icon: Users }, { label: 'Academics', icon: BookOpen }, { label: 'Attendance', icon: CalendarDays }, { label: 'Fees & billing', icon: CreditCard }],
  },
]

export default function Sidebar({ active, setActive, open, onClose, session }) {
  const isAdmin = session?.roles?.some((role) => ['SCHOOL_ADMIN', 'SUPER_ADMIN'].includes(role))
  const visibleGroups = groups.map((group) => ({
    ...group,
    links: group.links.filter(({ label }) => isAdmin || !['Teachers', 'Fees & billing'].includes(label)),
  }))

  return (
    <>
      {open && <button className="sidebar-backdrop" aria-label="Close navigation" onClick={onClose} />}
      <aside className={`sidebar ${open ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-mark"><GraduationCap size={21} /></div>
          <span>campus<span className="brand-light">OS</span></span>
          <button className="mobile-close" onClick={onClose} aria-label="Close menu"><X size={18} /></button>
        </div>
        <div className="campus-switch">
          <span className="campus-avatar">S</span>
          <span className="campus-copy"><strong>School workspace</strong><small>Student records</small></span>
        </div>
        <nav className="navigation" aria-label="Main navigation">
          {visibleGroups.map((group) => (
            <section key={group.label} className="nav-group">
              <p>{group.label}</p>
              {group.links.map(({ label, icon: Icon }) => (
                <button key={label} onClick={() => { setActive(label); onClose() }} className={`nav-link ${active === label ? 'nav-active' : ''}`}>
                  <Icon size={18} strokeWidth={1.8} /> <span>{label}</span>
                </button>
              ))}
            </section>
          ))}
          <section className="nav-group nav-bottom">
            <p>WORKSPACE</p>
            <button className={`nav-link ${active === 'Settings' ? 'nav-active' : ''}`} onClick={() => { setActive('Settings'); onClose() }}><Settings2 size={18} /><span>Settings</span></button>
          </section>
        </nav>
        <div className="sidebar-upgrade">
          <div className="upgrade-icon"><MessageSquareText size={18} /></div>
          <strong>School workspace</strong>
          <span>Manage student records, academic setup, and campus settings.</span>
        </div>
        <div className="sidebar-footer"><span className="status-dot" /> Dashboard &amp; student records</div>
      </aside>
    </>
  )
}
