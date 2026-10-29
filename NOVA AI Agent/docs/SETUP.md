# Setup

## Requirements

- Java 17+ (JDK)
- Maven 3.9+
- Optional: microphone for voice
- Optional: `OPENAI_API_KEY` for cloud LLM / Whisper STT
- Optional: Spotify Premium + `SPOTIFY_*` tokens for API playback

## Build

```bash
mvn clean package
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "analyze project"
```

## Environment

```bash
export OPENAI_API_KEY=sk-...          # cloud AI + Whisper
export AGENT_OFFLINE=true             # force rule-based / no cloud
export SPOTIFY_ACCESS_TOKEN=...       # or refresh token + client id/secret
```

## Preferences

Stored in `data/preferences.json` (no passwords/API keys allowed).

## Emergency stop

Say or type `stop agent` — cancels cancellable work and blocks new tasks until reset.
