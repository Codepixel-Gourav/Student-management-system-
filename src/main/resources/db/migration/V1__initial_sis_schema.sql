CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    timezone VARCHAR(80) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name),
    UNIQUE (tenant_id, id)
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED'
        CHECK (status IN ('INVITED', 'ACTIVE', 'LOCKED', 'DISABLED')),
    mfa_enabled BOOLEAN NOT NULL DEFAULT false,
    mfa_secret_encrypted TEXT,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email),
    UNIQUE (tenant_id, id)
);
CREATE INDEX ix_app_users_email ON app_users (lower(email));

CREATE TABLE roles (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE
);
INSERT INTO roles (code) VALUES
    ('SUPER_ADMIN'), ('SCHOOL_ADMIN'), ('TEACHER'), ('STUDENT'), ('PARENT');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash BYTEA NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user_expiry ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    campus_id UUID NOT NULL,
    user_id UUID UNIQUE REFERENCES app_users(id) ON DELETE SET NULL,
    enrollment_no VARCHAR(60) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    department VARCHAR(120),
    enrollment_year INTEGER CHECK (enrollment_year BETWEEN 1900 AND 2200),
    date_of_birth DATE,
    gender VARCHAR(30),
    phone VARCHAR(32),
    address JSONB NOT NULL DEFAULT '{}'::jsonb,
    medical_notes_encrypted TEXT,
    admission_status VARCHAR(20) NOT NULL DEFAULT 'APPLIED'
        CHECK (admission_status IN ('APPLIED', 'UNDER_REVIEW', 'ADMITTED', 'ENROLLED', 'REJECTED', 'WITHDRAWN')),
    admission_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, enrollment_no),
    FOREIGN KEY (tenant_id, campus_id) REFERENCES campuses(tenant_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_students_tenant_name ON students (tenant_id, last_name, first_name);
CREATE INDEX ix_students_tenant_email ON students (tenant_id, lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX ux_students_tenant_email ON students (tenant_id, lower(email)) WHERE email IS NOT NULL;
CREATE INDEX ix_students_admission_status ON students (tenant_id, admission_status);

CREATE TABLE guardians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    full_name VARCHAR(180) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE student_guardians (
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    guardian_id UUID NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
    relationship VARCHAR(40) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (student_id, guardian_id)
);
CREATE INDEX ix_student_guardians_guardian ON student_guardians (guardian_id);

CREATE TABLE academic_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED')),
    CHECK (ends_on > starts_on),
    UNIQUE (tenant_id, name)
);
CREATE TABLE courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(180) NOT NULL,
    credits NUMERIC(4,1) NOT NULL DEFAULT 0 CHECK (credits >= 0),
    UNIQUE (tenant_id, code)
);
CREATE TABLE class_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    campus_id UUID NOT NULL,
    academic_period_id UUID NOT NULL REFERENCES academic_periods(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    grade_level VARCHAR(40) NOT NULL,
    room VARCHAR(80),
    UNIQUE (tenant_id, campus_id, academic_period_id, name),
    FOREIGN KEY (tenant_id, campus_id) REFERENCES campuses(tenant_id, id) ON DELETE RESTRICT
);
CREATE TABLE enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_section_id UUID NOT NULL REFERENCES class_sections(id) ON DELETE RESTRICT,
    enrolled_on DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN')),
    UNIQUE (student_id, class_section_id)
);
CREATE INDEX ix_enrollments_section_status ON enrollments (class_section_id, status);

CREATE TABLE attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_section_id UUID NOT NULL REFERENCES class_sections(id) ON DELETE CASCADE,
    course_id UUID REFERENCES courses(id) ON DELETE SET NULL,
    teacher_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_attendance_sessions_section_date ON attendance_sessions (class_section_id, starts_at);
CREATE TABLE attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL', 'RFID', 'BIOMETRIC', 'IMPORT')),
    marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, student_id)
);
CREATE INDEX ix_attendance_records_student_status ON attendance_records (student_id, status);

