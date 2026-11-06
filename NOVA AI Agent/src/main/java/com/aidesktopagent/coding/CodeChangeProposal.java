package com.aidesktopagent.coding;

import java.util.Objects;

/**
 * A proposed edit that has not been applied yet.
 * Day 6 produces proposals only; Day 8 will apply with confirmation and backups.
 */
public final class CodeChangeProposal {

    private final String relativePath;
    private final String description;
    private final String originalSnippet;
    private final String proposedSnippet;
    private final boolean validated;

    public CodeChangeProposal(String relativePath, String description,
                              String originalSnippet, String proposedSnippet,
                              boolean validated) {
        this.relativePath = Objects.requireNonNull(relativePath);
        this.description = description != null ? description : "";
        this.originalSnippet = originalSnippet != null ? originalSnippet : "";
        this.proposedSnippet = proposedSnippet != null ? proposedSnippet : "";
        this.validated = validated;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getDescription() {
        return description;
    }

    public String getOriginalSnippet() {
        return originalSnippet;
    }

    public String getProposedSnippet() {
        return proposedSnippet;
    }

    public boolean isValidated() {
        return validated;
    }

    public String toPreview() {
        return "Proposed change in " + relativePath + " ["
                + (validated ? "path OK" : "INVALID PATH") + "]\n"
                + "Reason: " + description + "\n"
                + "--- original ---\n" + originalSnippet + "\n"
                + "--- proposed ---\n" + proposedSnippet + "\n"
                + "(Not applied — review required)";
    }
}
