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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import ai.emailclaw.emailclaw.model.GlobalConfig;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProjectServiceTest {

    @Mock private AppContext repository;
    @Mock private ConfigManager configManager;

    private ProjectService projectService;
    private List<ProjectInfo> mockProjects;
    private GlobalConfig mockGlobalConfig;

    @BeforeEach
    public void setUp() {
        mockProjects = new ArrayList<>();
        ProjectInfo project1 = new ProjectInfo();
        project1.setId("project1");
        project1.setName("Project One");
        mockProjects.add(project1);

        mockGlobalConfig = new GlobalConfig();

        lenient().when(repository.configManager()).thenReturn(configManager);
        lenient().when(configManager.getProjects()).thenReturn(mockProjects);

        projectService = new ProjectService(repository);
    }

    @Test
    public void testList() {
        List<ProjectInfo> projects = projectService.list();
        assertEquals(1, projects.size());
        assertEquals("project1", projects.get(0).getId());
    }

    @Test
    public void testFindById() {
        Optional<ProjectInfo> found = projectService.findById("project1");
        assertTrue(found.isPresent());
        assertEquals("Project One", found.get().getName());

        Optional<ProjectInfo> notFound = projectService.findById("project2");
        assertFalse(notFound.isPresent());
    }

    @Test
    public void testCurrentDefault_WithGlobalConfig() {
        mockGlobalConfig.setCurrentProjectId("project1");
        lenient().when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);

        ProjectInfo current = projectService.currentDefault();
        assertEquals("project1", current.getId());
    }

    @Test
    public void testCurrentDefault_Fallback() {
        lenient().when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);

        ProjectInfo current = projectService.currentDefault();
        assertEquals("project1", current.getId());
        verify(configManager).saveGlobalConfig(mockGlobalConfig);
        assertEquals("project1", mockGlobalConfig.getCurrentProjectId());
    }

    @Test
    public void testSetCurrentProject() {
        lenient().when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);
        projectService.setCurrentProject("project1");
        verify(configManager).saveGlobalConfig(mockGlobalConfig);
        assertEquals("project1", mockGlobalConfig.getCurrentProjectId());
    }

    @Test
    public void testSave_NewProject() {
        ProjectInfo newProject = new ProjectInfo();
        newProject.setId("new-project");
        newProject.setName("New Project");

        projectService.save(newProject);

        verify(configManager)
                .saveProjects(
                        argThat(
                                list ->
                                        list.size() == 2
                                                && list.get(1).getId().equals("new-project")));
    }

    @Test
    public void testSave_UpdateProject() {
        ProjectInfo updatedProject = new ProjectInfo();
        updatedProject.setId("project1");
        updatedProject.setName("Project One Updated");

        projectService.save(updatedProject);

        verify(configManager)
                .saveProjects(
                        argThat(
                                list ->
                                        list.size() == 1
                                                && list.get(0)
                                                        .getName()
                                                        .equals("Project One Updated")));
    }

    @Test
    public void testRemove() {
        ProjectInfo projectToRemove = mockProjects.get(0);
        lenient().when(configManager.getGlobalConfig()).thenReturn(mockGlobalConfig);

        projectService.remove(projectToRemove);

        verify(configManager).saveProjects(argThat(List::isEmpty));
    }
}
