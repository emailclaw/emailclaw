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
package ai.emailclaw.emailclaw.model;

/**
 * Task metadata object.
 *
 * <p>Represents a task belonging to a project, implementing the {@link TaskDefinition} interface to provide
 * unified task identification semantics with {@link ai.emailclaw.emailclaw.model.CronJobModel.CronJobSpec}.
 *
 * @param id          Unique task identifier
 * @param projectId   The project ID it belongs to
 * @param name        Task name
 * @param description Task description
 * @param status      Task status (pending / in-progress / done)
 * @param createdAt   Creation time (ISO-8601)
 */
public record TaskInfo(
        String id,
        String projectId,
        String name,
        String description,
        String status,
        String createdAt)
        implements TaskDefinition {

    /** Creates an empty default task. */
    public static TaskInfo empty() {
        return new TaskInfo("", "default", "", "", "pending", "");
    }
}
