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
           ReportService, AiAssistantService, UserService, UserDetailsServiceImpl)
web/controller/ — MVC controllers (thin):
  DashboardController, ProductController, ClientController,
  SupplierController, CategoryController, StockController (income/expense/cancel),
  StockItemController (/stock/items/{id}/edit), MovementController (/movements/history),
  ProfileController, LoginController, ReportController, AiController,
  GlobalExceptionHandler (@ControllerAdvice)
clientcare/web/controller/ — ClientCare controllers:
  ClientCareController, ClientCareFollowupController, FollowUpActionController,
  AppointmentController, ScalpPhotoController, DriveFolderController, VisitController,
  GiftCertificateController (/clientcare/gift-certificates)
web/dto/ — form objects and filter DTOs
web/validator/ — custom Bean Validation annotations (ValidDateRange + DateRangeValidator)
web/interceptor/ — CurrentUriInterceptor
web/formatter/ — QuantityFormatter (@qf bean)
config/ — SecurityConfig, LocaleConfig, WebMvcConfig

## Key Rules
- ddl-auto=none, Liquibase manages schema (migrations 001-021)
- Controllers are thin, logic in services
- Never pass entities to templates, use DTOs
- Dirty checking for updates — no explicit save() on managed entities
- Credentials in application-dev.properties (gitignored)
- spring.profiles.active=dev (via VM options in IDE: -Dspring.profiles.active=dev)
- Production profile: application-prod.properties

## Validation architecture (2026-05-17)
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
  - StockController.editMovement — after editing stock batch
  - ClientController — after create/edit from context (returnTo=detail or returnTo=followups)
  - Pattern: `^/[^/].*` — blocks `//evil.com`, `https://...`, relative `../`

**StockItemController validation (added 2026-05-17):**
- `updateStockItem()` validates purchasePrice > 0 before saving
- Throws `IllegalArgumentException` if price <= 0 (defense-in-depth, DB will reject anyway)

**Product deactivation validation:**
- `deactivationReason` max length 200 characters (matches DB column VARCHAR(200))
- Controller validates length before service call; service does NOT re-validate
- If reason exceeds limit, user sees friendly error via flash message (not DB truncation exception)

**GlobalExceptionHandler (@ControllerAdvice):**
- `EntityNotFoundException` → redirect "/" with errorMessage flash
- `DataIntegrityViolationException` → redirect "/" with user-friendly message (covers DB truncation, unique violation)
- `ConstraintViolationException` → redirect "/" with violation message (from @Validated @RequestParam)
- `MethodArgumentTypeMismatchException` → redirect "/" with param name

**Flash messages:** always resolved via `messageSource.getMessage(...)` — no hardcoded strings in controllers.

## DB
Local: hairmony_dev, user: warehouse_user
Production: Neon PostgreSQL (credentials via Fly.io secrets)
Migrations: 001-users, 002-suppliers, 003-clients, 004-products,
            005-stock-items, 006-stock-movements, 007-categories,
            008-fix-categories, 009-insert-admin-user,
            010-add-movement-cancel (adds original_movement_id FK on stock_movements),
            011-add-writeoff-reason (adds write_off_reason VARCHAR(30) on stock_movements),
            012-create-scalp-photos (scalp_photos table + index on client_id),
            013-create-visits (visits table + index on client_id),
            014-add-client-drive-folder (drive_folder_url + drive_folder_id columns on clients),
            015-create-follow-ups (follow_ups table),
            016-add-product-deactivation (deactivated_at + deactivation_reason on products),
            017-create-appointments (appointments table + indexes on start_at and client_id;
              CHECK constraint: client_id IS NOT NULL OR NULLIF(TRIM(guest_name), '') IS NOT NULL),
            018-add-next-appointment-to-visits (next_appointment_id BIGINT FK on visits → appointments ON DELETE SET NULL),
            019-create-services (services table for salon services),
            020-create-gift-certificates (gift_certificates table + indexes on purchaser/recipient/status),
            021-add-visit-billing (service_id, price_at_time, payment_method, is_paid, certificate_code on visits)

## What's done
- Dashboard (/) with 4 KPI filter cards (All/In stock/Attention/Out), server-side
  activeStatus param + client-side toggle; filters (status/category/brand/search),
  clickable rows → product detail, active filter highlight (.filter-active);
  KPI "countOk" = OK+LOW, "lowStock" = LOW+OUT, "countOut" = OUT only;
  filter layout: Brand + Category selects on one row, Search full-width below;
  filters wrapped in <form onsubmit="return false"> for correct iOS/Android Prev/Next
  navigation between fields (filtering stays client-side/instant);
  × clear buttons on Brand and Category selects (mobile, d-md-none, JS-controlled);
  "Скинути (N)" reset button below search, visible when ≥1 of category/brand/search active;
  mobile table: Status column hidden (d-none d-md-table-cell) — row colors (table-danger/table-warning) convey status;
  table uses .stock-table class with table-layout:fixed + min-width:0 !important (overrides global .table{min-width:500px});
  col-stock=5rem, col-actions=6.5rem fixed; name column takes remaining width;
  action buttons: .btn-square (2rem×2rem, mobile-only via @media) — square, centered; desktop: normal px-md-2 padding;
  th font-size 0.72rem on mobile, centered via @media; desktop layout unchanged
- Products CRUD with soft delete, restore, detail page, brand autocomplete
- Stock batch (StockItem) edit modal on product detail — expiry date, batch number, price
- Categories CRUD with modal editing and Bootstrap delete modal (Ні/Так);
  deletion guarded — disabled with tooltip if category has products assigned;
  flash messages for create/delete; GET/POST /categories/new with returnTo=product
- Suppliers CRUD with detail page (/suppliers/{id}) showing purchase history;
  collapsible form; Bootstrap delete modal (Ні/Так);
  deletion guarded — disabled with tooltip if supplier has stock movements;
  flash messages for create/delete; GET/POST /suppliers/new with returnTo=income
- Clients CRUD with detail page (/clients/{id}) showing transaction history,
  collapsible form; deletion guarded — disabled with tooltip if client has movements
- Stock income (/movements/income) with FIFO, barcode scanner input (debounced AJAX),
  quantity step auto-set by unit (integer for PCS, decimal for ML/G);
  "Створити нового постачальника →" link pre-selects new supplier on return
- Stock expense (/movements/expense) with SALE/WRITE_OFF/ADJUSTMENT,
  available qty shown + enforced as max, barcode scanner input,
  quantity step auto-set by unit; insufficient stock error via i18n MessageSource;
  "Створити нового клієнта →" link pre-selects new client on return;
  sale price validation: unitPrice required and > 0 for SALE — both DTO @AssertTrue and service guard;
  writeOffReason required for WRITE_OFF — DTO @AssertTrue;
  below-cost warning: JS inline alert-warning when unitPrice < FIFO purchase price,
  confirm() on submit (data-fifo-price on product options via StockItemRepository.findFifoPricePerProduct());
  write-off reason selector (WriteOffReason enum): GIFT/EXPIRED/DAMAGED/SAMPLE/INTERNAL_USE/OTHER,
  shown only when WRITE_OFF selected; client field shown for SALE and WRITE_OFF+GIFT;
  "Створити нового клієнта →" full round-trip preserves product/type/quantity:
    link href built dynamically via updateNewClientLink() JS (reads current dropdown values),
    ClientController threads movementType+quantity through newForm/createNew,
    StockController.expenseForm() accepts movementType+quantity URL params and pre-fills DTO,
    form-autosave localStorage cleared synchronously when returning with pre-selected clientId
    (prevents autosave restore banner conflicting with URL-provided values)
- Barcode scanner page (/scan) — camera scan or manual entry, income/expense mode;
  income/expense mode buttons: colored icons (green/red), white when active
- MovementType enum: PURCHASE, SALE, WRITE_OFF, ADJUSTMENT, CANCELLATION
- WriteOffReason enum: GIFT, EXPIRED, DAMAGED, SAMPLE, INTERNAL_USE, OTHER
- StockDashboardRowDto with status OK/LOW/OUT
- Movement journal (/movements/history) — server-side filtering (JPA Specifications),
  server-side pagination PAGE_SIZE=100 (MovementHistoryService), filters: date range,
  type, write-off reason (always visible, label "Причина списання"), product name (text LIKE search), counterparty (text LIKE);
  write-off reason badge shown in type column for WRITE_OFF rows;
  auto-submit: selects/dates → on change, text inputs → debounced 500ms after min 3 chars
  (empty clears immediately); date inputs use showPicker() onclick for mobile calendar;
  focus restored after text auto-submit via sessionStorage;
  × clear buttons on all filter fields (mobile only, d-md-none, server-side th:if when active);
  "Скинути (N)" button visible only when ≥1 filter active, shows count;
  active filters highlighted with accent border + background (filter-active CSS);
  "з фільтром" badge in results count when any filter active;
  filter-field-inline CSS class: label+input inline on mobile, stacked on desktop;
  pagination controls preserve all filter params; warning banner when totalElements >= 500;
  product name, client name, supplier name are clickable links → detail pages with
  from=history so Back button returns to journal
