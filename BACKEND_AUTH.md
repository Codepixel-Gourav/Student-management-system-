# Backend authentication and tenant API

## Required configuration

Set `SMS_JWT_SECRET` to a random secret containing at least 32 bytes (for example, a securely generated 32-byte Base64 value). The application fails during startup if this is missing or too short. Access tokens are HS256 signed and last 15 minutes by default; configure `SMS_ACCESS_TOKEN_MINUTES` to a value from 1 through 60. Use a secret manager in deployed environments.

There is no public registration endpoint. For initial tenant-admin creation, configure `SMS_BOOTSTRAP_TENANT_ID`, `SMS_BOOTSTRAP_EMAIL`, `SMS_BOOTSTRAP_PASSWORD` (minimum 12 characters), and `SMS_BOOTSTRAP_DISPLAY_NAME` together for a one-time application startup. The tenant must already exist and be active. The runner creates one active `SCHOOL_ADMIN` account only if that tenant/email does not already exist; it refuses to reset or grant privileges to an existing account. Remove the bootstrap environment values after that startup. Never expose bootstrap values in public runtime configuration.

The database password must be stored in `app_users.password_hash` as a BCrypt hash. The bootstrap mechanism hashes its supplied password with BCrypt cost 12. Existing accounts must be `ACTIVE`, and their tenant must be `ACTIVE`, to sign in.

## Authentication contract

`POST /api/auth/login` is the only public application API:

```json
{
  "tenantSlug": "school-slug",
  "email": "admin@example.edu",
  "password": "..."
}
```

Success returns an `accessToken`, `tokenType` (`Bearer`), `expiresAt`, `userId`, `tenantId`, `email`, and role codes. Send it on other API calls as `Authorization: Bearer <accessToken>`. Invalid credentials return 401 without identifying which value was incorrect. There is no public user creation or refresh-token endpoint.

Authenticated tenant and roles come only from the verified token. Any legacy `tenantId` request parameter or JSON property does not select a tenant. `SCHOOL_ADMIN` and `SUPER_ADMIN` can mutate records; teachers have read access to student/dashboard and academic data. Campus settings, student, academic-period, course, and class-section writes are administrator-only.

## Implemented APIs

* `/api/students` — tenant-scoped list, detail, create, update, delete. Create bodies include `campusId` and student fields, but not `tenantId`. Medical notes and credentials are not returned.
* `/api/dashboard/summary` — authenticated user's tenant summary.
* `/api/academic-periods`, `/api/courses`, `/api/class-sections` — tenant-scoped CRUD.
* `/api/settings/campuses` — tenant-scoped CRUD.
* `/api/staff` — school-admin-only teacher account CRUD. Creation requires a 12-character minimum initial password; password changes use `PUT /api/staff/{id}/password`. Responses expose only account ID, email, display name, status, and timestamps; password hashes and MFA secrets are never selected or returned.
* `/api/attendance/sessions` — tenant-scoped session CRUD. Section, optional course, and optional teacher references are verified against the authenticated tenant; assigned teachers must have the `TEACHER` role.
* `/api/attendance/sessions/{sessionId}/records` and `/api/attendance/records/{id}` — tenant-scoped attendance-record CRUD. New marks require the student to be actively enrolled in that section and campus. API-created/edited records are always attributed to `MANUAL`.
* `/api/attendance/leave-requests` — school-admin-only CRUD, tenant-scoped through the request's student. New requests must begin with `PENDING`; end dates cannot precede start dates.
* `/api/invoices` — tenant-scoped CRUD via the owning student. Invoices with payment history cannot be edited/deleted; only invoices without payment records can be voided.
* `/api/invoices/{invoiceId}/payments` and `/api/payments` — tenant-scoped payment CRUD. The API records manual payments only; status transitions are restricted, successful amounts cannot exceed invoice totals, payment status recalculates invoice status, and successful/refunded payments cannot be deleted. Idempotency keys are enforced. External-provider callbacks and reconciliation are not integrated.
* `/actuator/health` remains public; other routes require authentication.

Leave-request reasons can contain sensitive personal details, so those endpoints are restricted to administrators. Finance APIs require administrator roles; teachers may read attendance sessions/records and create/update attendance marks. Student medical notes, credentials, and MFA secrets are not exposed by these APIs.

Other schema modules (enrollments, assignments, exams, timetable, fee structures, announcements, messaging, attachments, audit events, notifications, guardians, and approvals) do not yet have CRUD APIs. Tenant-level settings beyond campuses remain unimplemented.
