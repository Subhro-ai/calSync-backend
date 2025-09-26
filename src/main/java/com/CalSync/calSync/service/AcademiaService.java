package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.UserLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
    private static final String LOGIN_PAGE_URL = "https://academia.srmist.edu.in/accounts/p/10002227248/signin?hide_fp=true&servicename=ZohoCreator&service_language=en&css_url=/49910842/academia-academic-services/downloadPortalCustomCss/login&dcc=true&serviceurl=https%3A%2F%2Facademia.srmist.edu.in%2Fportal%2Facademia-academic-services%2FredirectFromLogin";

    public AcademiaService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // --- Authentication logic remains the same ---
    public String loginAndGetCookie(String username, String password) {
        // ... (existing login logic)
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

    // --- NEW: Implemented Data Scraping Methods ---

    public String fetchTimetable(String cookie) {
        String timetableUrl = getTimetableUrl();
        logger.info("Fetching timetable from: {}", timetableUrl);

        String rawHtml = webClient.get()
                .uri(timetableUrl)
                .header(HttpHeaders.COOKIE, cookie)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (rawHtml == null) {
            throw new IllegalStateException("Did not receive a response from the timetable page.");
        }
        return rawHtml;
    }

    public String fetchAcademicPlanner(String cookie) {
        String calendarUrl = getCalendarUrl();
        logger.info("Fetching academic planner from: {}", calendarUrl);

        try {
            String rawHtml = webClient.get().uri(calendarUrl)
                    .header(HttpHeaders.COOKIE, cookie)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (rawHtml == null) {
                throw new IllegalStateException("Did not receive a response from the calendar page: " + calendarUrl);
            }
            return rawHtml;
        } catch (WebClientResponseException.NotFound ex) {
            logger.error("Academic Planner page not found at {}. This can happen between semesters.", calendarUrl);
            // Return an empty HTML structure so the parsing doesn't fail
            return "<html><body></body></html>";
        }
    }

    private String getTimetableUrl() {
        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();
        // This logic might need adjustment based on when the academic year flips
        String academicYear = (currentYear - 1) + "_" + String.valueOf(currentYear).substring(2);
        return "https://academia.srmist.edu.in/srm_university/academia-academic-services/page/My_Time_Table_" + academicYear;
    }

    private String getCalendarUrl() {
        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();
        int month = currentDate.getMonthValue();
        String academicYearString;
        String semesterType;

        if (month >= 7 && month <= 12) { // ODD semester (e.g., Jul - Dec)
            semesterType = "ODD";
            academicYearString = currentYear + "_" + String.valueOf(currentYear + 1).substring(2);
        } else { // EVEN semester (e.g., Jan - Jun)
            semesterType = "EVEN";
            academicYearString = (currentYear - 1) + "_" + String.valueOf(currentYear).substring(2);
        }

        return "https://academia.srmist.edu.in/srm_university/academia-academic-services/page/Academic_Planner_" + academicYearString + "_" + semesterType;
    }
}
