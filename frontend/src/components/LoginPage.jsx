import { useState } from 'react'
import { GraduationCap, LogIn } from 'lucide-react'
import { login } from '../services/api.js'

export default function LoginPage({ onLogin }) {
  const [tenantSlug, setTenantSlug] = useState(import.meta.env.VITE_TENANT_SLUG || '')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      onLogin(await login({ tenantSlug, email, password }))
    } catch (loginError) {
      setError(loginError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-screen">
      <section className="login-card">
        <div className="login-brand"><span className="brand-mark"><GraduationCap size={21} /></span><strong>Student Management System</strong></div>
        <h1>Sign in</h1>
        <p>Use your school administrator or staff account.</p>
        {error && <div className="records-error" role="alert">{error}</div>}
        <form className="login-form" onSubmit={submit}>
          <label>School slug<input required maxLength="80" autoComplete="organization" value={tenantSlug} onChange={(event) => setTenantSlug(event.target.value)} /></label>
          <label>Email<input required type="email" maxLength="254" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <label>Password<input required type="password" maxLength="200" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          <button className="button-primary" disabled={submitting}><LogIn size={16} />{submitting ? 'Signing in…' : 'Sign in'}</button>
        </form>
      </section>
    </main>
  )
}
