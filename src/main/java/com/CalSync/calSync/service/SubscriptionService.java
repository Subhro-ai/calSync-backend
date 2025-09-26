package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.SubscriptionRequest;
import com.CalSync.calSync.model.User;
import com.CalSync.calSync.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AcademiaService academiaService;

    @Autowired
    public SubscriptionService(UserRepository userRepository, PasswordEncoder passwordEncoder, AcademiaService academiaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.academiaService = academiaService;
    }

    public String createSubscription(SubscriptionRequest request) {
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            // Handle case where user already has a subscription
            // For now, we'll just return the existing token
            return buildSubscriptionUrl(existingUser.get().getSubscriptionToken());
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword())); // Encrypt the password
        newUser.setSubscriptionToken(UUID.randomUUID().toString());

        userRepository.save(newUser);

        return buildSubscriptionUrl(newUser.getSubscriptionToken());
    }

    public String generateCalendar(String token) {
        Optional<User> userOptional = userRepository.findBySubscriptionToken(token);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("Subscription token not found");
        }

        // For this step, we are not yet decrypting the password, as we'll
        // need a secure way to handle that. We'll simulate the process for now.
        User user = userOptional.get();

        // 1. Authenticate and get session cookie
        // Note: In a real implementation, you'd need to decrypt the password here
        String sessionCookie = academiaService.loginAndGetCookie(user.getUsername(), "decrypted-password-placeholder");

        // 2. Scrape the necessary data
        String timetableHtml = academiaService.fetchTimetable(sessionCookie);
        String academicPlannerHtml = academiaService.fetchAcademicPlanner(sessionCookie);

        // 3. Process data and generate .ics file (logic to be added)
        // TODO: Parse HTML, merge data, and create the iCalendar file
        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//CalSync//EN\nEND:VCALENDAR";

        return icsContent;
    }

    private String buildSubscriptionUrl(String token) {
        // Replace with your actual domain in a production environment
        return "http://localhost:8080/api/calendar/" + token;
    }
}