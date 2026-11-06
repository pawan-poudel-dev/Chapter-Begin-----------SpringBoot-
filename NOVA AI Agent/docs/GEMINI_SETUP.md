# Free Google Gemini setup

The agent talks to Gemini through Google’s **OpenAI-compatible** endpoint (no paid OpenAI key required).

## 1. Get a free API key

1. Open [Google AI Studio](https://aistudio.google.com/apikey)
2. Sign in with a Google account
3. Create an API key
4. Copy the key (never commit it to git)

## 2. Set environment variables (Windows)

**PowerShell (current session):**
```powershell
$env:GEMINI_API_KEY = "your-key-here"
# optional explicit provider/model:
$env:AI_PROVIDER = "gemini"
$env:AI_MODEL = "gemini-2.0-flash"
```

**Windows permanent (System Properties → Environment Variables):**
- `GEMINI_API_KEY` = your key  
  or `GOOGLE_API_KEY` = your key

Then **restart IntelliJ** so it picks up the variables.

## 3. IntelliJ Run Configuration

Run → Edit Configurations → your Main run config → **Environment variables**:

```
GEMINI_API_KEY=your-key-here;AI_PROVIDER=gemini;AI_MODEL=gemini-2.0-flash
```

## 4. What you should see on startup

```
Using OpenAI-compatible client (provider=gemini)
...
apiKeyConfigured=true
```

If you still see `No API key found — using rule-based offline AI client`, the env var is not visible to the JVM.

## 5. Auto-detect

If `GEMINI_API_KEY` or `GOOGLE_API_KEY` is set and `OPENAI_API_KEY` is not, the agent switches to Gemini automatically (model `gemini-2.0-flash`).

## 6. Safety note

Gemini only **plans** actions. Java still validates paths, permissions, and destructive confirmations before anything runs on your PC.