- Movement cancellation — POST /movements/{id}/cancel reverses any PURCHASE/SALE/
  WRITE_OFF/ADJUSTMENT: restores or removes stock, records CANCELLATION movement;
  cancel button tooltip shows product name + qty + date (native title attr);
  confirmation modal shows movement detail (product, qty, unit, date) + confirm text;
  success flash message is informative: product name, qty, new stock level —
  two variants: movement.cancel.success (expense) and movement.cancel.success.purchase;
  StockService.cancelMovement() returns CancelResult record with all data for message;
  cancelled rows shown strikethrough, cancellation rows shown in gray;
  guard against double-cancel and cancelling a cancellation
- Reports page (/reports) — period presets (THIS_MONTH, LAST_MONTH, ALL_TIME, CUSTOM);
  KPI summary banner (revenue, purchases, gross profit + margin%, sales count);
  stock value snapshot (labelled "current snapshot" to distinguish from period KPIs);
  top sales top-7 with salesCount and share%; write-offs summary by product+reason
  with GIFT recipient name shown; top clients top-7 (clickable → client detail);
  purchases by supplier; margin analysis with absolute profit column (zł);
  slow movers table — products with stock > 0 but no sales in selected period;
  configurable expiry alert window;
  desktop layout: 2-col pairs use align-items-start (no height stretching);
  table styles: .table th font-size 0.72rem, mobile 0.68rem/0.8rem, table-striped on all tables
- i18n: uk (primary), pl, en; all UI strings via #{} — no hardcoded text in templates;
  all validation messages in messages.properties / messages_uk.properties / messages_pl.properties
  under keys: validation.quantity.positive, validation.price.positive, validation.size.maxN, etc.
- QuantityFormatter (@qf bean) — integers for PCS, decimals for ML/G
- CurrentUriInterceptor — active nav highlighting
- Language switcher preserves URL params via JS switchLang()
- Spring Security with DB authentication (users table)
- Login page with show/hide password
- Change password page (/profile/change-password) — DTO validates @Size(min=6,max=72)
  and @AssertTrue isPasswordsMatch(); service validates current password; flash messages via MessageSource
- Logout in sidebar
- Mobile responsive layout with burger menu and topbar;
  secondary table columns hidden on mobile via d-none d-md-table-cell
- Client-side search on all list pages with × clear button (mobile only, appears on input)
- Collapsible create forms on list pages
- Clickable table rows on mobile
- Cancel buttons on stock income/expense forms (back to dashboard)
- Mobile nav: Income/Expense links → scanner (/scan?mode=income/expense) on mobile,
  form pages (/movements/income/expense) on desktop (Bootstrap d-none/d-flex split);
  dashboard mobile buttons: both btn-outline-secondary, green icon (income) / red icon (expense)
