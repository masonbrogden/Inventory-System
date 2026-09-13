# Inventory-System

An event-driven order and inventory system built with Java 21 and Spring Boot 3.

## What this is

When you buy something online, two questions have to be answered: *did we take
the order?* and *do we actually have the item?* The obvious way to build that is
one program that does both. This project deliberately does not do that.

Instead there are two independent services. **Order Service** takes orders.
**Inventory Service** tracks stock. Each owns its own database, and neither can
read or write the other's tables. They never call each other directly — no REST
call, no shared library, no shared schema.

They coordinate by sending **events** through **Kafka**, a durable message log.
Order Service announces "an order was created" and moves on. Inventory Service
picks that announcement up whenever it is ready, checks stock, and announces
either "reserved" or "failed". Order Service hears that and finalises the order.

The practical payoff is that neither service has to be running for the other to
work. If Inventory Service is down when an order arrives, the order is still
accepted — the event waits in Kafka and gets handled when Inventory Service comes
back. Nothing is lost, and the customer was never made to wait for it.

The trade is that answers are not immediate. A `POST /orders` returns `PENDING`,
not `CONFIRMED`, because the stock check has not happened yet. It settles a few
milliseconds later. That gap is the fundamental cost of this design, and being
comfortable explaining it is most of the point of the project.

## Architecture

```
                               POST /orders
                                    │
                                    ▼
   ┌────────────────────────────────────────────┐    ┌──────────────────────┐
   │              Order Service                 │───▶│       orderdb        │
   │                  :8080                     │    │    Postgres :5432    │
   │                                            │    │    table: orders     │
   │   POST /orders → save order as PENDING     │    └──────────────────────┘
   │   publishes    → OrderCreated              │
   │   consumes     → InventoryReserved         │
   │                  InventoryFailed           │
   └──────┬───────────────────────────▲─────────┘
          │                           │
          │ OrderCreated              │ InventoryReserved
          │                           │ InventoryFailed
          │                           │
   - - - -│- - - - - - Kafka :9092 - -│- - - - - - - - - - - - - - - - -
          │                           │
          ▼                           │
   ┌──────────────┐            ┌──────┴───────────┐
   │ order-events │            │ inventory-events │
   └──────┬───────┘            └──────▲───────────┘
          │                           │
   - - - -│- - - - - - - - - - - - - -│- - - - - - - - - - - - - - - - -
          │                           │
          ▼                           │
   ┌────────────────────────────────────────────┐    ┌──────────────────────┐
   │            Inventory Service               │───▶│     inventorydb      │
   │                  :8081                     │    │   Postgres :5433     │
   │                                            │    │   tables:            │
   │   consumes   → OrderCreated                │    │     stock_items      │
   │   checks     → stock_items                 │    │     processed_events │
   │   publishes  → InventoryReserved / Failed  │    └──────────────────────┘
   └────────────────────────────────────────────┘
```

Both services run on the host; Kafka and both databases run in Docker.

## The full event flow

One order, every step in order:

| # | Where | What happens |
|---|---|---|
| 1 | Order Service | `POST /orders` arrives at `OrderController.create()` |
| 2 | Order Service | Jackson turns the JSON body into an `Order` via `@RequestBody` |
| 3 | Order Service | Controller nulls any client-sent `id` and forces `status = PENDING` |
| 4 | orderdb | `INSERT INTO orders`; Postgres assigns the id |
| 5 | Order Service | Publishes `OrderCreated` to `order-events` — **asynchronous** |
| 6 | Client | Receives `201 Created` with `status: PENDING`. The request is over |
| 7 | Kafka | Appends the message to `order-events` and assigns it an offset |
| 8 | Inventory Service | Listener container polls, `JsonDeserializer` builds its own `OrderCreated` |
| 9 | inventorydb | Idempotency check: has this `orderId` already been handled? |
| 10 | inventorydb | `findByItem(...)` looks up the stock row |
| 11 | Inventory Service | Decides: not stocked → fail; not enough → fail; otherwise reserve |
| 12 | inventorydb | On success, decrement `quantity_available` and insert into `processed_events` — **one transaction** |
| 13 | Inventory Service | Publishes `InventoryReserved` or `InventoryFailed` to `inventory-events` |
| 14 | Kafka | Commits the `inventory-service` consumer offset — only because step 11 did not throw |
| 15 | Order Service | Listener container picks the message off `inventory-events` |
| 16 | Order Service | `spring.json.type.mapping` translates the `__TypeId__` header to a local class |
| 17 | Order Service | Spring routes to the matching `@KafkaHandler` method |
| 18 | orderdb | `UPDATE orders SET status = 'CONFIRMED'` or `'CANCELLED'` |
| 19 | Kafka | Commits the `order-service` consumer offset. The order is final |

Steps 1–6 and 7–19 are fully decoupled. The caller was answered at step 6;
everything after that happens on background threads.

## Tech stack

