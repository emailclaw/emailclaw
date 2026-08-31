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

import ai.emailclaw.emailclaw.model.plan.Plan;
import java.util.List;
import java.util.Optional;

/**
 * Plan storage interface.
 *
 * <p>Responsible for CRUD persistence of {@link Plan} objects. Default implementation is {@link JsonFilePlanStore},
 * which uses JSON files stored in the layout of {@code workspace/{agentId}/plans/{planId}.json}.
 */
public interface PlanStore {

    /**
     * Save or update a plan.
     *
     * @param plan Plan object
     */
    void save(Plan plan);

    /**
     * Find a plan by ID.
     *
     * @param planId Plan ID
     * @return Matching plan
     */
    Optional<Plan> findById(String planId);

    /**
     * Find the current active plan by session ID.
     *
     * @param sessionId Session ID
     * @return Matching plan
     */
    Optional<Plan> findBySessionId(String sessionId);

    /**
     * List all plans under a specified Agent ID.
     *
     * @param agentId Agent ID
     * @return List of plans (descending by creation time)
     */
    List<Plan> findByAgentId(String agentId);

    /**
     * Delete the specified plan.
     *
     * @param planId Plan ID
     */
    void delete(String planId);

    /**
     * List all plans.
     *
     * @return Full list of plans
     */
    List<Plan> listAll();
}
