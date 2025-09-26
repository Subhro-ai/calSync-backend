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

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final AcademiaService academiaService;
    private final EncryptionService encryptionService;

    @Autowired
    public SubscriptionService(UserRepository userRepository, AcademiaService academiaService, EncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.academiaService = academiaService;
        this.encryptionService = encryptionService;
    }


    public String createSubscription(SubscriptionRequest request) {
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
        if (existingUser.isPresent()) {
            return buildSubscriptionUrl(existingUser.get().getSubscriptionToken());
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(encryptionService.encrypt(request.getPassword())); // Encrypt the password for storage
        newUser.setSubscriptionToken(UUID.randomUUID().toString());

        userRepository.save(newUser);

        return buildSubscriptionUrl(newUser.getSubscriptionToken());
    }

    public String generateCalendar(String token) {
        User user = userRepository.findBySubscriptionToken(token)
                .orElseThrow(() -> new RuntimeException("Subscription token not found"));

        // Decrypt the password before using it
        String decryptedPassword = encryptionService.decrypt(user.getPassword());

        String sessionCookie = academiaService.loginAndGetCookie(user.getUsername(), decryptedPassword);

        String timetableHtml = academiaService.fetchTimetable(sessionCookie);
        String academicPlannerHtml = academiaService.fetchAcademicPlanner(sessionCookie);

        // TODO: Parse HTML, merge data, and create the iCalendar file
        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//CalSync//EN\nEND:VCALENDAR";

        return icsContent;
    }

    private String buildSubscriptionUrl(String token) {
        return "http://localhost:8080/api/calendar/" + token;
    }
}