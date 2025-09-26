package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.SubscriptionRequest;
import com.CalSync.calSync.model.User;
import com.CalSync.calSync.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.CalSync.calSync.dto.SubscriptionRequest;
import com.CalSync.calSync.model.User;
import com.CalSync.calSync.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final AcademiaService academiaService;
    private final EncryptionService encryptionService;
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    public SubscriptionService(UserRepository userRepository, AcademiaService academiaService, EncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.academiaService = academiaService;
        this.encryptionService = encryptionService;
        logger.info("SubscriptionService has been instantiated.");
    }

    public String createSubscription(SubscriptionRequest request) {
        logger.info("SubscriptionService: createSubscription called for {}", request.getUsername());
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());

        if (existingUser.isPresent()) {
            logger.info("User {} already exists. Returning existing token.", request.getUsername());
            return buildSubscriptionUrl(existingUser.get().getSubscriptionToken());
        }

        logger.info("Creating new user for {}", request.getUsername());
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(encryptionService.encrypt(request.getPassword()));
        newUser.setSubscriptionToken(UUID.randomUUID().toString());

        userRepository.save(newUser);
        logger.info("New user {} saved successfully.", request.getUsername());

        // --- THIS LINE WAS MISSING ---
        return buildSubscriptionUrl(newUser.getSubscriptionToken());
    }

    public String generateCalendar(String token) {
        logger.info("SubscriptionService: generateCalendar called for token {}", token);
        User user = userRepository.findBySubscriptionToken(token)
                .orElseThrow(() -> new RuntimeException("Subscription token not found"));

        String decryptedPassword = encryptionService.decrypt(user.getPassword());
        logger.debug("Password decrypted for user {}", user.getUsername());

        String sessionCookie = academiaService.loginAndGetCookie(user.getUsername(), decryptedPassword);
        logger.info("Successfully received session cookie from AcademiaService.");

        String timetableHtml = academiaService.fetchTimetable(sessionCookie);
        String academicPlannerHtml = academia.fetchAcademicPlanner(sessionCookie);

        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//CalSync//EN\nEND:VCALENDAR";

        return icsContent;
    }

    private String buildSubscriptionUrl(String token) {
        // Replace with your actual domain in a production environment
        return "http://localhost:8080/api/calendar/" + token;
    }
}