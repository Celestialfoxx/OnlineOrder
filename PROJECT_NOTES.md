# Online Order Project Notes

中文速记：这是项目面试小抄，用来快速回忆“做了什么、怎么做、为什么这么做、怎么回答面试官”。

This document is a personal interview prep guide for the Online Order backend upgrade. It summarizes what was built, how each feature works, why it matters, and how to explain it in interviews.

## 1. Project Overview

中文速记：项目总览，从 CRUD 点餐系统升级成带 Redis、Kafka、幂等、乐观锁、限流的后端系统。

Online Order started as a basic CRUD online food ordering app. I upgraded it into a more scalable backend system using Spring Boot, PostgreSQL, Redis, Kafka, Docker Compose, and Spring Security.

The system now supports:

中文说明：这一段是项目能力清单，适合快速回忆系统到底支持哪些后端功能。

- user signup and session-based login
- restaurant/menu browsing
- cart management
- idempotent checkout
- order lifecycle tracking
- Redis caching
- Kafka asynchronous payment workflow
- simulated notification delivery
- optimistic locking for inventory concurrency
- Redis-backed rate limiting

High-level request flow:

中文说明：这一段是整体请求链路，从用户请求一路到数据库、缓存、消息队列和异步 consumer。

```text
User
  -> Spring Controller
  -> Service Layer
  -> PostgreSQL / Redis / Kafka
  -> Async Consumers
  -> Order status update / notification simulation
```

## 2. Architecture

中文速记：架构分层，Controller 接请求，Service 放业务，Repository 访问数据库，Redis/Kafka 负责缓存和异步解耦。

The backend keeps a layered structure:

中文说明：这一段是经典 Spring Boot 分层，面试时可以用来解释代码为什么好维护。

```text
Controller
  -> Service
  -> Repository
  -> PostgreSQL
```

Additional infrastructure:

中文说明：这一段是项目引入的基础设施，Redis 负责缓存/限流，Kafka 负责异步事件。

```text
Redis
  -> cache restaurant/menu/cart queries
  -> store rate limit counters

Kafka
  -> order.created
  -> payment.succeeded
  -> payment.failed

Docker Compose
  -> PostgreSQL
  -> Redis
  -> Kafka
```

Why this matters:

中文说明：这一段是架构价值，重点回答“为什么要这么分层/为什么要引入 Redis 和 Kafka”。

- Controller layer stays thin and handles HTTP.
- Service layer owns business logic.
- Repository layer owns database access.
- Kafka decouples checkout from payment/status/notification.
- Redis improves read performance and enables fast rate limiting.

## 3. Spring Security Session Authentication

中文速记：登录认证，重点是 Spring Security 自动处理 `/login`，成功后用 `JSESSIONID` 维持登录状态。

### What I Built

中文速记：这里讲项目用的是 session-based authentication，不是 JWT。

The app uses Spring Security session-based authentication.

Important files:

中文说明：这一段是定位认证代码用的，忘记登录逻辑在哪里时先看这些文件。

- `AppConfig.java`
- `CustomerService.java`
- `CustomerController.java`
- `CartController.java`
- `OrderController.java`

### How It Works

中文速记：注册是自己写的 controller/service，登录是 Spring Security filter 自动接管的。

Signup is handled by my own code:

```text
POST /signup
  -> CustomerController
  -> CustomerService
  -> JdbcUserDetailsManager.createUser(...)
  -> customers / authorities tables
```

Login is handled automatically by Spring Security:

```text
POST /login
  -> UsernamePasswordAuthenticationFilter
  -> JdbcUserDetailsManager
  -> PostgreSQL customers table
  -> PasswordEncoder checks password
  -> Spring creates session
  -> response returns JSESSIONID cookie
```

After login, protected APIs use:

中文说明：这一段是 controller 如何拿到当前登录用户。

```java
@AuthenticationPrincipal User user
```

This gets the currently logged-in user from the session.

### Interview Explanation

中文速记：面试回答模板，关键词是 `formLogin`、`JdbcUserDetailsManager`、`JSESSIONID`、`@AuthenticationPrincipal`。

