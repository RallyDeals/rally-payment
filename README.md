# Rally Payments

Event-driven payment microservice for the **RallyDeals** platform. It orchestrates card payments through Stripe, manages saved payment methods (wallet), and integrates with the rest of the RallyDeals ecosystem over Kafka using the transactional outbox + inbox patterns.

## Highlights

- **Stripe-backed payments** — PaymentIntents for charge, authorize/capture, cancel (void), and 3DS handling.
- **Saved payment methods** — buyer wallet built on Stripe SetupIntents with card fingerprinting for duplicate detection.
- **Per-user Stripe customers** — automatic customer provisioning, stored in `payment_profiles`.
- **Reliable messaging** — transactional **outbox** for publishing and **inbox** for idempotent consumption of Kafka messages.
- **Observability first** — Micrometer metrics, Prometheus, OTel tracing, structured JSON logging, custom health indicators, correlation/trace propagation across HTTP and Kafka.
- **Buyer-scoped API** — endpoints are scoped to the caller via the `X-User-Id` header; CORS enabled for the frontend.

## Tech Stack

| Layer        | Technology                                             |
| ------------ | ------------------------------------------------------ |
| Runtime      | Java 21, Spring Boot 4.1.0, Spring MVC                 |
| Persistence  | Spring Data JPA, PostgreSQL 16, Flyway migrations      |
| Messaging    | Spring Kafka, Kafka (native KRaft broker)              |
| Payments     | Stripe Java SDK 26.0.0                                 |
| Common       | `rally-common` (shared exceptions & contract) 0.3.0    |
| Observability| Micrometer, Prometheus, OpenTelemetry (OTLP), Logstash Logback (In progress) |
| API Docs     | springdoc-openapi (Swagger UI)                         |
| Build        | Maven                                                 |

### Reliability patterns

- **Transactional Outbox** — domain transitions and outbox rows are written in the same DB transaction by `PaymentEventPublisher` (driven by Spring domain events emitted from the `Payment` aggregate). `OutboxRelay` polls `PENDING` rows (`FOR UPDATE SKIP LOCKED`), publishes to Kafka, and tracks retries until `max_retries`.
- **Inbox / idempotency** — every consumed Kafka message is recorded in `inbox_messages` keyed by message id; duplicates are skipped. Stripe webhooks use the same inbox deduplication.
- **Optimistic locking** — `version` columns on `payments`, `payment_methods`, and `payment_profiles`.

## Project Structure

```
src/main/java/com/rally/payment
├── api/
│   ├── controller/        # PaymentController, PaymentMethodController, StripeWebhookController
│   └── dto/               # request/response records
├── config/                # Stripe, Kafka, CORS (WebConfig), property classes
├── enums/                 # PaymentStatus
├── events/                # domain events (PaymentCharged, PaymentFailed, ...)
├── exception/             # ApiExceptionHandler
├── filters/               # CorrelationIdFilter, HttpRequestLoggingFilter
├── health/                # KafkaHealthIndicator, OutboxLagHealthIndicator
├── messaging/
│   ├── consumer/          # PaymentInitiationListener
│   ├── contract/          # message types, headers, payload records
│   ├── inbox/             # InboxMessage
│   ├── Interceptors/      # KafkaHeaderMdcInterceptor
│   └── outbox/            # OutboxMessage
├── metrics/               # PaymentMetrics
├── model/                 # Payment, PaymentMethod, PaymentMethodCard, PaymentProfile
├── relay/                 # OutboxRelay, OutboxPublisher
├── repository/            # Spring Data repositories
├── service/               # PaymentService, PaymentQueryService, PaymentMethodService,
│                          # PaymentProfileService, StripeProvisioningService,
│                          # StripeWebhookService, PaymentEventPublisher
└── stripe/                # StripePaymentGateway, StripeMetadata

src/main/resources/
├── db/migration/          # Flyway SQL migrations (V1..V11)
├── application.properties
└── logback-spring.xml
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker + Docker Compose
- Stripe account (test keys) and optionally the [Stripe CLI](https://stripe.com/docs/stripe-cli)
- Access to the private `com.rally:rally-common` package (GitHub Maven Packages; add credentials to your Maven `settings.xml`)

## Getting Started

### 1. Configure environment

Copy `.env.example` to `.env` and fill in the values:

```bash
cp .env.example .env
```

Minimum required values:

```ini
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

### 2. Start infrastructure

```bash
docker compose up -d
```

This starts:

