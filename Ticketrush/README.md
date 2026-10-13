# TicketRush: Watch a Race Condition Happen

500 bots try to buy 50 tickets at the exact same moment. Pick a locking strategy and see whether the shop **oversells**.

## The three strategies
| Strategy | What it does | Result |
|---|---|---|
| **Naive** | `if (stock > 0) { stock--; }` as two separate steps | Oversells: many threads pass the check before anyone decrements |
| **Synchronized** | The whole check-and-buy sits inside a lock | Correct, but buyers queue up, so it is slower |
| **Atomic (CAS)** | Reserve with one `compareAndSet`, then do the slow work in parallel | Correct and fast |

A 1 ms delay stands in for a database call or payment step. Real systems always have a gap like that, and that gap is where race conditions live.

## How it works
- A `TicketStrategy` interface with three implementations, picked through a `Strategy` enum
- `SimulationService` starts every bot on a thread pool and releases them together with a `CountDownLatch`
- Each result is checked against the stock, so oversold tickets are counted, not guessed
- JUnit tests prove the safe strategies never oversell and that the naive one does

## Tech
Java 17 · Spring Boot 3 · `java.util.concurrent` · JUnit 5 · Vanilla JS

## Run
```bash
mvn spring-boot:run
# open http://localhost:8084
mvn test
```

## API
`POST /api/simulate?strategy=NAIVE|SYNCHRONIZED|ATOMIC&stock=50&bots=500`

## Roadmap
Database version with `@Version` optimistic locking and `SELECT ... FOR UPDATE` · Redis-based stock counter · Docker
