package com.CalSync.calSync.controller;

import com.CalSync.calSync.dto.SubscriptionRequest;
import com.CalSync.calSync.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Autowired
    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Endpoint to create a new calendar subscription.
     * @param request The request body containing the user's username and password.
     * @return A JSON object containing the unique subscription URL.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody SubscriptionRequest request) {
        String subscriptionUrl = subscriptionService.createSubscription(request);
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
            headers.setContentType(MediaType.valueOf("text/calendar"));
            headers.setContentDispositionFormData("attachment", "calendar.ics");
            return new ResponseEntity<>(icsContent, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            // Handle cases like an invalid token
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
