package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.CourseSlot;
import com.CalSync.calSync.dto.DayEvent;
import com.CalSync.calSync.dto.DaySchedule;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CalendarService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarService.class);
    // Create a flexible date formatter that can handle different date formats
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("[dd-MMM-yyyy][d-MMM-yyyy]") // Handles both single and double digit days
            .toFormatter(Locale.ENGLISH);
    
    // Create a more flexible time formatter that can handle both single and double digit hours
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("[h:mm a][hh:mm a]") // Handles both single digit (h) and double digit (hh) hours
            .toFormatter(Locale.ENGLISH); // Ensure English locale for AM/PM parsing
    
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    public String generateIcsContent(List<DaySchedule> timetable, List<DayEvent> academicPlanner) {
        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//CalSync//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);

        // Create a quick lookup map for the timetable
        Map<String, DaySchedule> timetableMap = timetable.stream()
                .collect(Collectors.toMap(DaySchedule::getDayOrder, schedule -> schedule));
        
        // ** LOGGING FOR VERIFICATION **
        logger.debug("Timetable Day Orders available for matching: {}", timetableMap.keySet());
        int eventCount = 0;

        // Iterate through every day in the academic planner
        for (DayEvent dayEvent : academicPlanner) {
            String plannerDayOrder = dayEvent.getDayOrder();
            DaySchedule daySchedule = timetableMap.get(plannerDayOrder);

            // ** LOGGING FOR VERIFICATION **
            if (daySchedule != null) {
                logger.debug("Match found! Planner Day Order: '{}' matches Timetable Day Order.", plannerDayOrder);
                // We have classes on this day, so create events for them
                for (CourseSlot courseSlot : daySchedule.getClasses()) {
                    if (courseSlot.isClass()) {
                        try {
                            VEvent event = createEventForCourse(courseSlot, dayEvent.getDate());
                            calendar.getComponents().add(event);
                            eventCount++;
                        } catch (Exception e) {
                            logger.error("Could not create event for course {} on date {}: {}", 
                                courseSlot.getCourseCode(), dayEvent.getDate(), e.getMessage());
                            logger.debug("Failed time string was: '{}'", courseSlot.getTime());
                        }
                    }
                }
            } else if (plannerDayOrder != null && !plannerDayOrder.isEmpty() && !plannerDayOrder.equalsIgnoreCase("Holiday")) {
                 // Log only if it's a day that should have classes but no match was found
                logger.debug("No match for Planner Day Order: '{}'", plannerDayOrder);
            }
        }
        logger.info("Total calendar events generated: {}", eventCount);
        return calendar.toString();
    }

    private VEvent createEventForCourse(CourseSlot course, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
        String[] timeParts = course.getTime().split(" - ");
        
        if (timeParts.length != 2) {
            throw new IllegalArgumentException("Invalid time format: " + course.getTime());
        }

        LocalTime startTime = parseTimeWithFallback(timeParts[0].trim());
        LocalTime endTime = parseTimeWithFallback(timeParts[1].trim());

        java.util.Calendar startCal = java.util.Calendar.getInstance();
        startCal.setTime(java.util.Date.from(date.atTime(startTime).atZone(ZONE_ID).toInstant()));

        java.util.Calendar endCal = java.util.Calendar.getInstance();
        endCal.setTime(java.util.Date.from(date.atTime(endTime).atZone(ZONE_ID).toInstant()));

        String eventName = course.getCourseCode() + " - " + course.getCourseTitle();
        VEvent event = new VEvent(new DateTime(startCal.getTime()), new DateTime(endCal.getTime()), eventName);

        // Create a stable, unique ID for the event
        String uidContent = dateStr + course.getCourseCode() + course.getTime();
        event.getProperties().add(new Uid(generateUid(uidContent)));

        return event;
    }

    private LocalTime parseTimeWithFallback(String timeStr) {
        try {
            // First try with the flexible formatter
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            logger.debug("Primary time parsing failed for '{}', trying fallback methods", timeStr);
            
            // Fallback 1: Try with basic h:mm a pattern
            try {
                DateTimeFormatter fallback1 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
                return LocalTime.parse(timeStr, fallback1);
            } catch (DateTimeParseException e1) {
                // Fallback 2: Try with hh:mm a pattern
                try {
                    DateTimeFormatter fallback2 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
                    return LocalTime.parse(timeStr, fallback2);
                } catch (DateTimeParseException e2) {
                    // Fallback 3: Manual parsing as last resort
                    return parseTimeManually(timeStr);
                }
            }
        }
    }

    private LocalTime parseTimeManually(String timeStr) {
        // Manual parsing for edge cases
        String cleanTime = timeStr.trim().toUpperCase();
        logger.debug("Attempting manual parsing for time: '{}'", cleanTime);
        
        boolean isPM = cleanTime.endsWith("PM");
        boolean isAM = cleanTime.endsWith("AM");
        
        if (!isPM && !isAM) {
            throw new IllegalArgumentException("Time string must contain AM or PM: " + timeStr);
        }
        
        // Remove AM/PM and trim
        String timeOnly = cleanTime.substring(0, cleanTime.length() - 2).trim();
        
        String[] parts = timeOnly.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr);
        }
        
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            // Convert to 24-hour format
            if (isPM && hour != 12) {
                hour += 12;
            } else if (isAM && hour == 12) {
                hour = 0;
            }
            
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Could not parse time components from: " + timeStr, e);
        }
    }

    private String generateUid(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString() + "@calsync.com";
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not generate UID", e);
        }
    }
}