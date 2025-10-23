package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.DayEvent;
import com.CalSync.calSync.dto.DaySchedule;
import com.CalSync.calSync.dto.SubscriptionRequest;
import com.CalSync.calSync.model.User;
import com.CalSync.calSync.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final AcademiaService academiaService;
    private final EncryptionService encryptionService;
    private final ParsingService parsingService;
    private final CalendarService calendarService;
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    public SubscriptionService(UserRepository userRepository, AcademiaService academiaService, EncryptionService encryptionService, ParsingService parsingService, CalendarService calendarService) {
        this.userRepository = userRepository;
        this.academiaService = academiaService;
        this.encryptionService = encryptionService;
        this.parsingService = parsingService;
        this.calendarService = calendarService;
        logger.info("SubscriptionService has been instantiated with all dependencies.");
    }

    public String createSubscription(SubscriptionRequest request, HttpServletRequest httpRequest) {
        logger.info("SubscriptionService: createSubscription called for {}", request.getUsername());

        // STEP 1: Validate credentials BEFORE doing anything else.
        // This will throw InvalidCredentialsException if login fails.
        // We also immediately log out after validation.
        String validationCookie = null;
        try {
            validationCookie = academiaService.loginAndGetCookie(request.getUsername(), request.getPassword());
            logger.info("Credentials for {} are valid.", request.getUsername());
        } catch (Exception e) {
            // Let the exception propagate to be handled by the controller
            throw e;
        } finally {
            if (validationCookie != null) {
                academiaService.logout(validationCookie);
                logger.info("Session for credential validation successfully terminated.");
            }
        }


        // STEP 2: Now that credentials are valid, find or create the user.
        Optional<User> existingUserOpt = userRepository.findByUsername(request.getUsername());

        if (existingUserOpt.isPresent()) {
            logger.info("User {} already exists. Updating password and returning existing token.", request.getUsername());
            User existingUser = existingUserOpt.get();
            existingUser.setPassword(encryptionService.encrypt(request.getPassword()));
            userRepository.save(existingUser);
            return buildSubscriptionUrl(existingUser.getSubscriptionToken(), httpRequest);
        }

        logger.info("Creating new user for {}", request.getUsername());
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(encryptionService.encrypt(request.getPassword()));
        newUser.setSubscriptionToken(UUID.randomUUID().toString());

        userRepository.save(newUser);
        logger.info("New user {} saved successfully.", request.getUsername());

        return buildSubscriptionUrl(newUser.getSubscriptionToken(), httpRequest);
    }

    public String generateCalendar(String token) {
        // logger.info("SubscriptionService: generateCalendar called for token {}", token);
        String sessionCookie = null; // To store the cookie for the finally block
        try {
            User user = userRepository.findBySubscriptionToken(token)
                    .orElseThrow(() -> new RuntimeException("Subscription token not found or invalid."));

            // STEP 1: AUTHENTICATE
            String decryptedPassword = encryptionService.decrypt(user.getPassword());
            sessionCookie = academiaService.loginAndGetCookie(user.getUsername(), decryptedPassword);
            // logger.info("Step 1/4: Authentication successful.");

            // STEP 2: SCRAPE DATA
            String timetableHtml = academiaService.fetchTimetable(sessionCookie);
            String academicPlannerHtml = academiaService.fetchAcademicPlanner(sessionCookie);
            // logger.info("Step 2/4: Raw HTML data scraped successfully.");

            // STEP 3: PARSE DATA
            List<DaySchedule> timetable = parsingService.parseTimetable(timetableHtml);
            List<DayEvent> academicPlanner = parsingService.parseAcademicPlanner(academicPlannerHtml);
            // logger.info("Step 3/4: HTML parsed into structured objects.");
            
            if (academicPlanner.isEmpty()) {
                logger.warn("Academic planner parsing resulted in an empty list.");
            } else {
                logger.info("Parsed the first 5 days of the academic planner: {}", academicPlanner.subList(0, Math.min(5, academicPlanner.size())));
            }

            // STEP 4: GENERATE CALENDAR
            String icsContent = calendarService.generateIcsContent(timetable, academicPlanner);
            logger.info("Step 4/4: iCalendar (.ics) content generated successfully.");

            // logger.info("Generated ICS Content (first 300 chars): {}", icsContent.substring(0, Math.min(icsContent.length(), 300)));
            return icsContent;

        } catch (Exception e) {
            logger.error("An unexpected error occurred in generateCalendar for token {}:", token, e);
            throw new RuntimeException("Failed to generate calendar. See server logs for details.", e);
        } finally {
            // STEP 5: LOGOUT
            if (sessionCookie != null) {
                logger.info("Attempting to log out session for token {}", token);
                academiaService.logout(sessionCookie);
            }
        }
    }

    private String buildSubscriptionUrl(String token, HttpServletRequest request) {
        // Build the URL dynamically without the .ics extension
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/api/calendar/" + token)
                .build()
                .toUriString();
    }
}