- DevTools enabled (dev profile only)
- Minimal PWA support: manifest.webmanifest, theme-color, apple-touch-icon meta tags in both layouts;
  SecurityConfig permits /manifest.webmanifest and /icons/**;
  generate-icons.html utility at project root — open in browser to generate icon-192.png + icon-512.png
  from favicon SVG; no service worker (offline/push not needed in MVP)
- Product deactivation audit: deactivated_at + deactivation_reason fields (migration 016);
  Bootstrap modal with optional reason textarea replaces native confirm(); deactivated products shown
  with table-secondary row + strikethrough name; detail page shows alert-warning banner with date+reason;
  income/expense buttons hidden for inactive products; showInactive=true preserved in Back button URL
- AI Assistant page (/ai) — chat widget backed by Google Gemini 2.5 Flash
  (v1beta endpoint); builds warehouse context (stock levels + last 30-day
  movements) and calls Gemini via RestClient (no extra deps); response
  language follows active locale (uk→Ukrainian, pl→Polish, en→English);
  AJAX, no page reload; 3 quick-question buttons; fully i18n'd UI;
  API key via GEMINI_API_KEY env var / gemini.api.key in dev properties

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

### Calendar event colors (`appointments/day.html` `eventDidMount`)
| State              | Background | Border     | Text      |
|--------------------|------------|------------|-----------|
| PLANNED            | `#fff3cd`  | `#ffe69c`  | `#664d03` |
| CONFIRMED          | `#4a7c59`  | `#4a7c59`  | `#fff`    |
| COMPLETED          | `#e2e3e5`  | `#c4c8cb`  | `#41464b` |
| CANCELLED          | `#dc3545`  | `#dc3545`  | `#fff`    |
| NO_SHOW            | `#fd7e14`  | `#fd7e14`  | `#fff`    |
| Overdue (PLANNED/CONFIRMED + past end) | `#f8d7da` | `#f1aeb5` | `#842029` |
| In-progress (PLANNED/CONFIRMED + now inside) | `#d1e7dd` | `#a3cfbb` | `#0a3622` |

### Client detail appointment badges (inline style in `fragments/client-detail.html`)
| Badge              | Background | Text      |
|--------------------|------------|-----------|
| Upcoming appt      | `#d1e7dd`  | `#0a3622` |
| Overdue appt       | `#f8d7da`  | `#842029` |

### Dashboard KPI cards (inline style in `clientcare/dashboard.html`)
| Card                  | Background | Text      |
|-----------------------|------------|-----------|
| Заплановані записи    | `#fff3cd`  | `#664d03` |
| Пропущені записи      | `#f8d7da`  | `#842029` |

## UX patterns (apply consistently)
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
  form-autosave localStorage cleared synchronously in `<script>` (not in DOMContentLoaded)
  when returning with a pre-selected entity to prevent restore banner overwriting URL-provided values
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
- Filter UX pattern (client detail, product detail, supplier detail): filter bar with
  Row 1 = від/до date range (two input-group side by side, id=dateFromWrap/dateToWrap);
  Row 2 = type select + gift button (client detail only) / product name search (supplier detail);
  Row 3 = product/client name search (client detail only);
  filter-select-wrap for selects with × button (d-md-none, style=display:none, shown via JS);
  input-group for date/search with × button (d-md-none); filter-active class on wrapper div
  (not on select/input itself) for green border; "Скинути (N)" button below filters, visible
  when ≥1 active; all via updateResetBtn() JS; date inputs use showPicker() onclick for mobile;
  data-date="yyyy-MM-dd" on each <tr> for client-side date range filtering
- Client detail quick filter "Подарунки": toggle button in same row as type select;
  when active — filter-active border, × shown inside button text, type select disabled;
  data-label attr holds i18n text (btn.quickFilter.gifts); blur() on toggle to avoid focus gray;
  counted in reset button; i18n: uk=Подарунки, pl=Prezenty, en=Gifts
- badge-writeoff (.badge-writeoff), filter-select-wrap, and .btn-square CSS are global in layout/main.html;
  .btn-square — mobile-only square icon button (2rem×2rem), defined in page @media block, not global
- Supplier detail: Тип операції column removed (redundant in purchase history context);
  filter bar added with від/до dates + product name search; .table { min-width: 0 }
- Dashboard clickable rows pass from=dashboard; product detail Back button handles it → /
- Movement journal thead: accent-light via --bs-table-bg; th vertical-align: middle
- Table styles (detail pages + reports): .table th font-size 0.72rem, vertical-align middle;
  mobile: 0.68rem/0.4rem padding for th, 0.8rem/0.4rem for td; table-striped on history tables;
  .table-xs class for extra-compact rows (padding 0.2rem 0.5rem) — used on stock batches table;
  .table-auto resets font-size to inherit (used on product info table to keep default size);
  supplier detail: .table { min-width: 0 } so history table fits mobile width without scroll
- Badge colors (global, layout/main.html): .badge-writeoff — soft pink (#f5a3b0 bg, #7d2535 text);
  used for WRITE_OFF movement type badges in history, product detail, client detail
- Movement journal thead: accent-light background via --bs-table-bg; th vertical-align: middle
- Date filter labels від/до use #{movements.filter.dateFrom} / #{movements.filter.dateTo}
  across all three detail pages — no hardcoded text

## Stock expense validation
- unitPrice required and > 0 for SALE — validated in StockExpenseDto via @AssertTrue isUnitPriceValidForSale()
  AND service guard (defense-in-depth); message key: stock.expense.salePriceRequired
- writeOffReason required for WRITE_OFF — validated in StockExpenseDto via @AssertTrue isWriteOffReasonRequired()
  message key: stock.expense.writeOffReasonRequired
- movementType PURCHASE and CANCELLATION blocked in StockService.registerExpense() — cannot be submitted via expense form
- Below-cost warning (not a hard block): JS compares unitPrice with data-fifo-price on product option;
  shows inline alert-warning; confirm() on submit if below cost
- data-fifo-price populated from StockItemRepository.findFifoPricePerProduct() —
  returns purchasePrice of oldest available batch per product (one query for all products)
- StockService.getFifoPricesPerProduct() → Map<Long, BigDecimal>, passed to model as fifoPrices
- WriteOffReason set only when movementType == WRITE_OFF (else null)
- Client set for SALE and for WRITE_OFF+GIFT (gift recipient)

## Movement cancellation details
- StockMovement.originalMovementId (Long) links a CANCELLATION back to its source
- StockMovementRepository.existsByOriginalMovementId() — guard for double-cancel
- StockMovementRepository.existsBySupplierId() / existsByClientId() — delete guards
- StockMovementRepository.findCancelledMovementIds(Set<Long>) — @Query returns which
  IDs on the current history page have been cancelled (for UI indicators)
- StockMovementRepository.findAllSupplierIdsWithMovements() / findAllClientIdsWithMovements()
  — used to build disabled-delete sets in list controllers
- StockMovementRepository.findAllBySupplierIdOrderByCreatedAtDesc() — supplier detail history
- StockService.cancelMovement() returns CancelResult record: productName, qtyFormatted,
  unitLabel, newStockFormatted, isPurchase — controller picks message key and formats
- Cancel button visible only for non-CANCELLATION, not-yet-cancelled rows
  (PURCHASE can also be cancelled; guard: batch qty must equal original qty)

## Reports details
- ReportService.getReportSummary(from, to) — totalRevenue, totalPurchases,
  grossProfit (revenue − COGS), marginPct, salesCount
- ReportService.getStockValue() — current stock value from StockItemRepository
- ReportService.getTopSales(from, to) — top 7 by revenue; salesCount, sharePct per product
- ReportService.getWriteOffsSummary(from, to) — grouped by product+reason; row includes
  clientNames (comma-separated distinct clients) for GIFT rows
- ReportService.getTopClients(from, to) — top 7 by totalSpent
- ReportService.getMarginAnalysis(from, to) — includes profit (totalRevenue − totalCOGS) per product
- ReportService.getTrends(from, to) — monthly aggregation: labels (yyyy-MM), revenue[],
  purchases[], salesCount[]; fills all months in range with zeros to avoid chart gaps;
  passed to model as trendLabels/trendRevenue/trendPurchases/trendSalesCount for Thymeleaf
  inline JS; /reports/trends GET handled by ReportController.trends()
- trends.html — Chart.js 4 from CDN; line chart (revenue vs purchases, fill + tension 0.3);
  bar chart (sales count); labels formatted via Intl.DateTimeFormat using page lang;
  accent color read from CSS --accent var at runtime; "no data" state hides revenue chart;
  period selector identical to /reports; Back → /reports button in page-header (right edge);
  i18n: report.trends.title/report.trends.revenueVsPurchases/report.trends.salesActivity/btn.trends
- "Тренди" button in /reports page-header, right-aligned, same row as title on all screen sizes;
  local CSS override: .page-header { flex-direction: row !important } + .page-header .btn { flex: 0 !important }
  applied in both reports.html and trends.html to prevent global mobile column-stack override
- ReportController.resolvePeriod() extracted as private method — shared by /reports and /reports/trends
- Previous-period comparison in KPI banner: ▲+12.3% / ▼-5.1% inline in existing subtitle lines
  (no extra card height); previousPeriod() maps THIS_MONTH/LAST_MONTH → prev month,
  CUSTOM → same duration shifted back, ALL_TIME → null (hidden);
  delta(): prev=0 + curr>0 → +100% (growth signal), prev=0 + curr=0 → null (hidden);
  shown for revenue, purchases, gross profit
- ReportService.getSlowMovers(from, to) — products with stock > 0 but no SALE in period;
  uses StockMovementRepository.findProductIdsWithSalesBetween() +
  StockItemRepository.getStockSummaryPerProduct()
- ReportService.getEarliestMovementDate() — used for ALL_TIME preset
- StockItemRepository.getTotalStockValue() — SUM(quantity * purchasePrice)
- StockItemRepository.getStockSummaryPerProduct() — product id/name/unit + total qty (stock > 0)
- StockMovementRepository.findWriteOffsBetween() / findEarliestMovementDate()
- StockMovementRepository.findProductIdsWithSalesBetween() — for slow movers

## Security
- DB-based authentication via UserDetailsServiceImpl
- BCrypt password encoding
- All routes protected except /login, /logout, static resources (/favicon.svg, /css/**, /js/**, /images/**, /webjars/**)
- `/error` in permitAll — correct error page instead of redirect loop
- Default user: admin (change password after first login)
- Open redirect protection on all `returnTo` params: `safeRedirect()` accepts only `/[^/].*`

## Deploy
- Production: https://otchenashhair-warehouse.fly.dev/
- Platform: Fly.io (Amsterdam region, 1 shared machine, 512MB RAM)
- Database: Neon PostgreSQL (eu-central-1, Frankfurt)
- CI/CD: GitHub Actions
  - test.yml — triggers on push to develop and PRs to master; runs ./mvnw test (unit tests only, no DB required)
  - deploy.yml — triggers on push to master; runs unit tests first, then builds Docker image and deploys to Fly.io
  - deploy-on-comment.yml — triggers on PR comment "/deploy" by repo owner, same deploy flow
- Secrets managed via Fly.io secrets (DB_URL, DB_USERNAME, DB_PASSWORD,
  SPRING_PROFILES_ACTIVE, GEMINI_API_KEY)

## Local development
Run with VM option: -Dspring.profiles.active=dev
DB credentials in application-dev.properties (gitignored)

## OtchenashHair ClientCare — module architecture

### Decision (2026-05-13)
Application split into two logical modules within one Spring Boot app:
- **OtchenashHair Warehouse** — existing inventory management, not touched
- **OtchenashHair ClientCare** — new module: follow-ups, visits, protocols, AI recommendations

### Core rules
- Warehouse URLs unchanged — zero migration risk
- ClientCare URLs: `/clientcare/**`
- `/clients` — warehouse client directory (purchase history, stock operations)
- `/clientcare/clients` — ClientCare master client list (all clients, full profile, scalp photos)
- Shared domain entities (Client, Product, StockMovement) remain in `domain/` — neither module owns them
- No new entities in MVP wave — all data from existing stock_movements

### Package structure
```
com.hairmony.warehouse/
  domain/           ← SHARED (entities, repos — unchanged)
  repository/       ← SHARED (unchanged)
  config/           ← SHARED (unchanged)
  web/validator/    ← custom Bean Validation (ValidDateRange, DateRangeValidator)
  warehouse/        ← existing controllers/services stay here
    web/controller/
    service/
    web/dto/
  clientcare/       ← new module
    web/controller/
    service/
    web/dto/
```

### Layout and navigation
- `layout/main.html` — warehouse layout + module switcher pills in topbar
- `layout/clientcare.html` — ClientCare layout with sidebar: Огляд / Клієнти / Follow-up черга / Календар
- `CurrentUriInterceptor` — adds `activeModule` (warehouse/clientcare) and `currentUri` to model
- Mobile topbar: `[≡] OtchenashHair  [Склад] [ClientCare]` (pill switcher, active = accent color)
- Desktop: module switcher at top of sidebar
- ClientCare sidebar nav items: `bi-grid` Огляд→`/clientcare`, `bi-people` Клієнти→`/clientcare/clients`,
  `bi-list-check` Follow-up→`/clientcare/followups`, `bi-calendar3` Календар→`/clientcare/appointments`

### ClientCare — what's done

**Wave 1 — DONE (2026-05-13, no schema change)**

Data source: `StockMovementRepository.findClientSaleStats()` — groups SALE movements by client,
excludes cancelled SALEs (NOT EXISTS CANCELLATION with originalMovementId = sale.id).

Dashboard `/clientcare` — 8 KPI cards split into two labelled sections:
Section "ЗАПИСИ": "Пропущені записи" (`bi-calendar-x`, red, `?visitFilter=overdue`) + "Заплановані записи" (`bi-calendar-check`, green, `?visitFilter=scheduled`)
(section label and card names renamed from "ВІЗИТИ"/"Пропущені візити"/"Заплановані візити" → "ЗАПИСИ"/"Пропущені записи"/"Заплановані записи" in Wave 6, 2026-05-18)
Section "ПОКУПКИ":
| Card | Link |
|---|---|
| Кому написати (>30 days + phone) | `/clientcare/followups` |
| Давно не купували (>60 days) | `/clientcare/followups?minDays=60` |
| VIP без активності (top 20% + >30 days) | `/clientcare/followups?minDays=30` |
| Нещодавні клієнти (<14 days) | `/clientcare/followups` |
| Повторна покупка можлива (25–40 days) | `/clientcare/followups` |
| Всі клієнти | `/clientcare/followups` |

Visit KPI counts come from `visitRepository.findAllLatestWithNextVisitDate()` in `getDashboardData()`;
`ClientCareDashboardDto` has `overdueVisitCount` + `upcomingVisitCount` fields.
"Кому написати" uses `bi-chat-dots` icon (messaging, not phone).

`/clientcare/followups` — two independent filter rows:
- Purchase: All / 30+ / 60+ / 90+ days pills (`minDays` param)
- Visit: All / Прострочені / Заплановані / Без візиту pills (`visitFilter` param)
Both filters are AND conditions; each pill preserves the other dimension in href.
Filter logic: `if (minDays > 0 && (!c.hasPurchaseSignal() || daysSince < minDays)) return false;`
then `switch (visitFilter) { "overdue" → c.visitOverdue(); "scheduled" → today||upcoming; "none" → !hasVisitSignal; }`

Visit signals loaded via `findAllLatestWithNextVisitDate()` (no date threshold — all visits with nextVisitDate).
`VisitRepository.findAllLatestWithNextVisitDate()` replaced the old 7-day-threshold query.

Queue page layout: client count next to h1 (mobile: same row; desktop: subtitle below).
"Back" button hidden on mobile (`d-none d-md-inline-flex`).

Activity log button shows a small round badge in top-right corner with count of activity records.
Badge is rendered server-side from `activityCounts` map (`Map<Long, Integer>`) and updates
dynamically via `syncActivityCountBadge()` after every `loadActivity()` call.
`FollowUpRepository.countPerClient()` — `@Query` returning `[clientId, count]` pairs in one query.
`FollowUpService.getActivityCountsPerClient()` → `Map<Long, Integer>`, passed to model.

Activity modal footer: "Додати нотатку" (btn-success) + "Видалити всі записи" (btn-outline-danger, disabled until ≥1 record).
"Видалити всі записи" — confirm → `POST /clientcare/followups/{clientId}/activity/clear` → closes modal + reloads page
(page reload required because DONE/SNOOZE records are also deleted, changing client's queue section).
`FollowUpRepository.deleteAllByClientId()` — `@Modifying @Query`.
`FollowUpService.deleteAllActivity()`, `FollowUpActionController.clearActivity()` (`@ResponseBody`).

DONE action (Variant A): auto-hides client from queue for 7 days (`dueDate = today + 7`).
`isRecentlyDone()` checks `action == DONE && dueDate > today`. Both `isActiveSnoozed() || isRecentlyDone()` exclude from active queue.
"Виконані" collapsed section mirrors "Відкладені" — shows done clients with activity log + undo button.
`undoDone()` → `POST /returnToQueue` → page reload.
`returnToQueue()` bulk-deletes all active SNOOZE+DONE records with future dueDate via `deleteActiveHidingRecords()`.

Deleting DONE/SNOOZE from activity modal → closes modal + page reload (queue position changes).
Deleting NOTE → only reloads activity fragment (no position change).
`deleteNote(noteId, action)` in JS: `if action === 'DONE' || 'SNOOZE' → reload page; else loadActivity()`.

Thymeleaf 3.1 security: `th:onclick` with string concatenation blocked for event handlers.
Fix: use `th:data-*` attributes + static `onclick="fn(this.dataset.field)"`.

`ClientCareDashboardDto` fields: `totalClients` (all in DB), `totalClientsInQueue` (with non-cancelled SALEs),
plus 5 purchase KPI counts + `overdueVisitCount` + `upcomingVisitCount`.
"Всього клієнтів: N" — clickable link to `/clientcare/clients`; desktop: subtitle under h1; mobile: right-aligned on same row as h1 (inner `d-flex w-100`, outer `w-100` to fill page-header width).

**Wave 2a — DONE (2026-05-13, migration 012)**

Scalp Photos MVP — `ScalpPhoto` entity with Google Drive URL links; no OAuth, no file upload.

Entity stack: `domain/scalp/ScalpZone.java` (enum), `domain/scalp/ScalpPhoto.java`,
`repository/ScalpPhotoRepository.java` (findAllByClientIdOrderByTakenAtDescCreatedAtDesc),
`clientcare/service/ScalpPhotoService.java` (save with driveFileId regex extraction; delete with ownership guard; findById + update for edit),
`clientcare/web/controller/ScalpPhotoController.java` (GET/POST /clientcare/photos/new, GET/POST /clientcare/photos/{id}/edit, POST /clientcare/photos/{id}/delete, GET /clientcare/clients/{clientId}/photos),
`clientcare/web/dto/ScalpPhotoDto.java` (`@Pattern` + `@PastOrPresent` + `@Size(max=500)` on driveUrl + `@DateTimeFormat(ISO.DATE)` on takenAt).

Gallery page `/clientcare/clients/{clientId}/photos` — zone filter pills, CSS grid (`minmax(200px,1fr)`), Drive thumbnail (`sz=w400`), pencil edit + delete per tile.
`returnTo=${currentPageUrl}` passed from "Всі фото" link → gallery Back button returns to correct context (warehouse or clientcare).

Liquibase migration 012 — `scalp_photos` table + index on client_id.

**`/clientcare/clients` — ClientCare master client list (2026-05-13)**

`ClientController` maps to both `{"/clients", "/clientcare/clients"}` — one controller, two contexts.
`isClientCare(HttpServletRequest)` helper determines context via URI prefix.
- `GET /clientcare/clients` → `clientcare/clients/list.html` (clientcare layout, `findAll()`)
- `POST /clientcare/clients` (create) → redirect `/clientcare/clients`
- `POST /clientcare/clients/{id}/delete` → redirect `/clientcare/clients`
- Edit/create forms: use `/clientcare/clients/{id}/edit`, redirect back to clientcare context

**Shared client detail fragment:**
`fragments/client-detail.html` — single source of content for client detail page.
Both `clients/detail.html` and `clientcare/clients/detail.html` are thin wrappers (`th:insert`):
- `clients/detail.html` → `layout/main`
- `clientcare/clients/detail.html` → `layout/clientcare`

`ClientController.detail()` sets context-aware model attributes:
| Context | `backUrl` | `editUrl` | `currentPageUrl` |
|---|---|---|---|
| warehouse | `/clients` | `/clients/{id}/edit?returnTo=detail` | `/clients/{id}` |
| `from=clientcare` (followups) | `/clientcare/followups` | `/clients/{id}/edit?returnTo=detail` | `/clients/{id}?from=clientcare` |
| `/clientcare/clients/{id}` | `/clientcare/clients` | `/clientcare/clients/{id}/edit?returnTo=detail` | `/clientcare/clients/{id}` |
| `from=appointments` | `/clientcare/appointments?date=...` | `/clientcare/clients/{id}/edit?returnTo=detail` | `/clientcare/clients/{id}?from=appointments&date=...` |

"Додати фото" button lives in the "Фото шкіри голови" card header (not page header).

### ClientCare — roadmap

**Wave 2a — DONE — Scalp Photos + Client list + Shared detail fragment**

**Wave 2b — Google Drive direct upload — CANCELLED**
Decided not to implement. Photos are stored and viewed directly in Google Drive; app does not display or upload photos.

**Wave 2c — FollowUp entity — DONE (2026-05-14)**
`FollowUp` (id, client_id, action, dueDate, note, createdAt).
Actions: DONE / SNOOZE / NOTE. DONE auto-hides 7 days (dueDate = today+7). SNOOZE hides until dueDate.
`FollowUpRepository`, `FollowUpService`, `FollowUpActionController`, `ClientCareFollowupController`.
Activity log modal per client: AJAX fragment, add note, delete single record, "Видалити всі записи" button.
Activity count badge on log button (server-side + JS dynamic sync).

**Wave 3 — Visit entity — DONE (2026-05-13, migration 013)**
`Visit` entity: id, client_id, visit_date, complaint, scalp_condition, recommendations, next_visit_date, notes, created_at.
Migration 013 — `visits` table + index on client_id.
`domain/visit/Visit.java`, `repository/VisitRepository.java`, `clientcare/service/VisitService.java` (JPA, dirty-checking for updates).
`VisitController` at `/clientcare/visits` — CRUD; `nextVisitDate` field removed from form (stays in DB for legacy data).
`VisitDto` — `@DateTimeFormat(ISO.DATE)` on visitDate; `@Size(max=2000)` on all textarea fields.
`@ValidDateRange` and `VisitController.validateNextVisitDate()` removed — no longer needed since `nextVisitDate` not submitted from form.
Visit form — all textarea fields auto-resize (JS `scrollHeight`), uniform min-height.
Visits visible in both warehouse and clientcare client detail (shared fragment, no condition).
Card shows: date, скарга, стан, Рекомендації: ..., Нотатки: ..., наступний візит. Sorted by visitDate desc.

**Wave 3b — Google Drive client folder link — DONE (2026-05-14, migration 014)**
Link a Drive folder URL per client. App does not display photos — clicking "Відкрити папку" opens the folder in the browser.

Schema: fields on `Client` (migration 014):
- `drive_folder_url VARCHAR(500)` — source of truth, pasted by user
- `drive_folder_id VARCHAR(100)` — nullable, best-effort regex extract from URL

UI in shared client detail fragment — "Папка Google Drive" card:
- Not linked: "Додати папку" button
- Linked: "Підключена" badge + "Відкрити папку" (opens Drive in new tab) + pencil edit + × remove

Controllers: `DriveFolderController` — GET/POST `/clientcare/clients/{id}/drive-folder/edit`, POST `/clientcare/clients/{id}/drive-folder/remove`
Form: `drive-folder-form.html`, DTO: `DriveFolderDto` (`@NotNull clientId`, `@NotBlank @Size(max=500) @Pattern` on driveFolderUrl)
No Drive API, no Service Account — purely URL storage.

**Wave 3b-2 — Google Drive virtual gallery — CANCELLED**
Decided not to implement. Photos are viewed directly in Google Drive via "Відкрити папку" link.

**Wave 4 — Protocol entity — POSTPONED**
`Protocol` (id, name, products, durationDays)
Postponed: makes sense when the client base grows and repeat treatment courses become common.
Not relevant at the current stage of the salon.

**Wave 5 — Appointment Calendar — DONE (2026-05-17, migration 017)**
(see full spec in ClientCare — technical notes below)

`Appointment` entity: id, client_id (nullable), guest_name, guest_phone, start_at, end_at,
status (PLANNED/CONFIRMED/COMPLETED/CANCELLED/NO_SHOW), appointment_type, notes, created_at, updated_at.
DB CHECK constraint: `client_id IS NOT NULL OR NULLIF(TRIM(guest_name), '') IS NOT NULL`.
Business logic in service layer ensures either client or guest info is provided.

`AppointmentDto`:
- `@AssertTrue isClientOrGuestPresent()` — existing client or guest name required
- `@AssertTrue isEndAfterStart()` — endAt must be after startAt (replaces controller-level check)
- `@Size(max=150)` guestName, `@Size(max=50)` guestPhone, `@Size(max=2000)` notes
- `@DateTimeFormat(ISO.DATE_TIME)` on startAt/endAt for correct binding with datetime-local input

`AppointmentController` at `/clientcare/appointments`:
- `GET /` — day view with date param (defaults to today)
- `GET /` accepts `@RequestParam @DateTimeFormat(iso=ISO.DATE) LocalDate date` (auto-validated)
- Past appointment validation: cannot create/edit appointment with startAt < now() - 5 minutes
- `GET/POST /new` — create form; accepts `linkVisitId` param; after save calls `visitService.linkAppointment()` if set; respects `returnTo`
- `GET/POST /{id}/edit` — edit form; `editForm()` passes `formDate` to model; accepts/respects `returnTo`
- `POST /{id}/delete`
- `POST /{id}/status` — status transitions
- `@DateTimeFormat(iso=DATE)` on date params; `parseStartTime()` with silent fallback

`AppointmentService` — CRUD + `getDayAppointments(date)` + `changeStatus(id, status)` + `getAppointmentsByDateForClient(clientId)`.
`AppointmentService.save()` returns `Long` (saved appointment ID) — needed for `linkAppointment()` call.

Calendar form shows ALL validation errors in unified `<ul>` via `#fields.allErrors()` — works with both field and @AssertTrue errors.

Calendar `/clientcare/appointments` — FullCalendar v6 (CDN), day/week/month views with view switcher in toolbar.
Drag-and-drop and resize: `POST /{id}/reschedule?start=...&end=...` — only PLANNED/CONFIRMED events are draggable (editable:false for others).
Click event → edit form. Click empty slot → new appointment form (preserves link-visit context params).
`GET /clientcare/appointments/api?start=...&end=...` — JSON event feed; strips timezone suffix from params for robust LocalDateTime parsing.
`toCalendarEvent()` in controller: builds FC event JSON with id/title/start/end/editable/extendedProps(status,clientId,phone,editUrl).
  backgroundColor/borderColor/textColor removed from backend — all coloring done client-side via `eventDidMount` in `day.html`.
Event coloring (client-side, `eventDidMount`): PLANNED=warning-subtle (#fff3cd/#664d03), CONFIRMED=accent green (#4a7c59),
  CANCELLED=red (#dc3545), NO_SHOW=orange (#fd7e14), COMPLETED=secondary-subtle (#e2e3e5/#41464b);
  overdue (PLANNED/CONFIRMED + start < now + end ≤ now)=danger-subtle (#f8d7da/#842029);
  in-progress (PLANNED/CONFIRMED + start ≤ now + end > now)=success-subtle (#d1e7dd/#0a3622).
  Text of light-bg events forced bold via `querySelectorAll('div,span').forEach(el => el.style.color = text)`.
Calendar CSS overrides (project palette): `--fc-event-bg-color:#6b9e79`, today column header=accent bg+white text,
  today date circle in month view, green more-link; today column fill `--fc-today-bg-color:#f1f9f2` (accent-light).
Day view: column header row hidden (`display:none`); weekday name shown in nav toolbar (mobile: via `updateMobileToolbar()`;
  desktop: dynamic `<div class="fc-title-weekday">` injected into `.fc-toolbar-title` in `datesSet` callback).
View preference persisted in `localStorage('fcView')`; defaults to `timeGridDay` on mobile, `timeGridWeek` on desktop.
Locale: Spring `#locale.language` mapped to FullCalendar locale (uk/pl/en); `@fullcalendar/core locales-all.global.min.js` loaded from CDN.
CSRF: reschedule POST sends token as request param (same as form submissions).
ClientController.detail() loads `appointmentsByDate` for clientcare/appointments context (`from=appointments`).

**Wave 5b — Calendar-based time selection from visit card — DONE (2026-05-18)**
"Запланувати →" → calendar → click slot → appointment form pre-filled with time, clientId, linkVisitId, returnTo.
"Переглянути календар →" button shown when `linkVisitId != null or clientLocked eq true`.

**Wave 6 — Appointment completion flow — DONE (2026-05-18)**
"Завершити прийом →" on edit page → COMPLETED + redirect to visit form.
Overdue: amber banner + "Прийшов" / "Не прийшов" buttons.
NO_SHOW: redirect to edit page with action panel ("Черга follow-up" / "Записати повторно").
See full spec in TODO → Wave 6 section.

**Wave 7 — Gift Certificates — DONE (2026-05-20, migration 020)**
Full gift certificate lifecycle: issue, cancel, restore, delete. Redemption happens via visit payment only.
`domain/gift/GiftCertificate.java` — JPA entity (id, code, purchaser_client_id, purchaser_name, purchaser_phone,
  recipient_client_id, recipient_name, recipient_phone, service_id, service_name, status, notes, expires_at,
  issued_at, redeemed_at, cancelled_at). FK refs to clients/services stored as plain Long columns (not @ManyToOne).
`domain/gift/GiftCertificateStatus.java` — enum: ACTIVE / REDEEMED / EXPIRED / CANCELLED.
`repository/GiftCertificateRepository.java` — `findAllByOrderByIssuedAtDesc()`, `findByStatusOrderByIssuedAtDesc()`,
  `findForClient()` (@Query), `expireOverdue()` (@Modifying JPQL UPDATE).
`GiftCertificateService` — `@Transactional`; calls `syncExpired()` (expireOverdue) before every list query;
  `issue()` auto-creates client records for free-text purchaser/recipient and stores phone.
  Status transitions (`cancel/restore`) return the updated entity (used for success message code).
  `restore()` — CANCELLED → ACTIVE (clears cancelledAt).
  `delete()` — hard delete; throws if status is REDEEMED (redemption is permanent).
`GiftCertificateController` at `/clientcare/gift-certificates`:
  - `GET /` — two modes: `?clientId=N` → client view (all statuses, no filter bar);
    standalone → status filter (server-side) + client instant search (client-side, `data-purchaser`/`data-recipient`).
  - `GET/POST /new` — issue form; `purchaserId` param pre-fills purchaser dropdown.
  - `GET /{id}` — detail page; status badge in page-header next to cert code.
  - `POST /{id}/cancel|restore|delete` — status transitions and hard delete.
  - No `POST /{id}/redeem` — redemption is triggered by visit payment (CERTIFICATE method in visit form).
Client detail fragment: shows only the latest cert; "Переглянути всі →" link to `?clientId=N` when >1.
List page: status filter + client name instant search (placeholder "За ім'ям клієнта в сертифікаті").
Code generation: 8-char alphanumeric (4+4 dash-separated), unambiguous alphabet (excludes 0/O, 1/I/L, 5/S, 8/B).

**Wave 8 — Visit Billing Fields — DONE (2026-05-21, migration 021)**
Architecture decision: Appointment = attendance only. Visit = protocol + payment.
All financial fields moved from Appointment to Visit.
Migration 021 adds to `visits`: `service_id BIGINT FK → services ON DELETE SET NULL`,
  `price_at_time NUMERIC(10,2)`, `payment_method VARCHAR(20)`,
  `is_paid BOOLEAN NOT NULL DEFAULT FALSE`, `certificate_code VARCHAR(20)`.
`Visit.java` — real JPA `@Column` fields (was `@Transient` in draft).
`VisitDto` — added: `serviceId`, `priceAtTime` (@DecimalMin), `paymentMethod`, `paid`, `certificateCode` (@Size max=20).
`VisitService.applyCertificatePayment()` — validates cert code, sets REDEEMED + redeemedAt in same transaction.
`VisitService.getClientIdsWithUnpaidVisits()` — `Set<Long>` via `VisitRepository.findClientIdsWithUnpaidVisits()`.
`VisitController` — `populateFormModel()` helper loads activeServices + paymentMethods; `rejectCertificateError()` helper.
Visit form billing section: service select, payment method select, price input (hidden when CERTIFICATE),
  certificate code input (hidden when not CERTIFICATE), paid checkbox (accent color via CSS var override).
JS submit guard: price=0 → "Ціну не вказано. Зберегти?"; price>0 + !paid → "Оплату не підтверджено. Зберегти?".
Payment badges in visit cards: "Очікує оплати" (red, shown when price/method set but !paid);
  "Оплачено" (green) — in both `fragments/client-detail.html` and `clientcare/clients/visits.html`.
`AppointmentDto` — all financial fields removed. Appointment form billing section removed.
`ClientCareDashboardDto` — added `unpaidVisitCount` field.
`ClientCareService` — computes `unpaidVisitCount` via `visitService.getClientIdsWithUnpaidVisits().size()`.
Dashboard KPI card "Очікує оплати" — in "ЗАПИСИ" section, hidden when count=0, links to `/clientcare/clients`.
Client list — `−$` icon (red, bold, 1rem) next to name when client has unpaid visits;
  `clientsWithUnpaidVisits` Set passed to model only in clientcare context.
Font consistency — `body { font-family: var(--bs-body-font-family) }` added to both layouts.

---

## ClientCare — technical notes

### Gift Certificates (Wave 7 — DONE, 2026-05-20; updated Wave 8, 2026-05-21)
- `GiftCertificate` JPA entity in `domain/gift/`; FK references to clients/services stored as plain Long columns (no lazy-load issues in templates)
- `GiftCertificateStatus` enum in `domain/gift/` (moved from dto package)
- `syncExpired()` called before every list query — batch UPDATE via `@Modifying` JPQL (not per-row)
- Two list modes: `clientId` param → client view (all statuses); standalone → status filter + client-side search
- Client instant search: JS reads `data-purchaser`/`data-recipient` row attrs, `filterByClient()` hides non-matching rows
- Issue flow auto-creates client records for free-text purchaser and recipient (same pattern as before)
- Status transitions return updated entity so controller can use `cert.getCode()` for flash message
- `GiftCertificateFormDto` stays in `clientcare/web/dto/` (form validation only)
- **No redeem button anywhere in UI** — redemption happens only when user selects CERTIFICATE payment in visit form
- `restore()` transitions CANCELLED → ACTIVE (not REDEEMED); clears `cancelledAt`
- `delete()` hard-deletes; guards against deleting REDEEMED certs (permanent records)
- Detail page: status badge sits in page-header inline with cert code (left side), "Назад" on right
- Client detail fragment: shows only latest cert; "Переглянути всі →" appears only when >1 cert

### Scalp Photos (Wave 2a — DONE)
- `driveUrl` is the source of truth; `driveFileId` nullable (best-effort regex extract)
- No Google API, no OAuth — Wave 2a is manual link mode only
- `ScalpPhotoService` in `clientcare/service/`; controller at `ScalpPhotoController` (no class-level @RequestMapping, full paths per method)
- Gallery page at `/clientcare/clients/{clientId}/photos` — zone filter pills, CSS grid, thumbnail preview via `drive.google.com/thumbnail?id={fileId}&sz=w400`; "Відкрити фото" button removed (tapping tile opens Drive directly)
- Gallery Back button uses `returnTo` param (passed from "Всі фото" link as `currentPageUrl`) — returns to correct context (warehouse `/clients/{id}` or clientcare `/clientcare/clients/{id}`)
- Zone filter pills preserve `returnTo` on each link
- Detail page shows last 6 photos as compact grid (`minmax(110px,1fr)`); "Всі фото" link passes `returnTo=${currentPageUrl}`
- Thumbnail fallback: `onerror` hides `<img>`, shows `bi-image` icon (handles private/broken files)
- "Додати фото" button lives in the "Фото шкіри голови" card-header
- `ScalpPhotoDto` — `@DateTimeFormat(ISO.DATE)` on `takenAt` (required for edit form date binding)

### Visits (Wave 3 — DONE, updated 2026-05-21)
- `VisitService` fully JPA-based; update uses dirty checking (no explicit save)
- `@DateTimeFormat(ISO.DATE)` mandatory on all `LocalDate` DTO fields for `<input type="date">` edit binding
- `VisitController.validateNextVisitDate()` removed — cross-field validation was in DTO; `nextVisitDate` field removed from form entirely (field stays in DB for legacy data)
- `@ValidDateRange` annotation removed from `VisitDto` — no longer needed since `nextVisitDate` not submitted from form
- Auto-resize textareas: `resize:none; overflow:hidden; min-height:2.6rem` + JS `scrollHeight` on `input` event + on page load
- `VisitService.save()` returns `Long` (saved visit ID) — needed for action=schedule redirect
- `returnTo` support: GET/POST /new and /{id}/edit accept `returnTo` param; form passes it as hidden input; `safeRedirect()` used in controller
- Visit edit links in `clients/visits.html` pass `returnTo=/clientcare/clients/{id}/visits` so Back navigates to visits list
- **Billing fields (Wave 8, migration 021):** `serviceId`, `priceAtTime`, `paymentMethod`, `paid`, `certificateCode` — real JPA columns
- `applyCertificatePayment()`: CERTIFICATE method → validates code, sets cert REDEEMED + redeemedAt, sets visit.paid=true
- `getClientIdsWithUnpaidVisits()` → `Set<Long>`; used by ClientCareService (dashboard KPI) and ClientController (list icons)
- Visit form: `id="visitForm"` required — JS `getElementById` targets correct form (layout logout form is also a `<form>`)
- Payment warning JS: price=0 → confirm "Ціну не вказано"; price>0 + !paid → confirm "Оплату не підтверджено"
- Visit card layout: next-appointment indicators live inside `flex:1` column (same as visits.html); recommendations not text-muted; notes fst-italic

**Visit → Appointment link (migration 018, 2026-05-17):**
`visits.next_appointment_id BIGINT FK → appointments(id) ON DELETE SET NULL` — links a visit to its planned next appointment.

`VisitDto` fields added: `nextAppointmentId` (Long), `nextAppointmentStartAt` (LocalDateTime) — read-only, populated from `Visit.nextAppointment`.

`VisitService.linkAppointment(visitId, appointmentId)` — sets `visit.nextAppointment` via dirty checking; called by `AppointmentController` after saving new appointment when `linkVisitId` param is present.

`VisitService.unlinkCompletedAppointment(appointmentId)` — clears `nextAppointment` on whichever visit points to this appointment; called by `AppointmentController.complete()` after status → COMPLETED so the old visit card no longer shows a stale green "Наступний запис" badge. `VisitRepository.findByNextAppointmentId(Long)` Spring Data derived query added.

**Visit form appointment status section:**
- Replaces old `nextVisitDate` date picker
- No appointment: amber alert "Наступний візит не запланований" + "Запланувати →" button (action=schedule)
- Appointment exists: green alert "Наступний запис: DD.MM.YYYY о HH:mm" + "Відкрити →" link to calendar day

**action=schedule flow:**
1. User clicks "Запланувати →" in visit form → POST with `action=schedule`
2. `VisitController` saves visit (or updates), gets `visitId`
3. Redirects to `/clientcare/appointments/new?clientId=X&linkVisitId={visitId}&returnTo=/clientcare/visits/{visitId}/edit`
4. User creates appointment; `AppointmentController.save()` detects `linkVisitId` → calls `visitService.linkAppointment()`
5. Redirects to `returnTo` (visit edit form), which now shows green appointment status

**visit card in client-detail / visits list:**
- Shows green "Наступний запис: дата о час" when `nextAppointmentId != null`
- Shows amber "Не запланований" + "Запланувати →" link when null; link: `/clientcare/appointments/new?clientId=X&linkVisitId=Y&returnTo={currentPageUrl}`

**AppointmentController /{id}/edit (fixed 2026-05-17):**
- `editForm()` now accepts `returnTo`, passes `formDate` to model (was missing — Cancel button navigated to root `/`)
- POST `/{id}/edit` accepts `returnTo`, redirects there after successful update

### FollowUp queue (Wave 2c — DONE)
- `FollowUp` entity: id, client_id, action (DONE/SNOOZE/NOTE), dueDate (nullable), note VARCHAR(500), createdAt
- No status field — state derived from latest record per client: `isActiveSnoozed()` = SNOOZE + dueDate > today; `isRecentlyDone()` = DONE + dueDate > today
- `returnToQueue()` bulk-deletes all SNOOZE+DONE with future dueDate (not just latest) via `@Modifying @Query`
- Activity count badge: `FollowUpRepository.countPerClient()` one query for all clients; `FollowUpService.getActivityCountsPerClient()` → `Map<Long, Integer>`; badge has `data-activity-badge="{clientId}"` for JS sync
- `syncActivityCountBadge()` in JS: counts `#activityLog [data-action]` rows after each `loadActivity()`; creates badge DOM node if wasn't rendered server-side (was 0 on load); disables "Видалити всі" btn when count=0
- "Видалити всі записи" always does page reload after success (DONE/SNOOZE records deleted → client position changes)
- Thymeleaf 3.1 blocks string expressions in `th:onclick` — use `th:data-*` + `onclick="fn(this.dataset.field)"`
- Two-filter system: `minDays` (purchase) AND `visitFilter` (visit) are independent; each pill href preserves the other param; controller applies AND logic
- Visit signals replaced by appointment signals: `ClientCareService` now uses `AppointmentRepository` instead of `VisitRepository` for followup queue — `visits.next_visit_date` field no longer drives the queue
- FollowUpActionController: `@Validated` on class; `@Min(1) @Max(365)` on snooze `days`; `@Size(max=500)` on note/text;
  all `returnTo` params validated via `safeRedirect()` helper

**Note:** All FollowUpActionController POST endpoints use `safeRedirect(returnTo, "/clientcare/followups")` —
returnTo must start with `/` and NOT be protocol-relative (`//evil.com` blocked).

### Google Drive folder (Wave 3b — DONE)
- No Drive API, no Service Account — purely URL storage per client
- `Client` has `driveFolderUrl` (source of truth) and `driveFolderId` (nullable regex extract)
- `DriveFolderController`: GET/POST edit form, POST remove; validation via `@Pattern` + `@Size(max=500)` + `@NotNull clientId` on `DriveFolderDto`
- "Відкрити папку" opens `client.driveFolderUrl` in browser (`target="_blank"`)
- Card visible in shared `fragments/client-detail.html` for both warehouse and clientcare contexts

### Security
- `/error` added to Security permitAll — always show real error page instead of redirect loop
- `GlobalExceptionHandler` catches `EntityNotFoundException`, `DataIntegrityViolationException`,
  `ConstraintViolationException`, `MethodArgumentTypeMismatchException` — redirects with flash errorMessage
  - Handles DB truncation errors (DataIntegrityViolationException) gracefully with user-friendly message
  - Prevents white-label error pages for common validation failures
- `@ControllerAdvice` also logs all exceptions at WARN level with context (no sensitive data)
- `ConstraintViolationException` covers `@Validated @RequestParam` failures (e.g., snooze days out of range)
- Open redirect: `safeRedirect(returnTo, fallback)` — accepts only `/[^/].*` regex

### Event architecture (future, after Wave 6)
Domain events (published AFTER_COMMIT):
- `SaleCompletedEvent` → `FollowupService.analyzeAfterSale()`
- `PurchaseCompletedEvent`
- `WriteOffCompletedEvent`

Rules:
- `@TransactionalEventListener(phase = AFTER_COMMIT)` — mandatory, prevents events on rolled-back txns
- Expiry scanning via `@Scheduled(cron = "0 9 * * *")` — not a real-time event
- `StockDepletedEvent` fires on threshold crossing only (`previousQty > min && newQty <= min`)
- Thin listeners: listener calls service, logic lives in service

## TODO

### ClientCare
- Wave 2b — CANCELLED — photos not displayed in app, viewed directly in Google Drive
- Wave 2c — DONE — FollowUp entity with DONE/SNOOZE/NOTE; activity log modal; count badge; "Видалити всі" button
- Wave 3 — DONE — Visit entity with JPA + migration 013
- Wave 3b — DONE — Google Drive folder link per client: migration 014, fields on Client, DriveFolderController, "Відкрити папку" button in shared client detail fragment
- Wave 3b-2 — CANCELLED — virtual gallery not needed
- Wave 4 — Protocol entity — POSTPONED (treatment type → recommended product list; not relevant at current stage)
- Wave 5 — DONE — Appointment Calendar: migration 017, AppointmentController, AppointmentService, AppointmentDto, day view
  (see details in "Appointment Calendar" section above — NOT in TODO)
- Wave 5b — Calendar-based time selection from client detail visit card — DONE (2026-05-18)
- Wave 6 — Appointment completion flow — DONE (2026-05-18)
- Wave 7 — Gift Certificates — DONE (2026-05-20) — migration 020, JPA entity, service, controller, client detail block, list with status+client filter
- Wave 8 — Visit Billing Fields — DONE (2026-05-21) — migration 021, financial fields on Visit, certificate redemption via visit, unpaid KPI card + client list icon

**Note:** Wave 5 spec was moved to main "What's done" section. TODO reflects only remaining work.

---

### Wave 6 — Appointment completion flow — DONE (2026-05-18)

**Problem:** There is no Appointment → Visit link. Current link is one-directional: Visit → Appointment (via `next_appointment_id`).
When a client arrives and the appointment is completed, the user has no guided path to record what happened (visit protocol).

**Gap in current flow:**
- Appointment status can be changed to COMPLETED via status buttons in the calendar
- But there is no prompt to create a Visit record for that session
- User must manually navigate to `/clientcare/visits/new?clientId=X` — not intuitive

**Options discussed:**
- **Variant A (MVP, preferred):** Add "Завершити прийом" button on the appointment edit/view page.
  Click → sets status=COMPLETED + redirects to visit form with `clientId` and `visitDate=today` pre-filled.
  After saving the visit → return to calendar. No schema change needed.
- **Variant B:** Bidirectional link — add `appointment_id FK` on visits table (migration required).
  Enables: from completed appointment → see its protocol; from visit → see the source appointment.
  Required if scalp photos must be linked to a specific appointment, not just a client.
- **Variant C:** Inline quick-notes form on status change to COMPLETED — more invasive, skipped for now.

**Decisions (2026-05-18):**
1. Single user — form must be as fast as possible, minimal clicks
2. Stock write-offs are NOT linked to visits — managed separately via /movements/expense
3. Scalp photos linked to client only, not to visit/appointment — no bidirectional FK needed

**Implementation: Variant A. No schema change.**
- "Завершити прийом і записати протокол →" button on appointment edit page (shown when status = PLANNED or CONFIRMED and client is linked, not a guest)
- `canComplete` boolean set in `AppointmentController.editForm()`, passed to model
- Button is a separate `<form>` with `POST /{id}/complete` action (outside the main appointment form)
- `AppointmentController.complete()`: changes status → COMPLETED, calls `visitService.unlinkCompletedAppointment(id)` to clear stale nextAppointment on old visit, redirects to `/clientcare/visits/new?clientId=X&returnTo=/clientcare/appointments?date=YYYY-MM-DD`
- Visit form pre-fills clientId and visitDate=today (existing behavior)
- After saving the visit → return to calendar day (via returnTo)
- Variant B (appointment_id FK on visits) — NOT needed given current requirements

**CRITICAL: nested form bug (fixed 2026-05-19):**
HTML does not allow nested `<form>` elements — browsers silently ignore inner form tags. The overdue/complete/noshow action blocks (`<form th:action="…/complete">` etc.) were originally placed INSIDE the main appointment `<form>`, causing "Завершити прийом" to submit the outer save form instead of `POST /{id}/complete`. Fix: all three blocks moved to AFTER the main `</form>`, still inside the card-body but as sibling elements.

**Overdue appointment UX (2026-05-18):**
- `isOverdue` boolean = `canComplete && startAt < now`; passed to model in `editForm()`
- When overdue: amber banner "Час прийому минув" + two buttons replace the single complete button:
  - "Прийшов — записати протокол" → `POST /{id}/complete` (same flow)
  - "Не прийшов" → `POST /{id}/status?status=NO_SHOW`
- When `canComplete and !isOverdue`: regular single "Завершити прийом →" button shown

**NO_SHOW post-action flow (2026-05-18):**
- `POST /{id}/status?status=NO_SHOW` redirects to `GET /{id}/edit` (not to calendar)
- Edit page detects `isNoShow` = `status == NO_SHOW && clientId != null`; shows action panel:
  - Gray alert with `bi-person-x-fill text-danger` icon
  - "Черга follow-up" → `/clientcare/followups?visitFilter=overdue`
  - "Записати повторно" → `/clientcare/appointments/new?clientId=X&appointmentType=X&notes=X&rebookedFromId=X`
- Client stays in follow-up queue (FOLLOWUP_OVERDUE_STATUSES includes NO_SHOW)

**NO_SHOW rebook flow (2026-05-19):**
- "Записати повторно" passes `appointmentType`, `notes`, `rebookedFromId` (original appointment ID) as URL params
- `AppointmentController.newForm()` accepts these params; pre-fills DTO with type and notes; calls `addMissedAppointmentBanner()`
- `addMissedAppointmentBanner()`: loads original appointment's `startAt`, puts `missedAppointmentAt` + `rebookedFromId` in model
- `form.html` shows amber banner above the card: `bi-person-x-fill text-danger` + "Клієнт не з'явився на прийом DD.MM.YYYY о HH:mm"
- `rebookedFromId` persisted as hidden input in form — banner survives validation errors

**Client field locked in edit mode (2026-05-19):**
- `AppointmentController.editForm()` always calls `lockClientForEdit(dto, model)` — no toggle or dropdown shown
- `lockClientForEdit()`: sets `clientLocked=true`; for existing client → loads name from service; for guest → shows "guestName · guestPhone"
- `clientLocked` template block updated: existing client submits hidden `clientId`; guest submits hidden `guestName` + `guestPhone`
- Same lock applied in `update()` error re-render paths

**KPI status sets split (2026-05-18):**
- `ClientCareService` now has two overdue constants:
  - `FOLLOWUP_OVERDUE_STATUSES = [PLANNED, CONFIRMED, NO_SHOW]` — follow-up queue signal (NO_SHOW = needs contact)
  - `KPI_OVERDUE_STATUSES = [PLANNED, CONFIRMED]` — dashboard "Пропущені записи" card (NO_SHOW already handled)
- Dashboard "Пропущені записи" count drops to 0 after NO_SHOW (user already acted)
- Follow-up queue still shows NO_SHOW clients as overdue signal

**Drag-and-drop past prevention (2026-05-18):**
- `day.html`: `eventAllow: (dropInfo) => dropInfo.start >= new Date()` — blocks drag to past client-side
- `AppointmentService.reschedule()`: server-side guard — throws if `newStart < now - 5min`

**Follow-up overdue/upcoming fix (2026-05-18):**
- `ClientCareService` now uses `now` (not `todayStart`) as the boundary for upcoming vs. overdue
- Appointments earlier today that have already passed are correctly counted as overdue
- Applied in both `getFollowupQueue()` and `getDashboardData()`

**Follow-up queue same-day overdue fix (2026-05-19):**
- Bug: appointment today (time passed, still PLANNED/CONFIRMED) → `DAYS.between(today, today) = 0` → `visitToday()` = true → appeared in `visitFilter=scheduled` instead of `overdue`
- Fix: in `getFollowupQueue()` overdue loop, if `days == 0` force to `-1` — appointment confirmed past by `findOverdueForClients(startAt < now)` but same calendar date
- `visitOverdue()` checks `days < 0`; `visitToday()` checks `days == 0` — now correctly separated

**"Переглянути календар" button fix (2026-05-18):**
- Condition changed from `th:if="${linkVisitId != null}"` to `th:if="${linkVisitId != null or clientLocked eq true}"`
- Button now persists even when `linkVisitId` is lost from URL after calendar round-trip (client still pre-selected)
- `eq true` handles null `clientLocked` gracefully (SpEL `or` with null throws)

**Dashboard KPI labels (2026-05-18):**
- Section label "Візити" → "Записи" (uk), "Wizyty / Zapisy" (pl), "Appointments" (en)
- Cards: "Пропущені візити" → "Пропущені записи", "Заплановані візити" → "Заплановані записи"

---

### Wave 5b — Calendar-based time selection from client detail visit card — DONE (2026-05-18)

**Flow:** "Запланувати →" on visit card → appointment form → "Переглянути календар →" → day calendar → click slot → form pre-filled with time, clientId, linkVisitId, returnTo preserved throughout.

**Implementation:**
- `AppointmentController.dayView()` — accepts optional `clientId`, `linkVisitId`, `returnTo`; adds to model as `calClientId`, `calLinkVisitId`, `calReturnTo`
- `appointments/day.html` — prev/next nav links and FAB use `@{...}` with null-safe params (Thymeleaf omits null); JS inline vars `CAL_CLIENT_ID/CAL_LINK_VISIT/CAL_RETURN_TO`; click-to-create handler appends params to URL via `encodeURIComponent`
- `appointments/form.html` — "Переглянути календар →" button shown when `linkVisitId != null or clientLocked eq true`; `updateCalendarBrowseLink()` builds URL from `startDate` input + clientId select/hidden + BROWSE_LINK_VISIT/BROWSE_RETURN_TO inline vars; called on page load, on date change, on client change

### Warehouse features
- Low stock email notifications — daily digest when items drop below minStockLevel;
  Spring @Scheduled + spring-boot-starter-mail
- Export to Excel — reports page + movement history; Apache POI (xlsx)
- Inventory count / stock-take — formal workflow: enter physical counts per product,
  system auto-generates ADJUSTMENT movements for the differences;
  scanner page `/scan?mode=stocktake` as dedicated entry point — add back to sidebar as "Інвентаризація"
  (standalone "Сканер" nav link was removed 2026-05-19 as redundant — income/expense already go to /scan with mode pre-selected)
- User roles (ADMIN/OPERATOR) — Role entity already exists; @PreAuthorize on
  delete/cancel/deactivate endpoints to restrict to ADMIN only;
  IDOR protection needed when roles are introduced:
  - DriveFolderController — verify operator can only edit clients they own
  - ScalpPhotoController — verify photo belongs to client accessible by operator
  - Implementation pattern TBD when roles are designed (service-layer ownership check recommended)
- Print barcode labels — printable label with product name + barcode from product detail page
- AI: conversation history / multi-turn chat (currently stateless per request)

### Infrastructure
- Spring Session for multi-machine session sharing (if needed when scaling beyond 1 machine)
- User management page (if multiple users needed)
- Swagger/OpenAPI — intentionally skipped: app is server-rendered Thymeleaf MVC,
  not a REST API; only 3 internal @ResponseBody endpoints (barcode/search);
  revisit if a mobile app or external integrations are added
- PWA service worker / offline cache — intentionally skipped in MVP; add if offline resilience needed

### Quality
- Unit tests written (38 tests, no DB required, run with ./mvnw test):
  - StockServiceTest — FIFO deduction (single/multi-batch, exact/partial, insufficient stock),
    cancel guards (double-cancel, cancel-of-cancellation, partially used purchase, null batch),
    cancel success paths (expense restores stock item, purchase zeroes batch,
    CANCELLATION movement linked via originalMovementId), getCancelledMovementIds empty shortcut
  - StockDashboardRowDtoTest — status OK / LOW (= min, < min) / OUT (0, 0.000)
  - ReportServiceTest — revenue, gross profit, margin%, salesCount, top sales grouping +
    share%, top clients sorting + null-client exclusion, margin analysis sorting
  - ProductServiceTest — 7 tests
  - WarehouseApplicationTests — @Disabled (requires live PostgreSQL, run manually with dev profile)
