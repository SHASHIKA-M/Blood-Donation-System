# Blood Bank Manager (role `BLOOD_BANK_MANAGER`)

- Controller: `controller/portal/ManagerPortalController.java`
- View: `templates/portal/manager.html`
- Demo: `manager / pass123` → `/portal/manager`

## Endpoints
- `GET /portal/manager?branch=` — stock dashboard, low-stock alerts, approvals, appointments
- `POST /portal/manager/stock/add` — intake batch → Pending Screening
- `POST /portal/manager/stock/adjust` — +intake / −FEFO write-off (≤100)
- `POST /portal/manager/collect` — collect from donor dropdown (eligible only)
- `POST /portal/manager/appointments/{id}/status` — Completed/Cancelled
- `POST /portal/manager/approve/{id}` — locks compatible units (Reserved)
- `POST /portal/manager/reject/{id}`, `POST /portal/manager/request/{id}/delete`
