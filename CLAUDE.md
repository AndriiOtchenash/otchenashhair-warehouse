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
- Categories CRUD with modal editing
- Suppliers CRUD with tooltip notes, collapsible form
- Clients CRUD with detail page, transaction history, collapsible form
- Stock income (/movements/income) with FIFO, barcode scanner input (debounced AJAX),
  quantity step auto-set by unit (integer for PCS, decimal for ML/G)
- Stock expense (/movements/expense) with SALE/WRITE_OFF/ADJUSTMENT,
  available qty shown + enforced as max, barcode scanner input,
  quantity step auto-set by unit; insufficient stock error via i18n MessageSource
- Barcode scanner page (/scan) — camera scan or manual entry, income/expense mode
- MovementType enum: PURCHASE, SALE, WRITE_OFF, ADJUSTMENT, CANCELLATION
- StockDashboardRowDto with status OK/LOW/OUT
- Movement journal (/movements/history) with server-side filtering (JPA Specifications),
  pagination, date/type/product/counterparty filters, auto-submit on change
- Movement cancellation — POST /movements/{id}/cancel reverses any SALE/WRITE_OFF/
  ADJUSTMENT: restores stock (new StockItem batch), records CANCELLATION movement with
  original_movement_id set and human-readable Ukrainian note; cancelled rows shown
  strikethrough, cancellation rows shown in gray; confirm modal in UI;
  guard against double-cancel and cancelling a cancellation
- Reports page (/reports) — expiry alerts, top sales by revenue,
  purchases summary by supplier, margin analysis with %; period presets
  (THIS_MONTH, LAST_MONTH, CUSTOM) and configurable expiry window
- i18n: uk (primary), pl, en
- QuantityFormatter (@qf bean) — integers for PCS, decimals for ML/G
- CurrentUriInterceptor — active nav highlighting
- Language switcher preserves URL params via JS switchLang()
- Spring Security with DB authentication (users table)
- Login page with show/hide password
- Change password page (/profile/change-password)
- Logout in sidebar
- Mobile responsive layout with burger menu and topbar
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

## Movement cancellation details
- StockMovement.originalMovementId (Long) links a CANCELLATION back to its source
- StockMovementRepository.existsByOriginalMovementId() — guard for double-cancel
- StockMovementRepository.findCancelledMovementIds(Set<Long>) — @Query returns which
  IDs on the current history page have been cancelled (for UI indicators)
- StockService.cancelMovement() validates type, checks guards, saves restored StockItem,
  builds note "Скасування: {name}, {qty} {unit}, {dd.MM.yyyy}", saves CANCELLATION movement
- StockService.getCancelledMovementIds() — called by MovementController for the model
- Cancel button visible only for non-PURCHASE, non-CANCELLATION, not-yet-cancelled rows

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
  - deploy.yml — triggers on push to master, builds Docker image with flyctl --remote-only, deploys to Fly.io
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
- Supplier detail page — /suppliers/{id} with purchase history (mirrors client detail)
- Print barcode labels — printable label with product name + barcode from product detail page
- AI: conversation history / multi-turn chat (currently stateless per request)

### Infrastructure
- Spring Session for multi-machine session sharing (if needed when scaling beyond 1 machine)
- User management page (if multiple users needed)

### Quality
- Unit tests for StockService — FIFO deduction logic, cancel guards, KPI counts
