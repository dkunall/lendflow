package com.lendflow.bureau.adapter.http;

import com.lendflow.bureau.application.BureauClient;
import com.lendflow.bureau.application.BureauRequest;
import com.lendflow.bureau.application.BureauResponse;
import com.lendflow.shared.error.UpstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
class HttpBureauClient implements BureauClient {

    private final RestClient client;
    private final BureauResilience resilience;

    HttpBureauClient(RestClient.Builder builder,
                     @Value("${lendflow.bureau.url}") String baseUrl,
                     @Value("${lendflow.bureau.timeout:3s}") Duration timeout,
                     @Value("${lendflow.bureau.retry.max-attempts:3}") int maxAttempts,
                     @Value("${lendflow.bureau.retry.first-wait:200ms}") Duration firstWait,
                     @Value("${lendflow.bureau.breaker.window:20}") int window,
                     @Value("${lendflow.bureau.breaker.minimum-calls:10}") int minimumCalls,
                     @Value("${lendflow.bureau.breaker.failure-rate:50}") float failureRate,
                     @Value("${lendflow.bureau.breaker.open-for:30s}") Duration openFor,
                     @Value("${lendflow.bureau.max-concurrent:8}") int maxConcurrent,
                     @Value("${lendflow.bureau.max-wait-for-slot:1s}") Duration maxWaitForSlot) {
        HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.resilience = new BureauResilience(new BureauResilience.Settings(maxAttempts, firstWait, window,
            minimumCalls, failureRate, openFor, maxConcurrent, maxWaitForSlot));
    }

    @Override
    public BureauResponse fetch(BureauRequest request) {
        return resilience.call(() -> once(request));
    }

    CircuitBreaker breaker() {
        return resilience.breaker();
    }

    private BureauResponse once(BureauRequest request) {
        Map<String, Object> body = Map.of(
            "pan", request.pan(),
            "name", request.fullName(),
            "dateOfBirth", request.dateOfBirth().toString(),
            "consent", Map.of("version", request.consentVersion(), "givenAt", request.consentAt().toString()));
        try {
            BureauResponse response = client.post().uri("/bureau/v1/credit-report")
                .body(body)
                .retrieve()
                .body(BureauResponse.class);
            if (response == null) {
                throw new TransientBureauFailure("empty response");
            }
            return response;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.is5xxServerError() || status.value() == 429) {
                throw new TransientBureauFailure("HTTP " + status.value());
            }
            throw new UpstreamUnavailableException("bureau-rejected", "Bureau refused the request: HTTP " + status.value());
        } catch (ResourceAccessException e) {
            throw new TransientBureauFailure(e.getMostSpecificCause().getClass().getSimpleName());
        }
    }
}