I used Spring Security with `formLogin()` and `JdbcUserDetailsManager`. Spring Security handles `/login` automatically. I configured SQL queries to load users and authorities from PostgreSQL. Once authenticated, Spring creates a session and returns a `JSESSIONID` cookie. Later requests include that cookie, and controllers access the current user with `@AuthenticationPrincipal`.

## 4. Order Lifecycle

中文速记：订单生命周期，重点是用 enum 明确表示订单状态，而不是用散乱字符串。

### What I Built

中文速记：这里列出订单状态 enum。

I added an `OrderStatus` enum:

```text
CREATED
PAYMENT_PENDING
PAID
CONFIRMED
CANCELLED
FAILED
```

Orders are stored in the `orders` table, and order item snapshots are stored in `order_line_items`.

中文说明：`orders` 记录订单本身，`order_line_items` 记录下单当时的商品快照。

### Why Order Line Item Snapshot Matters

中文速记：订单快照，防止以后菜单名/价格变化影响历史订单。

When a user checks out, the order should preserve the menu item name and price at that time.

Example:

```text
Today:
Burger = $10
User places order

Tomorrow:
Restaurant changes Burger to $12
```

The old order should still show:

```text
Burger = $10
```

That is why `order_line_items` stores:

中文说明：这一段是快照字段清单，目的是让历史订单不受后续菜单变化影响。

- menu item id
- restaurant id
- item name snapshot
- unit price
- quantity
- line total

### Interview Explanation

中文速记：面试回答模板，强调历史订单需要稳定记录当时的商品名和价格。

I modeled order lifecycle explicitly with an enum and persisted immutable order line item snapshots. This prevents historical orders from changing when menu data changes later.

## 5. Idempotent Checkout

中文速记：幂等 checkout，核心是同一个用户同一个 key 重试不会重复下单。

### What I Built

中文速记：这里讲 `Idempotency-Key` header 和 `idempotency_records` 表。

Checkout accepts an HTTP header:

中文说明：客户端每次 checkout 传一个唯一 key，用来识别“这是不是同一次下单重试”。

```http
Idempotency-Key: test-checkout-001
```

I added an `idempotency_records` table to track:

中文说明：这张表就是幂等记录表，保存 key 和它最终对应的订单结果。

- idempotency key
- customer id
- operation type
- request path
- created order id
- response status
- created timestamp
- expiration timestamp

### How It Works

中文速记：流程是先检查/占住幂等 key，再创建订单，最后把 order id 写回幂等记录。

Checkout flow:

```text
POST /cart/checkout
  -> check idempotency_records
  -> if completed record exists, return existing order
  -> if in-progress record exists, return conflict
  -> create in-progress idempotency record
  -> create order
  -> create order line items
  -> clear cart
  -> update idempotency record with created order id
  -> publish Kafka OrderCreatedEvent
```

### Why This Matters

中文速记：解决重复点击、网络重试、客户端超时重试导致重复订单的问题。

Without idempotency:

```text
User clicks checkout twice
or mobile network retries
or client times out and retries
```

The system might create duplicate orders.

With idempotency:

```text
same user + same key + same operation
  -> returns the same order
  -> does not create duplicate order
```

### Interview Explanation

中文速记：面试回答模板，强调用数据库记录 key，重复请求返回同一张订单。

I implemented idempotent checkout using an `Idempotency-Key` header and a database-backed idempotency record. The checkout transaction first reserves the key, then creates the order, and finally stores the created order id. Duplicate retries return the original order instead of creating a new one.

## 6. Transaction-Safe Checkout

中文速记：事务安全，checkout 里的多个数据库操作要么全部成功，要么失败回滚。

### What I Built

中文速记：这里讲 `OrderService.checkout()` 使用 `@Transactional`。

`OrderService.checkout()` is transactional.

Inside one transaction:

中文说明：这些步骤必须作为一个整体成功，否则 checkout 会留下不一致状态。

- idempotency record is created
- order is created
- inventory is deducted
- order line items are created
- cart is cleared
- idempotency record is completed

### Why This Matters

中文速记：库存失败或订单创建失败时，不能留下半成品订单或错误购物车状态。

If inventory deduction fails, the transaction rolls back.

中文说明：库存扣减失败时，Spring 会回滚整个 checkout，不会只回滚库存。

That means:

```text
No partial order
No cart cleanup
No completed idempotency record
No inconsistent database state
```

### Interview Explanation

