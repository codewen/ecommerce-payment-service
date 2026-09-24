# Ecommerce Payment Service

Java 25 and Spring Boot 4.1 reference implementation of a failure-aware ecommerce checkout. It models commercetools carts and postcode inventory, Braintree payments, one-off purchases, refunds, and consented auto delivery without requiring external accounts.

The project is deliberately designed for system-design and senior Java interviews: the interesting paths are duplicate requests, uncertain gateway outcomes, concurrent cart changes, expired stock reservations, compensation, and crash recovery.

## What is implemented

- Mock commercetools adapter with postcode inventory and ten-minute `ReserveOnCart` behavior
- Optimistic cart version checks
- Priced cart snapshot before payment
- Authorize, create order, then capture flow
- HTTP and gateway idempotency keys
- Database uniqueness for cart attempts and auto-delivery occurrences
- Braintree timeout represented as an unknown outcome, never an assumed decline
- Scheduled payment reconciliation
- Transactional `OrderPaid` outbox record
- Idempotent partial refunds with over-refund protection
- Consented auto-delivery agreements with configurable weekly cadence
- Duplicate-safe daily occurrence discovery
- Out-of-stock substitution request and explicit decision endpoint
- Consistent API errors and Spring Boot Actuator health/metrics
- Integration tests for the principal failure and retry paths

The detailed reasoning and diagrams are in [docs/architecture.md](docs/architecture.md).

## Technology

- Java 25
- Spring Boot 4.1.1
- Spring MVC and Bean Validation
- Spring Data JPA
- H2 in PostgreSQL compatibility mode for a self-contained demonstration
- Maven

For a production deployment, replace H2 with PostgreSQL and replace the in-memory commercetools and Braintree adapters without changing the application services.

## Run

Prerequisites: JDK 25 and Maven 3.9+.

```bash
mvn test
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## One-off checkout example

Seed postcode inventory:

```bash
curl -X PUT http://localhost:8080/mock/commercetools/inventory \
  -H 'Content-Type: application/json' \
  -d '{"postcode":"2000","sku":"DOG-FOOD","quantity":20}'
```

Create a reserved cart:

```bash
curl -X POST http://localhost:8080/mock/commercetools/carts \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId":"customer-1",
    "postcode":"2000",
    "lines":[{"sku":"DOG-FOOD","quantity":2,"unitPrice":"39.95"}]
  }'
```

Use the returned cart ID and version:

```bash
curl -X POST http://localhost:8080/api/checkouts \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: checkout-customer-1-001' \
  -H 'X-Session-Id: browser-session-123' \
  -d '{
    "customerId":"customer-1",
    "cartId":"REPLACE_ME",
    "cartVersion":1,
    "paymentMethodToken":"nonce-success"
  }'
```

Repeat the identical request with the same `Idempotency-Key`. It returns the original checkout and does not invoke another authorization or capture.

### Simulate an uncertain payment

Use this payment token:

```json
"paymentMethodToken": "nonce-timeout-success"
```

The initial response is `PAYMENT_OUTCOME_UNKNOWN`. The reconciliation worker retrieves the already-created authorization with the same gateway request key and resumes order creation without charging again.

Use `nonce-decline` to simulate a processor decline.

## Partial refund

```bash
curl -X POST http://localhost:8080/api/checkouts/CHECKOUT_ID/refunds \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: refund-return-001' \
  -d '{"amount": "39.95", "reason": "One unit returned"}'
```

Repeating the same refund key returns the original refund. A request that would exceed the captured total is rejected.

## Auto delivery

Create an agreement with explicit future-payment consent:

```bash
curl -X POST http://localhost:8080/api/auto-deliveries \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId":"customer-1",
    "postcode":"2000",
    "paymentMethodToken":"nonce-success",
    "cadenceWeeks":4,
    "nextDeliveryOn":"2026-10-01",
    "futurePaymentConsent":true,
    "items":[{"sku":"DOG-FOOD","quantity":1,"unitPrice":39.95}]
  }'
```

Run discovery manually for a demonstration:

```bash
curl -X POST http://localhost:8080/api/auto-deliveries/admin/run/2026-10-01
```

The database uniqueness constraint on `(subscription_id, scheduled_for)` makes rescanning safe. Each occurrence uses the deterministic checkout key:

```text
autodelivery:{subscriptionId}:{scheduledFor}
```

## Package layout

```text
application/       checkout, reconciliation, refund and auto-delivery use cases
domain/            money and lifecycle states
port/              commercetools and Braintree boundaries
adapter/           deterministic mocks
persistence/       JPA entities, constraints and repositories
web/               REST endpoints and error contract
docs/               architecture and failure-mode reasoning
```

## Important design decisions

- A timeout becomes `PAYMENT_OUTCOME_UNKNOWN`, not `FAILED`.
- Payment intent is persisted before the gateway call.
- Stable gateway request keys are reused after crashes.
- Cart ID and version protect the immutable checkout snapshot.
- Session ID is trace context, not an idempotency boundary.
- Stock is extended or reacquired before calling Braintree.
- Authorization is voided when order creation cannot complete.
- Outbox delivery is at least once; consumers must deduplicate event IDs.
- Auto-delivery substitutions require explicit customer consent.

## Production hardening deliberately left behind adapters

- PostgreSQL migrations and monthly occurrence partitioning
- Real commercetools and Braintree SDK clients
- OAuth/JWT authorization and customer ownership checks
- PCI-compliant tokenization at the browser boundary
- Kafka publisher and consumer inbox table
- Signed substitution links and email provider
- OpenTelemetry traces and business SLO dashboards
- Rate limiting, secrets management, and audit-log export

