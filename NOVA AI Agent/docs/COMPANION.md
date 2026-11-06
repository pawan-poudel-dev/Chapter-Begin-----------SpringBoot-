# Aether companion (always-on-top)

Jarvis-style window that stays on top of other apps.

## Run (IntelliJ)

Program arguments:
```
--companion
```
Or run `Main` with **no arguments** on a normal desktop — companion opens by default.

Text-only:
```
--cli
```

## How to use

| Action | What to do |
|--------|------------|
| Talk | **Click the robot** or press **Space**, then speak |
| Type | Type in the box, press **Enter** |
| Hide | Click **Hide** (minimizes) |
| Quit | Close the window or type `quit` |

## Voice

- Needs a microphone
- Cloud STT needs `OPENAI_API_KEY` (Whisper) or compatible key
- Without STT key you can still **type** commands in the window

## Gemini (smarter understanding)

```
GEMINI_API_KEY=your-key
AI_PROVIDER=gemini
```

## Gesture

- **Tap / click** the robot = “gesture” to start listening
- Full camera hand-tracking is **not** included yet (would need OpenCV/MediaPipe)

## Design

Original neon robot **Aether** — friendly cartoon robot, **not** Doraemon or any protected character.