| Piece | Version | Notes |
|---|---|---|
| Java | 21 | |
| Spring Boot | 3.5.16 | Latest 3.x on Maven Central |
| Build tool | Maven | Via the bundled `./mvnw` wrapper — no local Maven install needed |
| Kafka | 3.9.2 | KRaft mode, single broker, no ZooKeeper |
| PostgreSQL | 16 | One instance per service |
| springdoc-openapi | 2.9.1 | Swagger UI for Order Service. 2.x is the Spring Boot 3 line; 3.x needs Boot 4 |

Verified running versions: Java 21.0.12.1, Spring Boot 3.5.16, Hibernate 6.6.53,
Tomcat 10.1.55, PostgreSQL 16.15, Kafka 3.9.2.

## Project layout

```
Inventory-System/
├── docker-compose.yml          Kafka + both Postgres instances
├── order-service/              package com.inventorysystem.order
│   └── src/main/java/.../
│       ├── Order.java                    entity
│       ├── OrderStatus.java              PENDING | CONFIRMED | CANCELLED
│       ├── OrderRepository.java
│       ├── OrderController.java          POST /orders, publishes OrderCreated
│       ├── OrderCreated.java             event it publishes
│       ├── InventoryReserved.java        events it consumes
│       ├── InventoryFailed.java
│       └── InventoryEventListener.java   consumer, finalises the order
└── inventory-service/          package com.inventorysystem.inventory
    └── src/main/java/.../
        ├── StockItem.java                entity
        ├── StockItemRepository.java
        ├── StockSeeder.java              3 sample items on first startup
        ├── ProcessedEvent.java           idempotency ledger
        ├── ProcessedEventRepository.java
        ├── OrderCreated.java             event it consumes
        ├── InventoryReserved.java        events it publishes
        ├── InventoryFailed.java
        └── OrderEventListener.java       consumer, reserves stock
```

Each service keeps its **own copy** of every event class. The services share a
message shape, never a class — so neither needs recompiling when the other
changes.

## Prerequisites

- **JDK 21** — `brew install openjdk@21`
- **Docker Desktop**, running — the `docker compose` command ships with it

Maven is *not* required; each service carries a `./mvnw` wrapper script that
downloads the correct Maven version on first use.

## Setup and running

**1. Start the infrastructure** from the repo root:

```bash
docker compose up -d
```

First run pulls the images (~90s); later runs start in about 6 seconds.

**2. Wait until both databases report `healthy`:**

```bash
docker compose ps
```

This matters — Postgres reports `Up` a second or two before it accepts
connections, and a service launched into that gap dies with a connection
refusal. Kafka has no healthcheck, so check it separately:

```bash
docker logs kafka | grep "Kafka Server started"
```

**3. Start Order Service** in its own terminal:

```bash
cd order-service && ./mvnw spring-boot:run
```

**4. Start Inventory Service** in a third terminal:

```bash
cd inventory-service && ./mvnw spring-boot:run
```

A successful start is `Started …Application in N seconds` with no stack trace.

**5. Send an order:**

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","quantity":2}'
```

Or use the [Swagger UI](#swagger-ui): open http://localhost:8080/swagger-ui.html,
expand `POST /orders`, and click **Try it out**.

**6. Watch it settle:**

```bash
docker exec order-db psql -U orderuser -d orderdb \
  -c "select id, item, quantity, status from orders order by id desc limit 5;"

docker exec inventory-db psql -U inventoryuser -d inventorydb \
  -c "select item, quantity_available from stock_items order by id;"
```

The order lands as `PENDING` and becomes `CONFIRMED` or `CANCELLED` within a few
milliseconds.

**Stopping:** `Ctrl+C` in each service terminal, then:

```bash
docker compose down       # keeps database volumes
docker compose down -v    # also wipes both databases
```

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

The table is named `orders`, not `order`, because `order` is a reserved SQL word.

### Inventory Service

No HTTP endpoints. It is driven entirely by Kafka events.

### Swagger UI

Order Service documents its own API with springdoc-openapi. With the service
running, open:

**http://localhost:8080/swagger-ui.html**

It redirects to `/swagger-ui/index.html`, which lists `POST /orders` alongside
the `Order` schema. **Try it out** sends a real request — it saves an order and
publishes `OrderCreated` exactly like the `curl` above. The raw OpenAPI spec the
page is built from is at http://localhost:8080/v3/api-docs, ready to import into
Postman or feed to a client generator.

Most of the page is generated, not written:

| What the page shows | Where it comes from |
|---|---|
| `POST /orders` | `@RequestMapping` + `@PostMapping` — generated |
| A required `Order` request body | `@RequestBody` — generated |
| A `201` response returning `Order` | `@ResponseStatus(HttpStatus.CREATED)` and the return type — generated |
| Field names, types, and the three `status` values | Jackson's view of `Order`, and the `OrderStatus` enum — generated |
| The endpoint's one-line summary | `@Operation(summary = …)` on `OrderController.create()` |
| The model and field descriptions | `@Schema(description = …)` in `Order` |
| `id` and `status` left out of the example request | `@Schema(accessMode = Schema.AccessMode.READ_ONLY)` in `Order` |

springdoc reads the *structure* straight from the Spring MVC code, so that part
cannot drift out of date. The annotations add only the *meaning* the code cannot
express, and none of them change behaviour — Jackson ignores them, and the
controller still overwrites `id` and `status` whatever the docs say.

Inventory Service has no Swagger UI because it has no HTTP endpoints. The Kafka
side is not in the spec either — springdoc only sees HTTP — so the events are
documented under [Events](#events).

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

**`InventoryReserved`** — stock was available and has been decremented. Moves the
order to `CONFIRMED`.

**`InventoryFailed`** — the item is not stocked, or there is not enough of it.
Moves the order to `CANCELLED`.

Both carry the same shape:

```json
{"orderId":6,"item":"gadget","quantity":2}
```

Because both land on the same topic, Order Service tells them apart using the
`__TypeId__` header that `JsonSerializer` attaches, translated onto its own
classes by `spring.json.type.mapping`.

### Watching a topic

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic inventory-events \
  --from-beginning
```

