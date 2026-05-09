# Online Order: Secure and Scalable Food Ordering Backend

Online Order is a Spring Boot based food ordering platform that has been upgraded from a basic CRUD application into a scalable backend system with production-oriented patterns: idempotent checkout, order lifecycle management, Redis caching, Kafka event processing, optimistic locking for inventory concurrency, and Redis-backed rate limiting.

The project supports restaurant and menu browsing, session-based user authentication, cart management, checkout, asynchronous payment simulation, order status tracking, and notification simulation.

## Highlights

- Built RESTful APIs with Java, Spring Boot, Spring Controllers, Spring Data JDBC, and PostgreSQL.
- Implemented idempotency-key based checkout to prevent duplicate order creation under retries and unstable network conditions.
- Added an order lifecycle state model with `CREATED`, `PAYMENT_PENDING`, `PAID`, `CONFIRMED`, `CANCELLED`, and `FAILED`.
- Integrated Redis cache-aside caching for high-read restaurant/menu/cart queries with TTL-based expiration.
- Introduced Kafka-based asynchronous workflow for order creation, payment simulation, order status updates, and notification simulation.
- Implemented optimistic locking with `stock` and `version` fields to prevent overselling under concurrent checkout requests.
- Added Redis-backed rate limiting to protect checkout and menu APIs from traffic spikes and abuse.
- Containerized local infrastructure with Docker Compose for PostgreSQL, Redis, and Kafka.
- Secured user/cart/order workflows with Spring Security session-based authentication.

## Tech Stack

| Area | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3, Spring Web |
| Persistence | PostgreSQL, Spring Data JDBC |
| Security | Spring Security, session-based authentication |
| Cache | Redis, Spring Cache |
| Messaging | Kafka, Spring Kafka |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Local Infra | Docker Compose |
| Frontend | React, Ant Design |

## Architecture

```text
React Frontend / API Client
        |
        v
Spring Controllers
        |
        v
Service Layer
  - CustomerService
  - CartService
  - OrderService
  - InventoryService
  - PaymentService
  - NotificationService
  - RateLimitService
        |
        +--------------------+
        |                    |
        v                    v
PostgreSQL              Redis Cache
        |
        v
Kafka Event Topics
  - order.created
  - payment.succeeded
  - payment.failed
        |
        v
Kafka Consumers
  - PaymentEventConsumer
  - OrderPaymentConsumer
  - NotificationConsumer
```

The codebase keeps a clear controller-service-repository structure while using event-driven components to model microservices-style backend behavior inside a single deployable Spring Boot application.

## Core Features

- User signup and login with session authentication.
- Restaurant and menu browsing.
- Cart management.
- Idempotent checkout with `Idempotency-Key`.
- Order creation with immutable line-item snapshots.
- Order status tracking through `GET /orders/{orderId}`.
- Async payment simulation and order status updates through Kafka.
- Simulated email/push notification consumer.
- Redis caching for read-heavy restaurant/menu/cart access.
- Inventory stock deduction with optimistic locking and limited retry.
- Redis-backed rate limiting for checkout and menu endpoints.

## Order Lifecycle

Orders use an explicit lifecycle enum:

```text
CREATED
PAYMENT_PENDING
PAID
CONFIRMED
CANCELLED
FAILED
```

Current event flow:

```text
POST /cart/checkout
        |
        v
OrderService creates order with status CREATED
        |
        v
OrderEventProducer publishes OrderCreatedEvent
        |
        v
PaymentEventConsumer simulates payment
        |
        +--> PaymentSucceededEvent --> OrderPaymentConsumer --> status PAID
        |
        +--> PaymentFailedEvent -----> OrderPaymentConsumer --> status FAILED
        |
        v
NotificationConsumer simulates email/push notification
```

## Idempotent Checkout

Checkout accepts an `Idempotency-Key` request header:

```http
POST /cart/checkout
Idempotency-Key: test-checkout-001
```

The backend records the combination of:

- customer id
- idempotency key
- operation type

If the same customer retries the same checkout request with the same key, the backend returns the existing order instead of creating a duplicate order.

This protects checkout from:

- browser retries
- mobile network instability
- duplicate button clicks
- client timeout retries

## Transaction Safety

Checkout is handled inside a transactional service method. The flow includes:

- idempotency record creation
- order creation
- inventory deduction
- order line item snapshot creation
- cart cleanup
- idempotency record completion
- Kafka event publication

If an exception is thrown during inventory deduction or order creation, the database transaction rolls back.

## Redis Caching

The application uses Spring Cache with Redis for read-heavy endpoints:

| Data | Cache Name | Key |
| --- | --- | --- |
| Restaurant list | `restaurantList` | `all` |
| Restaurant menu | `restaurantMenu` | restaurant id |
| Cart | `cart` | customer id |

Redis is used with a cache-aside pattern:

```text
Read request
   |
   | cache hit
   v
Return Redis value

Read request
   |
   | cache miss
   v
Load from PostgreSQL -> store in Redis -> return response
```

TTL is configured in `application.yml`.

