# ⚡ PulseVote — Real-Time Live Polling with Spring Boot

Create a poll, share the link or QR code, and watch votes update **live** on every connected screen.

## Tech
Java 17 · Spring Boot 3 · Spring Data JPA · H2 · Server-Sent Events (SSE) · Vanilla JS

## Features
- REST API to create polls and vote
- Real-time updates pushed to all viewers via SSE (`SseEmitter`)
- Persistent storage (H2 file DB, swap for PostgreSQL in one line)
- Shareable link + auto-generated QR code
- One-vote-per-browser guard

## Run
```bash
mvn spring-boot:run
# open http://localhost:8080
```
Open the same poll in two windows and vote in one; the other updates instantly.

## API
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/polls` | Create poll `{question, options[]}` |
| GET | `/api/polls/{id}` | Get poll |
| POST | `/api/polls/{id}/vote/{optionId}` | Cast vote |
| GET | `/api/polls/{id}/stream` | SSE live stream |

## Roadmap
WebSocket/STOMP version · PostgreSQL · Docker · Auth + poll owners · Rate limiting
