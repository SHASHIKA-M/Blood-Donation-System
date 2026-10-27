# IT Admin (role `IT_ADMIN`)

- Controller: `controller/portal/ItPortalController.java` (+ legacy `AdminController` `/admin/*`)
- Views: `templates/portal/it.html`, `templates/admin/*.html`
- Demo: `itadmin / pass123`, `admin / admin123` → `/portal/it`

## Endpoints
- `GET /portal/it` — RBAC matrix, staff/donor user tables, audit log
- `POST /portal/it/permissions` — tick-box scopes per role
- `POST /portal/it/users[/{id}/toggle-status|/{id}/edit|/{id}/delete]`
- Shared guards live in `controller/portal/BasePortalController.java`
  (`requireRole`, `requireDonor`); access errors → `config/PortalExceptionHandler.java`
