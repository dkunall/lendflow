package com.lendflow.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * One set of containers per JVM, shared by every integration test. Each test creates its own applicants and
 * applications, so tests never need to clean up after each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwt.Decoder.class)
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("lendflow");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0")
        .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx256m");

    /** The mock bureau, loaded from the same mapping files the compose stack uses. */
    protected static final WireMockServer BUREAU = new WireMockServer(WireMockConfiguration.options()
        .dynamicPort().globalTemplating(true).usingFilesUnderDirectory("mock-bureau"));

    private static final SecureRandom RANDOM = new SecureRandom();
    // Generated per run so no key ever lives in the repository, and fixed for the JVM so every context agrees.
    private static final String DATA_KEY = randomKey();
    private static final String LOOKUP_KEY = randomKey();

    static {
        Startables.deepStart(POSTGRES, KAFKA).join();
        BUREAU.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lendflow.bureau.url", BUREAU::baseUrl);
        registry.add("lendflow.bureau.timeout", () -> "1s");
        registry.add("lendflow.bureau.retry.first-wait", () -> "20ms");
        registry.add("lendflow.crypto.data-key", () -> DATA_KEY);
        registry.add("lendflow.crypto.lookup-key", () -> LOOKUP_KEY);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbc;

    protected Api anonymous;

    @BeforeEach
    void clients() {
        anonymous = Api.at(port);
    }

    protected Api as(String subject, String... scopes) {
        return anonymous.as(TestJwt.token(subject, scopes));
    }

    protected static String unique() {
        return Long.toHexString(System.nanoTime());
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
