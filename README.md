# 💳 Payment System — Spring Boot + GraphQL

A **production-ready** payment processing system built with Spring Boot 3.2, Spring for GraphQL,
PostgreSQL, and JWT security. Designed for reliability, scalability, and observability.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Client (Web / Mobile)                       │
└───────────────────────────┬─────────────────────────────────────────┘
                            │  HTTP / WebSocket
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Security (JWT Filter)                       │
│              JwtAuthenticationFilter → SecurityContextHolder          │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  GraphQL Layer (/graphql, /graphql-ws)               │
│                                                                       │
│  @QueryMapping     @MutationMapping     @SubscriptionMapping          │
│  @SchemaMapping    @Argument            @ContextValue                 │
│                                                                       │
│  ┌────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │ PaymentGQL     │  │SubscriptionCtrl  │  │ GraphQLExceptionRslvr│  │
│  │ Controller     │  │  (Reactor Sinks) │  │ (Error Classification│  │
│  └───────┬────────┘  └──────────────────┘  └──────────────────────┘  │
└──────────┼──────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Service Layer                                 │
│                                                                       │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────────┐  │
│  │PaymentService│ │AccountService│ │RiskService │ │AuditService  │  │
│  │              │ │              │ │            │ │  (@Async)    │  │
│  │ @CircuitBreak│ │ @Cacheable   │ │            │ │              │  │
│  │ @Retry       │ │ @CacheEvict  │ │            │ │              │  │
│  └──────┬───────┘ └──────────────┘ └────────────┘ └──────────────┘  │
└─────────┼───────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Data Layer (JPA / Hibernate)                    │
│                                                                       │
│  Accounts  ──────── Payments ──────── Transactions                   │
│                        │                                              │
│                        ├──── Refunds                                  │
│                        └──── AuditLogs                                │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │  PostgreSQL (HikariCP pool, Flyway migrations, Optimistic Lock) │  │
│  └───────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### Run with Docker

```bash
# Start PostgreSQL
docker run -d \
  --name payments-db \
  -e POSTGRES_DB=payments \
  -e POSTGRES_USER=payments \
  -e POSTGRES_PASSWORD=secret \
  -p 5432:5432 \
  postgres:16-alpine

# Run the application
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-DGRAPHIQL_ENABLED=true"
```

### GraphiQL Playground
Open: http://localhost:8080/graphiql

---

## 📡 GraphQL API

### Queries

```graphql
# Get account
query {
  account(id: "a0000000-0000-0000-0000-000000000001") {
    id email fullName status currency balance
    payments(page: 0, size: 5) {
      content { id referenceId amount status }
      totalElements hasNext
    }
  }
}

# List payments with filters
query {
  payments(
    filter: { status: COMPLETED, currency: USD, minAmount: "100" }
    sort: { field: "createdAt", direction: DESC }
    page: 0, size: 20
  ) {
    content {
      referenceId amount status processingFee netAmount
      sender { email }
      recipient { email }
    }
    totalElements totalPages
  }
}

# Analytics
query {
  paymentStats(currency: USD, period: "month") {
    totalVolume totalCount successRate averageAmount
  }
}
```

### Mutations

```graphql
# Create account
mutation {
  createAccount(input: {
    email: "merchant@example.com"
    fullName: "My Merchant"
    currency: USD
    externalId: "MERCHANT-001"
  }) { id email status }
}

# Initiate payment
mutation {
  initiatePayment(input: {
    senderId: "..."
    recipientId: "..."
    amount: "250.00"
    currency: USD
    method: CREDIT_CARD
    description: "Order #12345"
    idempotencyKey: "unique-client-key-001"
  }) {
    success
    message
    payment { id referenceId status processingFee netAmount }
    errors { field message }
  }
}

# Confirm payment
mutation {
  confirmPayment(paymentId: "...") {
    success payment { status completedAt }
  }
}

# Refund
mutation {
  refundPayment(input: {
    paymentId: "..."
    amount: "100.00"
    reason: "Customer requested"
    idempotencyKey: "refund-unique-key-001"
  }) {
    success
    refund { id amount status processedAt }
  }
}
```

### Subscriptions (WebSocket)

```graphql
subscription {
  paymentStatusUpdated(paymentId: "...") {
    id status updatedAt
  }
}
```

---

## 🔑 Key Design Decisions

| Concern | Solution |
|---|---|
| **Idempotency** | Every mutation carries an `idempotencyKey`; duplicate requests return the original result |
| **Concurrency** | Pessimistic locks (`SELECT FOR UPDATE`) on account rows during balance changes |
| **Optimistic Locking** | `@Version` on `Account` and `Payment` entities to detect stale updates |
| **Validation** | Domain-layer `PaymentValidator` + Bean Validation annotations on DTOs |
| **Fraud Detection** | `RiskService` scoring (amount, velocity, account age, KYC) — extend for ML integration |
| **Resilience** | Resilience4j Circuit Breaker + Retry wrapping the payment processor |
| **Audit** | Async `AuditService` writing immutable `AuditLog` records with IP / user-agent |
| **Error Handling** | `GraphQLExceptionResolver` maps domain exceptions to typed GraphQL errors |
| **Observability** | Micrometer + Prometheus metrics on field fetch latency + error rates |
| **Security** | JWT-based stateless auth; `@PreAuthorize` on all resolvers |
| **Schema Mapping** | `@QueryMapping`, `@MutationMapping`, `@SchemaMapping`, `@SubscriptionMapping` |

---

## 🧪 Testing

```bash
# All tests
./mvnw test

# Unit tests only
./mvnw test -Dtest="*Test"

# Integration tests only
./mvnw test -Dtest="*IntegrationTest"
```

### Test Coverage Areas

| Layer | Test File | Annotations |
|---|---|---|
| Domain Model | `DomainModelTest` | `@Nested`, `@DisplayName` |
| Validation | `PaymentValidatorTest` | `@ParameterizedTest`, `@CsvSource` |
| Service (unit) | `PaymentServiceTest` | `@ExtendWith(MockitoExtension)` |
| Service (unit) | `AccountServiceTest` | `@Mock`, `@InjectMocks` |
| Repository | `RepositoryIntegrationTest` | `@DataJpaTest` |
| GraphQL layer | `PaymentGraphQLControllerTest` | `@GraphQlTest`, `GraphQlTester` |
| Full lifecycle | `PaymentSystemIntegrationTest` | `@SpringBootTest`, `@Transactional` |

---

## 📊 Monitoring

```bash
# Health
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Custom metrics
graphql_field_fetch_seconds_*   # per-field GraphQL latency
graphql_errors_total             # total GraphQL errors
```

---

## 📁 Project Structure

```
src/main/java/com/payments/
├── PaymentSystemApplication.java
├── config/
│   ├── AppConfig.java             # Thread pool, instrumentation
│   ├── GraphQLConfig.java         # Scalar registrations
│   └── SecurityConfig.java        # JWT security chain
├── exception/
│   ├── PaymentExceptions.java     # Domain exception hierarchy
│   ├── Exceptions.java            # Factory methods
│   └── GraphQLExceptionResolver.java
├── graphql/
│   ├── resolver/
│   │   ├── PaymentGraphQLController.java   # @QueryMapping / @MutationMapping
│   │   └── SubscriptionController.java     # @SubscriptionMapping
│   ├── scalar/
│   │   └── CustomScalars.java     # Currency scalar
│   └── directive/
│       └── GraphQLMetricsInstrumentation.java
├── model/
│   ├── entity/                    # JPA entities
│   └── dto/                       # Input/output records
├── repository/                    # Spring Data JPA repositories
├── security/
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   └── AccountUserDetailsService.java
├── service/
│   ├── AccountService.java
│   ├── PaymentService.java
│   ├── AuditService.java
│   ├── RiskService.java
│   └── impl/
│       ├── AccountServiceImpl.java
│       └── PaymentServiceImpl.java
└── validation/
    └── PaymentValidator.java
```