- **Kafka** broker (`apache/kafka-native`, port `9092`) and **Kafka UI** (`http://localhost:8080`)
- **PostgreSQL** for payments (host port `5433`)
- **Stripe CLI** that listens for Stripe events and forwards them to `http://host.docker.internal:8089/api/v1/payments/webhook`

> The Stripe CLI forwards to the host machine because the payment service runs locally. The port and path follow `SERVER_PORT` / `STRIPE_WEBHOOK_PATH`.

### 3. Run the service

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8089`.

- Swagger UI: `http://localhost:8089/swagger-ui.html`
- OpenAPI spec: `http://localhost:8089/api-docs`


## Configuration

All settings are env-driven with sane defaults. `.env` is loaded via `spring.config.import=optional:file:.env[.properties]`.

| Variable                        | Default                     | Description                                        |
| ------------------------------- | --------------------------- | -------------------------------------------------- |
| `SERVER_PORT`                   | `8089`                      | HTTP port of the payment service                   |
| `STRIPE_API_KEY`                | _(required)_                | Stripe API key                                     |
| `STRIPE_WEBHOOK_SECRET`         | _(required)_                | Stripe webhook signing secret                      |
| `STRIPE_WEBHOOK_PATH`           | `/api/v1/payments/webhook`  | Webhook endpoint path                              |
| `DB_HOST` / `DB_PORT`           | `localhost` / `5433`        | PostgreSQL host / port                             |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `payment_db` / `user` / `password` | Database credentials                   |
| `KAFKA_BOOTSTRAP_SERVERS`       | `localhost:9092`            | Kafka bootstrap servers                            |
| `CORS_ALLOWED_ORIGINS`          | `http://localhost:4200`     | Comma-separated allowed origins                    |
| `LOGGING_LEVEL_ROOT` / `LOGGING_LEVEL_RALLY` / `LOGGING_LEVEL_HIBERNATE_SQL` | `INFO` / `DEBUG` / `DEBUG` | Log levels                  |
| `MANAGEMENT_HEALTH_SHOW_DETAILS`| `always`                    | Actuator health detail exposure                    |
| `OTEL_EXPORTER_OTLP_ENDPOINT`   | `http://localhost:4318/v1/traces` | OTLP trace exporter endpoint               |

### Stripe currency

The default currency is set via `stripe.currency=egp` in `application.properties`.

## Kafka Messaging Contract

### Consumed topic: `order.payments_requested`

Messages carry the message type in the `X-Type` header. Supported types:

| Message type | Payload                                    | Behavior                              |
| ------------ | ------------------------------------------ | ------------------------------------- |
| `Payment.InitRequired.Charge`   | `{ userId, orderId, paymentMethodId, amount }` | Create payment and charge        |
| `Payment.InitRequired.Authorize`| `{ userId, orderId, paymentMethodId, amount }` | Create payment and authorize    |
| `Payment.SettlementRequired.Capture` | `{ paymentId, orderId }`              | Capture an authorized payment         |
| `Payment.SettlementRequired.Void`    | `{ paymentId, orderId }`              | Void / release held funds             |
| `Payment.Timeout`               | `{ orderId }`                             | Resolve settlement timeout            |

### Published topic: `payment.events`

Published via the outbox relay:

`Payment.Charged`, `Payment.Captured`, `Payment.Authorized`, `Payment.Failed`, `Payment.Voided`

> `Payment.Initialized` and `Payment.RequiresAction` are intentionally **not** published — the order-payment contract does not map them.

### Standard headers

| Header           | Meaning                                  |
| ---------------- | ---------------------------------------- |
| `X-Id`           | Unique message id                        |
| `X-Type`         | Message type (see above)                 |
| `X-Correlation-Id` | Correlation id (order id by default)   |
| `X-Causation-Id` | Id of the message that caused this one    |
| `X-Trace-Id`     | Distributed trace id                     |

## Payment Lifecycle

```
 PENDING ──▶ REQUIRES_ACTION ──▶ (3DS retry)
    │
    ├──▶ AUTHORIZED ──▶ CAPTURED
    │         │
    │         └──▶ VOIDED        (manual void / settlement timeout)
    ├──▶ CHARGED
    │
    └──▶ FAILED
```

`PaymentStatus` values: `PENDING`, `AUTHORIZED`, `CHARGED`, `CAPTURED`, `FAILED`, `VOIDED`, `REQUIRES_ACTION`, `REFUNDED`, `PARTIALLY_REFUNDED`.