## Kafka Event Workflow

Kafka decouples checkout from downstream processing.

Topics:

| Topic | Producer | Consumer |
| --- | --- | --- |
| `order.created` | `OrderService` / `OrderEventProducer` | `PaymentEventConsumer` |
| `payment.succeeded` | `PaymentEventConsumer` | `OrderPaymentConsumer`, `NotificationConsumer` |
| `payment.failed` | `PaymentEventConsumer` | `OrderPaymentConsumer`, `NotificationConsumer` |

Consumer groups are separated by responsibility:

- `onlineorder-payment-service`
- `onlineorder-order-service`
- `onlineorder-notification-service`

This allows different backend components to independently react to the same payment event.

## Inventory Concurrency

The `menu_items` table includes:

```sql
stock INTEGER NOT NULL DEFAULT 100,
version INTEGER NOT NULL DEFAULT 0
```

`InventoryService` uses optimistic locking:

```sql
UPDATE menu_items
SET stock = stock - :quantity,
    version = version + 1
WHERE id = :menuItemId
  AND stock >= :quantity
  AND version = :version
```

If the update affects `1` row, stock deduction succeeds.

If the update affects `0` rows:

- stock may be insufficient, or
- another checkout updated the same item first.

The service reloads the latest `stock/version` and performs a limited retry. If stock is insufficient or retries are exhausted, checkout returns `409 CONFLICT`.

## Redis Rate Limiting

The application includes a request filter that performs rate limiting before requests reach controllers.

Protected endpoints:

| API | Scope | Limit |
| --- | --- | --- |
| `POST /cart/checkout` | authenticated user, fallback to IP | 5 requests/minute |
| `GET /restaurants/menu` | IP address | 60 requests/minute |
| `GET /restaurant/{restaurantId}/menu` | IP address | 60 requests/minute |

The limiter stores counters in Redis using fixed one-minute windows:

```text
rate_limit:checkout:user:test@example.com:202605081730
rate_limit:menu:ip:127.0.0.1:202605081730
```

Each request increments the Redis counter atomically with `INCR`. When a key is created for the first request in a window, the service sets a one-minute TTL to prevent stale rate limit keys from accumulating.

If a client exceeds the configured limit, the filter stops the request before it reaches the controller and returns:

```http
HTTP/1.1 429 Too Many Requests
```

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please try again later.",
  "timestamp": "..."
}
```

## Local Setup

### Prerequisites

- Java 17
- Docker Desktop
- Gradle wrapper included in the repo

### Start Infrastructure

```bash
docker compose up -d db redis kafka
```

### Run Tests

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```

### Run Backend

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootRun
```

Backend runs on:

```text
http://localhost:8080
```

## Example API Flow

### Signup

```bash
curl -i -X POST http://localhost:8080/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password","first_name":"Test","last_name":"User"}'
```

### Login And Save Session Cookie

```bash
curl -i -c cookie.txt -X POST http://localhost:8080/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=test@example.com&password=password"
```

### Add Item To Cart

```bash
curl -i -b cookie.txt -X POST http://localhost:8080/cart \
  -H "Content-Type: application/json" \
  -d '{"menu_id":1}'
```

### Checkout With Idempotency Key

```bash
curl -i -b cookie.txt -X POST http://localhost:8080/cart/checkout \
  -H "Idempotency-Key: test-checkout-001"
```

### Query Order Status

```bash
curl -i -b cookie.txt http://localhost:8080/orders/1
```

### Check Inventory In PostgreSQL

```bash
docker compose exec db psql -U postgres -d onlineorder \
  -c "SELECT id, name, stock, version FROM menu_items WHERE id = 1;"
```

Expected after successful checkout:

```text
stock decreases
version increases
```

### Verify Rate Limiting

Run this command while the backend is running:

```bash
for i in {1..61}; do
  echo "Request $i"
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/restaurants/menu
done
```

Expected result:

```text
Requests within the limit return 200
Requests after the per-minute limit return 429
```

## Testing

The project includes focused unit tests for:

- idempotent checkout behavior
- order creation and cart cleanup
- inventory optimistic locking success path
- insufficient stock failure path
- version conflict retry path
- Redis rate limit allow/reject behavior

Run:

```bash
./gradlew test
```

## System Design Talking Points

This project demonstrates backend concepts commonly discussed in system design and Java backend interviews:

- clear REST API design with controller-service-repository layering
- idempotent checkout API using `Idempotency-Key`
- transaction-safe order creation
- order lifecycle state modeling
- Redis cache-aside strategy for high-read traffic
- Redis-backed rate limiting for traffic governance
- Kafka-based event-driven architecture
- consumer group separation by backend responsibility
- optimistic locking to prevent overselling
- limited retry for version conflicts
- graceful `409 CONFLICT` handling for inventory contention
- local production-like development environment with Docker Compose

## Deployment Note

The project has been containerized with Docker and was previously deployed through AWS ECR and AWS App Runner. The current local development setup uses Docker Compose for PostgreSQL, Redis, and Kafka.
