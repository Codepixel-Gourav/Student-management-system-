import { useState } from 'react'
import { GraduationCap, LogIn } from 'lucide-react'
import { login, register } from '../services/api.js'

export default function LoginPage({ onLogin }) {
  const [registering, setRegistering] = useState(false)
  const [tenantSlug, setTenantSlug] = useState(import.meta.env.VITE_TENANT_SLUG || '')
  const [tenantName, setTenantName] = useState('')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const authenticatedSession = registering
        ? await register({ tenantName, tenantSlug, email, password, displayName })
        : await login({ tenantSlug, email, password })
      onLogin(authenticatedSession)
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
        <h1>{registering ? 'Create your school' : 'Sign in'}</h1>
        <p>{registering ? 'Register your school and first administrator account.' : 'Use your school administrator or staff account.'}</p>
        {error && <div className="records-error" role="alert">{error}</div>}
        <form className="login-form" onSubmit={submit}>
          {registering && <label>School name<input required maxLength="180" autoComplete="organization" value={tenantName} onChange={(event) => setTenantName(event.target.value)} /></label>}
          <label>School slug<input required minLength={registering ? 3 : undefined} maxLength="80" pattern={registering ? '[a-z0-9]+(-[a-z0-9]+)*' : undefined} title={registering ? 'Use lowercase letters and numbers separated by single hyphens.' : undefined} autoComplete="organization" value={tenantSlug} onChange={(event) => setTenantSlug(event.target.value)} /></label>
          {registering && <label>Administrator name<input required maxLength="180" autoComplete="name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>}
          <label>Email<input required type="email" maxLength="254" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <label>Password<input required type="password" minLength={registering ? 12 : undefined} maxLength="200" autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          <button className="button-primary" disabled={submitting}><LogIn size={16} />{submitting ? 'Please wait…' : registering ? 'Register school' : 'Sign in'}</button>
        </form>
        <button className="login-mode-toggle" type="button" disabled={submitting} onClick={() => { setRegistering((value) => !value); setError('') }}>
          {registering ? 'Already registered? Sign in' : 'New school? Create an account'}
        </button>
      </section>
    </main>
  )
}
