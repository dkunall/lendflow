package com.lendflow.config;

import com.lendflow.shared.web.ProblemHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Coarse role checks by path. Ownership (an applicant's own application) and approval limits are checked in
 * the services, because only they know who owns what and how large the loan is.
 */
@Configuration
class SecurityConfig {

    private static final String APPLICANT = "SCOPE_lendflow.applicant";
    private static final String UNDERWRITER = "SCOPE_lendflow.underwriter";
    private static final String CREDIT_MANAGER = "SCOPE_lendflow.credit_manager";
    private static final String CREDIT_HEAD = "SCOPE_lendflow.credit_head";
    private static final String POLICY = "SCOPE_lendflow.policy";
    private static final String OPS = "SCOPE_lendflow.ops";

    @Bean
    SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/applications", "/v1/applications/*/submit",
                    "/v1/applications/*/withdraw", "/v1/applications/*/documents", "/v1/offers/*/accept")
                    .hasAuthority(APPLICANT)
                .requestMatchers(HttpMethod.PATCH, "/v1/applications/*").hasAuthority(APPLICANT)
                .requestMatchers(HttpMethod.GET, "/v1/applications", "/v1/applications/**", "/v1/offers/**")
                    .hasAnyAuthority(APPLICANT, UNDERWRITER, CREDIT_MANAGER, CREDIT_HEAD, OPS)
                .requestMatchers("/v1/tasks", "/v1/tasks/**").hasAnyAuthority(UNDERWRITER, CREDIT_MANAGER, CREDIT_HEAD)
                .requestMatchers("/v1/rulesets", "/v1/rulesets/**", "/v1/scorecards", "/v1/scorecards/**",
                    "/v1/shadow-report").hasAuthority(POLICY)
                .requestMatchers("/v1/disbursements", "/v1/disbursements/**").hasAuthority(OPS)
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> { })
                .authenticationEntryPoint((request, response, e) ->
                    writeProblem(response, json, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required"))
                .accessDeniedHandler((request, response, e) ->
                    writeProblem(response, json, HttpStatus.FORBIDDEN, "forbidden", "Token lacks the required role")))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeProblem(response, json, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required"))
                .accessDeniedHandler((request, response, e) ->
                    writeProblem(response, json, HttpStatus.FORBIDDEN, "forbidden", "Token lacks the required role")));
        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper json, HttpStatus status, String code,
                                     String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemHandler.problem(status, code, detail));
    }
}
