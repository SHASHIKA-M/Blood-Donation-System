# BloodConnect — Blood Donation Management System

Real-world style blood donation platform (Spring Boot 3 + Thymeleaf + SQL Server):
donor journey (register → book → donate → history), QA screening, compatible-stock
reservation, approval, cold-chain dispatch, role portals, audit trail.

## Quick start (IntelliJ IDEA)

1. Open this folder in IntelliJ → Trust Project (Maven auto-imports)
2. Project SDK = **Java 17**
3. SQL Server running with `Blood_Connect` database
4. Run dropdown → **BloodDonationApplication** → ▶ Run
5. Open http://localhost:8088

CLI: `run.bat` (or `mvn spring-boot:run`)

## Roles & demo logins (password `pass123`, admin `admin123`)

| Role | Username | Portal |
|---|---|---|
| Donor | name = username, phone = password | `/portal/donor` |
| Recipient (staff) | `recipient` | `/portal/recipient` |
| Hospital Coordinator | `hospital` | `/portal/hospital` |
| QA Officer | `qa` | `/portal/qa` |
| Delivery Officer | `delivery` | `/portal/delivery` |
| Blood Bank Manager | `manager` | `/portal/manager` |
| IT Admin | `itadmin` | `/portal/it`, `/admin/dashboard` |

Per-role details: [`docs/roles/`](docs/roles/) (controller + template + flow per role).

## Blood lifecycle

`register → collect → Pending Screening → QA screen → Approved → approve(reserve) → schedule → Delivered`
120-day donor gap, ABO-compatible matching, FEFO, nightly expiry sweep.

## Photography

Public-facing pages use real clinical/community photography instead of illustrations.
Images live in `src/main/resources/static/images/photos/` (served at `/images/photos/*.jpg`),
sourced from **Pexels** (free for commercial use, no attribution required, no watermarks).

| File | Subject | Used on |
|---|---|---|
| `hero-donation.jpg` | Donor giving blood on a donation bed | Home hero, OG image, search hero |
| `donor-thumbs-up.jpg` | Smiling donor after donating | Hero overlay, donor profile |
| `nurse-donor.jpg` | Nurse drawing blood from donor | Home hero overlay, login split |
| `donor-arm-bags.jpg` | Donor arm + collection bag | About, donation process |
| `health-check.jpg` | Blood-pressure check | Process step 1, eligibility page |
| `lab-screening.jpg` | Centrifuge / lab screening | Process step 3 |
| `hospital-care.jpg` | Nurse caring for a patient | Process step 4, requests board |
| `nurse-patient.jpg` | Nurse with patient in a ward | Requests board banner |
| `donation-clinic.jpg` | Donors on clinic beds | Home CTA |
| `donor-event.jpg` | Outdoor donation drive | Search hero, register, about |
| `blood-drive-closeup.jpg` | Donor at a medical-centre drive | Find-donor band, profile hero |
| `campaign-community.jpg` | Community drive | Why-donate card |
| `campaign-outdoor.jpg` | Outdoor campaign setup | Campaigns card |
| `campaign-indoor.jpg` | Indoor charity drive | Campaigns card, register hero |
| `blood-bags.jpg` / `blood-bags-labels.jpg` | Blood bags in storage | Stock hero, cold-chain banner |
| `blood-sample-tube.jpg` | Blood sample for screening | Why-donate card |
| `medical-team.jpg` | Medical team | About hero, about strip |
| `volunteers.jpg` | Healthcare worker + volunteers | Contact hero, about strip |
| `collection-monitor.jpg` | Collection monitor | Reserved for QA views |

Styling classes live at the end of `static/css/style.css`: `.photo`, `.hero-media`,
`.hero-photo--*`, `.photo-card`, `.process-grid`, `.banner-photo`, `.photo-strip`,
`.card-photo-head`, `.login-split`. Bump the `style.css?v=` query in `layout.html`
when the stylesheet changes (static assets are cached for 365 days).

## Codebase map (role-wise)

- `controller/portal/` — **one controller per role** (`Donor/Recipient/Hospital/Qa/Delivery/Manager/ItPortalController` + shared `BasePortalController`)
- `templates/portal/*.html` — matching view per role
- `controller/WebController.java` — public site, `AdminController.java` — legacy admin
- `service/` — business rules, `model/` + `repository/` — JPA entities
