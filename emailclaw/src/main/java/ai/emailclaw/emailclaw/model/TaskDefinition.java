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
 * Task definition interface.
 *
 * <p>Extracts common fields from {@link TaskInfo} and {@link ai.emailclaw.emailclaw.model.CronJobModel.CronJobSpec},
 * unifying the core semantics of "task identifier + project it belongs to + name".
 *
 * <p>Any record representing an "entity that can be scheduled or tracked by the system" can implement this interface,
 * providing unified type constraints when generic operations by id / projectId / name are needed.
 */
public interface TaskDefinition {

    /** Unique task identifier. */
    String id();

    /** The project ID it belongs to. */
    String projectId();

    /** Task name. */
    String name();
}
