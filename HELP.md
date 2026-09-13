# CampusOS Student Information System

This repository is the foundation for a multi-campus SIS. The backend is Spring Boot 4 / Java 21, PostgreSQL, Flyway, HikariCP and Redis. The Vite client is React with Tailwind CSS, Lucide, Framer Motion and Recharts.

> **Deployment boundary:** this is a working foundation, not a finished enterprise product. The initial migration and student CRUD are implemented; the dashboard currently uses sample data. Authentication, authorization enforcement, delivery providers (SMS/email), payments, file storage, PDF generation, WebSocket messaging and the remaining module APIs must be completed and security-reviewed before production use. The current student endpoints accept a `tenantId` parameter and are not safe to expose publicly.

## 1. PostgreSQL schema and migration

`src/main/resources/db/migration/V1__initial_sis_schema.sql` creates the initial schema. Flyway applies it before Hibernate starts; Hibernate is set to `validate`, never to generate or mutate production schema.

| Domain | Tables |
| --- | --- |
| Tenancy and identity | `tenants`, `campuses`, `app_users`, `roles`, `user_roles`, `refresh_tokens` |
| Student records | `students`, `guardians`, `student_guardians`, `attachments` |
| Academic structure | `academic_periods`, `courses`, `class_sections`, `enrollments` |
| Attendance and leave | `attendance_sessions`, `attendance_records`, `leave_requests`, `leave_approvals` |
| Learning and assessment | `assignments`, `assignment_submissions`, `exams`, `exam_results`, `timetable_slots` |
| Finance | `fee_structures`, `invoices`, `payments` |
| Communication and audit | `announcements`, `notifications`, `conversations`, `conversation_members`, `messages`, `audit_events` |

Primary keys are UUIDs. Tenant ownership, unique enrollment numbers, state checks, monetary checks, useful lookup indexes and explicit deletion behavior are encoded in the DDL. Medical notes are represented as encrypted ciphertext; encrypt/decrypt keys belong in a managed KMS, never in PostgreSQL or source control. Attachments store object-storage keys and checksums, not file blobs.

### Migrating existing MySQL records

1. Back up and inventory the source database; map legacy records to a tenant, campus and academic period before importing.
2. Provision PostgreSQL, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`, then run the app once to let Flyway create the schema.
3. Use a repeatable ETL (e.g. pgloader or a versioned import job) that maps legacy numeric IDs to UUIDs and records an old-ID-to-new-ID crosswalk. Import parent tables before children and preserve orphan/error rows in a rejected-record report.
4. Validate counts, unique email/enrollment constraints, foreign keys, fee totals, and sample records. Run the import in staging, then schedule a write freeze and final delta import for cutover.
5. Do not edit an already-applied Flyway migration. Add a new `V2__...sql` migration for subsequent schema changes.

## 2. Backend structure and implementation sequence

Keep each domain in its own package as it grows:

```text
com.example.SMS
  auth/          login, JWT validation/issuance, refresh rotation, MFA
  student/       admissions, student profile, guardians, documents
  attendance/    sessions, attendance records, leave workflow
  academics/     courses, sections, timetable, assignments, exams
  finance/       fee structures, invoices, payment providers, webhooks
  communications/announcements, notifications, messaging
  reporting/     projections, exports, report-card generation
  shared/        tenant context, error model, audit, pagination
```

The current student slice lives in `controller`, `service`, `repository`, `entity` and `dto`. It supports validated student creation, tenant-filtered pagination, retrieval and deletion. Pagination is capped at 100 and sorting is allow-listed. Build new domain endpoints with request/response DTOs rather than binding JPA entities directly.

Recommended service boundaries and key workflows:

* **Identity:** BCrypt password hashes; short-lived signed access JWTs; rotating opaque refresh tokens persisted only as hashes; revoke a token family on reuse; admin TOTP MFA; rate-limited login and password reset. Require an authenticated principal and derive tenant/roles from signed claims, never from a request-supplied tenant ID. Apply `@PreAuthorize` and tenant-scoped repository predicates.
* **Admissions/SIS:** application state machine (`APPLIED → UNDER_REVIEW → ADMITTED → ENROLLED`), immutable audit events, guardian relationships, and document metadata backed by private object storage with signed URLs.
* **Attendance/leave:** idempotent device-ingest endpoints, unique `(session_id, student_id)` marking, queued parent alerts via an outbox, and explicit teacher/admin leave approval transitions.
* **Academics:** validate timetable overlaps for teacher, room and class in a transaction; persist assignment submissions with a plagiarism-provider interface; publish grades only after authorization and audit.
* **Finance:** immutable invoice lines and payment ledger, server-side penalty policy, provider idempotency keys, and signature-verified/replay-safe webhooks. Never trust a browser-reported payment result.
* **Communication/reporting:** store in-app events in `notifications`, deliver email/SMS asynchronously, authorize every conversation member before WebSocket subscription, and generate exports from read-only reporting queries.

Add controller advice that emits stable `ProblemDetail` responses, request correlation IDs, structured logs with sensitive fields redacted, and `@Transactional` service boundaries. Use Redis for explicitly disposable cache/rate-limit state; PostgreSQL remains the source of truth. Configure provider secrets through a secret manager.

### Local backend services

Set local credentials only for local development. Start PostgreSQL and Redis, then run:

```powershell
docker compose up -d postgres redis
.\mvnw.cmd spring-boot:run
```

The server listens on `http://localhost:8080`; health probes are at `/actuator/health`. Student API examples:

```text
GET    /api/students?tenantId=<uuid>&page=0&size=20&sortBy=lastName
GET    /api/students/<student-uuid>?tenantId=<uuid>
POST   /api/students
DELETE /api/students/<student-uuid>?tenantId=<uuid>
```

`POST` requires `tenantId`, `campusId`, `enrollmentNo`, `firstName`, and `lastName`; optional fields include `email`, `department`, `enrollmentYear`, and `dateOfBirth`.

## 3. Frontend structure and run guide

The Vite app is in `frontend/`:

```text
src/
  App.jsx
  components/  Sidebar, MetricCard, EnrollmentChart, RecentStudents
  styles.css
```

It includes responsive navigation, dashboard metric cards, enrollment visualization, attendance summary, a searchable/paginated-ready student table layout, activity feed, light/dark toggle and action toasts. Dashboard chart, metric and activity values are demo fixtures and should be replaced with tenant-scoped API queries plus loading/error/empty states. The Vite dev server proxies `/api` to the Spring Boot server.

```powershell
cd frontend
npm install
npm run dev
npm run build
```

For the production client, split each domain into route-level pages, use a typed API client, server-side pagination/sorting, React Query cache invalidation, accessible form validation, and a tested authentication flow. Keep refresh credentials in secure, HttpOnly, SameSite cookies; do not persist access or refresh tokens in local storage.

## 4. Production rollout gates

Before deployment: implement and test the identity/MFA flow and role/tenant enforcement; replace dashboard fixtures; complete module APIs and asynchronous workers; add PostgreSQL/Redis TLS, backups/PITR, secret management, migrations in CI, observability/alerts, retention policies, data export/deletion workflows, and disaster-recovery exercises. Add integration tests against PostgreSQL and Redis, plus authorization tests proving users cannot access another tenant's records.