CREATE TABLE leave_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'TEACHER_APPROVED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_on >= starts_on)
);
CREATE INDEX ix_leave_requests_student_status ON leave_requests (student_id, status);
CREATE TABLE leave_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_request_id UUID NOT NULL REFERENCES leave_requests(id) ON DELETE CASCADE,
    approver_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    stage SMALLINT NOT NULL CHECK (stage IN (1, 2)),
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    comment TEXT,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (leave_request_id, stage)
);

CREATE TABLE assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_section_id UUID NOT NULL REFERENCES class_sections(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    teacher_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    due_at TIMESTAMPTZ NOT NULL,
    max_score NUMERIC(7,2) NOT NULL CHECK (max_score > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_assignments_section_due ON assignments (class_section_id, due_at);
CREATE TABLE assignment_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    attachment_id UUID,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    score NUMERIC(7,2),
    feedback TEXT,
    plagiarism_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED'
        CHECK (plagiarism_status IN ('NOT_CHECKED', 'QUEUED', 'COMPLETE', 'FAILED')),
    UNIQUE (assignment_id, student_id)
);
CREATE INDEX ix_submissions_student ON assignment_submissions (student_id, submitted_at DESC);

CREATE TABLE exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_section_id UUID NOT NULL REFERENCES class_sections(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    title VARCHAR(180) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    max_score NUMERIC(7,2) NOT NULL CHECK (max_score > 0),
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_exams_section_starts ON exams (class_section_id, starts_at);
CREATE TABLE exam_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    score NUMERIC(7,2) NOT NULL CHECK (score >= 0),
    grade VARCHAR(8),
    remarks TEXT,
    UNIQUE (exam_id, student_id)
);
CREATE INDEX ix_exam_results_student ON exam_results (student_id, exam_id);

CREATE TABLE timetable_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_section_id UUID NOT NULL REFERENCES class_sections(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    teacher_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    room VARCHAR(80) NOT NULL,
    weekday SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    CHECK (ends_at > starts_at)
);
CREATE INDEX ix_timetable_teacher ON timetable_slots (teacher_user_id, weekday, starts_at);
CREATE INDEX ix_timetable_room ON timetable_slots (room, weekday, starts_at);

CREATE TABLE fee_structures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    academic_period_id UUID NOT NULL REFERENCES academic_periods(id) ON DELETE RESTRICT,
    grade_level VARCHAR(40) NOT NULL,
    fee_name VARCHAR(120) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    due_on DATE NOT NULL
);
CREATE INDEX ix_fee_structures_period_grade ON fee_structures (academic_period_id, grade_level);
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    invoice_no VARCHAR(60) NOT NULL UNIQUE,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    penalty NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (penalty >= 0),
    total NUMERIC(12,2) GENERATED ALWAYS AS (subtotal + penalty) STORED,
    due_on DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'VOID', 'OVERDUE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_invoices_student_status_due ON invoices (student_id, status, due_on);
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    provider VARCHAR(30) NOT NULL CHECK (provider IN ('MANUAL', 'RAZORPAY', 'STRIPE')),
    provider_reference VARCHAR(180),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('INITIATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_invoice_status ON payments (invoice_id, status);

CREATE TABLE announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    audience VARCHAR(20) NOT NULL DEFAULT 'ALL'
        CHECK (audience IN ('ALL', 'TEACHERS', 'STUDENTS', 'PARENTS')),
    publish_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_announcements_tenant_publish ON announcements (tenant_id, publish_at DESC);
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP'
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'READ')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, created_at DESC)
    WHERE status <> 'READ';

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, user_id)
);
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_messages_conversation_time ON messages (conversation_id, created_at DESC);

CREATE TABLE attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    storage_key TEXT NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    checksum_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE assignment_submissions
    ADD CONSTRAINT fk_submission_attachment
    FOREIGN KEY (attachment_id) REFERENCES attachments(id) ON DELETE SET NULL;

CREATE TABLE audit_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
    actor_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_tenant_time ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_actor_time ON audit_events (actor_user_id, occurred_at DESC);