中文速记：面试回答模板，强调 Spring transaction rollback 避免 partial state。

I used Spring `@Transactional` around checkout so database writes behave atomically. If inventory deduction or order creation fails, Spring rolls back the transaction and the checkout does not leave partial state.

## 7. Redis Cache-Aside

中文速记：Redis 缓存，重点是 cache-aside：先查缓存，miss 再查数据库并回填。

### What I Built

中文速记：这里列出被缓存的数据和相关文件。

I added Redis caching for high-read data:

| Data | Cache |
| --- | --- |
| restaurant list | `restaurantList` |
| restaurant menu | `restaurantMenu` |
| cart | `cart` |

Important files:

中文说明：这一段是 Redis 缓存相关代码入口。

- `RedisConfig.java`
- `RestaurantService.java`
- `MenuItemService.java`
- `CartService.java`

### How It Works

中文速记：这里讲缓存命中直接返回，缓存未命中才查 PostgreSQL。

Cache-aside pattern:

```text
Request data
  -> check Redis
  -> if hit, return cached value
  -> if miss, query PostgreSQL
  -> store result in Redis
  -> return result
```

Spring annotations:

中文说明：`@Cacheable` 负责读缓存/写缓存，`@CacheEvict` 负责数据变化后清缓存。

```java
@Cacheable
@CacheEvict
```

Redis config uses JSON serialization so DTOs can be stored and restored correctly.

### Why JSON Serialization Was Needed

中文速记：Redis 存的是 bytes，需要把 Java DTO 转 JSON 存进去再读回来。

Redis stores bytes. Spring needs to convert Java DTOs to bytes and back.

中文说明：Redis 不认识 Java object，所以必须序列化成 JSON/bytes 存进去，再反序列化读回来。

I configured:

```text
GenericJackson2JsonRedisSerializer
```

This stores cached values as JSON instead of requiring every DTO to implement Java `Serializable`.

### Interview Explanation

中文速记：面试回答模板，强调 Redis 降低高频菜单/餐厅查询的数据库压力。

I implemented Redis cache-aside for high-read restaurant and menu APIs. Redis reduces database load on repeated menu browsing. I also configured JSON serialization for Redis cache values to avoid Java serialization issues.

## 8. Kafka Event-Driven Workflow

中文速记：Kafka 异步事件流，重点是 checkout 不同步做支付和通知，而是发事件让 consumer 后台处理。

### What I Built

中文速记：这里列出 topic、producer、consumer、event model。

Kafka topics:

中文说明：topic 可以理解成不同类型事件的消息通道。

```text
order.created
payment.succeeded
payment.failed
```

Producer:

中文说明：producer 负责把事件发到 Kafka。

- `OrderEventProducer`

Consumers:

中文说明：consumer 负责监听 Kafka topic 并处理事件。

- `PaymentEventConsumer`
- `OrderPaymentConsumer`
- `NotificationConsumer`

Event models:

- `OrderCreatedEvent`
- `PaymentSucceededEvent`
- `PaymentFailedEvent`

### Flow

中文速记：完整异步链路：创建订单 -> 发 OrderCreatedEvent -> 模拟支付 -> 更新订单状态 -> 模拟通知。

```text
checkout
  -> create order with status CREATED
  -> publish OrderCreatedEvent
  -> PaymentEventConsumer receives order.created
  -> PaymentService simulates payment
  -> publish PaymentSucceededEvent or PaymentFailedEvent
  -> OrderPaymentConsumer updates order status to PAID or FAILED
  -> NotificationConsumer simulates email/push notification
```

### Why Kafka

中文速记：Kafka 的意义是解耦和异步，让 checkout 不被支付/通知拖慢。

Without Kafka:

中文说明：同步模式下，checkout 会被支付和通知拖慢。

```text
checkout request must do everything synchronously:
create order
process payment
update status
send notification
```

With Kafka:

中文说明：异步模式下，checkout 只负责创建订单和发事件，后续工作交给 consumer。

```text
checkout only creates order and emits event
payment/status/notification happen asynchronously
```

Benefits:

中文说明：这一段是 Kafka 的项目亮点，可以直接转成面试回答。

- faster checkout response
- better fault isolation
- independent consumers
- easier to add notification/inventory/payment workflows later

### Consumer Groups

