import { motion } from 'framer-motion'

export default function MetricCard({ title, value, note, icon: Icon, tone }) {
  return (
    <motion.article className="metric-card" whileHover={{ y: -3 }} transition={{ duration: 0.18 }}>
      <div className="metric-top">
        <span className={`metric-icon ${tone}`}><Icon size={19} strokeWidth={1.9} /></span>
      </div>
      <p className="metric-title">{title}</p>
      <div className="metric-value">{value}</div>
      <div className="metric-bottom"><span>{note}</span></div>
    </motion.article>
  )
}
