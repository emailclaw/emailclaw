/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 */
package ai.emailclaw.emailclaw.service.memory;

/**
 * Scope of memory.
 */
public enum MemoryScope {
    /** Global memory across all projects, such as user preferences. */
    GLOBAL,

    /** Project-specific memory, such as architecture constraints and task context. */
    PROJECT
}
