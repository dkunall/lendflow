package com.lendflow.support;

import java.nio.charset.StandardCharsets;

public final class Documents {

    private Documents() {
    }

    /** Small, but starts with a real PDF header, which is all the upload check looks at. */
    public static byte[] pdf(String text) {
        return ("%PDF-1.4\n% " + text + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static void uploadRequired(Api api, String applicationId, boolean secured) {
        String path = "/v1/applications/" + applicationId + "/documents";
        api.upload(path, "PAN_CARD", "pan.pdf", pdf("pan")).expect(201);
        api.upload(path, "INCOME_PROOF", "salary-slips.pdf", pdf("income")).expect(201);
        if (secured) {
            api.upload(path, "PROPERTY_DOCUMENT", "sale-deed.pdf", pdf("property")).expect(201);
        }
    }
}
