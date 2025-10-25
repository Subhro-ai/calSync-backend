package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.UserLookupResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AcademiaService {

    private final WebClient webClient;
    private static final Logger logger = LoggerFactory.getLogger(AcademiaService.class);
    private static final String BASE_URL = "https://academia.srmist.edu.in";
    private static final String LOGIN_PAGE_URL = BASE_URL + "/accounts/p/10002227248/signin?hide_fp=true&servicename=ZohoCreator&service_language=en&css_url=/49910842/academia-academic-services/downloadPortalCustomCss/login&dcc=true&serviceurl=" + BASE_URL + "/portal/academia-academic-services/redirectFromLogin";

    // Enhanced browser headers
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final String ACCEPT_ENCODING = "gzip, deflate, br, zstd";
    private static final String SEC_CH_UA = "\"Google Chrome\";v=\"129\", \"Not=A?Brand\";v=\"8\", \"Chromium\";v=\"129\"";
    private static final String SEC_CH_UA_MOBILE = "?0";
    private static final String SEC_CH_UA_PLATFORM = "\"Windows\"";

    public AcademiaService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    private String combineCookies(String existingCookies, List<String> newCookies) {
        if (newCookies == null || newCookies.isEmpty()) {
            return existingCookies;
        }

        Map<String, String> cookieMap = new LinkedHashMap<>();
        
        if (existingCookies != null && !existingCookies.isEmpty()) {
            for (String cookie : existingCookies.split("; ")) {
                if (cookie.contains("=")) {
                    String[] parts = cookie.split("=", 2);
                    if(parts.length == 2) {
                        cookieMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }
        
        for (String cookieStr : newCookies) {
            String[] parts = cookieStr.split(";")[0].split("=", 2); 
            if (parts.length == 2) {
                cookieMap.put(parts[0].trim(), parts[1].trim());
            }
        }
        
        return cookieMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    public String loginAndGetCookie(String username, String password) {
        logger.debug("Step 1: Fetching initial cookies from {}", LOGIN_PAGE_URL);
        
        // Add delay to appear more human-like
        try {
            Thread.sleep(500 + (long)(Math.random() * 500)); // 500-1000ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        ResponseEntity<String> initialResponse = webClient.get()
                .uri(LOGIN_PAGE_URL)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .header(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
                .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=0")
                .retrieve()
                .toEntity(String.class)
                .block();

        if (initialResponse == null || !initialResponse.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to get a valid response from the main login page. Status: " + (initialResponse != null ? initialResponse.getStatusCode() : "N/A"));
        }

        String sessionCookies = String.join("; ", initialResponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        String sessionCsrfToken = extractCsrfToken(sessionCookies);
        logger.debug("Successfully obtained session cookies and CSRF token.");

        // Add another small delay
        try {
            Thread.sleep(300 + (long)(Math.random() * 300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.debug("Step 2: Performing user lookup for username: {}", username);
        ResponseEntity<UserLookupResponse> lookupResponseEntity = performUserLookup(username, sessionCookies, sessionCsrfToken);
        
        UserLookupResponse lookupResponse = lookupResponseEntity.getBody();
        if (lookupResponse == null || lookupResponse.getLookupData() == null || lookupResponse.getLookupData().getIdentifier() == null) {
            logger.error("User lookup failed. Response body: {}", lookupResponse);
            throw new InvalidCredentialsException("User lookup failed. Invalid username.");
        }
        
        String updatedCookies = combineCookies(sessionCookies, lookupResponseEntity.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        String updatedCsrfToken = extractCsrfToken(updatedCookies);
        
        if (!updatedCsrfToken.equals(sessionCsrfToken)) {
            logger.debug("CSRF token was refreshed during lookup. Old: {}, New: {}", 
                sessionCsrfToken.substring(0, Math.min(10, sessionCsrfToken.length())), 
                updatedCsrfToken.substring(0, Math.min(10, updatedCsrfToken.length())));
        }

        UserLookupResponse.LookupData lookupData = lookupResponse.getLookupData();
        logger.debug("Successfully performed user lookup. Identifier: {}", lookupData.getIdentifier());

        // Add delay before final login
        try {
            Thread.sleep(400 + (long)(Math.random() * 400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.debug("Step 3: Completing login for identifier: {}", lookupData.getIdentifier());
        return completeLogin(password, lookupData, updatedCookies, updatedCsrfToken);
    }

    private String extractCsrfToken(String cookies) {
        Pattern pattern = Pattern.compile("iamcsr=([^;]+)");
        Matcher matcher = pattern.matcher(cookies);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Could not find the 'iamcsr' cookie in the response headers.");
    }

    private ResponseEntity<UserLookupResponse> performUserLookup(String username, String sessionCookies, String csrfToken) {
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
                .header(HttpHeaders.REFERER, LOGIN_PAGE_URL)
                .header(HttpHeaders.ORIGIN, BASE_URL)
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .header(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
                .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .contentType(MediaType.valueOf("application/x-www-form-urlencoded"))
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .toEntity(UserLookupResponse.class)
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
            .header(HttpHeaders.REFERER, LOGIN_PAGE_URL)
            .header(HttpHeaders.ORIGIN, BASE_URL)
            .header(HttpHeaders.USER_AGENT, USER_AGENT)
            .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
            .header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
            .header(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
            .header("sec-ch-ua", SEC_CH_UA)
            .header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
            .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
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

        if (responseBody != null && (responseBody.contains("\"errors\"") || responseBody.contains("error") || responseBody.contains("SIGNIN_NON_TRUSTED_DOMAIN_BLOCKED"))) {
            logger.error("Login failed! Response contains error. Body: {}", responseBody);
            if (responseBody.contains("SIGNIN_NON_TRUSTED_DOMAIN_BLOCKED")) {
                 logger.error("Login failed due to SIGNIN_NON_TRUSTED_DOMAIN_BLOCKED.");
                 throw new InvalidCredentialsException("Login blocked by server security. This may be due to automated access detection. Please try again later or contact support.");
            }
            throw new InvalidCredentialsException("Invalid username or password.");
        }

        if (statusCode.is2xxSuccessful()) {
            logger.debug("Login successful. Authentication completed.");
            return combineCookies(sessionCookies, responseEntity.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
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
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                    .header(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                    .header(HttpHeaders.REFERER, BASE_URL + "/portal/academia-academic-services")
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
                    .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
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
        int month = currentDate.getMonthValue();
        String academicYear;
        if (month >= 1 && month <= 6) {
            academicYear = (currentYear - 1) + "_" + String.valueOf(currentYear).substring(2); 
        } else {
            academicYear = currentYear + "_" + String.valueOf(currentYear + 1).substring(2); 
        }
        
        String url = BASE_URL + "/srm_university/academia-academic-services/page/My_Time_Table_2023_24";
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
    
    public void logout(String cookie) {
        String logoutUrl = "https://academia.srmist.edu.in/accounts/p/10002227248/logout?servicename=ZohoCreator&serviceurl=https://academia.srmist.edu.in";
        try {
            ResponseEntity<Void> response = webClient.get()
                .uri(logoutUrl)
                .header(HttpHeaders.COOKIE, cookie)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .header(HttpHeaders.REFERER, BASE_URL + "/portal/academia-academic-services")
                .retrieve()
                .toBodilessEntity()
                .block();
            
            if (response != null && (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection())) {
                logger.info("Successfully initiated logout from Academia server. Status: " + response.getStatusCode());
            } else {
                logger.warn("Academia server returned an unexpected status for logout: " + (response != null ? response.getStatusCode() : "N/A"));
            }
        } catch (Exception e) {
            logger.error("An error occurred while trying to log out from Academia server.", e);
        }
    }
}