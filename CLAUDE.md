# CLAUDE.md

## Project: OtchenashHair Warehouse
Inventory management for a trichology salon.

## Tech stack
Java 21, Spring Boot 3.4.5, Thymeleaf, PostgreSQL, Liquibase,
Spring Security, Lombok, DevTools, Spring Data JPA, JPA Specifications.

## Package: com.hairmony.warehouse

## Architecture
domain/ — JPA entities (category, client, product, stock, supplier, user, visit, scalp, followup, appointment, gift)
repository/ — Spring Data JPA + JpaSpecificationExecutor for movements
service/ — business logic (ProductService, StockService, ClientService,
           SupplierService, CategoryService, MovementHistoryService,
           ReportService, AiAssistantService, UserService, UserDetailsServiceImpl,
           TelegramService, ReminderService)
web/controller/ — MVC controllers (thin):
  DashboardController, ProductController, ClientController,
  SupplierController, CategoryController, StockController (income/expense/cancel),
  StockItemController (/stock/items/{id}/edit), MovementController (/movements/history),
  ProfileController, LoginController, ReportController, AiController,
  TelegramWebhookController (POST /telegram/webhook),
  ReminderController (GET /internal/reminders),
  GlobalExceptionHandler (@ControllerAdvice)
clientcare/web/controller/ — ClientCare controllers:
  ClientCareController, ClientCareFollowupController, FollowUpActionController,
  AppointmentController, ScalpPhotoController, DriveFolderController, VisitController,
  GiftCertificateController (/clientcare/gift-certificates),
  ClientCareFinanceController (/clientcare/finance),
  ClientTelegramController (/clientcare/clients/{id}/telegram)
web/dto/ — form objects and filter DTOs
web/validator/ — custom Bean Validation annotations (ValidDateRange + DateRangeValidator)
web/interceptor/ — CurrentUriInterceptor
web/formatter/ — QuantityFormatter (@qf bean)
config/ — SecurityConfig, LocaleConfig, WebMvcConfig, TelegramConfig

## Key Rules
- ddl-auto=none, Liquibase manages schema (migrations 001-028)
- Controllers are thin, logic in services
- Never pass entities to templates, use DTOs
- Dirty checking for updates — no explicit save() on managed entities
- Credentials in application-dev.properties (gitignored)
- spring.profiles.active=dev (via VM options in IDE: -Dspring.profiles.active=dev)
- Production profile: application-prod.properties

## Validation architecture
All DTOs use Jakarta Bean Validation. Rules:

**DTO-level constraints (fail fast):**
- `@NotBlank` / `@NotNull` on required fields
- `@Size(max=N)` on every String field — must match DB column length
- `@DecimalMin` on all numeric fields (quantity > 0.001, price > 0, unitSize > 0.001, minStockLevel ≥ 0)
- `@Pattern` on phone (digits/spaces/+/-/()) and URLs (Google Drive)
- `@AssertTrue` for cross-field rules (e.g. isUnitPriceValidForSale, isEndAfterStart, isPasswordsMatch)
- `@ValidDateRange(startField, endField)` — custom class-level annotation in `web/validator/`;
  binds the error directly to the endField node for correct Thymeleaf `th:errors` display

**Service-level guards (defense-in-depth):**
- Stock availability (insufficientStock)
- SALE price > 0 (redundant after DTO @AssertTrue, kept as defense-in-depth)
- purchasePrice > 0 in updateStockItem (IllegalArgumentException)
- purchasePrice >= 0 in registerIncome (0 allowed — free gift/sample; JS confirm before submit)
- movementType guard in registerExpense — blocks PURCHASE/CANCELLATION types
- Movement cancellation guards (double-cancel, partially used batch)

**Controller-level:**
- `@Valid` on all @ModelAttribute DTO parameters
- BindingResult always checked before service call
- Open redirect protection: `returnTo` params validated via `safeRedirect()` —
  only accepts `/[^/].*` pattern (blocks `//evil.com`, absolute URLs)
- `deactivationReason` length check before service call (max 200 chars)
- `@Validated` + `@Min(1) @Max(365)` on snooze `days` param in FollowUpActionController
- `safeRedirect(returnTo, fallback)` applied in:
  - FollowUpActionController — all POST methods (addNote, snooze, markDone, undoDone, returnToQueue)
  - StockController — POST income, POST expense, editMovement
  - ClientController — after create/edit from context (returnTo=detail or returnTo=followups)
  - Pattern: `^/[^/].*` — blocks `//evil.com`, `https://...`, relative `../`

**StockItemController validation:**
- `updateStockItem()` validates purchasePrice > 0; throws `IllegalArgumentException` if <= 0

**Product deactivation validation:**
- `deactivationReason` max 200 chars (matches DB VARCHAR(200)); controller validates before service call

**GlobalExceptionHandler (@ControllerAdvice):**
- `EntityNotFoundException` → redirect "/" with errorMessage flash
- `DataIntegrityViolationException` → redirect "/" with user-friendly message (covers DB truncation, unique violation)
- `ConstraintViolationException` → redirect "/" with violation message (from @Validated @RequestParam)
- `MethodArgumentTypeMismatchException` → redirect "/" with param name

**Flash messages:** always resolved via `messageSource.getMessage(...)` — no hardcoded strings in controllers.

## DB
Local: hairmony_dev, user: warehouse_user
Production: Neon PostgreSQL (credentials via Fly.io secrets)
Migrations:
001-users, 002-suppliers, 003-clients, 004-products, 005-stock-items, 006-stock-movements,
007-categories, 008-fix-categories, 009-insert-admin-user,
010-add-movement-cancel (original_movement_id FK on stock_movements),
011-add-writeoff-reason (write_off_reason VARCHAR(30) on stock_movements),
012-create-scalp-photos (scalp_photos + index on client_id),
013-create-visits (visits + index on client_id),
014-add-client-drive-folder (drive_folder_url + drive_folder_id on clients),
015-create-follow-ups (follow_ups table),
016-add-product-deactivation (deactivated_at + deactivation_reason on products),
017-create-appointments (appointments + indexes on start_at, client_id;
  CHECK: client_id IS NOT NULL OR NULLIF(TRIM(guest_name),'') IS NOT NULL),
018-add-next-appointment-to-visits (next_appointment_id BIGINT FK → appointments ON DELETE SET NULL),
019-create-services (services table),
020-create-gift-certificates (gift_certificates + indexes on purchaser/recipient/status),
021-add-visit-billing (service_id, price_at_time, payment_method, is_paid, certificate_code on visits),
022-appointment-service-link (drops appointment_type; adds service_id BIGINT FK → services ON DELETE SET NULL),
023-add-gift-certificate-price (price NUMERIC(10,2) NOT NULL DEFAULT 0 on gift_certificates),
024-add-product-recommended-price (recommended_price NUMERIC(10,2) nullable on products),
025-create-salon-services (services table — name, description, duration),
026-add-telegram-client (telegram_chat_id BIGINT nullable + telegram_link_token VARCHAR(64) unique nullable on clients),
027-add-appointment-reminders (reminder_48h_sent_at, reminder_24h_sent_at, reminder_2h_sent_at TIMESTAMP nullable on appointments),
028-remove-guest-appointments (drops guest_name, guest_phone from appointments; sets client_id NOT NULL),
029-create-calendar-tasks (calendar_tasks table: id, task_date DATE, client_id FK, appointment_id FK, text VARCHAR(500), is_done BOOL, created_at)

## Warehouse features
- **Dashboard** (/) — KPI cards (All/OK/LOW/OUT), status/category/brand/search filters, clickable rows; `.stock-table` with table-layout:fixed
- **Products** — CRUD + soft deactivate/restore + brand autocomplete; StockItem batch edit modal (expiry, batch number, price); `recommendedPrice` field (nullable NUMERIC(10,2)) — shown on detail page, auto-fills sale price on expense form when SALE selected; "Витрата" button disabled (with tooltip) when `currentQuantity <= 0`; hard delete if no stock/movement references (`isDeletable()`, trash icon on detail header)
  - **New product form** progressive unlock (`syncProductFormLock()`): fields unlock sequentially as required fields filled — Name → Category → Unit → Unit Size → Min Stock → Description
  - **Barcode uniqueness** pre-validated in `ProductService.validateBarcodeUnique()` before DB insert/update; empty string normalized to `null` via `trimOrNull()`; `CategoryRepository.getReferenceById()` used to avoid `TransientObjectException` when binding category from form
