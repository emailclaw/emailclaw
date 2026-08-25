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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.emailclaw.emailclaw.storage.JsonStore;
import org.junit.jupiter.api.Test;

public class CronJobModelJsonTest {

    private static final String JOBS_JSON_WITHOUT_MISFIRE =
            """
            {
              "version": 1,
              "jobs": [
                {
                  "id": "job-1",
                  "projectId": "default",
                  "name": "News digest",
                  "enabled": true,
                  "schedule": {
                    "type": "cron",
                    "cron": "0 7,12,17,23 * * *",
                    "timezone": "UTC"
                  },
                  "taskId": "",
                  "inputPrompt": "Fetch news",
                  "saveResultToInbox": true,
                  "runtime": {
                    "maxConcurrency": 1,
                    "timeoutSeconds": 120,
                    "shareSession": true
                  }
                }
              ]
            }
            """;

    @Test
    void testRuntimeSpecMayOmitMisfireGraceSeconds() {
        JsonStore store = new JsonStore();
        CronJobModel.JobsFile file =
                store.parse(JOBS_JSON_WITHOUT_MISFIRE, CronJobModel.JobsFile.class, null);
        assertNotNull(file, "cron-jobs.json without misfireGraceSeconds must parse");
        assertEquals(1, file.jobs().size());
        CronJobModel.JobRuntimeSpec runtime = file.jobs().get(0).runtime();
        assertEquals(60, runtime.misfireGraceSeconds());
    }

    @Test
    void testRuntimeSpecExplicitValuesArePreserved() {
        JsonStore store = new JsonStore();
        String json =
                """
                {"version":1,"jobs":[{"id":"j","projectId":"p","name":"n","enabled":true,
                "schedule":{"type":"cron","cron":"0 9 * * *","timezone":"UTC"},
                "runtime":{"maxConcurrency":2,"timeoutSeconds":300,"misfireGraceSeconds":600,
                "shareSession":false}}]}
                """;
        CronJobModel.JobsFile file = store.parse(json, CronJobModel.JobsFile.class, null);
        assertNotNull(file);
        CronJobModel.JobRuntimeSpec runtime = file.jobs().get(0).runtime();
        assertEquals(2, runtime.maxConcurrency());
        assertEquals(300, runtime.timeoutSeconds());
        assertEquals(600, runtime.misfireGraceSeconds());
    }
}