Leave it running and POST an order in another terminal. Swap the topic name for
`order-events` to watch the other side. Add `--property print.headers=true` to
see the `__TypeId__` header.

### Seeded stock

Inventory Service seeds `stock_items` on first startup, and only when the table
is empty:

| item | quantity_available |
|---|---|
| `widget` | 10 |
| `gadget` | 5 |
| `gizmo` | 2 |

## Idempotency

Kafka guarantees **at-least-once** delivery: a consumer commits its read position
*after* processing, so a crash in that gap means the same message is delivered
again on restart. Rebalances, slow consumers, producer retries and deliberate
replays all cause the same thing.

That is fine for an operation like "set status to CONFIRMED", which has the same
result however many times it runs. It is not fine for `quantity -= n`, which
would oversell stock.

Inventory Service therefore keeps a `processed_events` table whose primary key is
the order id:

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
method — which is load-bearing, not decoration. Without it, a crash between
decrementing and recording would leave the event eligible for redelivery and
stock would drop twice.

The `existsById` check is a check-then-act and would be racy under concurrency;
the `PRIMARY KEY` constraint is the real guarantee, rejecting a second insert
outright.

Prove it by republishing an event the consumer has already handled:

```bash
echo '{"orderId":11,"item":"gadget","quantity":1}' | \
  docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic order-events
```

Stock stays put, and the log shows
`Order 11 already processed - ignoring duplicate delivery`.

## Tests

```bash
cd inventory-service && ./mvnw test
```

`OrderEventListenerTest` covers the stock-decrement logic with three cases:
enough stock reserves and publishes `InventoryReserved`; insufficient stock
leaves the row untouched and publishes `InventoryFailed`; an already-processed
event does nothing at all.

They are plain Mockito unit tests — no Spring context, no database, no broker —
so they run in well under a second and need nothing running.

## Connection details

| | Order Service | Inventory Service |
|---|---|---|
| HTTP port | 8080 | 8081 |
| Database | `orderdb` | `inventorydb` |
| DB host port | 5432 | 5433 |
| DB user / password | `orderuser` / `orderpass` | `inventoryuser` / `inventorypass` |
| Kafka consumer group | `order-service` | `inventory-service` |

Credentials are plain local-development values, committed on purpose so the
project runs straight after cloning. The databases exist only in local
containers.

To open a database directly:

```bash
docker exec -it order-db psql -U orderuser -d orderdb
docker exec -it inventory-db psql -U inventoryuser -d inventorydb
```

## Future work

### Notification Service

A third service subscribing to `inventory-events` and emailing the customer when
an order is confirmed or cancelled. It is the cleanest demonstration of why this
architecture was chosen: it would require **no change to either existing
service**. A new consumer group simply starts reading a topic that is already
being written, and because Kafka retains the log, it could even replay history
to backfill notifications for orders placed before it existed.

### Full Saga pattern with compensating transactions

The current flow is a two-step saga that happens not to need unwinding — stock is
only ever decremented on success, so a cancelled order leaves nothing to undo.

A realistic order pipeline has more steps: reserve stock, take payment, book
shipping. If payment fails after stock was reserved, that reservation has to be
released. Because each service owns its own database, there is no distributed
transaction to roll back — the fix is a **compensating transaction**, an explicit
counter-action published as its own event (`StockReleased` in response to
`PaymentFailed`).

Doing this properly also means addressing the **dual-write problem** that exists
today: the database commit and the Kafka publish are not atomic, so a crash
between them can leave an order with no event. The standard fix is the
**transactional outbox pattern** — write the outgoing event into a table in the
same transaction as the state change, and let a separate relay publish it.

### JWT authentication

`POST /orders` is currently open to anyone who can reach port 8080. Adding Spring
Security with JWT bearer tokens would let Order Service authenticate the caller
and attach a real customer id to the order instead of accepting whatever is sent.

The interesting part is what happens at the service boundary. The token
authenticates an HTTP request, but Kafka events are not HTTP requests — so the
downstream service has no token to validate. The usual answer is that Order
Service validates once at the edge and events carry an already-trusted identity,
with the trust boundary drawn around the Kafka cluster itself.
