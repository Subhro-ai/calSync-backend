package com.CalSync.calSync.controller;

import com.CalSync.calSync.dto.SubscriptionRequest;
import com.CalSync.calSync.service.InvalidCredentialsException;
import com.CalSync.calSync.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    // Add the logger declaration
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    @Autowired
    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Endpoint to create a new calendar subscription.
     * @param request The request body containing the user's username and password.
     * @param httpRequest The incoming HTTP request, used to determine the base URL.
     * @return A JSON object containing the unique subscription URL.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody SubscriptionRequest request, HttpServletRequest httpRequest) {
        // Pass the httpRequest to the service
        String subscriptionUrl = subscriptionService.createSubscription(request, httpRequest);
        Map<String, String> response = Map.of("subscriptionUrl", subscriptionUrl);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to retrieve the generated iCalendar (.ics) file.
     * This is the URL that calendar clients will use to subscribe.
     * @param token The unique subscription token.
     * @return The .ics file content with the appropriate headers.
     */
@GetMapping("/calendar/{token}")
public ResponseEntity<String> getCalendar(@PathVariable String token) {
    try {
        String icsContent = subscriptionService.generateCalendar(token);
        HttpHeaders headers = new HttpHeaders();

        // Set proper media type
        MediaType mediaType = new MediaType("text", "calendar", StandardCharsets.UTF_8);
        headers.setContentType(mediaType);

        // Use inline disposition, not form-data
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"calsync.ics\"");

        return new ResponseEntity<>(icsContent, headers, HttpStatus.OK);
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
        logger.error("Authentication failed: {}", ex.getMessage());
        Map<String, String> response = Map.of("error", "Invalid Credentials", "message", "Login failed. Please check your username and password.");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }
}

