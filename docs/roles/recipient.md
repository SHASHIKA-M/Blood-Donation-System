# Recipient — staff (role `RECIPIENT`, display "Recipient / Patient")

- Controller: `controller/portal/RecipientPortalController.java`
- View: `templates/portal/recipient.html`
- Demo: `recipient / pass123` → `/portal/recipient`
- Donor-linked accounts are bounced to `/portal/donor`.

## Endpoints
- `GET /portal/recipient?bloodGroup=` — available stock + request board
- `POST /portal/recipient/request` — submit (forced `Urgent`)
- `POST /portal/recipient/request/{id}/edit` — blocked once Delivered/Dispatch Scheduled
- `POST /portal/recipient/request/{id}/delete` — same block
