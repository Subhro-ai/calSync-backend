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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CalendarService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    public String generateIcsContent(List<DaySchedule> timetable, List<DayEvent> academicPlanner) {
        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//CalSync//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);

        // Create a quick lookup map for the timetable
        Map<String, DaySchedule> timetableMap = timetable.stream()
                .collect(Collectors.toMap(DaySchedule::getDayOrder, schedule -> schedule));

        // Iterate through every day in the academic planner
        for (DayEvent dayEvent : academicPlanner) {
            // Find the corresponding schedule for that day's order (e.g., "Day1", "Day2")
            DaySchedule daySchedule = timetableMap.get(dayEvent.getDayOrder());

            if (daySchedule != null) {
                // We have classes on this day, so create events for them
                for (CourseSlot courseSlot : daySchedule.getClasses()) {
                    if (courseSlot.isClass()) {
                        try {
                            VEvent event = createEventForCourse(courseSlot, dayEvent.getDate());
                            calendar.getComponents().add(event);
                        } catch (Exception e) {
                            logger.error("Could not create event for course {} on date {}", courseSlot.getCourseCode(), dayEvent.getDate(), e);
                        }
                    }
                }
            }
        }
        return calendar.toString();
    }

    private VEvent createEventForCourse(CourseSlot course, String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
        String[] timeParts = course.getTime().split(" - ");
        LocalTime startTime = LocalTime.parse(timeParts[0], TIME_FORMATTER);
        LocalTime endTime = LocalTime.parse(timeParts[1], TIME_FORMATTER);

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
