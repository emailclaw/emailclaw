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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill metadata object.
 */
public record SkillInfo(
        String name,
        String title,
        String description,
        String content,
        String source,
        String installedFrom,
        String storageName,
        boolean builtIn,
        boolean enabled,
        String updatedAt,
        List<String> channels,
        List<String> tags,
        Map<String, Object> config) {
    public SkillInfo {
        if (name == null) name = "";
        if (title == null) title = "";
        if (description == null) description = "";
        if (content == null) content = "";
        if (source == null) source = "";
        if (installedFrom == null) installedFrom = "";
        if (storageName == null) storageName = "";
        if (updatedAt == null) updatedAt = "";
        if (channels == null) channels = new ArrayList<>();
        if (tags == null) tags = new ArrayList<>();
        if (config == null) config = new LinkedHashMap<>();
    }
}
