# AI Desktop Agent

Java desktop AI agent: natural-language commands → structured actions → safe execution.

**Status:** Days 1–60 COMPLETE — release candidate  
Java 17 LTS · Maven · Jackson · SLF4J/Logback

## Pipeline

```
Voice / typed text
    → AIClient (rule-based offline, or OpenAI-compatible)
    → JSON ActionPlan
    → ActionParser
    → ActionValidator
    → ActionExecutor (Application / Browser / File / Keyboard)
    → Computer
```

The LLM **never** executes OS commands. It only proposes structured JSON.  
Java validates every action before any side effect.

## Quick Start

```bash
mvn clean package

# One-shot command
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Open Chrome"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Create a folder called AI Projects"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Create a file called test.txt"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Open YouTube"

# Interactive REPL
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar
```

### Optional LLM

```bash
export OPENAI_API_KEY=sk-...
# optional: AI_PROVIDER, AI_MODEL, AI_BASE_URL
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Open IntelliJ IDEA"
```

Without an API key the **rule-based offline client** handles common phrases.

## Supported actions

| Type | Example |
|------|---------|
| OPEN_APPLICATION | Open Chrome |
| CLOSE_APPLICATION | Close Firefox |
| OPEN_WEBSITE | Open YouTube |
| SEARCH_WEB | Search for Java tutorials |
| OPEN_FOLDER | Open folder Documents |
| CREATE_FILE | Create a file called test.txt |
| CREATE_FOLDER | Create a folder called AI Projects |
| READ_FILE | (via plan) |
| MOVE_FILE / COPY_FILE | (with `destination` parameter) |
| DELETE_FILE | Requires typing **YES** to confirm |
| TYPE_TEXT | Type Hello World |
| PRESS_KEY | Press ENTER |
| PLAY_YOUTUBE | Play on YouTube lofi hip hop |
| PLAY_SPOTIFY / PAUSE_MEDIA / STOP_MEDIA | (planned) |
| RUN_COMMAND | Allow-listed utilities only (`date`, `ls`, …) |

## Safety

- Unknown action types rejected
- Path traversal (`..`) rejected
- `RUN_COMMAND` allow-list + no shell metacharacters
- Only `http`/`https` URLs for browse
- **DELETE_FILE** and **RUN_COMMAND** require interactive confirmation (`YES`)
- API keys only from environment variables — never from property files or logs
- Logging redacts secrets

## Project layout

```
com.aidesktopagent
├── Main.java
├── agent/          Agent, AgentException
├── ai/             AIClient, RuleBasedAIClient, OpenAiCompatibleClient
├── actions/        Action, ActionType, ActionPlan, ActionResult,
│                   ActionParser, ActionValidator
├── computer/       ActionExecutor, CompositeActionExecutor,
│                   ApplicationExecutor, BrowserExecutor,
│                   FileExecutor, KeyboardExecutor
├── config/         AppConfig, ConfigLoader
└── logging/        LoggingService, LoggingBootstrap
```

## Tests

```bash
mvn test
```

57 tests covering parser, validator, rule-based AI, file executor, agent planning, config, logging.

## License

Proprietary — internal project.


## Day 4 — Voice

Pipeline: **Microphone → STT → Agent → Validator → Executor → TTS**

### Providers

| Role | Implementation | Requirements |
|------|----------------|--------------|
| STT | `WhisperHttpSpeechToText` | `OPENAI_API_KEY` — sends one WAV per utterance to OpenAI `/v1/audio/transcriptions` |
| STT | `MockSpeechToText` | Tests / offline demos |
| Mic | `MicrophoneManager` (`javax.sound.sampled`) | OS microphone permission |
| TTS | `SystemProcessTextToSpeech` | `espeak` / `espeak-ng` / macOS `say` |
| TTS | `ConsoleTextToSpeech` | Always available fallback |

**Privacy:** Audio is captured only while MIC ON for an utterance. Bytes are sent to Whisper only for that request when configured. Nothing is uploaded in the background.

### Run

```bash
# Voice REPL (Enter = push-to-talk when Whisper key set; or type text)
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar --voice

# One-shot as if spoken
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar --voice "Open Chrome"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar --voice "Create a folder called Java Projects"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar --voice "Stop agent"

# Continuous (Enter stops)
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar --voice --continuous
```

### States

`IDLE → LISTENING → TRANSCRIBING → THINKING → VALIDATING → EXECUTING → SUCCESS/ERROR`

Destructive actions still require typing **YES**. Emergency phrases: `stop agent`, `cancel`, `abort`.


## Day 5 — YouTube, Spotify, Browser & Media

### Actions added
`SEARCH_YOUTUBE`, `OPEN_YOUTUBE_VIDEO`, `PLAY_YOUTUBE`, `OPEN_SPOTIFY`, `SEARCH_SPOTIFY`,
`PLAY_SPOTIFY`, `RESUME_MEDIA`, `NEXT_TRACK`, `PREVIOUS_TRACK`, `GET_MEDIA_STATUS`

