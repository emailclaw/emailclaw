/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw.service.plan;

import ai.emailclaw.emailclaw.model.plan.PlanNotebook;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plan hint cache - prevents repeatedly generating the same hint text under the same plan state.
 *
 * <p>The cache key is a composite key of {@code planId + "-" + currentHint}.
 * Triggers a new HintBlock injection only when its content actually changes.
 * The cache is automatically invalidated when a subtask status changes.
 */
public class PlanHintCache {
    private static final Logger LOGGER = Logger.getLogger(PlanHintCache.class.getName());

    /** planId -> cachedHintText mapping. */
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Get the hint text from the cache.
     *
     * @param planId Plan ID
     * @return Cached hint text, or null if it doesn't exist
     */
    public String get(String planId) {
        return cache.get(planId);
    }

    /**
     * Update the cache entry. If the new hint is identical to the cached one, returns false, indicating no injection is needed.
     *
     * @param planId  Plan ID
     * @param hint    Current hint text
     * @return true if the hint text changed; false if identical to the cache
     */
    public boolean putIfChanged(String planId, String hint) {
        if (planId == null) {
            return false;
        }
        String cached = cache.get(planId);
        if (hint != null && hint.equals(cached)) {
            return false;
        }
        cache.put(planId, hint == null ? "" : hint);
        LOGGER.log(Level.FINE, "Plan hint cache updated: planId={0}", planId);
        return true;
    }

    /**
     * Invalidate the cache for the specified plan (called when subtask status changes).
     *
     * @param planId Plan ID
     */
    public void invalidate(String planId) {
        if (planId != null) {
            cache.remove(planId);
            LOGGER.log(Level.FINE, "Plan hint cache invalidated: planId={0}", planId);
        }
    }

    /**
     * Check if the hint changed based on PlanNotebook and update the cache.
     *
     * @param notebook PlanNotebook
     * @return true if the hint changed
     */
    public boolean checkAndUpdate(PlanNotebook notebook) {
        if (notebook == null) {
            return false;
        }
        return putIfChanged(notebook.getPlanId(), notebook.getCurrentHint());
    }

    /** Clear all caches. */
    public void clear() {
        cache.clear();
    }
}
