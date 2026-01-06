package com.justsyncit.scanner;

/**
 * Enumeration of strategies for handling symbolic links during filesystem
 * scanning.
 * Follows Open/Closed Principle by allowing extension without modification.
 */
public enum SymlinkStrategy {

    /**
     * Follow symbolic links and process the target files.
     * May lead to infinite loops if links create cycles.
     */
    FOLLOW("Follow symbolic links"),

    /**
     * Do not follow symbolic links, but record them as entries.
     * The link itself is processed, not the target.
     */
    RECORD("Record symbolic links without following"),

    /**
     * Skip symbolic links entirely.
     * Neither the link nor its target is processed.
     */
    SKIP("Skip symbolic links entirely");

    /** Human-readable description of the strategy. */
    private final String description;

    /**
     * Creates a new SymlinkStrategy with the specified description.
     *
     * @param description human-readable description of the strategy
     */
    SymlinkStrategy(String description) {
        this.description = description;
    }

    /**
     * Gets the description of this strategy.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}