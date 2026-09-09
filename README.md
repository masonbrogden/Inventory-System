# Inventory-System

An event-driven order and inventory system built with Java 21 and Spring Boot 3.

Two services communicate **only** through Kafka — there are no direct HTTP calls
between them. Each service owns its own PostgreSQL database and never reads or
writes the other's tables.

```
                    ┌──────────────────┐
     HTTP :8080 ──▶ │  Order Service   │ ──▶ orderdb      (Postgres :5432)
                    └────────┬─────────┘
                             │
                          Kafka :9092
                             │
                    ┌────────▼─────────┐
     HTTP :8081 ──▶ │ Inventory Service│ ──▶ inventorydb  (Postgres :5433)
                    └──────────────────┘
```

## Status

Task 1 complete and verified end to end: both services build, boot, and connect
to their own database; the Kafka broker is reachable from the host.
**No business logic, entities, endpoints, or Kafka topics yet.**

Verified running versions: Java 21.0.12.1, Spring Boot 3.5.16, Hibernate 6.6.53,
Tomcat 10.1.55, PostgreSQL 16.15, Kafka 3.9.2.

## Tech stack

| Piece | Version | Notes |
|---|---|---|
| Java | 21 | |
| Spring Boot | 3.5.16 | Latest 3.x on Maven Central |
| Build tool | Maven | Via the bundled `./mvnw` wrapper — no local Maven install needed |
| Kafka | 3.9.2 | KRaft mode, single broker, no ZooKeeper |
| PostgreSQL | 16 | One instance per service |

## Layout

```
Inventory-System/
├── docker-compose.yml        Kafka + both Postgres instances
├── order-service/            Spring Boot app, package com.inventorysystem.order
└── inventory-service/        Spring Boot app, package com.inventorysystem.inventory
```

## Prerequisites

- **JDK 21** — `brew install openjdk@21`
- **Docker Desktop**, running — the `docker compose` command ships with it

Maven is *not* required; each service carries a `./mvnw` wrapper script that
downloads the correct Maven version on first use.

## Running it

**1. Start the infrastructure** (from the repo root):

```bash
docker compose up -d
```

Check that all three containers are up, and that both databases report `healthy`:

```bash
docker compose ps
```

**2. Start Order Service** (in its own terminal):

```bash
cd order-service && ./mvnw spring-boot:run
```

**3. Start Inventory Service** (in a third terminal):

```bash
cd inventory-service && ./mvnw spring-boot:run
```

Order Service comes up on <http://localhost:8080>, Inventory Service on
<http://localhost:8081>. Neither exposes any endpoints yet — a successful start
means "Started …Application in N seconds" with no stack trace.

**Stopping:** `Ctrl+C` in each service terminal, then `docker compose down` at
the repo root. Add `-v` to `docker compose down` to also wipe the database
volumes and start clean next time.

## Connection details

| | Order Service | Inventory Service |
|---|---|---|
| HTTP port | 8080 | 8081 |
| Database | `orderdb` | `inventorydb` |
| DB host port | 5432 | 5433 |
| DB user / password | `orderuser` / `orderpass` | `inventoryuser` / `inventorypass` |
| Kafka consumer group | `order-service` | `inventory-service` |

Credentials are plain local-development values and are committed on purpose so
the project runs out of the box.

To open a database directly:

```bash
docker exec -it order-db psql -U orderuser -d orderdb
docker exec -it inventory-db psql -U inventoryuser -d inventorydb
```
