# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

`pos-service` is a Spring Boot 4.0.5 / Kotlin 2.2.21 backend service for the Nivora POS (Point of Sale) system. It uses Spring Data JPA with PostgreSQL as the database.

## Commands

### Build & Run
```bash
./gradlew build              # Compile and package
./gradlew bootRun            # Run the application locally
./gradlew bootJar            # Build executable JAR
```

### Testing
```bash
./gradlew test               # Run all tests
./gradlew test --tests "id.nivorapos.pos_service.SomeTest"  # Run a single test class
./gradlew test --tests "id.nivorapos.pos_service.SomeTest.methodName"  # Run a single test method
```

### Seeder
```bash
# Jalankan seeder untuk mengisi data awal ke database
./gradlew bootRun --args='--spring.profiles.active=seeder'
```
Seeder bersifat idempotent — aman dijalankan berulang kali, data yang sudah ada akan di-skip.

### Other
```bash
./gradlew clean              # Clean build outputs
./gradlew dependencies       # Show dependency tree
```

## Architecture

**Stack**: Kotlin + Spring Boot 4.0.5, Spring Web (MVC), Spring Data JPA, Spring Security, PostgreSQL

**Base package**: `id.nivorapos.pos_service`

**Layered structure**:
- `controller/` — REST controllers (prefix `/pos`)
- `service/` — Business logic
- `repository/` — Spring Data JPA repositories
- `entity/` — JPA entities; `allOpen` plugin dikonfigurasi agar JPA proxying bekerja tanpa keyword `open`
- `dto/request/` & `dto/response/` — Request/response DTOs
- `config/` — SecurityConfig, GlobalExceptionHandler
- `security/` — JwtUtil, JwtAuthenticationFilter, UserDetailsServiceImpl, SecurityUtils
- `seeder/` — DataSeeder (aktif hanya dengan profile `seeder`)

## Key Configuration Notes

- `application.properties` currently only sets `spring.application.name=pos-service`. PostgreSQL datasource and JPA settings need to be added (or placed in `application-local.properties`).
- Kotlin compiler has JSR305 strict mode enabled — null-safety annotations from Java libraries are enforced.
- The `allOpen` Gradle plugin is configured for `@Entity`, `@MappedSuperclass`, and `@Embeddable` so JPA can subclass entities without `open` modifiers.
- Java 17 toolchain is required.
