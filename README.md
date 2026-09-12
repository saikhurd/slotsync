# SlotSync — Concurrency-Safe Booking & Scheduling Platform

[![Backend Tests](https://github.com/saikhurd/slotsync/actions/workflows/backend-tests.yml/badge.svg)](https://github.com/saikhurd/slotsync/actions/workflows/backend-tests.yml)

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

| Layer      | Technology                                                         |
|------------|---------------------------------------------------------------------|
| Backend    | Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security, Flyway  |
| Database   | PostgreSQL                                                           |
| Frontend   | React 18, Vite, Axios, React Router                                  |
| Auth       | Stateless JWT access tokens + hashed, rotated refresh tokens         |
| Resilience | Bucket4j rate limiting (separate buckets for auth vs. booking)       |
| Testing    | JUnit 5, Mockito, MockMvc, Testcontainers (real Postgres), GitHub Actions CI |

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
guarantee holds. **This test runs automatically on every push via GitHub
Actions** — see the badge at the top of this file for current status.

## Security

- Stateless JWT access tokens (short-lived) for authentication. Anonymous
  authentication is explicitly disabled in `SecurityConfig` so unauthenticated
  requests return a clean `401` (which the frontend's token-refresh
  interceptor listens for) rather than a `403`.
- Refresh tokens are random 384-bit values; only a SHA-256 digest is stored
  server-side (never the raw token), and each refresh **rotates** the token —
  the old one is revoked rather than reused. A daily scheduled job purges
  expired/revoked tokens so the table doesn't grow unbounded.
- RBAC is enforced centrally in `SecurityConfig` (`/api/admin/**` requires
  `ROLE_ADMIN`) rather than scattered across `@PreAuthorize` annotations on
  individual controller methods, for easier auditing. The frontend also
  hides the Admin nav link and blocks the `/admin` route client-side for
  non-admins — a UX nicety, not the actual security boundary, which is
  enforced server-side regardless of what the client claims.
- Two independent rate-limit buckets via Bucket4j: 10 requests/minute per IP
  on the booking endpoint (the concurrency-sensitive path), and a tighter
  5 requests/minute per IP on login/register (the classic brute-force
  target). Idle buckets are evicted automatically after 30 minutes so the
  in-memory map doesn't grow unbounded. `X-Forwarded-For` is only trusted
  when explicitly configured (`TRUST_PROXY_HEADERS=true`), since trusting it
  unconditionally would let a direct caller spoof any IP and dodge the limit.
- A generic exception handler logs the real error server-side but only ever
  returns a safe, generic message to the client — so an unexpected bug never
  leaks a stack trace or internal detail.

## Running it locally

### Backend
```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/slotsync
export DB_USER=slotsync
export DB_PASSWORD=slotsync
export JWT_SECRET=$(openssl rand -base64 32)
mvn spring-boot:run
```
Flyway creates the schema automatically on startup.

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
If `mvn test` reports a Docker-related connection error locally, that's a
known class of issue with certain Docker Desktop versions and the
Testcontainers Java client — it doesn't reflect the correctness of the code.
The tests run cleanly and are automatically verified on every push via the
GitHub Actions workflow linked at the top of this file, which runs on a
standard, known-compatible Docker setup.

## What's built and verified

- JWT auth with refresh rotation, RBAC (server-side enforced, client-side
  reflected), rate limiting, and the full concurrency-safe booking flow
- Resource management (admin), my-bookings view with cancellation
- Full automated test suite, including the 20-thread concurrency proof,
  passing in CI (badge above)
- Manually verified end-to-end: registration, login, admin promotion,
  resource creation/deactivation, booking, cancellation, rate-limit
  enforcement (via direct API calls), and token-refresh-on-expiry

## Possible extensions

Email notifications on booking/cancellation, a calendar-grid UI instead of a
manual date/time picker, and multi-resource group bookings.
