import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { motion } from 'framer-motion'

export default function MetricCard({ title, value, delta, note, icon: Icon, tone, negative }) {
  const ChangeIcon = negative ? ArrowDownRight : ArrowUpRight
  return (
    <motion.article className="metric-card" whileHover={{ y: -3 }} transition={{ duration: 0.18 }}>
      <div className="metric-top">
        <span className={`metric-icon ${tone}`}><Icon size={19} strokeWidth={1.9} /></span>
        <button className="more-button" aria-label={`More ${title} options`}>···</button>
      </div>
      <p className="metric-title">{title}</p>
      <div className="metric-value">{value}</div>
      <div className="metric-bottom">
        <span className={`metric-delta ${negative ? 'delta-negative' : ''}`}><ChangeIcon size={14} /> {delta}</span>
        <span>{note}</span>
      </div>
    </motion.article>
  )
}
