package com.lendflow.kyc.adapter.http;

import com.lendflow.kyc.application.PanRegistry;
import com.lendflow.kyc.domain.PanVerification;
import com.lendflow.shared.error.UpstreamUnavailableException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the PAN verification endpoint. No retry here: a failure surfaces to the workflow job, which retries
 * with backoff from the database so a restart does not lose the attempt count.
 */
@Component
class HttpPanRegistry implements PanRegistry {

    private final RestClient client;

    HttpPanRegistry(RestClient.Builder builder, @Value("${lendflow.kyc.registry-url}") String baseUrl,
                    @Value("${lendflow.kyc.timeout:2s}") Duration timeout) {
        // HTTP/1.1 explicitly: the JDK client otherwise tries an h2c upgrade, which the mock does not handle.
        HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public PanVerification verify(String pan, String declaredName) {
        try {
            Response response = client.post().uri("/pan/verify")
                .body(Map.of("pan", pan, "name", declaredName))
                .retrieve()
                .body(Response.class);
            if (response == null || response.status() == null) {
                throw new UpstreamUnavailableException("pan-registry-unavailable", "PAN registry returned no answer");
            }
            return new PanVerification("VALID".equals(response.status()), response.nameOnRecord());
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("pan-registry-unavailable", "PAN registry did not answer: "
                + e.getClass().getSimpleName());
        }
    }

    record Response(String status, String nameOnRecord) {
    }
}
