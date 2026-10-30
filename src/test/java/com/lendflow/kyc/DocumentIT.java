package com.lendflow.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.support.Api;
import com.lendflow.support.Documents;
import com.lendflow.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentIT extends IntegrationTest {

    @Test
    void reuploadingReplacesTheCurrentDocument() {
        Api neha = as("neha-" + unique(), "lendflow.applicant");
        String id = draft(neha, "PERSONAL");
        String path = "/v1/applications/" + id + "/documents";

        neha.upload(path, "PAN_CARD", "pan-blurry.pdf", Documents.pdf("first")).expect(201);
        String second = neha.upload(path, "PAN_CARD", "../../etc/pan.pdf", Documents.pdf("second")).expect(201)
            .body().path("id").asText();

        Api.Response list = neha.get(path).expect(200);
        assertThat(list.body()).hasSize(1);
        assertThat(list.body().get(0).path("id").asText()).isEqualTo(second);
        assertThat(list.body().get(0).path("fileName").asText()).isEqualTo("pan.pdf");
        assertThat(list.body().get(0).path("mediaType").asText()).isEqualTo("application/pdf");
        int stored = jdbc.sql("SELECT count(*) FROM documents WHERE application_id = ?::uuid").param(id)
            .query(Integer.class).single();
        assertThat(stored).isEqualTo(2);
    }

    @Test
    void onlyPdfJpegAndPngAreAcceptedWhateverTheFileIsCalled() {
        Api neha = as("neha-" + unique(), "lendflow.applicant");
        String id = draft(neha, "PERSONAL");

        Api.Response rejected = neha.upload("/v1/applications/" + id + "/documents", "INCOME_PROOF", "payslip.pdf",
            "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII)).expect(400);

        assertThat(rejected.body().path("code").asText()).isEqualTo("document-format");
    }

    @Test
    void filesOverTheLimitAreRefused() {
        Api neha = as("neha-" + unique(), "lendflow.applicant");
        String id = draft(neha, "PERSONAL");
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(Documents.pdf("x"), 0, big, 0, 8);

        assertThat(neha.upload("/v1/applications/" + id + "/documents", "INCOME_PROOF", "big.pdf", big).status())
            .isEqualTo(413);
    }

    @Test
    void aPropertyDocumentIsOnlyForHomeLoans() {
        Api neha = as("neha-" + unique(), "lendflow.applicant");

        neha.upload("/v1/applications/" + draft(neha, "PERSONAL") + "/documents", "PROPERTY_DOCUMENT", "deed.pdf",
            Documents.pdf("deed")).expect(400);
        neha.upload("/v1/applications/" + draft(neha, "HOME") + "/documents", "PROPERTY_DOCUMENT", "deed.pdf",
            Documents.pdf("deed")).expect(201);
    }

    @Test
    void onlyTheOwnerUploadsAndStaffCanDownload() {
        Api owner = as("owner-" + unique(), "lendflow.applicant");
        String id = draft(owner, "PERSONAL");
        byte[] pdf = Documents.pdf("salary");
        String docId = owner.upload("/v1/applications/" + id + "/documents", "INCOME_PROOF", "slip.pdf", pdf)
            .expect(201).body().path("id").asText();

        as("other-" + unique(), "lendflow.applicant")
            .upload("/v1/applications/" + id + "/documents", "PAN_CARD", "pan.pdf", pdf).expect(404);
        Api.Response download = as("uw-" + unique(), "lendflow.underwriter")
            .get("/v1/applications/" + id + "/documents/" + docId + "/content").expect(200);
        assertThat(download.headers().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(download.headers().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    private static String draft(Api api, String product) {
        return api.post("/v1/applications", Map.of("product", product)).expect(201).body().path("id").asText();
    }
}