## API Reference

All request/response bodies are JSON. Payment-method and user-scoped payment endpoints require the `X-User-Id` header (buyer identity) instead of a path `userId`.

### Payments — `/api/payments`

| Method | Path                         | Description                              |
| ------ | ---------------------------- | ---------------------------------------- |
| POST   | `/api/payments`              | Create a payment (returns `201`)         |
| GET    | `/api/payments/{paymentId}`  | Get a payment by id                      |
| GET    | `/api/payments/user/{userId}`| List payments for a user (`X-User-Id`)   |
| GET    | `/api/payments/order/{orderId}` | List payments for an order             |
| POST   | `/api/payments/{paymentId}/authorize` | Authorize a payment                |
| POST   | `/api/payments/{paymentId}/capture`  | Capture an authorized payment     |
| POST   | `/api/payments/{paymentId}/fail`     | Mark a payment as failed           |
| POST   | `/api/payments/{paymentId}/void`     | Void a payment                     |

**Create payment**

```json
{
  "paymentMethodId": "pm_...",
  "userId": "00000000-0000-0000-0000-000000000000",
  "orderId": "00000000-0000-0000-0000-000000000000",
  "amount": 100.00
}
```

### Saved payment methods — `/api/payment-methods`

| Method | Path                                 | Description                                   |
| ------ | ------------------------------------ | --------------------------------------------- |
| GET    | `/api/payment-methods`               | List the buyer's saved methods (masked)       |
| GET    | `/api/payment-methods/{methodId}`    | Get one saved method                          |
| POST   | `/api/payment-methods/setup-intent`  | Start adding a card (Stripe SetupIntent)      |
| POST   | `/api/payment-methods`               | Confirm a SetupIntent and save the method     |
| PUT    | `/api/payment-methods/{methodId}/default`    | Set default method                  |
| DELETE | `/api/payment-methods/{methodId}/default`    | Clear the default flag               |
| DELETE | `/api/payment-methods/{methodId}`    | Hard-delete a saved method                   |

Responses never expose the raw Stripe token; card details are returned masked (`cardBrand`, `cardLast4`, `cardExpMonth`, `cardExpYear`, `cardFingerprint`).

### Stripe webhook

`POST {stripe.webhook-path}` (default `/api/v1/payments/webhook`)

Handled event types: `payment_intent.succeeded`, `payment_intent.canceled`, `payment_intent.payment_failed`, `setup_intent.succeeded`.

## Observability (In progress...)

- **Metrics** — exposed at `/actuator/prometheus`. Custom metrics in `PaymentMetrics`: payment created/succeeded/failed counters (by currency & method), `outbox.relay.pending.count` gauge, `outbox.relay.publish.failures.count`, `stripe.webhook.received.count`, `stripe.webhook.processing.duration`, `kafka.event.processed.count`.
- **Health** — `/actuator/health` with custom `KafkaHealthIndicator` (topic reachability) and `OutboxLagHealthIndicator` (marks DOWN when stale `PENDING` outbox messages exist; threshold via `outbox.relay.health-stale-ms`).
- **Tracing** — OpenTelemetry via Micrometer tracing bridge; OTLP exporter (`OTEL_EXPORTER_OTLP_ENDPOINT`). Kafka observation propagates W3C `traceparent` automatically; `OutboxRelay` re-parents spans using the stored `X-Trace-Id`.
- **Logging** — plain text for `local`/`dev` profiles, JSON (Logstash encoder) elsewhere; rolling file appender to `logs/payment-service.log`. MDC keys: `traceId`, `spanId`, `correlationId`, `causationId`, `incomingMessageId`.
- **Correlation** — `CorrelationIdFilter` propagates `X-Correlation-Id`/`X-Trace-Id` across HTTP; `KafkaHeaderMdcInterceptor` does the same across Kafka consumers.

## Database Migrations

Flyway migrations live in `src/main/resources/db/migration` (`V1__initial_schema.sql` … `V11__create_payment_profiles.sql`) and run automatically on startup with `ddl-auto=validate`.

Key tables: `payments`, `payment_methods`, `payment_profiles`, `inbox_messages`, `outbox_messages`.

## Contributing

1. Fork the repo and create a feature branch.
2. Follow conventional commit messages (`feat:`, `fix:`, `refactor:`, `chore:`).
3. Keep migrations additive; never edit an already-applied migration.
4. Ensure `mvn verify` passes before opening a pull request.

## License

Proprietary — part of the RallyDeals platform.
