# Inventory-System

An event-driven order and inventory system built with Java 21 and Spring Boot 3.

Two services communicate **only** through Kafka — there are no direct HTTP calls
between them. Each service owns its own PostgreSQL database and never reads or
writes the other's tables.

```
                    ┌──────────────────┐
     HTTP :8080 ──▶ │  Order Service   │ ──▶ orderdb      (Postgres :5432)
                    └──┬────────────▲──┘
                       │            │
          order-events │            │ inventory-events
                    ┌──▼────────────┴──┐
                    │ Inventory Service│ ──▶ inventorydb  (Postgres :5433)
                    └──────────────────┘
                          Kafka :9092
```

## Status

**Task 1** — scaffolding, Docker infrastructure, service config. Verified: both
services build, boot, and connect to their own database; the Kafka broker is
reachable from the host.

**Task 2** — Order Service now has an `Order` entity, an `OrderRepository`, and a
`POST /orders` endpoint.

**Task 3** — `POST /orders` publishes an `OrderCreated` event to the
`order-events` topic as JSON.

**Task 4** — Inventory Service has a `StockItem` entity seeded with 3 items, and
a Kafka consumer on `order-events`. It reserves stock when available and
publishes `InventoryReserved` or `InventoryFailed` to `inventory-events`.

**Task 5** — Order Service consumes `inventory-events` and moves the order to
`CONFIRMED` or `CANCELLED`. **The loop is closed**: a POST now settles on a
final status with no direct call between the services.

**Task 6** — Inventory Service's consumer is idempotent. It records each handled
order id in a `processed_events` table and skips anything it has seen before, so
a redelivered event cannot decrement stock twice.

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
<http://localhost:8081>. A successful start means "Started …Application in N
seconds" with no stack trace. See [API](#api) for what you can call.

**Stopping:** `Ctrl+C` in each service terminal, then `docker compose down` at
the repo root. Add `-v` to `docker compose down` to also wipe the database
volumes and start clean next time.

## API

### Order Service

`POST /orders` — create an order. Status is always set to `PENDING` by the
server; any `id` or `status` in the request body is ignored.

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":5}'
```

```
HTTP/1.1 201
{"id":1,"item":"widget","quantity":5,"status":"PENDING"}
```

Order Service owns one table, `orders` (named that way because `order` is a
reserved SQL word). Hibernate creates it at startup from the `Order` entity.

Each successful `POST /orders` also publishes an event (see [Events](#events)).

### Inventory Service

No endpoints yet.

## Events

| Topic | Produced by | Consumed by |
|---|---|---|
| `order-events` | Order Service | Inventory Service |
| `inventory-events` | Inventory Service | Order Service |

All events are JSON. Topics are created automatically on first publish, with the
broker default of one partition.

**`OrderCreated`** — published on every successful `POST /orders`:

```json
{"orderId":3,"item":"widget","quantity":5}
```

**`InventoryReserved`** — stock was available and has been decremented. Moves
the order to `CONFIRMED`.
**`InventoryFailed`** — the item is not stocked, or there is not enough of it.
Moves the order to `CANCELLED`.
Both carry the same shape:

```json
{"orderId":6,"item":"gadget","quantity":2}
```

Both land on the same topic, so Order Service distinguishes them via the
`__TypeId__` header, translated onto its own classes by
`spring.json.type.mapping`.

### Watching a topic

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic inventory-events \
  --from-beginning
```

Leave it running and POST an order in another terminal to watch events arrive.
Swap the topic name for `order-events` to watch the other side. `Ctrl+C` to stop.
Add `--property print.headers=true` to also see the `__TypeId__` header that
`JsonSerializer` attaches.

### Seeded stock

Inventory Service seeds `stock_items` on first startup (only when the table is
empty):

| item | quantity_available |
|---|---|
| `widget` | 10 |
| `gadget` | 5 |
| `gizmo` | 2 |

Check current levels at any time:

```bash
docker exec inventory-db psql -U inventoryuser -d inventorydb \
  -c "select item, quantity_available from stock_items order by id;"
```

## End-to-end flow

```
POST /orders                     order row inserted as PENDING
      │
      ├─ OrderCreated ──▶ order-events
                               │
                               ▼
                     Inventory Service checks stock_items
                               │
              ┌────────────────┴────────────────┐
        enough stock                      not enough
              │                                 │
      decrement, publish                 publish
      InventoryReserved                  InventoryFailed
              │                                 │
              └────────────▶ inventory-events ◀─┘
                               │
                               ▼
                     Order Service updates the order
                        CONFIRMED  or  CANCELLED
```

Watch a single order settle:

```bash
curl -s -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" -d '{"item":"widget","quantity":2}'

docker exec order-db psql -U orderuser -d orderdb \
  -c "select id, item, quantity, status from orders order by id desc limit 5;"
```

## Idempotency

Kafka guarantees *at-least-once* delivery, so a consumer must expect the same
message more than once. Inventory Service keeps a `processed_events` table whose
primary key is the order id:

```
    Column    |            Type             | Nullable
--------------+-----------------------------+----------
 order_id     | bigint                      | not null
 processed_at | timestamp(6) with time zone |
Indexes:
    "processed_events_pkey" PRIMARY KEY, btree (order_id)
```

`OrderEventListener` checks that table first and returns early on a hit. The
check, the stock decrement and the insert all run inside one `@Transactional`
method, so they commit together or not at all.

Prove it by republishing an event the consumer has already handled:

```bash
echo '{"orderId":11,"item":"gadget","quantity":1}' | \
  docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic order-events

docker exec inventory-db psql -U inventoryuser -d inventorydb \
  -c "select item, quantity_available from stock_items order by id;"
```

Stock stays put, and the log shows
`Order 11 already processed - ignoring duplicate delivery`.

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
