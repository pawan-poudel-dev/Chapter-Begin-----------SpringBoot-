# ClubBouncer: Three Rate Limiters, One Door

A nightclub bouncer that only lets a few guests in per time window. Pick an algorithm, knock on the door, spam it, and watch the spots refill live.
Then run the **boundary attack** to see why the simplest rate limiter has a flaw.

## The three bouncers
| Algorithm | How it works | Trade-off |
|---|---|---|
| **Fixed window** | Counts requests in fixed blocks of time, then resets | Simple, but a burst at the end of one block plus a burst at the start of the next lets almost 2x the limit through |
| **Sliding window** | Remembers the exact time of each recent request | Precise, but stores up to `limit` timestamps per guest |
| **Token bucket** | A bucket refills at a steady rate and each request takes a token | Allows short bursts, then holds callers to the average rate. Stores two numbers per guest |

## The boundary attack
With a limit of 5 per 10 seconds: one request opens the window, four more arrive just before it closes, and five more arrive just after it resets.
The fixed window lets 9 through in about a second. The sliding window and token bucket let 5.
The same scenario is a unit test, using a fake clock so the timing is exact.

## Engineering notes
- A small `RateLimiter` interface with three implementations. The service picks one through an enum.
- Time comes from an injected `java.time.Clock`, so tests control time instead of sleeping.
- State is per guest, guarded by per-guest locks, and a concurrency test checks that 500 simultaneous requests let exactly 50 through.
- Blocked requests get HTTP `429 Too Many Requests` with a `Retry-After` header.
- `limit` and `window-seconds` are configurable in `application.properties`.

## Tech
Java 17 · Spring Boot 3 · `java.time.Clock` · JUnit 5 · Vanilla JS

## Run
```bash
mvn spring-boot:run
# open http://localhost:8085
mvn test
```

## API
- `GET /api/enter?algo=FIXED_WINDOW|SLIDING_WINDOW|TOKEN_BUCKET&guest=name` returns 200, or 429 with `Retry-After`
- `GET /api/status?algo=...&guest=name` returns the spots left, without using one

## Limits and roadmap
This is a single-server, in-memory demo. Guest state is never cleaned up, and the limits are not shared across servers.
Next steps: evict idle guests, key by client IP in a servlet filter, and a Redis-backed version for multiple servers.
