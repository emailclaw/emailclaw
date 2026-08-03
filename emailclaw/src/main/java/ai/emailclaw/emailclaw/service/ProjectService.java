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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Project management service.
 *
 * <p>Responsible for maintaining the list of project data in the system and the state persistence of the currently selected project.
 */
public class ProjectService {
    private static final Logger LOGGER = Logger.getLogger(ProjectService.class.getName());
    public static final String PROJECT_ID_DEFAULT = "default";

    private final AppContext repository;
    private final ConfigManager configManager;
    private final List<Runnable> listeners = new ArrayList<>();

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    /**
     * Construct the project management service.
     *
     * @param repository Application persistence context
     */
    public ProjectService(AppContext repository) {
        this.repository = repository;
        this.configManager = repository.configManager();
        LOGGER.info("ProjectService initialization completed");
    }

    /**
     * Get the list of all projects.
     *
     * @return List of projects
     */
    public List<ProjectInfo> list() {
        return configManager.getProjects();
    }

    /**
     * Find a project by ID.
     *
     * @param id Project ID
     * @return Matched project Optional
     */
    public Optional<ProjectInfo> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return list().stream().filter(p -> id.equals(p.getId())).findFirst();
    }

    /**
     * Get the current default or selected project.
     *
     * <p>Read currentProjectId from global-config.json first; if not found, fall back to the project with isDefault=true or id="default".
     *
     * @return Currently selected project
     */
    public ProjectInfo currentDefault() {
        GlobalConfig globalConfig = configManager.getGlobalConfig();
        String currentId = globalConfig.getCurrentProjectId();
        if (currentId != null && !currentId.isBlank()) {
            Optional<ProjectInfo> found = findById(currentId);
            if (found.isPresent()) {
                return found.get();
            }
        }
        List<ProjectInfo> projects = list();
        ProjectInfo selected =
                projects.stream()
                        .filter(ProjectInfo::isDefault)
                        .findFirst()
                        .orElseGet(
                                () ->
                                        projects.stream()
                                                .filter(p -> PROJECT_ID_DEFAULT.equals(p.getId()))
                                                .findFirst()
                                                .orElseGet(
                                                        () -> {
                                                            if (!projects.isEmpty()) {
                                                                return projects.get(0);
                                                            }
                                                            ProjectInfo def = new ProjectInfo();
                                                            def.setId(PROJECT_ID_DEFAULT);
                                                            def.setName("Default");
                                                            def.setDefault(true);
                                                            return def;
                                                        }));
        if (!selected.getId().equals(currentId)) {
            setCurrentProject(selected.getId());
        }
        return selected;
    }

    /**
     * Set the currently selected project ID and persist to disk.
     *
     * @param projectId Project ID
     */
    public void setCurrentProject(String projectId) {
        LOGGER.log(Level.INFO, "Set current project ID: {0}", projectId);
        GlobalConfig globalConfig = configManager.getGlobalConfig();
        globalConfig.setCurrentProjectId(projectId);
        configManager.saveGlobalConfig(globalConfig);
    }

    /**
     * Save or update project information.
     *
     * @param project Project object
     */
    public void save(ProjectInfo project) {
        if (project == null || project.getId().isBlank()) {
            return;
        }
        LOGGER.log(Level.INFO, "Save project information: {0}", project.getId());
        List<ProjectInfo> projects = new ArrayList<>(list());
        int index = -1;
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getId().equals(project.getId())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            projects.set(index, project);
        } else {
            projects.add(project);
        }
        configManager.saveProjects(projects);
        notifyListeners();
    }

    /**
     * Delete the specified project (default project cannot be deleted).
     *
     * @param project Project object to delete
     */
    public void remove(ProjectInfo project) {
        if (project == null || project.isDefault() || PROJECT_ID_DEFAULT.equals(project.getId())) {
            LOGGER.warning("Default project is not allowed to be deleted");
            return;
        }
        LOGGER.log(Level.INFO, "Delete project: {0}", project.getId());
        List<ProjectInfo> projects = new ArrayList<>(list());
        projects.removeIf(p -> p.getId().equals(project.getId()));
        configManager.saveProjects(projects);

        GlobalConfig globalConfig = configManager.getGlobalConfig();
        if (project.getId().equals(globalConfig.getCurrentProjectId())) {
            setCurrentProject(PROJECT_ID_DEFAULT);
        }
        notifyListeners();
    }
}