- **Categories** — CRUD, guarded delete (disabled with tooltip if has products assigned)
- **Suppliers** — CRUD + detail page with purchase history; guarded delete
- **Clients** — CRUD + detail page with transaction history; guarded delete
- **Stock income** (/movements/income) — FIFO batches, barcode AJAX, create-supplier round-trip; compact mobile form with purchase total (qty × price); `returnTo` support (back to dashboard or product detail); all fields locked until product selected (`syncFieldsLock()`); submit button disabled until price field is filled (value `!== ''`, including 0); zero price allowed with JS `confirm()` before submit (`stock.income.zeroPriceConfirm`); on product change quantity is cleared
  - **Last purchase price / supplier pre-fill**: `StockMovementRepository.findLastPurchasePricePerProduct()` and `findLastSupplierPerProduct()` — both exclude cancelled PURCHASEs via `NOT EXISTS (CANCELLATION with originalMovementId = m.id)`; price uses `StockMovement.unitPrice` (not StockItem) to avoid false zeros from sold-out batches
- **Stock expense** (/movements/expense) — SALE/WRITE_OFF/ADJUSTMENT; below-cost JS warning + confirm(); create-client round-trip; WriteOffReason: GIFT/EXPIRED/DAMAGED/SAMPLE/INTERNAL_USE/OTHER; `returnTo` support; all fields + movement type locked until product selected; barcode scanner icon (`<a id="scannerLink">`) navigates to `/scan?mode=expense` with clientId/returnTo context
  - **productId pre-selected** (from dashboard/product detail): product shown as locked gray div (`field-locked`), hidden input for submit, hidden `<select id="productSelect">` (no `ts-client`) retains `data-*` attrs for JS; `lockedProductName` passed from controller; `syncFieldsLock()` detects via `input[type=hidden][name=productId]`
  - **clientId pre-selected** (`clientLocked=true`): separate layout block with client locked, movementType forced to SALE
  - **Recommended price auto-fill**: `data-rec-price` on each product option; fills `unitPriceInput` on SALE type select (in `toggleTypeFields()`) and on product change (`updateStockHint()`); on product change always syncs — sets rec price or clears if none
- **Barcode scanner** (/scan) — camera + manual, income/expense mode; accepts `clientId` + `returnTo` URL params, appended to expense redirect after scan (`getStockUrl()`, `updateManualLink()`)
- **Movement journal** (/movements/history) — JPA Spec server-side filtering + pagination (100/page); debounced auto-submit; clickable product/client/supplier links (`from=history` pattern)
- **Movement cancellation** — reverses PURCHASE/SALE/WRITE_OFF/ADJUSTMENT; guards: double-cancel, partially used batch
- **Reports** (/reports) — period presets, KPI banner with previous-period deltas, top sales/clients, margin analysis, slow movers; Trends chart at /reports/trends (Chart.js 4)
- **AI Assistant** (/ai) — Gemini 2.5 Flash, warehouse context, AJAX chat; `GEMINI_API_KEY` env var
- **i18n** — uk (primary), pl, en; all strings via `#{}` and `messageSource.getMessage()`; no hardcoded text in controllers
  - **CRITICAL**: `messages_uk.properties` and `messages_pl.properties` store Cyrillic as `\uXXXX` escape sequences. Never save these files with raw UTF-8 bytes (e.g. from an editor that converts escapes) — Spring reads `.properties` as ISO-8859-1 by default, raw UTF-8 Cyrillic will render as `ÐÐ¾ÐºÑÐ¿ÐºÐ¸`. Always edit via Claude Code tools or add keys manually as `\uXXXX`.
- **PWA** — manifest.webmanifest, icons, no service worker

## Color palette

### CSS variables (defined in both `layout/main.html` and `layout/clientcare.html` `:root`)
| Variable          | Value     | Usage                                        |
|-------------------|-----------|----------------------------------------------|
| `--accent`        | `#4a7c59` | Primary brand green — buttons, active nav, links, badges |
| `--accent-light`  | `#e8f5e9` | Hover bg on table rows, card headers (clientcare), filter active border highlight bg |
| `--sidebar-bg`    | `#1e2d24` | Sidebar / mobile topbar background           |
| `--sidebar-text`  | `#c8d8cb` | Default sidebar link text                    |
| `--sidebar-hover` | `#2e4a35` | Sidebar link hover background                |
| `--sidebar-active`| `#4a7c59` | Active sidebar nav link background (= accent)|

### Page background & chrome
| Element              | Color     |
|----------------------|-----------|
| `body` background    | `#f4f6f4` |
| Page title           | `#1e2d24` |
| Card shadow          | `rgba(0,0,0,0.07)` |
| Card header border   | `#f0f0f0` |
| Sidebar dividers     | `#2e4a35` |
| Lang/pill borders    | `#3a5a42` |
| Nav section label    | `#6b8c74` |
| Brand accent span    | `#8fb59a` |
| Table header text    | `#6c757d` |

### Buttons
| Button               | Color     |
|----------------------|-----------|
| btn-primary bg       | `#4a7c59` (accent) |
| btn-primary hover    | `#3d6b4a` |
| btn-outline-primary  | accent (border + text); hover/active = accent bg, white text |

`btn-outline-primary` is overridden in both layouts via Bootstrap 5 CSS variables (`--bs-btn-color`, `--bs-btn-border-color`, `--bs-btn-hover-*`, `--bs-btn-active-*`, `--bs-btn-focus-shadow-rgb`) — covers all states without flash. Do NOT use `btn-outline-success` for action buttons — use `btn-outline-primary` for consistency.

### Signal / status badges (`.days-*` — defined in `followups.html`, reused inline elsewhere)
| Class / usage              | Background | Text      | Semantic meaning                  |
|----------------------------|------------|-----------|-----------------------------------|
| `.days-urgent`             | `#f8d7da`  | `#842029` | Overdue / danger (Bootstrap danger-subtle) |
| `.days-warn`               | `#fff3cd`  | `#664d03` | Warning / planned (Bootstrap warning-subtle) |
| `.days-ok`                 | `#d1e7dd`  | `#0a3622` | OK / in-progress / done (Bootstrap success-subtle) |
| COMPLETED events / secondary | `#e2e3e5` | `#41464b` | Neutral (Bootstrap secondary-subtle) |
| `.badge-done`              | `#d1e7dd`  | `#0a3622` | Done action badge in followup queue |

### Global CSS classes (defined in both layouts, always available)
| Class              | Background  | Text      | Usage                              |
|--------------------|-------------|-----------|------------------------------------|
| `.badge-writeoff`  | `#f5a3b0`   | `#7d2535` | WRITE_OFF movement type badge (soft pink) |
| `.badge-active`    | `#d4edda`   | `#155724` | Active status badge                |
| `.badge-inactive`  | `#f8d7da`   | `#721c24` | Inactive status badge              |
| `.filter-active`   | —           | —         | `2px solid var(--accent)` border on active filter wrappers |

### Calendar event colors (`appointments/day.html`)
Colors computed by `eventStyle(status, start, end)` — single source of truth used in both
`buildEventContent` (month pill) and `eventDidMount` (timeGrid wrapper).
`eventColor()` returns the saturated dot color for mobile month view.

| State              | Background | Border     | Text      |
|--------------------|------------|------------|-----------|
| PLANNED            | `#fff3cd`  | `#ffe69c`  | `#664d03` |
| CONFIRMED          | `#d1e7dd`  | `#a3cfbb`  | `#0a3622` |
| COMPLETED          | `#e2e3e5`  | `#c4c8cb`  | `#41464b` |
| CANCELLED          | `#f5e6f0`  | `#d4b8d4`  | `#6b4c6b` |
| NO_SHOW            | `#fd7e14`  | `#fd7e14`  | `#fff`    |
| Overdue (PLANNED/CONFIRMED + past end)       | `#f8d7da` | `#f1aeb5` | `#842029` |
| In-progress (PLANNED/CONFIRMED + now inside) | `#d1e7dd` | `#a3cfbb` | `#0a3622` |

