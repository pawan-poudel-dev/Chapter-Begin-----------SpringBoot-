package com.aidesktopagent.coding;

/**
 * Ensures proposed patches only target files inside the workspace
 * and do not contain path-traversal or absolute paths outside the root.
 */
public final class PatchValidator {

    private final ProjectWorkspace workspace;

    public PatchValidator(ProjectWorkspace workspace) {
        this.workspace = workspace;
    }

    public ValidationOutcome validatePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ValidationOutcome.invalid("Empty path");
        }
        if (relativePath.contains("\0")) {
            return ValidationOutcome.invalid("Null byte in path");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            // Absolute paths must still resolve inside workspace
            try {
                workspace.resolveSafe(relativePath);
            } catch (SecurityException | IllegalArgumentException e) {
                return ValidationOutcome.invalid(e.getMessage());
            }
        }
        if (normalized.contains("..")) {
            try {
                workspace.resolveSafe(relativePath);
            } catch (SecurityException e) {
                return ValidationOutcome.invalid("Path traversal rejected: " + relativePath);
            }
        }
        try {
            workspace.resolveSafe(relativePath);
            return ValidationOutcome.ok();
        } catch (SecurityException | IllegalArgumentException e) {
            return ValidationOutcome.invalid(e.getMessage());
        }
    }

    public CodeChangeProposal validateProposal(String relativePath, String description,
                                               String original, String proposed) {
        ValidationOutcome v = validatePath(relativePath);
        return new CodeChangeProposal(relativePath, description, original, proposed, v.valid);
    }

    public record ValidationOutcome(boolean valid, String reason) {
        static ValidationOutcome ok() {
            return new ValidationOutcome(true, null);
        }

        static ValidationOutcome invalid(String reason) {
            return new ValidationOutcome(false, reason);
        }
    }
}
