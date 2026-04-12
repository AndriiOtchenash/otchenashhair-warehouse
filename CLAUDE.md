# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Structure

This repo contains two independent Spring Boot projects:

| Directory | Package | Database | Purpose |
|-----------|---------|----------|---------|
| `/` (root) | `com.hairmony.warehouse` | PostgreSQL + Liquibase | Production-grade inventory for a trichology salon |
| `/warehouse/` | `com.example.warehouse` | H2 embedded | OpenAPI-first prototype with code generation |

Both require Java 21 and use Maven with the included wrapper (`./mvnw`).

## Root Project (`hairmony-warehouse`)

### Commands

```bash
# Build
mvn clean install

# Run (requires PostgreSQL on localhost:5432, database: hairmony, credentials: postgres/postgres)
mvn spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=WarehouseApplicationTests
```

### Stack

- Spring Boot 3.4.5, Spring Security, Spring Data JPA
- PostgreSQL with **Liquibase** migrations (`classpath:db/changelog/db.changelog-master.yaml`)
- `ddl-auto=none` — schema is managed exclusively via Liquibase changesets, never by Hibernate

## Sub-project (`/warehouse/`)

### Commands

```bash
cd warehouse

# Build and generate OpenAPI sources
mvn clean install

# Run (H2 embedded — no external DB needed)
mvn spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=WarehouseApplicationTests

# Regenerate OpenAPI sources only
mvn clean compile
```

### OpenAPI-First Architecture

The API contract lives in `src/main/resources/openapi/openapi.yaml`. The OpenAPI Generator Maven plugin (v7.10.0) generates at compile time:
- Controller interfaces → `target/generated-sources/.../com/example/warehouse/api/`
- DTO models (suffix `DTO`) → `target/generated-sources/.../com/example/warehouse/model/`

**Never edit generated sources directly** — modify `openapi.yaml` and recompile.

`HomeController` implements the generated `YarnApi` interface.

### Annotation Processor Order

`pom.xml` configures `maven-compiler-plugin` annotation processor paths so that MapStruct runs after Lombok. New mappers must follow the existing `YarnMapper.java` pattern (`@Mapper(componentModel = "spring")`).

### Stack

- Spring Boot 3.4.2, H2 embedded (`jdbc:h2:file:./warehouse-data`)
- Swagger UI at `http://localhost:8080/swagger-ui.html`
- MapStruct 1.5.5 + Lombok 1.18.30 (both annotation processors)
- Thymeleaf templates in `src/main/resources/templates/`
