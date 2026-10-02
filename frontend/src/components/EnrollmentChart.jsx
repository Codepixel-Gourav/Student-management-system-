import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export default function EnrollmentChart({ data }) {
  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 12, right: 8, left: -16, bottom: 0 }}>
          <defs>
            <linearGradient id="currentFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#635bdb" stopOpacity={0.18} />
              <stop offset="95%" stopColor="#635bdb" stopOpacity={0} />
            </linearGradient>
            <linearGradient id="previousFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#a5b4fc" stopOpacity={0.1} />
              <stop offset="95%" stopColor="#a5b4fc" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} stroke="#edf0f5" strokeDasharray="4 4" />
          <XAxis dataKey="month" axisLine={false} tickLine={false} tick={{ fill: '#9298a8', fontSize: 11 }} dy={10} />
          <YAxis axisLine={false} tickLine={false} tick={{ fill: '#9298a8', fontSize: 11 }} ticks={[2000, 3000, 4000, 5000]} tickFormatter={(v) => `${v / 1000}k`} />
          <Tooltip formatter={(value) => [value.toLocaleString(), 'Students']} contentStyle={{ border: '1px solid #eceef4', borderRadius: 12, boxShadow: '0 8px 24px #1d23400d' }} />
          <Area type="monotone" dataKey="previous" stroke="#aeb4c4" strokeWidth={2} strokeDasharray="5 5" fill="url(#previousFill)" />
          <Area type="monotone" dataKey="current" stroke="#635bdb" strokeWidth={2.5} fill="url(#currentFill)" activeDot={{ r: 5, strokeWidth: 0 }} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}
