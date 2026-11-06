# Security & Privacy

## Filesystem (P0)

- `SafePathResolver` is the single policy for all file ops
- Absolute paths rejected
- Path **component** `..` rejected (filenames like `report..old.txt` allowed)
- Symlink targets outside workspace rejected
- Copy/move refuse overwrite unless `overwrite=true`
- Non-empty directory delete refused

## Permissions (P0)

- Locked-down defaults: no FILES_WRITE, CODING, BUILD, SHELL
- `PermissionRegistry.interactiveDesktop()` enables write/coding/build for normal desktop use
- SHELL remains denied until explicitly granted
- Enforcement:
  1. `PermissionGuardedExecutor` wraps every executor (Main)
  2. Optional `ActionAuthorization` in Agent for a second gate

## Destructive ops

- Confirmation shows operation, target, destination, irreversibility
- Fail-closed multi-step plans

## Build / shell

- Build goals: **exact** allowlist match only (no prefix)
- Single goal token (no free-form args)
- RUN_COMMAND allowlisted tokens only; SHELL off by default

## AI endpoints

- HTTPS required for remote hosts
- HTTP only for localhost
- No embedded userinfo

## Secrets

- Keys from environment only
- Log sanitization