中文速记：consumer group 决定是“分工消费”还是“不同业务都收到一份消息”。

Different responsibilities use different consumer groups:

中文说明：不同 group 都能收到同一 topic 的消息；同一个 group 里面是多个实例分工消费。

```text
onlineorder-payment-service
onlineorder-order-service
onlineorder-notification-service
```

This means the same `payment.succeeded` event can be processed by both:

```text
OrderPaymentConsumer
NotificationConsumer
```

If they used the same group, Kafka would treat them as workers in the same group and only one might receive the message.

### Kafka Serialization Issue

中文速记：Kafka 存 bytes，不存 Java object；type header 帮 consumer 知道要反序列化成哪个 event class。

Kafka stores bytes, not Java objects.

Spring Kafka uses:

中文说明：producer 写 JSON bytes，consumer 再把 JSON bytes 还原成对应 event class。

```text
JsonSerializer
JsonDeserializer
```

The producer must include type headers so consumers know which Java class to deserialize into.

I removed:

```yaml
spring.json.add.type.headers: false
```

because it caused consumers to receive JSON bytes without class type information.

### Interview Explanation

中文速记：面试回答模板，强调 event-driven workflow 解耦订单、支付、通知。

I introduced Kafka to decouple checkout from payment and notification. Order creation publishes an event, payment processing consumes it asynchronously, then emits success/failure events that update order status and trigger notification simulation.

## 9. Optimistic Locking Inventory

中文速记：乐观锁库存，重点是用 `stock + version` 防止并发 checkout 超卖。

### What I Built

中文速记：这里列出库存相关字段、service、异常和 repository SQL。

I added inventory fields to `menu_items`:

中文说明：`stock` 是库存数量，`version` 是乐观锁版本号。

```sql
stock INTEGER NOT NULL DEFAULT 100,
version INTEGER NOT NULL DEFAULT 0
```

I added:

中文说明：这一段是库存扣减相关代码入口。

- `InventoryService`
- `InventoryNotAvailableException`
- `deductStockWithVersion(...)` in `MenuItemRepository`

### SQL

中文速记：核心 SQL，同时检查库存够不够和 version 有没有被别人改过。

```sql
UPDATE menu_items
SET stock = stock - :quantity,
    version = version + 1
WHERE id = :menuItemId
  AND stock >= :quantity
  AND version = :version
```

### How It Prevents Overselling

中文速记：两个用户同时买时，先成功的人会把 version 改掉，后一个人的旧 version 更新失败。

Two users read:

```text
stock = 1
version = 3
```

User A updates first:

```text
stock = 0
version = 4
```

User B tries to update using old version:

```text
WHERE version = 3
```

The update affects `0` rows, so checkout fails or retries.

### Retry Logic

中文速记：version 冲突不一定没库存，所以重新读最新 stock/version，有限重试。

If update fails:

中文说明：更新失败可能是库存没了，也可能只是 version 被别人先改了，所以要重新读一次。

```text
reload latest stock/version
if stock is insufficient, return 409
if stock is still available, retry with latest version
retry at most 3 times
```

### Why This Matters

中文速记：防止库存只有 1 份却卖出 2 份的超卖问题。

This prevents:

```text
stock = 1
two users checkout
both orders succeed
overselling happens
```

### Interview Explanation

中文速记：面试回答模板，强调 conditional update、version check、limited retry、409 conflict。

I implemented optimistic locking using a `version` column and conditional update SQL. If another transaction updates the same menu item first, the version check fails. The service reloads latest stock/version and retries a limited number of times. If stock is insufficient or conflicts continue, checkout returns `409 CONFLICT`.

## 10. Redis-Backed Rate Limiting

中文速记：Redis 限流，重点是 filter 在 controller 前拦截请求，用 Redis INCR 统计每分钟请求次数。

### What I Built

中文速记：这里列出限流 service、filter、测试和受保护接口。

I added:

- `RateLimitService`
- `RateLimitingFilter`
- `RateLimitServiceTests`

Protected endpoints:

中文说明：这一段是当前限流规则，checkout 更严格，菜单查询更宽松。

| API | Limit |
| --- | --- |
| `POST /cart/checkout` | 5 requests/minute |
| `GET /restaurants/menu` | 60 requests/minute |
| `GET /restaurant/{id}/menu` | 60 requests/minute |

