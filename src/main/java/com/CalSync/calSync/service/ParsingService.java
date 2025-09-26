package com.CalSync.calSync.service;

import com.CalSync.calSync.dto.CourseSlot;
import com.CalSync.calSync.dto.DayEvent;
import com.CalSync.calSync.dto.DaySchedule;
import com.CalSync.calSync.dto.TimetableData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ParsingService {

    private static final Logger logger = LoggerFactory.getLogger(ParsingService.class);

    private record CourseInfo(String courseTitle, String courseCode, String courseType, String courseCategory, String courseRoomNo) {}

    public List<DaySchedule> parseTimetable(String rawHtml) {
        String cleanHtml = decodeHtml(extractEncodedContent(rawHtml));
        Document doc = Jsoup.parse(cleanHtml);

        String batchText = getTextFromTableRow(doc, "Batch:");
        int batch = 1; // Default to batch 1
        if (batchText != null && !batchText.trim().isEmpty()) {
            try {
                String batchNumberStr = batchText.trim().split("/")[1];
                batch = Integer.parseInt(batchNumberStr);
            } catch (Exception e) {
                logger.error("Could not parse batch number from text: '{}'. Defaulting to batch 1.", batchText, e);
            }
        }

        Map<String, CourseInfo> slotMap = new HashMap<>();
        Elements allCells = doc.select(".course_tbl td");

        for (int i = 11; i < allCells.size(); i += 11) {
            Elements cols = new Elements(allCells.subList(i, Math.min(i + 11, allCells.size())));
            if (cols.size() < 11) continue;

            CourseInfo info = new CourseInfo(
                cols.get(2).text().trim(), // Course Title
                cols.get(1).text().trim(), // Course Code
                cols.get(6).text().trim(), // Course Type
                cols.get(5).text().trim(), // Category
                cols.get(9).text().trim()  // Room No.
            );

            String[] slots = cols.get(8).text().trim().split("-");
            for (String slot : slots) {
                if (!slot.trim().isEmpty()) {
                    slotMap.put(slot.trim(), info);
                }
            }
        }

        List<DaySchedule> timetable = new ArrayList<>();
        List<TimetableData.DayDefinition> scheduleForBatch = TimetableData.BATCH_SLOTS.getOrDefault(batch, TimetableData.BATCH_SLOTS.get(1));

        for (TimetableData.DayDefinition dayDef : scheduleForBatch) {
            DaySchedule daySchedule = new DaySchedule();
            daySchedule.setDayOrder(dayDef.dayOrder().replaceAll("\\s+", "")); // "Day 1" -> "Day1"

            List<CourseSlot> courseSlots = new ArrayList<>();
            for (int j = 0; j < dayDef.slots().size(); j++) {
                String slotName = dayDef.slots().get(j);
                CourseInfo courseInfo = slotMap.get(slotName);

                CourseSlot courseSlot = new CourseSlot();
                courseSlot.setSlot(slotName);
                courseSlot.setTime(dayDef.time().get(j));

                if (courseInfo != null) {
                    courseSlot.setClass(true);
                    courseSlot.setCourseTitle(courseInfo.courseTitle());
                    courseSlot.setCourseCode(courseInfo.courseCode());
                    courseSlot.setCourseType(courseInfo.courseType());
                    courseSlot.setCourseCategory(courseInfo.courseCategory());
                    courseSlot.setCourseRoomNo(courseInfo.courseRoomNo());
                } else {
                    courseSlot.setClass(false);
                }
                courseSlots.add(courseSlot);
            }
            daySchedule.setClasses(courseSlots);
            timetable.add(daySchedule);
        }
        return timetable;
    }

    public List<DayEvent> parseAcademicPlanner(String rawHtml) {
        Document doc = Jsoup.parse(rawHtml);
        Element zmlDiv = doc.selectFirst("div.zc-pb-embed-placeholder-content");
        if (zmlDiv == null) {
            logger.warn("Could not find the 'zmlvalue' div on the calendar page.");
            return new ArrayList<>();
        }
        Document innerDoc = Jsoup.parse(zmlDiv.attr("zmlvalue"));
        Element mainTable = innerDoc.selectFirst("table[bgcolor='#FAFCFE']");
        if (mainTable == null) {
            logger.warn("Could not find the main calendar table inside 'zmlvalue'.");
            return new ArrayList<>();
        }

        List<DayEvent> academicCalendar = new ArrayList<>();
        Elements dataRows = mainTable.select("tr:gt(0)");

        for (Element row : dataRows) {
            // The academic planner has 5 columns per month. We just iterate all.
            Elements tds = row.select("td");
            for (int i = 0; i + 3 < tds.size(); i += 5) {
                String date = tds.get(i).text().trim();
                if (date.isEmpty()) continue;

                String day = tds.get(i + 1).text().trim();
                String event = tds.get(i + 2).selectFirst("strong").text().trim();
                String dayOrder = tds.get(i + 3).text().trim().replaceAll("\\s+", ""); // "Day 1" -> "Day1"

                academicCalendar.add(new DayEvent(date, day, event, dayOrder));
            }
        }
        return academicCalendar;
    }

    private String getTextFromTableRow(Document doc, String label) {
        Element cell = doc.selectFirst("td:contains(" + label + ")");
        return (cell != null && cell.nextElementSibling() != null) ? cell.nextElementSibling().text().trim() : "";
    }

    private String extractEncodedContent(String rawHtml) {
        final Pattern pattern = Pattern.compile("pageSanitizer\\.sanitize\\('(.*)'\\);", Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(rawHtml);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String decodeHtml(String encodedHtml) {
        try {
            String partiallyCleaned = encodedHtml
                .replaceAll("\\\\x([0-9A-Fa-f]{2})", "%$1")
                .replaceAll("\\\\'", "'")
                .replaceAll("\\\\\"", "\"");
            return URLDecoder.decode(partiallyCleaned, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error decoding HTML", e);
        }
    }
}