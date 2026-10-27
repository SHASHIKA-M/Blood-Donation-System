# QA Officer (role `QA_OFFICER`)

- Controller: `controller/portal/QaPortalController.java`
- View: `templates/portal/qa.html`
- Demo: `qa / pass123` → `/portal/qa`

## Endpoints
- `GET /portal/qa?unitId=` — dropdown lists **Pending Screening only**; history table lists all
- `POST /portal/qa/screening` — hiv/hepB/hepC/syphilis/malaria (unchecked = reactive) → Approved or Rejected
- `POST /portal/qa/unit/{id}/discard?reason=` — Discarded
- `POST /portal/qa/unit/{id}/notes` — QA notes