### How It Works

中文速记：Redis key 按接口类型、用户/IP、当前分钟组成，每次请求 INCR 一次。

Each request builds a Redis key:

中文说明：key 里包含接口类型、用户或 IP、当前分钟，用来统计这一分钟内的请求次数。

```text
rate_limit:checkout:user:test@example.com:202605081730
rate_limit:menu:ip:127.0.0.1:202605081730
```

The format is:

```text
rate_limit:{scope}:{identity}:{currentMinute}
```

Then Redis does:

中文说明：`INCR` 增加计数，`EXPIRE` 设置过期时间，避免旧窗口一直占内存。

```text
INCR key
EXPIRE key 60 seconds
```

If count exceeds the limit, `RateLimitingFilter` returns:

```http
429 Too Many Requests
```

### Why Filter

中文速记：filter 的意义是在请求进入 controller 前拦截，超限就直接返回 429。

The filter runs before controllers:

中文说明：filter 是前置拦截点，适合做认证、日志、限流这种通用检查。

```text
HTTP request
  -> RateLimitingFilter
  -> Spring Security filters
  -> Controller
```

If the request exceeds the limit:

中文说明：超限时直接写 response 并 return，不进入 controller。

```text
write 429 response
return
do not call filterChain.doFilter(...)
controller never runs
```

### Why Redis

中文速记：Redis INCR 是原子且很快，适合做多实例共享的限流计数器。

Redis `INCR` is atomic and fast.

中文说明：原子意味着多个请求同时加计数也不会加错，适合高并发限流。

It is good for rate limiting because:

- counters are shared across app instances
- operations are low latency
- TTL automatically clears old counters
- it protects expensive endpoints from traffic spikes

### Interview Explanation

中文速记：面试回答模板，强调 OncePerRequestFilter、Redis fixed-window counter、429 response。

I implemented Redis-backed fixed-window rate limiting using a Spring `OncePerRequestFilter`. The filter checks request path, builds a Redis counter key by endpoint and user/IP, atomically increments the counter, and returns `429` when the client exceeds the configured limit.

## 11. Docker Compose

中文速记：Docker Compose，本地一键启动 PostgreSQL、Redis、Kafka。

### What I Built

中文速记：这里列出 docker-compose 管理的三个外部服务。

`docker-compose.yml` starts:

- PostgreSQL
- Redis
- Kafka

Common commands:

中文说明：这些是本地启动/查看/重启基础设施最常用的 Docker 命令。

```bash
docker compose up -d db redis kafka
docker compose ps
docker compose stop kafka
docker compose rm -f kafka
docker compose up -d kafka
```

### Why Docker Compose

中文速记：不用手动安装基础设施，保证本地开发环境可复现。

It makes local development reproducible.

中文说明：可复现的意思是换一台机器也可以用同一份 compose 文件启动相同依赖。

Instead of manually installing PostgreSQL, Redis, and Kafka, I can start all infrastructure with one command.

### Interview Explanation

中文速记：面试回答模板，强调可复现 local infrastructure。

I used Docker Compose to provide a reproducible local backend environment with PostgreSQL, Redis, and Kafka. This makes the project easy to run and test locally.

## 12. Important CLI Commands

中文速记：常用命令速查，包含启动服务、启动后端、curl 登录、cookie、查库存。

### Start Infrastructure

中文速记：启动 PostgreSQL、Redis、Kafka 容器。

```bash
docker compose up -d db redis kafka
```

`-d` means detached mode, so containers run in the background.

中文说明：`-d` 就是后台运行，terminal 不会一直被容器日志占住。

### Run Backend

