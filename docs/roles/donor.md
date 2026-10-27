# Donor (role `DONOR`, display "Donor")

- Controller: `controller/portal/DonorPortalController.java`
- View: `templates/donor-profile.html` (shared public profile + owner dashboard)
- Account: auto-created at registration — username = lowercase name, password = phone digits
- Home: `GET /portal/donor` (login lands here)

## Endpoints
- `GET /portal/donor` — own profile + eligibility + appointments
- `POST /portal/donor/appointments` — book (branch, scheduledAt, notes?) — 120-day gap enforced
- `POST /portal/donor/appointments/{id}/cancel` — cancel own
- Public profile edit/log: `POST /donor/{id}/edit`, `POST /donor/{id}/donations` (owner-or-IT only)

## Demo flow
Register → note credentials → login → dashboard → book appointment → manager collects →
QA screens → history grows.
