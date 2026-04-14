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
           UserService, UserDetailsServiceImpl)
web/controller/ — MVC controllers (thin):
  DashboardController, ProductController, ClientController,
  SupplierController, CategoryController, StockController,
  MovementController, ProfileController, LoginController, ReportsController
web/dto/ — form objects and filter DTOs
web/interceptor/ — CurrentUriInterceptor
web/formatter/ — QuantityFormatter (@qf bean)
config/ — SecurityConfig, LocaleConfig, WebMvcConfig

## Key Rules
- ddl-auto=none, Liquibase manages schema (migrations 001-009)
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
            008-fix-categories, 009-insert-admin-user

## What's done
- Dashboard (/) with stock status, filters, search, column reorder
- Products CRUD with soft delete, restore, detail page, brand autocomplete
- Categories CRUD with modal editing
- Suppliers CRUD with tooltip notes, collapsible form
- Clients CRUD with detail page, transaction history, collapsible form
- Stock income (PURCHASE) with FIFO
- Stock expense (SALE, WRITE_OFF, ADJUSTMENT)
- StockDashboardRowDto with status OK/LOW/OUT
- Movement journal (/movements/history) with server-side filtering,
  pagination, date range, type and product filters
- Reports page (/reports) — stub, to be implemented
- i18n: uk (primary), pl, en
- QuantityFormatter — integers for PCS, decimals for ML/G
- CurrentUriInterceptor — active nav highlighting
- Language switcher preserves URL params via JS switchLang()
- Spring Security with DB authentication (users table)
- Login page with show/hide password
- Change password page (/profile/change-password)
- Logout in sidebar
- Mobile responsive layout with burger menu and topbar
- Client-side search on all list pages
- Collapsible create forms on list pages
- Clickable table rows on mobile
- DevTools enabled (dev profile only)

## Security
- DB-based authentication via UserDetailsServiceImpl
- BCrypt password encoding
- All routes protected except /login, /logout, static resources
- Default user: admin (change password after first login)

## Deploy
- Production: https://otchenashhair-warehouse.fly.dev/
- Platform: Fly.io (Amsterdam region, 1 shared machine, 512MB RAM)
- Database: Neon PostgreSQL (eu-central-1, Frankfurt)
- CI/CD: GitHub Actions on push to master branch
- Secrets managed via Fly.io secrets (DB_URL, DB_USERNAME, DB_PASSWORD,
  SPRING_PROFILES_ACTIVE)

## Local development
Run with VM option: -Dspring.profiles.active=dev
DB credentials in application-dev.properties (gitignored)

## TODO
- Reports page with real analytics
- Spring Session for multi-machine session sharing (if needed)
- Favicon fix
- User management page (if multiple users needed)