Note: CONFIRMED intentionally uses the same palette as In-progress (confirmed ≠ attended).
Month view desktop pill gets background applied directly on `.fc-month-pill` (not only on outer FC wrapper)
to avoid `.fc-daygrid-dot-event` transparent-background issue.

### Client detail appointment badges (inline style in `fragments/client-detail.html`)
| Badge              | Background | Text      |
|--------------------|------------|-----------|
| Upcoming appt      | `#d1e7dd`  | `#0a3622` |
| Overdue appt       | `#f8d7da`  | `#842029` |

### Dashboard KPI cards (inline style in `clientcare/dashboard.html`)
| Card                  | Background | Text      |
|-----------------------|------------|-----------|
| Записані клієнти      | `#fff3cd`  | `#664d03` |
| Пропущені записи      | `#f8d7da`  | `#842029` |
| Не прийшли            | `#fff0e6`  | `#7d3c00` |

## UX patterns (apply consistently)
- **Client select with search (TomSelect):** Any `<select>` used to pick a client from the database
  must have class `ts-client` — TomSelect is initialized globally in both `layout/main.html` and
  `layout/clientcare.html` for all `select.ts-client` elements (CDN tom-select@2.3.1, Bootstrap 5 skin,
  `allowEmptyOption: true`). When clearing a TomSelect programmatically use `el.tomselect.clear()`, not `el.value = ''`.
- Confirmation modals use btn.yes / btn.no ("Так" / "Ні") — not action-named buttons
- Delete buttons: active (btn-outline-danger + modal) when deletable;
  disabled (btn-outline-secondary + tooltip with reason) when guarded
- Guard pattern: service loads entity, checks constraint, throws IllegalStateException
  with i18n message; controller catches and puts in errorMessage flash attribute
- Deletion guard set loaded in controller.list() via getXxxIdsWithYyy() service method,
  passed to model as "xxxWithYyy" (e.g. suppliersWithMovements, categoriesWithProducts)
- Create-from-related-page pattern: GET/POST /entity/new?returnTo=page,
  on success redirect to /related/page?entityId=savedId,
  related page GET accepts entityId param and pre-fills DTO;
  form page shows "Створити новий X →" link under the select with color:var(--accent);
  link href built dynamically via JS so it captures current client-side field values
  (not server-rendered DTO which may be stale);
  "Створити новий X →" link hidden via JS when entity is already selected (syncCreateXxxLink pattern)
- **Edit form action bar:** buttons right-aligned (`justify-content-end`); order: Cancel (btn-outline-secondary btn-sm) → Save (btn-primary btn-sm); Save button starts `disabled`.
  - **Edit forms:** dirty detection via `initialSnapshot` / `getFormSnapshot()` / `syncSaveBtn()` pattern — snapshot taken on `DOMContentLoaded`, Save enabled when current state ≠ snapshot; use `const IS_EDIT = /*[[${entity.id != null}]]*/ false;` with `th:inline="javascript"` to branch logic.
  - **New-entity forms:** Save disabled until all required fields filled — check via JS `syncSaveBtn()` on `input`/`change` events.
  - Disabled Save must NOT look blue: `#saveBtn:disabled { background-color: transparent; border-color: #6c757d; color: #6c757d; opacity: 0.65; }` in page `<style>`.
  - Script must be INSIDE the `th:fragment="content"` div; target form by `id` (not `querySelector('form')` — layout logout form comes first).
- Flash messages: successMessage (alert-success) and errorMessage (alert-danger),
  rendered inline in each template (not in layout); dismissible;
  always resolved through messageSource.getMessage() — no hardcoded strings in controllers
- Informative success messages include entity name in quotes using {0} MessageFormat param
- Bootstrap Tooltip cannot coexist with data-bs-toggle="modal" on same element —
  use native title attribute instead (browser tooltip still shows)
- from=history pattern: links from /movements/history to detail pages
  (/products/{id}, /clients/{id}, /suppliers/{id}) carry ?from=history;
  detail page GET reads @RequestParam(required=false) String from, adds to model;
  Back button: ${from == 'history'} ? @{/movements/history} : @{/default-list}
- returnTo=detail pattern: Edit button on detail page passes ?returnTo=detail;
  editForm GET reads it and passes to model (hidden input in form);
  update POST reads returnTo and redirects to /entity/{id} if "detail", else list;
  Back/Cancel in form template handle returnTo=detail → /entity/{id}
- returnTo redirect safety: all `return "redirect:" + returnTo` must use safeRedirect() pattern —
  accepts only `/[^/].*` (blocks protocol-relative `//host` and absolute URLs)
- Detail pages layout: info strip (d-flex flex-wrap gap-4) above history table,
  phone/contact in page header subtitle, no duplicate data
- Product detail page exception: desktop uses info strip (d-none d-md-block) + full-width tables;
  mobile uses original table-in-card layout (d-md-none) — two separate blocks in template;
  stock level shown prominently in info strip with color: green(OK)/yellow(LOW)/red(OUT)
- Mobile tables: hide secondary columns with d-none d-md-table-cell;
  keep essential columns (name, status/type, quantity, actions) always visible
- Page-header add button (mobile): icon only on mobile, text hidden via `d-none d-md-inline ms-1` on the `<span>`.
  page-header stays in row layout on all screen sizes (title left, button right via justify-content:space-between).
  Pattern: `<i class="bi bi-plus-lg"></i><span class="d-none d-md-inline ms-1" th:text="...">Label</span>`
- **Back button (mobile):** text hidden on mobile via `d-none d-sm-inline` on the text `<span>` — only the
  `←` arrow icon shows on narrow screens. Applied globally to all templates with `bi-arrow-left`.
  Pattern: `<i class="bi bi-arrow-left me-1"></i><span class="d-none d-sm-inline" th:text="#{btn.back}">Назад</span>`
- Filter UX pattern (client detail, product detail, supplier detail): filter bar with
  Row 1 = від/до date range (two input-group side by side, id=dateFromWrap/dateToWrap);
  Row 2 = type select + gift button (client detail only) / product name search (supplier detail);
  Row 3 = product/client name search (client detail only);
  filter-select-wrap for selects with × button (d-md-none, style=display:none, shown via JS);
  input-group for date/search with × button (d-md-none); filter-active class on wrapper div
  (not on select/input itself) for green border; "Скинути (N)" button below filters, visible
  when ≥1 active; all via updateResetBtn() JS; date inputs use showPicker() onclick for mobile;
  data-date="yyyy-MM-dd" on each <tr> for client-side date range filtering
- **Date range validation** (`syncDateRange(changed)`): applied on all від/до filter pairs — sets `min`/`max` on inputs and clears conflicting value if `to < from`; server-side swap (`if (to.isBefore(from)) swap`) in `VisitController`, `MovementController`, `ReportController`, `ClientCareFinanceController`; pattern applied in: `client-detail.html`, `products/detail.html`, `suppliers/detail.html`, `journal.html`, `history.html`, `reports.html`, `trends.html`, `finance.html`
- Client detail quick filter "Подарунки": toggle button in same row as type select;
  when active — filter-active border, × shown inside button text, type select disabled;
  data-label attr holds i18n text (btn.quickFilter.gifts); blur() on toggle to avoid focus gray;
  counted in reset button; i18n: uk=Подарунки, pl=Prezenty, en=Gifts
- badge-writeoff (.badge-writeoff), filter-select-wrap, and .btn-square CSS are global in layout/main.html;
  .btn-square — mobile-only square icon button (2rem×2rem), defined in page @media block, not global
- Supplier detail: Тип операції column removed; filter bar with від/до dates + product name search; .table { min-width: 0 }
- Dashboard clickable rows pass from=dashboard; product detail Back button handles it → /
- Movement journal thead: accent-light via --bs-table-bg; th vertical-align: middle
- Table styles (detail pages + reports): .table th font-size 0.78rem, vertical-align middle;
  mobile: 0.75rem for th, 0.9rem for td; table-striped on history tables;
  .table-xs class for extra-compact rows (padding 0.2rem 0.5rem) — used on stock batches table;
  .table-auto resets font-size to inherit (used on product info table to keep default size)
- Date filter labels від/до use #{movements.filter.dateFrom} / #{movements.filter.dateTo}
  across all three detail pages — no hardcoded text
