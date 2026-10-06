# TypeDuel: Real-Time Multiplayer Typing Races

Create a room, share the invite link, and race your friends. Live progress bars and WPM update for everyone as you type.

## Tech
Java 17 · Spring Boot 3 · Spring WebSocket · Jackson · Vanilla JS

## How it works
- A single `/ws` endpoint handled by `RaceHandler` (a `TextWebSocketHandler`)
- Each room is a state machine: `WAITING -> COUNTDOWN -> RACING -> DONE`
- Messages are small JSON events: `join`, `start`, `progress`, `again`
- Room state is guarded with `synchronized` blocks, and each socket send is serialized
- The server computes WPM and finish ranks, so clients can't fake their results
- A scheduler thread runs the 3-second countdown

## Run
```bash
mvn spring-boot:run
# open http://localhost:8083 in two tabs
```

## Roadmap
Server-side anti-cheat (speed limits) · Leaderboard with a database · Docker deploy · Custom text packs (code snippets!)
