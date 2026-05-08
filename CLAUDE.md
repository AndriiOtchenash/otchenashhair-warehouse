# CLAUDE.md

## Project: OtchenashHair Warehouse
Inventory management for a trichology salon.

## Tech stack
Java 21, Spring Boot 3.4.5, Thymeleaf, PostgreSQL, Liquibase,
Spring Security, Lombok, DevTools, Spring Data JPA, JPA Specifications.

## Package: com.hairmony.warehouse

## Architecture
domain/ — JPA entities (category, client, product, stock, supplier, user)
repository/ — Spring Data JPA + JpaSpecificationExecutor for movements
service/ — business logic (ProductService, StockService, ClientService,
           SupplierService, CategoryService, MovementHistoryService,
           ReportService, AiAssistantService, UserService, UserDetailsServiceImpl)
web/controller/ — MVC controllers (thin):
  DashboardController, ProductController, ClientController,
  SupplierController, CategoryController, StockController (income/expense/cancel),
  StockItemController (/stock/items/{id}/edit), MovementController (/movements/history),
  ProfileController, LoginController, ReportController, AiController
web/dto/ — form objects and filter DTOs
web/interceptor/ — CurrentUriInterceptor
web/formatter/ — QuantityFormatter (@qf bean)
config/ — SecurityConfig, LocaleConfig, WebMvcConfig

## Key Rules
- ddl-auto=none, Liquibase manages schema (migrations 001-010)
- Controllers are thin, logic in services
- Never pass entities to templates, use DTOs
- Dirty checking for updates — no explicit save() on managed entities
- Credentials in application-dev.properties (gitignored)
- spring.profiles.active=dev (via VM options in IDE: -Dspring.profiles.active=dev)
- Production profile: application-prod.properties

## DB
Local: hairmony_dev, user: warehouse_user
Production: Neon PostgreSQL (credentials via Fly.io secrets)
Migrations: 001-users, 002-suppliers, 003-clients, 004-products,
            005-stock-items, 006-stock-movements, 007-categories,
            008-fix-categories, 009-insert-admin-user,
            010-add-movement-cancel (adds original_movement_id FK on stock_movements)

## What's done
- Dashboard (/) with 4 KPI filter cards (All/In stock/Attention/Out), server-side
  activeStatus param + client-side toggle; filters (status/category/brand/search),
  clickable rows → product detail, active filter highlight (.filter-active)
  KPI "countOk" = OK+LOW, "lowStock" = LOW+OUT, "countOut" = OUT only
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
  "Створити нового клієнта →" link pre-selects new client on return
- Barcode scanner page (/scan) — camera scan or manual entry, income/expense mode
- MovementType enum: PURCHASE, SALE, WRITE_OFF, ADJUSTMENT, CANCELLATION
- StockDashboardRowDto with status OK/LOW/OUT
- Movement journal (/movements/history) — server-side filtering (JPA Specifications),
  server-side pagination PAGE_SIZE=100 (MovementHistoryService), filters: date range,
  type, product name (text LIKE search), counterparty (text LIKE);
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
  stock value snapshot; top sales with salesCount and share%; write-offs summary;
  top clients (clickable → client detail); purchases by supplier; margin analysis;
  configurable expiry alert window
- i18n: uk (primary), pl, en; all UI strings via #{} — no hardcoded text in templates
- QuantityFormatter (@qf bean) — integers for PCS, decimals for ML/G
- CurrentUriInterceptor — active nav highlighting
- Language switcher preserves URL params via JS switchLang()
- Spring Security with DB authentication (users table)
- Login page with show/hide password
- Change password page (/profile/change-password)
- Logout in sidebar
- Mobile responsive layout with burger menu and topbar;
  secondary table columns hidden on mobile via d-none d-md-table-cell
- Client-side search on all list pages with × clear button (mobile only, appears on input)
- Collapsible create forms on list pages
- Clickable table rows on mobile
- Cancel buttons on stock income/expense forms (back to dashboard)
- Mobile nav: Income/Expense links → scanner (/scan?mode=income/expense) on mobile,
  form pages (/movements/income/expense) on desktop (Bootstrap d-none/d-flex split)
- DevTools enabled (dev profile only)
- AI Assistant page (/ai) — chat widget backed by Google Gemini 2.5 Flash
  (v1beta endpoint); builds warehouse context (stock levels + last 30-day
  movements) and calls Gemini via RestClient (no extra deps); response
  language follows active locale (uk→Ukrainian, pl→Polish, en→English);
  AJAX, no page reload; 3 quick-question buttons; fully i18n'd UI;
  API key via GEMINI_API_KEY env var / gemini.api.key in dev properties

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
  form page shows "Створити новий X →" link under the select with color:var(--accent)
- Flash messages: successMessage (alert-success) and errorMessage (alert-danger),
  rendered inline in each template (not in layout); dismissible
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
- Detail pages layout: info strip (d-flex flex-wrap gap-4) above history table,
  phone/contact in page header subtitle, no duplicate data
- Mobile tables: hide secondary columns with d-none d-md-table-cell;
  keep essential columns (name, status/type, quantity, actions) always visible

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
- ReportService.getTopSales(from, to) — salesCount, sharePct per product
- ReportService.getWriteOffsSummary(from, to) — by product + totalLoss
- ReportService.getTopClients(from, to) — by client, sorted by totalSpent
- ReportService.getEarliestMovementDate() — used for ALL_TIME preset
- StockItemRepository.getTotalStockValue() — SUM(quantity * purchasePrice)
- StockMovementRepository.findWriteOffsBetween() / findEarliestMovementDate()

## Security
- DB-based authentication via UserDetailsServiceImpl
- BCrypt password encoding
- All routes protected except /login, /logout, /reports/**, static resources (/favicon.svg, /css/**, /js/**, /images/**, /webjars/**)
- Default user: admin (change password after first login)

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

## TODO

### Features
- Low stock email notifications — daily digest when items drop below minStockLevel;
  Spring @Scheduled + spring-boot-starter-mail
- Export to Excel — reports page + movement history; Apache POI (xlsx)
- Inventory count / stock-take — formal workflow: enter physical counts per product,
  system auto-generates ADJUSTMENT movements for the differences
- User roles (ADMIN/OPERATOR) — Role entity already exists; @PreAuthorize on
  delete/cancel/deactivate endpoints to restrict to ADMIN only
- Print barcode labels — printable label with product name + barcode from product detail page
- AI: conversation history / multi-turn chat (currently stateless per request)

### Infrastructure
- Spring Session for multi-machine session sharing (if needed when scaling beyond 1 machine)
- User management page (if multiple users needed)

### Quality
- Unit tests written (29 tests, no DB required, run with ./mvnw test):
  - StockServiceTest — FIFO deduction (single/multi-batch, exact/partial, insufficient stock),
    cancel guards (double-cancel, cancel-of-cancellation, partially used purchase, null batch),
    cancel success paths (expense restores stock item, purchase zeroes batch,
    CANCELLATION movement linked via originalMovementId), getCancelledMovementIds empty shortcut
  - StockDashboardRowDtoTest — status OK / LOW (= min, < min) / OUT (0, 0.000)
  - ReportServiceTest — revenue, gross profit, margin%, salesCount, top sales grouping +
    share%, top clients sorting + null-client exclusion, margin analysis sorting
  - WarehouseApplicationTests — @Disabled (requires live PostgreSQL, run manually with dev profile)
