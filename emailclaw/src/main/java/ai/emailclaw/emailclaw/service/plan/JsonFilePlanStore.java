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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/**
 * Plan storage implementation based on JSON files.
 *
 * <p>Plan files are stored in the layout of {@code workspaceRoot/{agentId}/plans/{planId}.json}.
 * Uses Jackson 3.x for serialization/deserialization, supporting Java 8 time types.
 */
public class JsonFilePlanStore implements PlanStore {
    private static final Logger LOGGER = Logger.getLogger(JsonFilePlanStore.class.getName());
    private static final String PLANS_DIR = "plans";
    private static final String FILE_SUFFIX = ".json";

    private final ObjectMapper mapper;
    private final Path workspaceRoot;

    public JsonFilePlanStore(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper();
        LOGGER.log(
                Level.INFO,
                "JsonFilePlanStore initialization, storage root directory: {0}",
                this.workspaceRoot);
    }

    /** Get the directory where plan files for the specified agent are stored. */
    private Path plansDir(String agentId) {
        return workspaceRoot.resolve(agentId).resolve(PLANS_DIR);
    }

    /** Get the JSON file path for the specified plan. */
    private Path planFile(String agentId, String planId) {
        return plansDir(agentId).resolve(planId + FILE_SUFFIX);
    }

    /** Ensure the plans directory exists. */
    private Path ensurePlansDir(String agentId) {
        Path dir = plansDir(agentId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create plans directory: " + dir, e);
        }
        return dir;
    }

    @Override
    public void save(Plan plan) {
        if (plan == null || plan.getId() == null || plan.getId().isBlank()) {
            LOGGER.warning("Failed to save plan: plan or plan.id is empty");
            return;
        }
        try {
            ensurePlansDir(plan.getAgentId());
            Path file = planFile(plan.getAgentId(), plan.getId());
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            LOGGER.log(Level.FINE, "Plan saved: {0}", file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save plan: planId=" + plan.getId(), e);
        }
    }

    @Override
    public Optional<Plan> findById(String planId) {
        if (planId == null || planId.isBlank()) {
            return Optional.empty();
        }
        // Iterate through all agent directories to find
        List<Plan> all = listAll();
        return all.stream().filter(p -> planId.equals(p.getId())).findFirst();
    }

    @Override
    public Optional<Plan> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        List<Plan> all = listAll();
        // Return the latest active plan (latest updatedAt) under the session
        return all.stream()
                .filter(p -> sessionId.equals(p.getSessionId()))
                .max(Comparator.comparing(p -> p.getUpdatedAt()));
    }

    @Override
    public List<Plan> findByAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        Path dir = plansDir(agentId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        return loadPlansFromDir(dir);
    }

    @Override
    public void delete(String planId) {
        if (planId == null || planId.isBlank()) {
            return;
        }
        List<Plan> all = listAll();
        for (Plan p : all) {
            if (planId.equals(p.getId())) {
                Path file = planFile(p.getAgentId(), p.getId());
                try {
                    Files.deleteIfExists(file);
                    LOGGER.log(Level.FINE, "Plan deleted: {0}", file);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to delete plan file: " + file, e);
                }
                return;
            }
        }
    }

    @Override
    public List<Plan> listAll() {
        if (!Files.isDirectory(workspaceRoot)) {
            return List.of();
        }
        List<Plan> result = new ArrayList<>();
        try (Stream<Path> agentDirs = Files.list(workspaceRoot)) {
            List<Path> dirs = agentDirs.filter(Files::isDirectory).toList();
            for (Path agentDir : dirs) {
                Path plansDir = agentDir.resolve(PLANS_DIR);
                if (Files.isDirectory(plansDir)) {
                    result.addAll(loadPlansFromDir(plansDir));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to iterate agent directories", e);
        }
        result.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return result;
    }

    /** Load all .json plan files from the specified directory. */
    private List<Plan> loadPlansFromDir(Path dir) {
        List<Plan> plans = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jsonFiles =
                    files.filter(f -> f.toString().endsWith(FILE_SUFFIX) && Files.isRegularFile(f))
                            .toList();
            for (Path f : jsonFiles) {
                try {
                    String content = Files.readString(f, StandardCharsets.UTF_8);
                    Plan plan = mapper.readValue(content, Plan.class);
                    if (plan != null && plan.getId() != null) {
                        plans.add(plan);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Failed to read plan file (skipped): " + f, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to list plan files: " + dir, e);
        }
        return plans;
    }
}