中文速记：切到 Java 17 并启动 Spring Boot。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootRun
```

Spring Boot 3 requires Java 17.

中文说明：如果本机默认 Java 是 11，先切到 Java 17 再跑 `bootRun`。

### Signup

中文速记：注册用户，请求体是 JSON。

```bash
curl -i -X POST http://localhost:8080/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password","first_name":"Test","last_name":"User"}'
```

### Login And Save Cookie

中文速记：登录并把 Spring Security 返回的 JSESSIONID 保存到 cookie.txt。

```bash
curl -i -c cookie.txt -X POST http://localhost:8080/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=test@example.com&password=password"
```

`-c cookie.txt` saves the session cookie.

中文说明：`-c` 是把服务端返回的 cookie 写进文件。

### Use Cookie

中文速记：后续请求带上 cookie.txt，表示当前用户已登录。

```bash
curl -i -b cookie.txt http://localhost:8080/cart
```

`-b cookie.txt` sends the saved session cookie.

中文说明：`-b` 是请求时带上 cookie 文件，相当于告诉后端“我是已登录用户”。

### Check Inventory

中文速记：进入 PostgreSQL 容器执行 SQL，查看库存和 version。

```bash
docker compose exec db psql -U postgres -d onlineorder \
  -c "SELECT id, name, stock, version FROM menu_items WHERE id = 1;"
```

This runs a SQL query inside the PostgreSQL container.

中文说明：`docker compose exec db` 是进入 db 容器执行命令，`psql -c` 是执行一段 SQL。

## 13. Testing Strategy

中文速记：测试策略，unit test 测核心逻辑，manual test 跑完整业务链路。

Unit tests cover:

- checkout creates order and clears cart
- duplicate idempotency key returns existing order
- inventory insufficient stock failure
- optimistic lock version conflict retry
- Redis rate limit allow/reject paths

Manual tests cover:

- signup/login cookie flow
- add to cart
- checkout with idempotency key
- order status updates to `PAID`
- stock decreases and version increases
- menu endpoint returns `429` after rate limit is exceeded

## 14. Resume Talking Points

中文速记：简历 bullet 参考，挑 5-7 条放进简历，不需要全部照抄。

Possible resume bullets:

- Designed and developed a scalable online food ordering backend using Java, Spring Boot, PostgreSQL, Redis, Kafka, and Docker, supporting restaurant browsing, cart management, checkout, and order tracking.
- Implemented idempotency-key based checkout with transaction-safe order creation to prevent duplicate orders under retries and unstable network conditions.
- Built Kafka-based asynchronous event workflows to decouple order creation, payment simulation, order status updates, and notification delivery.
- Applied Redis cache-aside strategy for high-read restaurant/menu/cart queries and Redis-backed rate limiting to protect checkout and menu APIs from traffic spikes.
- Implemented optimistic locking with `stock/version` fields and limited retry to prevent inventory overselling under concurrent checkout requests.
- Secured user and order workflows with Spring Security session-based authentication backed by PostgreSQL user lookup.
- Containerized PostgreSQL, Redis, and Kafka with Docker Compose for reproducible local development and system design demos.

## 15. Common Interview Q&A

中文速记：面试问答速查，适合面试前快速复习。

### Why use idempotency key?

中文速记：防重复下单。

To prevent duplicate order creation when users retry checkout, double click, or experience network timeouts.

### Why Kafka?

中文速记：异步解耦 checkout、支付、通知。

Kafka decouples checkout from slow or failure-prone downstream work like payment and notification.

### Why Redis cache?

中文速记：降低高频读接口的数据库压力。

Restaurant and menu browsing are read-heavy. Redis reduces repeated database reads and improves response time.

### Why optimistic locking?

中文速记：防止并发扣库存导致超卖。

It prevents overselling by ensuring inventory updates only succeed if the version has not changed since the item was read.

### Why limited retry?

中文速记：version 冲突时重新读最新库存再试，但不能无限重试。

A version conflict does not always mean stock is gone. The service reloads latest stock/version and retries a few times, then fails gracefully if contention continues.

### Why Redis rate limiting?

中文速记：用 Redis 原子计数器限制请求频率，保护高风险接口。

Redis provides fast atomic counters with TTL and can be shared across app instances, making it suitable for request rate limiting.

### Is this a microservice architecture?

中文速记：不是完全微服务，是单体中使用微服务风格边界和事件驱动。

It is still a single Spring Boot application, but it uses microservices-style boundaries and event-driven design. Kafka consumers model separate responsibilities such as payment, order update, and notification.

### What would you improve next?

中文速记：后续可扩展方向，适合面试官追问时回答。

Potential improvements:

- split payment/inventory/notification into separate services
- add Spring Cloud Gateway if moving to real microservices
- add distributed tracing and structured logging
- add integration tests with Testcontainers
- implement real payment provider integration
- implement inventory reservation and release on payment failure
