# SlotSync — Concurrency-Safe Booking & Scheduling Platform

A portfolio-grade fullstack booking system built to demonstrate graduate-level
engineering judgment — correct concurrency handling under real contention,
layered architecture, and production-aware security decisions — rather than
a functional-but-naive CRUD app.

## The problem

Booking systems routinely double-book the same slot when concurrent requests
race past a naive "check availability, then insert" flow. If two people click
"Book" on the same conference room slot within milliseconds of each other, a
system that doesn't handle this explicitly will happily confirm both.

## Stack

| Layer      | Technology                                                        |
|------------|--------------------------------------------------------------------|
| Backend    | Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security, Flyway |
| Database   | PostgreSQL                                                          |
| Frontend   | React 18, Vite, Axios, React Router                                 |
| Auth       | Stateless JWT access tokens + hashed, rotated refresh tokens        |
| Resilience | Bucket4j rate limiting on the booking endpoint                      |
| Testing    | JUnit 5, Mockito, MockMvc, Testcontainers (real Postgres)            |

## Concurrency strategy (the technical centerpiece)

Three layers, each catching what the layer before it might miss:

1. **Fast path — JPA optimistic locking.** Every `Booking` row carries a
   `@Version` column. Two requests racing for the same slot both read
   version `N`; Hibernate stamps the `UPDATE`/`INSERT` with a version check,
   so whichever transaction commits first wins and the other throws
   `ObjectOptimisticLockingFailureException`.
2. **Backstop — a Postgres partial unique index** on
   `(resource_id, slot_start) WHERE status = 'CONFIRMED'`. This catches the
   case optimistic locking alone can't: two brand-new rows being inserted
   simultaneously, where there's no existing row version to contend over.
3. **Bounded retry.** A transient loss of the fast-path race doesn't
   necessarily mean the slot is gone — it means *this attempt* collided. The
   service retries up to 3 times before surfacing a conflict to the client.

Both failure modes are mapped explicitly to **HTTP 409 Conflict**, not a
generic 500, so the frontend can tell the user "pick another time" rather
than showing a server-error page.

**Design note on retries:** the retry loop calls back into
`self.attemptBooking(...)` via `@Lazy` self-injection, not `this.attemptBooking(...)`.
Calling `this.method()` directly bypasses Spring's transactional proxy
entirely, silently disabling `@Transactional` on the retried call — a subtle
bug that's easy to introduce and easy to miss in review.

`BookingConcurrencyTest` proves this end-to-end: it fires 20 threads at the
exact same slot simultaneously (via `ExecutorService` + `CountDownLatch`) and
asserts that exactly one booking ends up `CONFIRMED`. It runs against a real
Postgres container via Testcontainers, not H2 — H2 doesn't support Postgres
partial indexes, so testing against it would give false confidence that the
guarantee holds.

## Security

- Stateless JWT access tokens (short-lived) for authentication.
- Refresh tokens are random 384-bit values; only a SHA-256 digest is stored
  server-side (never the raw token), and each refresh **rotates** the token —
  the old one is revoked rather than reused.
- RBAC is enforced centrally in `SecurityConfig` (`/api/admin/**` requires
  `ROLE_ADMIN`) rather than scattered across `@PreAuthorize` annotations on
  individual controller methods, for easier auditing.
- The booking endpoint is rate-limited (10 requests/minute per client IP via
  Bucket4j) specifically because it's the endpoint most worth protecting from
  a scripted client hammering the concurrency fast path.

## Running it locally

### Backend
```bash
cd backend
# requires Postgres running locally, or point DB_URL at one
export DB_URL=jdbc:postgresql://localhost:5432/slotsync
export DB_USER=slotsync
export DB_PASSWORD=slotsync
export JWT_SECRET=$(openssl rand -base64 32)
mvn spring-boot:run
```
Flyway will create the schema automatically on startup.

### Frontend
```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

### Tests
```bash
cd backend
mvn test   # BookingConcurrencyTest and AuthControllerTest need Docker (Testcontainers)
```

## What's built vs. what's left

**Built:** JWT auth with refresh rotation, RBAC, rate limiting, the full
concurrency-safe booking flow, resource management (admin), my-bookings view
with cancellation, and the full test suite described above.

**Not verified in this environment:** this codebase was written without
network access to Maven Central, so `mvn compile`/`mvn test` have not been
run here. Run them locally before relying on this as a working demo — the
logic is complete and internally consistent with the design above, but a
compile-time typo can't be ruled out without an actual build.

**Possible extensions:** email notifications on booking/cancellation, a
calendar-grid UI instead of a manual date/time picker, and multi-resource
group bookings.
