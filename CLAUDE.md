# CLAUDE.md

This file provides guidance to Claude Code when working in this repository.

## Project

**Hairmony Warehouse** — inventory management system for a trichology salon.

## Stack

- Java 21
- Spring Boot 3.4.5
- Spring MVC + Thymeleaf (server-side rendering)
- Spring Security
- Spring Data JPA + Hibernate
- PostgreSQL (local: `hairmony_dev`, prod: TBD on Fly.io)
- Liquibase migrations
- Lombok
- Maven

## Commands

```bash
# Run (requires PostgreSQL on localhost:5432, database: hairmony_dev)
mvn spring-boot:run

# Build
mvn clean install

# Run tests
mvn test
```

## Architecture