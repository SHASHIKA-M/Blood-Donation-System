# Delivery Officer (role `DELIVERY_OFFICER`)

- Controller: `controller/portal/DeliveryPortalController.java`
- View: `templates/portal/delivery.html`
- Demo: `delivery / pass123` → `/portal/delivery`

## Endpoints
- Fleet: `POST /portal/delivery/vehicles[/{id}/status|/{id}/delete]`, same for `/couriers`
- `POST /portal/delivery/schedule` — Approved request + courier + vehicle + time → Dispatch Scheduled (units auto-reserved)
- `POST /portal/delivery/{id}/status` — Delivered consumes reserved units; Cancelled keeps reservation
- `POST /portal/delivery/{id}/cancel` — back to Approved pool
