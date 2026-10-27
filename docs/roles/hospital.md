# Hospital Coordinator (role `HOSPITAL_COORDINATOR`)

- Controller: `controller/portal/HospitalPortalController.java`
- View: `templates/portal/hospital.html`
- Demo: `hospital / pass123` → `/portal/hospital`

## Endpoints
- `GET /portal/hospital` — hospital requisitions + dispatch tracking
- `POST /portal/hospital/requisition` — submit (hospital auto-filled, availability shown)
- `POST /portal/hospital/requisition/{id}/edit|delete`
- `GET /portal/hospital/check-availability?bloodGroup&component` — plain-text count (AJAX)
