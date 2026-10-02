# Student-management-system-

Student Management System built with Spring Boot, React, Vite, and PostgreSQL.

## Production deployment

The Dockerfile builds `frontend/` with Vite, copies the generated `dist/` assets into the Spring Boot classpath at `src/main/resources/static/`, then packages the backend and UI into one runnable image. Configure Render to use the repository's Dockerfile; do not manually commit `frontend/dist/` or generated assets.

The application serves the UI and API from the same origin: browser routes are forwarded to `index.html`, static assets are served directly, and `/api/**` remains protected by bearer-token authentication. Vite uses `/api` as the default API base URL, so no CORS configuration or public backend URL is needed for the integrated deployment.

For local frontend development, run `npm ci` and `npm run dev` from `frontend/`; Vite proxies `/api` to `http://localhost:8080`. To build locally, run `npm run build` in `frontend/`. The resulting `frontend/dist/` is ignored by Git and is packaged automatically by Docker.

Set these Render environment variables before deploying:

- `DATABASE_URL` and `DATABASE_USERNAME` / `DATABASE_PASSWORD` for PostgreSQL.
- `SMS_JWT_SECRET` to a secure random secret of at least 32 bytes.

The first school tenant and its administrator are created through `POST /api/auth/register`; there are no tenant/admin bootstrap environment variables or provisioning queries on application startup. Since registration is public, configure request rate limits and abuse monitoring at the deployment edge. After registration, sign in is immediate; add a campus under Settings before creating student records.

See [BACKEND_AUTH.md](BACKEND_AUTH.md) for authentication behavior, API contracts, role permissions, and remaining schema areas without CRUD endpoints. `VITE_TENANT_ID` is intentionally not used: tenant scope is determined by the authenticated token, not by a client-controlled environment value.
