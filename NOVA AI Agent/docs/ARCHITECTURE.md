# Architecture

## Pipeline

```
Input (voice | text)
  → CommandNormalizer
  → ConversationContext (reference resolution)
  → AIClient (RuleBased | OpenAI-compatible)
  → ActionParser (JSON → ActionPlan)
  → ActionValidator + PlanValidator
  → PermissionRegistry
  → Confirmation (destructive / APPLY_CODE_CHANGE)
  → ActionExecutor (composite)
  → Verification / MediaStatus / BuildResult
  → TTS / console response
  → Audit log (redacted)
```

## Packages

| Package | Responsibility |
|---------|----------------|
| `agent` | Orchestration, state, task queue, workflows, cancellation |
| `ai` | LLM clients, command normalization |
| `actions` | Typed actions, parsing, validation |
| `computer` | Executors for OS-facing tools |
| `coding` | Project analysis, IDE, build, safe edits |
| `browser` / `music` | Web and media |
| `voice` | STT/TTS |
| `security` | Permissions, errors, retry, privacy |
| `memory` | Preferences |
| `automation` | Named workflows, reminders |
| `plugins` | Optional integrations |
| `avatar` | Robot mood (UI-agnostic) |
| `verification` | Feature matrix, acceptance |

## Safety

- LLM never executes shell or code directly
- Path containment via `ProjectWorkspace`
- URL scheme allowlist
- Secrets only from environment
- Preferences reject secret-like values
