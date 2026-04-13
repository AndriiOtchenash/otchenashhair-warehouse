# CLAUDE.md

## Project: OtchenashHair Warehouse
Inventory management for a trichology salon. Java 21, Spring Boot 3.4.5, Thymeleaf, PostgreSQL, Liquibase, Spring Security, Lombok, DevTools.

## Package: com.hairmony.warehouse

## Architecture
domain/ — JPA entities (category, client, product, stock, supplier, user)
repository/ — Spring Data JPA
service/ — business logic
web/controller/ — MVC controllers (thin)
web/dto/ — form objects
web/interceptor/ — CurrentUriInterceptor, adds currentUri to model
web/formatter/ — QuantityFormatter (@qf bean)
config/ — SecurityConfig, LocaleConfig, WebMvcConfig

## Key Rules
- ddl-auto=none, Liquibase manages schema (migrations 001-008)
- Controllers are thin, logic in services
- Never pass entities to templates, use DTOs
- Dirty checking for updates — no explicit save() on managed entities
- Credentials in application-dev.properties (gitignored)
- spring.profiles.active=dev

## DB
Local: hairmony_dev, user: warehouse_user
Migrations: 001-users, 002-suppliers, 003-clients, 004-products, 005-stock-items, 006-stock-movements, 007-categories, 008-fix-categories

## What's done
- Dashboard (/) with stock status, filters, search
- Products CRUD with soft delete, restore, detail page
- Categories CRUD (lookup table, not enum)
- Suppliers CRUD with tooltip notes
- Clients CRUD with detail page and transaction history
- Stock income (PURCHASE) with FIFO
- Stock expense (SALE, WRITE_OFF, ADJUSTMENT)
- StockDashboardRowDto with status OK/LOW/OUT
- i18n: uk (primary), pl, en
- QuantityFormatter — integers for PCS, decimals for ML/G
- CurrentUriInterceptor — active nav highlighting
- Language switcher preserves URL params via JS switchLang()
- Spring Security — all routes permitted for now (dev mode)
- DevTools enabled

## TODO
- Spring Security with real DB auth
- Movement journal page (/movements/history)
- Reports page
- Deploy to Fly.io + Neon PostgreSQL