### Behavior (honest limits)

| Feature | What works | Limitations |
|---------|------------|-------------|
| Open / search YouTube | Opens https URLs in default browser | Does **not** verify playback started; no ad skip |
| Play YouTube topic | Opens search or watch URL | User may need to click play / dismiss consent |
| Open / search Spotify | Opens open.spotify.com | Browser only without tokens |
| Spotify play/pause/next | Web API when configured | **Premium** + **active device** + OAuth token required |
| Pause YouTube | Not automated | Asks user to use browser controls |

### Spotify setup (optional API control)

1. Create an app at [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Authorization Code flow with scopes: `user-modify-playback-state user-read-playback-state user-read-currently-playing`
3. Export (never commit):

```bash
export SPOTIFY_CLIENT_ID=...
export SPOTIFY_CLIENT_SECRET=...
export SPOTIFY_REFRESH_TOKEN=...   # preferred
# or short-lived:
export SPOTIFY_ACCESS_TOKEN=...
```

4. Open Spotify on a phone/desktop/web player so a Connect device is active.
5. Premium account required for Web API playback endpoints.

Passwords are never stored. Tokens only via environment variables; logs redact secrets.

### Example commands

```bash
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Open YouTube"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Find a Java tutorial"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Play relaxing music on Spotify"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Pause"
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "Next track"
```


## Day 6 — Coding assistant foundation

Workspace-bound analysis only (no silent file writes):

| Command | Action |
|---------|--------|
| `analyze project` | `ANALYZE_PROJECT` — Maven/Gradle detection, source roots |
| `list sources` | `LIST_SOURCE_FILES` |
| `read src/main/java/...` | `READ_SOURCE_FILE` (inside workspace only) |
| paste stack trace | `EXPLAIN_ERROR` — plain-language diagnosis |
| `propose change for X.java` | `PROPOSE_CODE_CHANGE` — preview only, not applied |

Safety: `ProjectWorkspace` rejects path traversal; proposals never write disk until Day 8.


## Days 7–20 — Coding, desktop, conversation, memory

| Day | Feature |
|-----|---------|
| 7 | IDE detection/launch (`OPEN_IN_IDE`) |
| 8 | Safe code edit with backup (`APPLY_CODE_CHANGE`, requires YES) |
| 9 | Allowlisted Maven/Gradle goals (`RUN_BUILD`) |
| 10 | Original robot avatar moods bound to agent state |
| 11 | Application registry (aliases: browser, ide, terminal) |
| 12 | Keyboard executor (existing) + focus safety notes |
| 13 | Workspace file search (`SEARCH_FILES`, cancelable) |
| 14 | File ops via FileExecutor (copy/move/delete + confirmation) |
| 15 | WorkflowEngine multi-step sequences |
| 16 | CommandNormalizer synonyms + low-confidence detection |
| 17 | ConversationContext (“second result”, “that file”) |
| 18 | CancellationCoordinator emergency stop hooks |
| 19 | TaskQueue with priorities |
| 20 | PreferencesStore (no secrets) |

Avatar is console ASCII + `AvatarMood` events (JavaFX can subscribe later).


## Days 21–60 — Advanced coding through release

Implemented modules: ProjectIndex, compiler/test diagnosis, ProjectGenerator,
PermissionRegistry, RetryPolicy, AgentError, IntegrationRegistry, NamedWorkflows,
Reminders, PlanValidator, OfflineMode, PrivacyPolicy, FeatureMatrix, AcceptanceReport,
PermissionGuardedExecutor.

Docs: `docs/SETUP.md`, `docs/SECURITY.md`, `docs/ARCHITECTURE.md`

### Day 60 feature matrix (honest)

See `FeatureMatrix` — JavaFX full UI is **NOT_IMPLEMENTED**; Spotify is **NEEDS_CONFIG**;
core pipeline, coding, permissions, preferences, and tests are **IMPLEMENTED_TESTED**.

```bash
mvn clean test package
java -jar target/ai-desktop-agent-0.1.0-SNAPSHOT.jar "analyze project"
```


## Performance refinements

- `FastIntentRouter` — O(1) exact-phrase intents before regex
- `CompositeActionExecutor` — EnumMap ActionType routing built at startup
- Agent: command chains (`and then` / `&&`), context memory, low-confidence rejection
- Leaner logging on the hot path


## Extra utilities

| Command | Action |
|---------|--------|
| what time is it | `GET_TIME` |
| system info | `SYSTEM_INFO` |
| calculate 12*(3+4) | `CALCULATE` (safe math only) |
| copy hello / read clipboard | clipboard |
| save note shop milk / list notes | notes in `data/notes.json` |
| set timer 5 minutes | background timer |
| git status | read-only git in workspace |
| help | command cheat-sheet |