- **Compact horizontal form layout** (`compact-fields`): label|input pairs stacked vertically on mobile
  using `<div class="row gy-2 gx-0 align-items-center compact-fields">` with `col-4 col-form-label fw-semibold`
  label + `col-8` input column. CSS: `.compact-fields .form-control { text-align: right }`.
  Conditionally visible rows (e.g. price, client, write-off reason) have `id` on both the label and the
  input wrapper so JS can toggle them independently (`el.style.display`).
  Full-width alerts inside the row (e.g. below-cost warning) use `<div class="col-12">` inside the same
  `compact-fields` row — placed after the relevant field pair so they don't shift label alignment.
- **Mobile clear (×) buttons — two patterns:**
  - *Select:* wrap in `<div class="filter-select-wrap">`, add `<button class="btn btn-outline-secondary btn-sm d-md-none px-1" tabindex="-1" style="display:none">` with `<i class="bi bi-x-lg">` inside; show/hide via JS when value changes.
    TomSelect selects inside `filter-select-wrap` need extra CSS: `.filter-select-wrap > .ts-wrapper { flex: 1; min-width: 0 }` and `.filter-select-wrap .ts-wrapper.has-items .ts-control > input { width: 0 !important; min-width: 0 !important }` to prevent height jump when item selected.
  - *Number/text input:* wrap in `<div class="input-group">`, add same button after the `<input>`.
  - In both cases: button starts `style="display:none"`, shown when field has value, hidden on clear; `syncClearBtns()` called on `input` event and `DOMContentLoaded`.
- **Compact form footnote hint**: when a field hint is too long to place under the `col-8` input (would shift label alignment), add `<span class="text-muted fw-normal"> *</span>` inside the label and place the hint as `<div class="col-12 text-center"><span class="form-text text-muted">* …</span></div>` inside the same `compact-fields` row, directly after the field pair — `gy-2` spacing keeps it visually attached.
- **Locked layout pattern** (context-specific form): when a form is opened from a related entity page
  (e.g. `/movements/expense?clientId=X`), render a separate `th:if="${locked}"` block with pre-filled
  read-only fields (gray `div.form-control`) and hidden inputs for fixed values; omit redundant rows
  (e.g. movement type is always SALE — no need to show it).
- **Fields lock until product selected** (`syncFieldsLock()`): on income and expense forms, all inputs
  (qty, price, type, supplier/client selects, notes, etc.) are disabled until a product is chosen;
  an `alert-warning` hint is injected above `.compact-fields` via JS (created once, shown/hidden);
  `syncFieldsLock()` called from `onProductChange()` and `DOMContentLoaded`; for TomSelect selects
  use `el.tomselect.enable()` / `el.tomselect.disable()`; dependent links (e.g. createSupplierRow)
  also hidden while locked — `syncCreateSupplierLink()` checks `hasProduct` before showing.
- **Barcode input inline layout**: "або" label + barcode `input-group` on same row via
  `<div class="d-flex align-items-center gap-2 mt-2">` with `flex-shrink-0` on the label and
  `flex-grow-1` on the `input-group`.
- **Clickable scanner icon**: in expense form, the barcode `input-group-text` is an `<a id="scannerLink">`
  styled with `color:var(--accent); background:var(--accent-light)` + hover to full accent;
  `syncScannerLink()` builds href as `/scan?mode=expense` + `clientId`/`returnTo` from hidden inputs —
  called on `DOMContentLoaded`; scan.html reads these params via `URLSearchParams` and appends them
  to the post-scan redirect (`getStockUrl()`) and manual link (`updateManualLink()`).

## iOS WebKit patterns (apply consistently)

All iOS browsers (Safari and Chrome) use WebKit. These bugs recur — apply the patterns below whenever adding modals or async form submissions.

- **Modals inside scroll containers freeze:** Never place Bootstrap modals inside an element with `overflow: auto/scroll` or `-webkit-overflow-scrolling: touch`. iOS traps `position: fixed` children, causing the modal to appear dimmed and freeze the page. Always place modals AFTER the closing `</div>` of `.card` / `.card-body`. Already fixed in `appointments/form.html`.

- **`fetch()` + server redirect stalls Promise:** Do not use `fetch()` for form submissions where the server returns a `redirect:`. On iOS WebKit the Promise never resolves, so `.then()` never fires — the UI freezes while the request already succeeded on the server. Use `document.createElement('form')` + `form.submit()` instead (same pattern as `submitUnsnooze()` in `followups.html`):
  ```javascript
  const form = document.createElement('form');
  form.method = 'POST'; form.action = '/some/endpoint';
  const addField = (name, value) => {
      const i = document.createElement('input');
      i.type = 'hidden'; i.name = name; i.value = value;
      form.appendChild(i);
  };
  addField('field', value);
  addField(csrfParam, csrfToken);
  document.body.appendChild(form);
  form.submit();
  ```
  **Thymeleaf gotcha inside `th:inline="javascript"`:** Never use nested array literals `[['key', val], ...]` — Thymeleaf's `[[...]]` inline expression syntax conflicts and throws `TemplateProcessingException`. Use the `addField()` helper pattern above instead.

- **`modal-fullscreen-sm-down` breaks width when keyboard opens:** Bootstrap sets `width: 100vw` on `.modal-dialog`. When the keyboard opens, Bootstrap adds `padding-right` to `<body>` for scrollbar compensation — `100vw` includes that padding → horizontal overflow → broken layout. Fix: override with `width: 100%` (percentage is relative to parent, unaffected by body padding) and add `overflow-x: hidden`. Also use `max-height: 90dvh` (`dvh` = dynamic viewport height, shrinks with keyboard; iOS 15.4+) with `90vh` fallback:
  ```css
  @media (max-width: 575.98px) {
      #myModal .modal-dialog { width: 100%; max-width: 100%; }
      #myModal .modal-content { overflow-x: hidden; }
  }
  ```
  ```html
  <div class="modal-content" style="max-height:90vh; max-height:90dvh;">
  ```

- **Two-step modal chaining freezes page:** Never hide one Bootstrap modal and immediately show another on iOS — the second modal appears but freezes. Use a single modal with two `<div>` steps swapped via `style.display`, plus `overflow-hidden` on `.modal-content`. Reset to step 1 on `hidden.bs.modal`. Applied in `appointments/form.html` cancel modal.

## Stock expense validation
- unitPrice required and > 0 for SALE — validated in StockExpenseDto via @AssertTrue isUnitPriceValidForSale()
  AND service guard (defense-in-depth); message key: stock.expense.salePriceRequired
- writeOffReason required for WRITE_OFF — @AssertTrue isWriteOffReasonRequired(); message key: stock.expense.writeOffReasonRequired
- movementType PURCHASE and CANCELLATION blocked in StockService.registerExpense()
- Below-cost warning (not a hard block): JS compares unitPrice with data-fifo-price on product option;
  shows inline alert-warning; confirm() on submit if below cost
- data-fifo-price from StockItemRepository.findFifoPricePerProduct() — oldest available batch per product
- StockService.getFifoPricesPerProduct() → Map<Long, BigDecimal>, passed to model as fifoPrices
- WriteOffReason set only when movementType == WRITE_OFF (else null)
- Client set for SALE and for WRITE_OFF+GIFT (gift recipient)

## Movement cancellation details
- StockMovement.originalMovementId (Long) links a CANCELLATION back to its source
- StockMovementRepository.existsByOriginalMovementId() — guard for double-cancel
- StockMovementRepository.findCancelledMovementIds(Set<Long>) — @Query returns cancelled IDs on current page
- StockMovementRepository.findAllSupplierIdsWithMovements() / findAllClientIdsWithMovements() — disabled-delete sets
- StockService.cancelMovement() returns CancelResult record: productName, qtyFormatted, unitLabel, newStockFormatted, isPurchase
- Cancel button visible only for non-CANCELLATION, not-yet-cancelled rows (PURCHASE guardable if batch qty equals original qty)

## Reports details
- ReportService.getReportSummary(from, to) — totalRevenue, totalPurchases, grossProfit, marginPct, salesCount
- ReportService.getTopSales / getTopClients / getMarginAnalysis / getWriteOffsSummary / getSlowMovers(from, to)
- ReportService.getTrends(from, to) — monthly labels/revenue/purchases/salesCount; fills all months in range with zeros
- ReportController.resolvePeriod() — shared by /reports and /reports/trends
- Previous-period comparison: previousPeriod() maps THIS_MONTH/LAST_MONTH → prev month, CUSTOM → same duration shifted, ALL_TIME → null;
  delta(): prev=0 + curr>0 → +100%, prev=0 + curr=0 → null (hidden); shown for revenue, purchases, gross profit
