package com.lendflow.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

/** Gets applications to a given point in the lifecycle through the public API. */
public final class Flow {

    private Flow() {
    }

    public static String submit(Api applicant, Map<String, Object> draft) {
        String id = applicant.post("/v1/applications", draft).expect(201).body().path("id").asText();
        Documents.uploadRequired(applicant, id, "HOME".equals(draft.get("product")));
        applicant.post("/v1/applications/" + id + "/submit", null).expect(200);
        return id;
    }

    /** Waits for the background checks to move the application on from SUBMITTED and UNDERWRITING. */
    public static String awaitRouted(Api api, String id) {
        String[] status = new String[1];
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> {
            status[0] = api.get("/v1/applications/" + id).expect(200).body().path("status").asText();
            return !status[0].equals("SUBMITTED") && !status[0].equals("UNDERWRITING");
        });
        return status[0];
    }
}
