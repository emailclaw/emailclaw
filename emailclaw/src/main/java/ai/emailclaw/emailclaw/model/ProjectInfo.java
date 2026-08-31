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

import java.util.HashMap;
import java.util.Map;

/**
 * Project metadata object.
 *
 * <p>Represents a project created by the user (Project), containing the base directory and additional directories.
 */
public class ProjectInfo {
    private String id = "";
    private String name = "";
    private String baseDirectory = "";
    private Map<String, Boolean> additionalDirs = new HashMap<>();
    private String createdAt = "";

    /**
     * Get the project unique identifier.
     *
     * @return Project ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the project unique identifier.
     *
     * @param id Project ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the project name.
     *
     * @return Project name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the project name.
     *
     * @param name Project name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the project base directory.
     *
     * @return Project base directory
     */
    public String getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * Set the project base directory.
     *
     * @param baseDirectory Project base directory
     */
    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * Get the configuration of additional directories and whether they are writable.
     *
     * @return Additional directory mapping (Path -> Writable)
     */
    public Map<String, Boolean> getAdditionalDirs() {
        return additionalDirs;
    }

    /**
     * Set the configuration of additional directories and whether they are writable.
     *
     * @param additionalDirs Additional directory mapping
     */
    public void setAdditionalDirs(Map<String, Boolean> additionalDirs) {
        this.additionalDirs = additionalDirs;
    }

    /**
     * Get the project creation time.
     *
     * @return Project creation time
     */
    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * Set the project creation time.
     *
     * @param createdAt Project creation time
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectInfo that = (ProjectInfo) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
