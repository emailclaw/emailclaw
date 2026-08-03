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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model definition object.
 *
 * <p>Describes the model identifier, display name, capability tags, and model-level generation parameters.
 */
public class ModelInfo {
    private String id = "";
    private String providerId = "";
    private String name = "";
    private boolean builtIn = true;
    private boolean free = false;
    private boolean supportsImage = false;
    private boolean supportsVideo = false;
    private Map<String, Object> generateKwargs = new LinkedHashMap<>();
    private Integer maxTokens;
    private Integer maxInputLength;

    public ModelInfo() {}

    public ModelInfo(String id, String name, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.builtIn = builtIn;
    }

    /**
     * Get the unique model identifier.
     *
     * @return Model ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the unique model identifier.
     *
     * @param id Model ID
     */
    public void setId(String id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String _providerId) {
        providerId = _providerId;
    }

    /**
     * Get the model display name.
     *
     * @return Model display name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the model display name.
     *
     * @param name Model display name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Determine whether it is a built-in model.
     *
     * @return Whether it is a built-in model
     */
    public boolean isBuiltIn() {
        return builtIn;
    }

    /**
     * Set whether it is a built-in model.
     *
     * @param builtIn Whether it is a built-in model
     */
    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    /**
     * Determine whether it is a free model.
     *
     * @return Whether it is a free model
     */
    public boolean isFree() {
        return free;
    }

    /**
     * Set whether it is a free model.
     *
     * @param free Whether it is a free model
     */
    public void setFree(boolean free) {
        this.free = free;
    }

    /**
     * Determine whether the model supports image input.
     *
     * @return Whether image input is supported
     */
    public boolean isSupportsImage() {
        return supportsImage;
    }

    /**
     * Set whether the model supports image input.
     *
     * @param supportsImage Whether image input is supported
     */
    public void setSupportsImage(boolean supportsImage) {
        this.supportsImage = supportsImage;
    }

    /**
     * Determine whether the model supports video input.
     *
     * @return Whether video input is supported
     */
    public boolean isSupportsVideo() {
        return supportsVideo;
    }

    /**
     * Set whether the model supports video input.
     *
     * @param supportsVideo Whether video input is supported
     */
    public void setSupportsVideo(boolean supportsVideo) {
        this.supportsVideo = supportsVideo;
    }

    /**
     * Get the model-level generation parameters.
     *
     * @return Model-level generation parameters
     */
    public Map<String, Object> getGenerateKwargs() {
        return generateKwargs;
    }

    /**
     * Set the model-level generation parameters.
     *
     * @param generateKwargs Model-level generation parameters
     */
    public void setGenerateKwargs(Map<String, Object> generateKwargs) {
        this.generateKwargs = generateKwargs;
    }

    /**
     * Get the maximum number of output tokens.
     *
     * @return Maximum number of output tokens
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Set the maximum number of output tokens.
     *
     * @param maxTokens Maximum number of output tokens
     */
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * Get the maximum input length.
     *
     * @return Maximum input length
     */
    public Integer getMaxInputLength() {
        return maxInputLength;
    }

    /**
     * Set the maximum input length.
     *
     * @param maxInputLength Maximum input length
     */
    public void setMaxInputLength(Integer maxInputLength) {
        this.maxInputLength = maxInputLength;
    }
}
