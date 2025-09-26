package com.CalSync.calSync.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.CalSync.calSync.dto.UserLookupResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AcademiaService {

    private final WebClient webClient;
    private static final Logger logger = LoggerFactory.getLogger(AcademiaService.class);
    private static final String LOGIN_PAGE_URL = "https://academia.srmist.edu.in/accounts/p/10002227248/signin?hide_fp=true&servicename=ZohoCreator&service_language=en&css_url=/49910842/academia-academic-services/downloadPortalCustomCss/login&dcc=true&serviceurl=https%3A%2F%2Facademia.srmist.edu.in%2Fportal%2Facademia-academic-services%2FredirectFromLogin";

    public AcademiaService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String loginAndGetCookie(String username, String password) {
        // Step 1: Fetch initial cookies and CSRF token
        logger.debug("Step 1: Fetching initial cookies from {}", LOGIN_PAGE_URL);
        ResponseEntity<String> initialResponse = webClient.get()
                .uri(LOGIN_PAGE_URL)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .retrieve()
                .toEntity(String.class)
                .block();

        if (initialResponse == null || !initialResponse.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to get a valid response from the main login page. Status: " + (initialResponse != null ? initialResponse.getStatusCode() : "N/A"));
        }

        String sessionCookies = String.join("; ", initialResponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        String sessionCsrfToken = extractCsrfToken(sessionCookies);
        logger.debug("Successfully obtained session cookies and CSRF token.");

        // Step 2: Perform user lookup
        logger.debug("Step 2: Performing user lookup for username: {}", username);
        UserLookupResponse lookupResponse = performUserLookup(username, sessionCookies, sessionCsrfToken);
        UserLookupResponse.LookupData lookupData = lookupResponse.getLookupData();

        if (lookupData == null || lookupData.getIdentifier() == null) {
            logger.error("User lookup failed. Response: {}", lookupResponse);
            throw new IllegalStateException("User lookup failed. Check username or see logs for details.");
        }
        logger.debug("Successfully performed user lookup. Identifier: {}, Digest: {}", lookupData.getIdentifier(), lookupData.getDigest());

        // Step 3: Complete the login with password
        logger.debug("Step 3: Completing login for identifier: {}", lookupData.getIdentifier());
        return completeLogin(password, lookupData, sessionCookies, sessionCsrfToken);
    }

    private String extractCsrfToken(String cookies) {
        Pattern pattern = Pattern.compile("iamcsr=([^;]+)");
        Matcher matcher = pattern.matcher(cookies);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Could not find the 'iamcsr' cookie in the response headers.");
    }

    private UserLookupResponse performUserLookup(String username, String sessionCookies, String csrfToken) {
        String lookupUrl = "https://academia.srmist.edu.in/accounts/p/40-10002227248/signin/v2/lookup/" + username;

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("mode", "primary");
        formData.add("cli_time", String.valueOf(System.currentTimeMillis()));
        formData.add("servicename", "ZohoCreator");
        formData.add("service_language", "en");
        formData.add("serviceurl", "https://academia.srmist.edu.in/portal/academia-academic-services/redirectFromLogin");

        return webClient.post()
                .uri(lookupUrl)
                .header(HttpHeaders.COOKIE, sessionCookies)
                .header("x-zcsrf-token", "iamcsrcoo=" + csrfToken)
                .header("Referer", LOGIN_PAGE_URL)
                .contentType(MediaType.valueOf("application/x-www-form-urlencoded"))
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(UserLookupResponse.class)
                .block();
    }

    private String completeLogin(String password, UserLookupResponse.LookupData lookupData, String sessionCookies, String csrfToken) {
        ResponseEntity<String> responseEntity = webClient.post()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("academia.srmist.edu.in")
                .path("/accounts/p/40-10002227248/signin/v2/primary/{identifier}/password")
                .queryParam("digest", lookupData.getDigest())
                .build(lookupData.getIdentifier()))
            .header(HttpHeaders.COOKIE, sessionCookies)
            .header("x-zcsrf-token", "iamcsrcoo=" + csrfToken)
            .header("Referer", LOGIN_PAGE_URL)
            .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("passwordauth", Map.of("password", password)))
            .retrieve()
            .toEntity(String.class)
            .block();

        if (responseEntity != null && responseEntity.getHeaders().containsKey(HttpHeaders.SET_COOKIE)) {
            List<String> newCookies = responseEntity.getHeaders().get(HttpHeaders.SET_COOKIE);
            String finalCookies = sessionCookies + "; " + String.join("; ", newCookies);
            logger.debug("Login successful. Final cookies obtained.");
            return finalCookies;
        }

        logger.error("Login failed! Could not retrieve final authentication cookies. Status: {}", responseEntity != null ? responseEntity.getStatusCode() : "N/A");
        throw new IllegalStateException("Login failed: Could not retrieve final authentication cookies.");
    }

    // --- Placeholder Methods for Data Scraping ---
    public String fetchTimetable(String cookie) {
        // TODO: Implement the logic to scrape the "My Time Table" page
        return "<html><body>Timetable HTML</body></html>";
    }

    public String fetchAcademicPlanner(String cookie) {
        // TODO: Implement the logic to scrape the "Academic Planner" page
        return "<html><body>Academic Planner HTML</body></html>";
    }
}