- "Тренди" button: local CSS override `.page-header { flex-direction: row !important }` in reports.html + trends.html
  (prevents global mobile column-stack override)

## Security
- DB-based authentication via UserDetailsServiceImpl; BCrypt password encoding
- All routes protected except /login, /logout, static resources (/favicon.svg, /css/**, /js/**, /images/**, /webjars/**)
- `/error` in permitAll — correct error page instead of redirect loop
- `/telegram/webhook` in permitAll — secured by `X-Telegram-Bot-Api-Secret-Token` header in controller (not Spring Security)
- `/internal/reminders` in permitAll — secured by `X-Internal-Token` header in controller (not Spring Security)
- Default user: admin (change password after first login)
- Open redirect protection on all `returnTo` params: `safeRedirect()` accepts only `/[^/].*`

## Deploy
- Production: https://otchenashhair-warehouse.fly.dev/
- Platform: Fly.io (Amsterdam region, 1 shared machine, 512MB RAM)
- Database: Neon PostgreSQL (eu-central-1, Frankfurt)

### JVM memory tuning (Dockerfile)
Machine has 512MB RAM. JVM flags must be explicit — without limits Metaspace and Code Cache grow unbounded and trigger OOM kill.
Current flags: `-Xmx180m -Xms64m -XX:MaxMetaspaceSize=120m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -Xss256k`

Memory budget:
- Heap: 180MB (max)
- Metaspace: 120MB (cap — Spring+Hibernate+Thymeleaf ~80-100MB at runtime)
- Code cache: 64MB (cap — JIT compiled code)
- Thread stacks: ~8MB (256KB × ~30 Tomcat threads)
- JVM native overhead: ~20MB
- **Total: ~390MB**, leaving ~120MB headroom

**OOM root cause (2026-06-11):** GitHub Actions cron woke up suspended machine → cold JVM startup spike (class loading fills Metaspace fast) → OOM kill at 8s after start. Capped Metaspace + SerialGC (no G1GC region pre-allocation) + lower `-Xms` fixes the startup peak.
If `OutOfMemoryError: Metaspace` appears in logs → raise `-XX:MaxMetaspaceSize` to 140m.
- CI/CD: GitHub Actions
  - test.yml — triggers on push to develop and PRs to master; runs ./mvnw test (unit tests only, no DB required)
  - deploy.yml — triggers on push to master; runs unit tests first, then builds Docker image and deploys to Fly.io
  - deploy-on-comment.yml — triggers on PR comment "/deploy" by repo owner, same deploy flow
- Secrets managed via Fly.io secrets:
  | Secret | Description |
  |--------|-------------|
  | `DB_URL` | Neon PostgreSQL JDBC URL |
  | `DB_USERNAME` | DB user |
  | `DB_PASSWORD` | DB password |
  | `SPRING_PROFILES_ACTIVE` | `prod` |
  | `GEMINI_API_KEY` | Google Gemini API key (AI Assistant) |
  | `TELEGRAM_BOT_TOKEN` | Token from @BotFather |
  | `TELEGRAM_BOT_USERNAME` | Bot username without @ |
  | `TELEGRAM_MASTER_CHAT_ID` | Master's personal Telegram chat_id (receives manual reminder alerts) |
  | `TELEGRAM_WEBHOOK_SECRET` | Random string; validated via `X-Telegram-Bot-Api-Secret-Token` header |
  | `INTERNAL_SECRET` | Shared token for `/internal/reminders`; validated via `X-Internal-Token` header |
  | `TELEGRAM_HOW_TO_FIND_VIDEO_ID` | Telegram file_id of "how to find us" video (permanent, free hosting) |
  | `TELEGRAM_CHECKLIST_PHOTO_ID` | Telegram file_id of pre-consultation checklist photo |
- GitHub Secrets: `INTERNAL_SECRET` (used by reminders.yml cron to call `/internal/reminders`; must match Fly.io value)

## Timezone
- Fly.io runs in **UTC** by default; all appointment `LocalDateTime` values are stored in **Europe/Warsaw** (CEST/CET)
- All `LocalDateTime.now()` calls that compare against appointment times **must** use `LocalDateTime.now(ZoneId.of("Europe/Warsaw"))` — plain `LocalDateTime.now()` returns UTC on the server, causing off-by-2h bugs (e.g. "в процесі" banner showing 2 hours after appointment end)
- Affected files: `AppointmentController`, `AppointmentService` — already fixed; any new time comparisons must follow the same rule
- Do NOT rely on JVM default timezone for correctness — always pass `ZoneId.of("Europe/Warsaw")` explicitly

## Local development
Run with VM option: -Dspring.profiles.active=dev
DB credentials in application-dev.properties (gitignored)

---

## OtchenashHair ClientCare — module architecture

### Core rules
- Warehouse URLs unchanged — zero migration risk; ClientCare URLs: `/clientcare/**`
- `/clients` — warehouse client directory (purchase history, stock operations)
- `/clientcare/clients` — ClientCare master client list (full profile, scalp photos)
- Shared domain entities (Client, Product, StockMovement) remain in `domain/` — neither module owns them

### Package structure
```
com.hairmony.warehouse/
  domain/           ← SHARED (entities, repos — unchanged)
  repository/       ← SHARED (unchanged)
  config/           ← SHARED (unchanged)
  web/validator/    ← custom Bean Validation (ValidDateRange, DateRangeValidator)
  clientcare/       ← ClientCare module
    web/controller/
    service/
    web/dto/
```

### Layout and navigation
- `layout/main.html` — warehouse layout + module switcher pills in topbar
- `layout/clientcare.html` — ClientCare layout with sidebar: Огляд / Клієнти / Follow-up черга / Календар / Аналітика
- `CurrentUriInterceptor` — adds `activeModule` (warehouse/clientcare) and `currentUri` to model
- Mobile topbar: `[≡] OtchenashHair  [Склад] [ClientCare]` (pill switcher, active = accent color)
- ClientCare sidebar: `bi-grid` Огляд→`/clientcare`, `bi-people` Клієнти→`/clientcare/clients`,
  `bi-list-check` Follow-up→`/clientcare/followups`, `bi-calendar3` Календар→`/clientcare/appointments`,
  `bi-journal-text` Журнал→`/clientcare/visits`, `bi-bar-chart-line` Фінанси→`/clientcare/finance`

---

## ClientCare — technical notes

### Dashboard `/clientcare`
- `ClientCareDashboardDto`: totalClients, totalClientsInQueue, overdueVisitCount, upcomingVisitCount, unpaidVisitCount, noShowCount + 5 purchase KPI counts
- Section "ЗАПИСИ": three conditional cards (each hidden when count = 0):
  - **Пропущені записи** — past PLANNED/CONFIRMED without upcoming; `KPI_OVERDUE_STATUSES = [PLANNED, CONFIRMED]`
  - **Не прийшли** — NO_SHOW without upcoming appointment AND without a subsequent visit; `KPI_NO_SHOW_STATUSES = [NO_SHOW]`
  - **Записані клієнти** — clients with any future PLANNED/CONFIRMED appointment (renamed from "Заплановані записи")
  - **Очікує оплати** — hidden when 0
- Section "ПОКУПКИ": Кому написати (>30d + phone), Давно не купували (>60d), VIP без активності (top 20% + >30d), Нещодавні (<14d), Повторна покупка (25–40d), Всі клієнти
- Data source: `StockMovementRepository.findClientSaleStats()` — groups SALE by client, excludes cancelled SALEs
- Visit KPI counts from `AppointmentRepository` (not VisitRepository — `next_visit_date` no longer drives queue)

### Follow-up queue `/clientcare/followups`
- `FollowUp` entity: id, client_id, action (DONE/SNOOZE/NOTE), dueDate (nullable), note VARCHAR(500), createdAt
- State derived from latest record: `isActiveSnoozed()` = SNOOZE + dueDate > today; `isRecentlyDone()` = DONE + dueDate > today
- DONE auto-hides 7 days (dueDate = today+7); SNOOZE hides until dueDate
- `returnToQueue()` bulk-deletes all SNOOZE+DONE with future dueDate via `@Modifying @Query`
- Two independent filters (AND logic): `minDays` (purchase days since last sale) + `visitFilter` (overdue/scheduled/none)
  - `FOLLOWUP_OVERDUE_STATUSES = [PLANNED, CONFIRMED, NO_SHOW]` — follow-up queue signal
  - `KPI_OVERDUE_STATUSES = [PLANNED, CONFIRMED]` — dashboard "Пропущені записи"
  - `KPI_NO_SHOW_STATUSES = [NO_SHOW]` — dashboard "Не прийшли" (excludes clients with upcoming appt or subsequent visit)
- Overdue boundary uses `now` (not `todayStart`); same-day past appointments forced to `days = -1`
- Activity badge: `FollowUpRepository.countPerClient()` one query → `Map<Long, Integer>`; `syncActivityCountBadge()` JS
- "Видалити всі записи" always triggers page reload (DONE/SNOOZE deleted → client queue position changes)
- **`submitNote()` (activity modal):** after saving a note, closes the modal + `window.location.reload()` — same pattern as `clearAllActivity()`; Save button disabled immediately on click to prevent double-tap on iOS
- **Follow-up-only clients:** `ClientCareService.getFollowupQueue()` Step 3 — clients with any follow-up record but no purchase or appointment signal (e.g. a client whose only appointment was CANCELLED with "add to follow-up" checked) are added via `FollowUpRepository.findAllDistinctClientIds()`; these appear at priority 3 (same as purchase-only) with no purchase/visit signal badges
- `FollowUpActionController`: `@Validated`, `@Min(1) @Max(365)` on snooze days, `@Size(max=500)` on note; all `returnTo` via `safeRedirect(fallback="/clientcare/followups")`
- **Two-tab layout:** Tab 1 "Товари і записи" (purchase/appointment queue, existing logic); Tab 2 "Без запису" (clients whose latest visit has no linked appointment and no upcoming calendar appointment)
  - Tab nav CSS pattern identical to `client-detail.html` (`#followupTabs`, `flex-shrink-0` on `<li>`, `badge bg-secondary ms-1` count badges hidden when 0)
  - Tab persistence: `sessionStorage('followupTab')` on `shown.bs.tab` event
  - `UnresolvedVisitRowDto` record: `visitId, clientId, clientName, clientPhone, lastVisitDate, daysSinceLastVisit`; loaded in `ClientCareFollowupController.followups()` from `VisitService`
  - `VisitService.getClientsWithUnresolvedNextVisit()` / `getClientsWithSkippedNextVisit()` — delegate to two new `VisitRepository` JPQL queries
  - `VisitRepository.findLatestUnresolvedVisitsPerClient()` — fetches one visit per client (latest by date+createdAt) where `nextVisitSkipped=false AND nextAppointment IS NULL` AND no upcoming PLANNED/CONFIRMED appointment in `appointments` table (`NOT EXISTS` subquery)
  - `VisitRepository.findLatestSkippedVisitsPerClient()` — same "latest visit" logic, `nextVisitSkipped=true`; sorted most-recent-first
  - Tab 2 structure: collapsed card at top ("Відмічено як «Запис не потрібний»") with clickable `card-header` (accordion, `cursor:pointer; user-select:none`) + chevron icon; below it the main unresolved table with `--bs-table-bg: var(--accent-light)` on `<thead>`
  - Unskip from Tab 2 skipped section: form POSTs to `VisitController.unskipNext()` with `redirectTo=/clientcare/followups`

### Client list and detail
- `ClientController` maps to `{"/clients", "/clientcare/clients"}` — `isClientCare(HttpServletRequest)` helper via URI prefix
- `fragments/client-detail.html` — shared content; `clients/detail.html` and `clientcare/clients/detail.html` are thin `th:insert` wrappers
- `ClientController.detail()` sets context-aware `backUrl`/`editUrl`/`currentPageUrl`:

| Context | `backUrl` | `editUrl` |
|---|---|---|
| warehouse | `/clients` | `/clients/{id}/edit?returnTo=detail` |
| `from=clientcare` | `/clientcare/followups` | `/clients/{id}/edit?returnTo=detail` |
| `/clientcare/clients/{id}` | `/clientcare/clients` | `/clientcare/clients/{id}/edit?returnTo=detail` |
| `from=appointments` | `/clientcare/appointments?date=...` | `/clientcare/clients/{id}/edit?returnTo=detail` |

- `clientsWithUnpaidVisits` Set passed to model in clientcare context (for `−$` SVG icon next to name in list, and `bi-receipt-cutoff`-style KPI card on dashboard)
- `/clientcare/clients?unpaid=true` — client-side filter: hides clients without unpaid visits; activated via dashboard KPI card link; active state shown as dismissible pill ("Клієнти які мають неоплачений візит"); `clearUnpaidFilter()` removes param from URL via `history.replaceState` without reload
- `serviceNames` Map (`Map<Long, String>`) loaded via `SalonServiceService.findAll()` in two places:
  `VisitController.allVisits()` (visits list page) and `ClientController.detail()` (shared fragment);
  fragment guards with `serviceNames != null` (warehouse context has no serviceNames in model)
- **Appointment badges in client detail** (`fragments/client-detail.html`, "Інформація про клієнта" block):
  - All upcoming PLANNED/CONFIRMED appointments shown as green badges; grouped in `d-flex flex-wrap gap-1` (no line gap between items)
  - Overdue badge (NO_SHOW / past PLANNED/CONFIRMED) suppressed if client has a visit on or after the overdue appointment date — `ClientController.addAppointmentBadgeAttrs()` calls `visitService.hasVisitOnOrAfter(clientId, apptDate)`; same logic in `ClientCareService.getDashboardData()` for `noShowCount`

### Scalp Photos
- `ScalpPhoto` entity in `domain/scalp/`; `ScalpPhotoService` in `clientcare/service/`
- Gallery at `/clientcare/clients/{clientId}/photos` — zone filter pills, CSS grid, Drive thumbnail `?id={fileId}&sz=w400`
- `driveUrl` = source of truth; `driveFileId` nullable (regex extract); no Google API/OAuth
- `ScalpPhotoController` has no class-level `@RequestMapping` — full paths per method
- Thumbnail `onerror` hides img, shows `bi-image` icon (handles private/broken files)
- `returnTo` param passed from "Всі фото" link → gallery Back returns to correct context (warehouse or clientcare)

### Google Drive folder
- `Client.driveFolderUrl` (source of truth) + `driveFolderId` (nullable regex extract) — no Drive API/OAuth
- `DriveFolderController`: GET/POST edit, POST remove; `DriveFolderDto` with `@Pattern` + `@Size(max=500)` + `@NotNull clientId`

### Visit journal `/clientcare/visits`
- Cross-client visit journal; max 200 records (`.stream().limit(200)`); hint shown when list hits limit
- Filters: date range (від/до), service, payment status (paid/unpaid/all), client text search (client-side)
- Server-side filtering via `JpaSpecificationExecutor<Visit>` + `Specification<Visit>` with `root.fetch("client", JoinType.INNER)` — avoids PostgreSQL null-param type inference error from JPQL `:param IS NULL OR` pattern
- KPI strip (inline, accent-light bg): total visits + revenue for filtered period — placed above table, below filters
- Date separator rows (`.date-group-label`): grey background `#e2e3e5 / #41464b`; hidden when no visible rows below (two-pass `filterByClient()`)
- Client search state persisted via `sessionStorage` across filter form submits (form.submit override pattern)
- `table-auto` class on table to override layout `.table { min-width: 500px }` for horizontal scroll fix

### Visits
- `Visit` entity fields: client_id, visit_date, complaint, scalp_condition, recommendations, notes, next_appointment_id FK,
  + billing: service_id FK, price_at_time, payment_method, is_paid, certificate_code
- `nextVisitDate` DB column exists for legacy data; NOT submitted from form
- Visit form: `id="visitForm"` required — JS `getElementById` targets correct form (layout logout form is also a `<form>`)
- `VisitService.save()` returns `Long` (visitId) — needed for action=schedule redirect
- **`nextVisitSkipped` flag:** boolean on `Visit` entity; set via "Не потрібний прийом" button on visit edit form; resets naturally when a new visit is created for the same client; drives Tab 2 skipped section on `/clientcare/followups`
  - `VisitController.unskipNext(@PathVariable id, returnTo, redirectTo)`: if `redirectTo` present and safe → redirect there directly; otherwise redirect to visit edit form (with optional `returnTo`); `redirectTo` vs `returnTo` distinction: `redirectTo` is where this action goes; `returnTo` is the back-button destination passed to the next page
  - Visit form "skipped" indicator: `alert-secondary d-flex justify-content-between` — text+icon left (`bi-slash-circle`), rollback button right (`bi-arrow-counterclockwise`, `btn-outline-secondary btn-sm`)
- **Billing (migration 021) — Appointment = attendance only; Visit = protocol + payment:**
  - CASH/CARD → price + isPaid checkbox; CERTIFICATE → cert code field (price/isPaid hidden); BARTER → price (auto-paid); COMPLIMENTARY/PROMO → no price, auto-paid
  - `applyCertificatePayment()`: validates cert code, sets REDEEMED + redeemedAt in same transaction
  - `AUTO_PAID_METHODS` (BARTER/COMPLIMENTARY/PROMO): `paid=true`, `priceAtTime=null`
  - **Same-cert guard in `update()`:** compares existing vs incoming cert code — skips `applyCertificatePayment()` if same code (prevents "already redeemed" error when editing non-payment fields)
  - `getClientIdsWithUnpaidVisits()` → `Set<Long>` via `VisitRepository.findClientIdsWithUnpaidVisits()`
  - Certificate AJAX check: `GET /clientcare/gift-certificates/check?code=...` → `{valid, recipientName}`; `GiftCertificateService.findRecipientIfValid(code)` → `Optional<String>`
- **Visit → Appointment link (migration 018):** `visits.next_appointment_id FK → appointments ON DELETE SET NULL`
  - `VisitService.linkAppointment(visitId, appointmentId)` — dirty checking
  - `VisitService.unlinkCompletedAppointment(appointmentId)` — clears stale "Наступний запис" badge on completion; uses `VisitRepository.findByNextAppointmentId(Long)`
- **action=schedule flow:** POST with action=schedule → save visit → redirect to `/clientcare/appointments/new?clientId=X&linkVisitId={visitId}&returnTo=visit-edit`; on appointment save → `linkAppointment()` → redirect back to visit edit
- **`completeAppointmentId` pattern (deferred completion):** "Завершити прийом" does NOT set COMPLETED immediately; redirects to visit form with `completeAppointmentId` hidden field; `VisitController.save()` calls `appointmentService.changeStatus(COMPLETED)` + `unlinkCompletedAppointment()` only after visit saved — prevents stuck COMPLETED if user abandons form
- Visit form design: `form-section-label` (uppercase green label with icon), `time-block` (accent-light billing block), `form-divider` (`<hr>`), `form-actions` (bottom bar)

### Appointments
- `Appointment` entity: id, client_id (nullable), guest_name, guest_phone, start_at, end_at,
  status (PLANNED/CONFIRMED/COMPLETED/CANCELLED/NO_SHOW), service_id FK, notes, created_at, updated_at
- DB CHECK: `client_id IS NOT NULL OR NULLIF(TRIM(guest_name),'') IS NOT NULL`
- `AppointmentDto`: `@AssertTrue isClientOrGuestPresent()`, `@AssertTrue isEndAfterStart()`, `@DateTimeFormat(ISO.DATE_TIME)` on startAt/endAt; `serviceId` — `@NotNull(message="{appointment.service.required}")` (form shows `is-invalid` + `invalid-feedback` on the service select)
- `AppointmentService.save()` returns `Long` (appointmentId) — needed for `linkAppointment()` call
- Calendar: FullCalendar v6 CDN; day/week/month views; drag-drop reschedule (PLANNED/CONFIRMED only)
  - Event feed: `GET /clientcare/appointments/api?start=...&end=...`; event colors client-side via `eventDidMount`
  - View preference in `localStorage('fcView')`; locale from Spring `#locale.language`; CSRF via request param
- **Completion flow:**
  - `canComplete` = status PLANNED/CONFIRMED AND client linked (not guest); `isOverdue` = canComplete AND startAt < now
  - Not overdue: "Завершити прийом →" → `POST /{id}/complete` → redirect to visit form
  - Overdue: amber banner + "Прийшов" (→ complete) / "Не прийшов" (→ NO_SHOW)
  - **Nested form warning:** complete/noshow action blocks must be AFTER the main `</form>` — browsers silently ignore nested forms
- **NO_SHOW flow:** redirect to edit page; action panel: "Черга follow-up" + "Записати повторно"
  - "Записати повторно" passes `serviceId`, `notes`, `rebookedFromId`; new form shows amber "missed" banner
  - `rebookedFromId` in hidden input — banner survives validation errors
- `lockClientForEdit()`: in edit mode, client always locked (no dropdown shown); submits hidden `clientId` or `guestName`/`guestPhone`
- Drag-to-past blocked: client-side `eventAllow` + server-side `reschedule()` guard (throws if newStart < now - 5min)
- Wave 5b: `dayView()` accepts optional `clientId`, `linkVisitId`, `returnTo`; click-to-create handler appends these to new appointment URL
- Appointment form sections: Client → Service → Date/time (`time-block`) → Status (edit only) → Notes; CSS classes same as visit form

### Gift Certificates
- `GiftCertificate` entity in `domain/gift/`; FK refs to clients/services as plain Long columns (no lazy-load issues)
- `GiftCertificateStatus` enum: ACTIVE / REDEEMED / EXPIRED / CANCELLED
- `syncExpired()` called before every list query — batch JPQL `@Modifying` UPDATE (not per-row)
- Lifecycle: issue → cancel|restore → delete (hard delete; REDEEMED certs cannot be deleted)
- `restore()`: CANCELLED → ACTIVE (clears cancelledAt); NOT from REDEEMED state
- **No redeem button anywhere in UI** — redemption only via CERTIFICATE payment method in visit form
- Code: 8-char alphanumeric (4+4 dash-separated), unambiguous alphabet (excludes 0/O, 1/I/L, 5/S, 8/B)
- `price` field: amount paid by purchaser; 0 = complimentary salon gift; hidden in form when "від салону" via `syncPurchaserMode('salon')` JS
- List modes: `?clientId=N` → client view (all statuses); standalone → status filter (server-side) + client name search (client-side via `data-purchaser`/`data-recipient`)
- Status badge colors: ACTIVE=`bg-success`, REDEEMED=`bg-secondary` (grey), EXPIRED=`bg-warning text-dark`, CANCELLED=`bg-secondary`
- Role badges in client-detail: Покупець=secondary-subtle (`#e2e3e5`/`#41464b`), Отримувач=success-subtle (`#d1e7dd`/`#0a3622`)
- Cert code link: `color:var(--accent)` — applied via `.cert-code-link` class (NOT on `<td>` to prevent recipient name inheriting monospace font)
- Purchaser client select uses `class="ts-client"` — TomSelect search enabled

### Finance Analytics `/clientcare/finance`
- **Revenue model (cash accounting):** CASH/CARD paid visits (isPaid=true, priceAtTime>0) + cert sales in period (price>0, status≠CANCELLED)
  - BARTER/COMPLIMENTARY/PROMO visits: NOT in revenue (no cash exchanged); tracked in visit count only
  - CERTIFICATE-paid visits: NOT in revenue (cash captured at cert sale time)
  - `REVENUE_METHODS = {CASH, CARD}` constant in `ClientCareFinanceService`
  - `avgTicket` = revenue ÷ (cash/card paid visits + paid certs sold)
- `buildReport(from, to)` → Map with: revenue, visitCount, avgTicket, unpaidAmount, unpaidCount,
  certSoldCount, certSoldRevenue, certActiveCount, certLiability,
  byService (List<ServiceRevenueDto>), byPaymentMethod (List<PaymentBreakdownDto>),
  topClients (List<TopClientFinanceDto>), unpaidVisits (List<UnpaidVisitRowDto>)
- `buildTrends(from, to)` — monthly revenue + visit count; pre-fills all months in range with zeros
- Period: THIS_MONTH (default) / LAST_MONTH / ALL_TIME / CUSTOM; previous-period deltas for revenue/visitCount/avgTicket
- Uses `VisitRepository.findWithClientByPeriod()` (JOIN FETCH v.client — avoids lazy-load outside txn)
- `returnTo=${currentPageUrl}` on visit edit links — Back returns to finance page with preset preserved
- Chart: Chart.js 4 combo bar+line (visits=bars right axis, revenue=line left axis)

### Calendar Tasks `/clientcare/calendar-tasks`
- `CalendarTask` entity: `task_date DATE`, `client_id FK`, `appointment_id FK`, `text VARCHAR(500)`, `is_done BOOL` — all client/appointment links nullable; generic dated-task design
- `CalendarTaskService.findByDate(date)`: when `date == today` (Warsaw), prepends overdue pending tasks (`task_date < today AND is_done = false`) before today's own tasks; `getCountsByDateRange` adds overdue count to today's map entry — badge is correct without opening the modal
- Reminder button on NO_SHOW and CANCELLED appointment edit pages; `isNoShow`/`isCancelled` booleans from controller; pre-filled text differs: "не прийшов(ла)" vs "скасував(ла)" (via `REMINDER_IS_CANCELLED` JS constant)
- **Future Lead flow**: when a `leads` table is added — `ALTER TABLE calendar_tasks ADD COLUMN lead_id BIGINT REFERENCES leads(id) ON DELETE SET NULL`; add `leadId`/`leadName` to `CalendarTaskDto`; service and controller unchanged

### Thymeleaf 3.1 restrictions
- `th:onclick` with string concatenation blocked — use `th:data-*` attributes + `onclick="fn(this.dataset.field)"`
- Dynamic message key lookups `#{__{'prefix.' + var}__}` blocked — use `th:switch` / `th:case` with explicit static keys per enum value

---

## Telegram Bot

### Overview
Appointment reminders via Telegram Bot API. No third-party library — pure RestClient + raw HTTP.
Webhook registered automatically on `ApplicationReadyEvent` (prod profile only).

### Config — `TelegramConfig` (`@ConfigurationProperties("telegram")`)
- `telegram.bot.token`, `telegram.bot.username`, `master.chat-id`, `webhook.secret`
- `telegram.files.how-to-find-video-id`, `telegram.files.checklist-photo-id` — Telegram file_ids (permanent, free hosting)
- `RestClient` bean wired to `https://api.telegram.org/bot{token}`

### Client linking flow
1. Master opens `/clientcare/clients/{id}/telegram` → `ClientTelegramController` generates UUID token, saves to `client.telegramLinkToken`
2. Page shows deep link `https://t.me/{bot}?start={token}`; copy button copies full ready-to-send message to clipboard
3. Client clicks link → Telegram opens bot → client sends `/start TOKEN`
4. `TelegramWebhookController.handleMessage()` finds client by token, saves `chatId`, clears token (dirty checking, `@Transactional`)
5. Client receives "✅ Чудово!"; master receives "🔗 {name} підключив Telegram"
6. Client detail page header: gray `bi-telegram` icon if not connected; blue icon + green dot (Bootstrap dropdown) if connected
   - Dropdown: "Переслати посилання" → link page | "Відключити" → `#tgDisconnectModal` (Так/Ні)
   - `POST /clientcare/clients/{id}/telegram/remove` → clears chatId + token (`@Transactional` required for dirty checking)

### Reminder flow (day-based)
- **Trigger:** GitHub Actions cron (`reminders.yml`, `0 7 * * *` + `0 8 * * *`) calls `GET /internal/reminders`
  with `X-Internal-Token` header. Two schedules = 09:00 Wrocław year-round (CEST/CET). `reminder24hSentAt IS NULL` guard prevents duplicates.
  Fly.io uses `auto_stop_machines = 'suspend'` — `@Scheduled` won't fire.
- **Window:** all appointments tomorrow (dayStart..dayEnd), status IN (PLANNED, CONFIRMED), `reminder24hSentAt IS NULL`
- **With chatId:** sends message with ✅/❌ inline buttons; sets `reminder24hSentAt = now()`
- **Without chatId:** notifies master to remind manually
- **Message text:** `🌿 Нагадуємо: завтра ваш візит!\n📅 {date}\n🕐 {time}\n✂️ {service}\n📍 Jana Sebastiana Bacha 11, 50-305 Wrocław`
- Rate limit guard: `Thread.sleep(50)` between sends
- **Local test:** `curl -H "X-Internal-Token: dev-secret" http://localhost:8080/internal/reminders`

### Confirmation / cancellation flow (`TelegramWebhookController.handleCallbackQuery`)
- `confirm:{id}` → status CONFIRMED; edits original message (keeps full info + address); notifies master
- `cancel:{id}` → status CANCELLED; edits original message; notifies master with phone
- Guard: if status ≠ PLANNED/CONFIRMED → "Цей візит вже оброблено." (idempotent)
- After confirmation, sends post-confirm files then maps link as separate final message:
  - "Консультація первинна": video (HowToFind) with caption + checklist photo with caption → maps link
  - "Консультація повторна": checklist photo with caption → maps link
  - Other services: maps link only
- Maps link: `🗺 <a href="https://www.google.com/maps/dir/?api=1&destination=Jana+Sebastiana+Bacha+11%2C+50-305+Wroc%C5%82aw%2C+Poland">Прокласти маршрут →</a>` (sent via `sendMessageNoPreview`)

### Appointment guest UI vs DB
- Form UI has "guest" toggle (name + phone without selecting existing client) — UI kept as-is
- **In DB, guests never exist:** `AppointmentService.save()` auto-promotes any guest to a real `Client` entity
- `Appointment.guestName` / `guestPhone` fields removed (migration 028); `client_id NOT NULL`
- The CHECK constraint from migration 017 was also removed in 028

### i18n
- `telegram.*` keys added to all three properties files (`messages_uk.properties`, `messages_pl.properties`, `messages.properties`)
- Keys: `telegram.connect.title/hint/btn.copy/copied/instruction/clipboard.prefix/tooltip`, `telegram.resend.link`, `telegram.disconnect`, `telegram.disconnect.confirm`
- Telegram message content (reminders, confirmations) is hardcoded Ukrainian — outside Spring MVC i18n scope

---

## TODO

### ClientCare
- **Wave 4 — Protocol entity** — POSTPONED: treatment type → recommended product list; not relevant at current stage

### Warehouse
- **Brand entity** — refactor `brand` from plain String to JPA entity (migration: `brands` table + FK on `products`);
  CRUD by pattern of Category (BrandRepository, BrandService, BrandController, BrandDto, guard delete if has products);
  TomSelect select in product form with "Створити новий бренд →" round-trip link; brand filter on `/products` switches to id-based
- Low stock email notifications — daily digest; Spring @Scheduled + spring-boot-starter-mail
- Export to Excel — reports + movement history; Apache POI (xlsx)
- Inventory count / stock-take — formal workflow: physical counts → ADJUSTMENT movements; `/scan?mode=stocktake` entry point
- User roles (ADMIN/OPERATOR) — Role entity exists; @PreAuthorize on delete/cancel/deactivate;
  IDOR protection needed: DriveFolderController (client ownership), ScalpPhotoController (photo ownership)
- Print barcode labels — from product detail page
- AI: conversation history / multi-turn chat (currently stateless per request)

### Infrastructure
- **GitHub Actions cron reliability** — `/internal/reminders` cron had ~3h delay (2026-05-29); if delays recur — migrate trigger to **cron-job.org** (free HTTP cron, delay < 1 min); config: URL + `X-Internal-Token` header; keep GitHub Actions workflow as manual fallback
- **Google Calendar sync** — OAuth2 two-way sync; main complexity: token storage per user + conflict resolution
- **PostgreSQL backup** — pg_dump @Scheduled or Neon point-in-time recovery (check if sufficient before custom solution)
- Spring Session (if scaling beyond 1 machine)
- User management page (if multiple users needed)

### Quality
- 38 unit tests (./mvnw test, no DB required): StockServiceTest, StockDashboardRowDtoTest, ReportServiceTest, ProductServiceTest
- WarehouseApplicationTests — @Disabled (requires live PostgreSQL, run manually with dev profile)
