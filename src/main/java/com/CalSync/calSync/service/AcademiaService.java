package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.UserLookupResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AcademiaService {

    private final WebClient webClient;
    private static final Logger logger = LoggerFactory.getLogger(AcademiaService.class);
    private static final String BASE_URL = "https://academia.srmist.edu.in";
    private static final String LOGIN_PAGE_URL = BASE_URL + "/accounts/p/10002227248/signin?hide_fp=true&servicename=ZohoCreator&service_language=en&css_url=/49910842/academia-academic-services/downloadPortalCustomCss/login&dcc=true&serviceurl=" + BASE_URL + "/portal/academia-academic-services/redirectFromLogin";

    public AcademiaService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String loginAndGetCookie(String username, String password) {
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

        logger.debug("Step 2: Performing user lookup for username: {}", username);
        UserLookupResponse lookupResponse = performUserLookup(username, sessionCookies, sessionCsrfToken);
        UserLookupResponse.LookupData lookupData = lookupResponse.getLookupData();

        if (lookupData == null || lookupData.getIdentifier() == null) {
            logger.error("User lookup failed. Response: {}", lookupResponse);
            throw new InvalidCredentialsException("User lookup failed. Invalid username.");
        }
        logger.debug("Successfully performed user lookup. Identifier: {}, Digest: {}", lookupData.getIdentifier(), lookupData.getDigest());

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
        String lookupUrl = BASE_URL + "/accounts/p/40-10002227248/signin/v2/lookup/" + username;

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("mode", "primary");
        formData.add("cli_time", String.valueOf(System.currentTimeMillis()));
        formData.add("servicename", "ZohoCreator");
        formData.add("service_language", "en");
        formData.add("serviceurl", BASE_URL + "/portal/academia-academic-services/redirectFromLogin");

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

        if (responseEntity == null) {
            logger.error("Login failed! Response entity is null.");
            throw new InvalidCredentialsException("Login failed: No response from authentication server.");
        }

        HttpStatus statusCode = (HttpStatus) responseEntity.getStatusCode();
        String responseBody = responseEntity.getBody();
        
        logger.debug("Login response status: {}", statusCode);
        logger.debug("Login response body (first 200 chars): {}", 
            responseBody != null && responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);

        // Check if the response body indicates an error
        if (responseBody != null && (responseBody.contains("\"errors\"") || responseBody.contains("error"))) {
            logger.error("Login failed! Response contains error. Body: {}", responseBody);
            throw new InvalidCredentialsException("Invalid username or password.");
        }

        // If we got a 200 OK and no errors in the body, consider it successful
        if (statusCode.is2xxSuccessful()) {
            // Combine all cookies (initial session + any new ones from login)
            StringBuilder finalCookies = new StringBuilder(sessionCookies);
            
            if (responseEntity.getHeaders().containsKey(HttpHeaders.SET_COOKIE)) {
                List<String> newCookies = responseEntity.getHeaders().get(HttpHeaders.SET_COOKIE);
                if (newCookies != null && !newCookies.isEmpty()) {
                    finalCookies.append("; ").append(String.join("; ", newCookies));
                }
            }
            
            logger.debug("Login successful. Authentication completed.");
            return finalCookies.toString();
        }

        logger.error("Login failed! Unexpected status code: {}", statusCode);
        throw new InvalidCredentialsException("Login failed with status: " + statusCode);
    }
    
    public String fetchTimetable(String cookie) {
        String timetableUrl = getTimetableUrl();
        return fetchPageContent(timetableUrl, cookie);
    }

    public String fetchAcademicPlanner(String cookie) {
        String academicPlannerUrl = getCalendarUrl();
        return fetchPageContent(academicPlannerUrl, cookie);
    }

    private String fetchPageContent(String url, String cookie) {
        logger.info("Attempting to fetch content from: {}", url);
        try {
            return webClient.get().uri(url)
                    .header(HttpHeaders.COOKIE, cookie)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            logger.error("Page not found at {}", url);
            throw new IllegalStateException("Calendar/Timetable page not found at " + url);
        }
    }

    private String getTimetableUrl() {
        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();
        String academicYear = (currentYear - 2) + "_" + String.valueOf(currentYear - 1).substring(2);
        String url = BASE_URL + "/srm_university/academia-academic-services/page/My_Time_Table_" + academicYear;
        logger.info("Generated Timetable URL: {}", url);
        return url;
    }

    private String getCalendarUrl() {
        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();
        int month = currentDate.getMonthValue();
        String academicYearString;
        String semesterType;

        if (month >= 1 && month <= 6) {
            semesterType = "EVEN";
            academicYearString = (currentYear - 1) + "_" + String.valueOf(currentYear).substring(2);
        } else {
            semesterType = "ODD";
            academicYearString = currentYear + "_" + String.valueOf(currentYear + 1).substring(2);
        }
        
        String url = BASE_URL + "/srm_university/academia-academic-services/page/Academic_Planner_" + academicYearString + "_" + semesterType;
        logger.info("Generated Calendar URL: {}", url);
        return url;
    }
}