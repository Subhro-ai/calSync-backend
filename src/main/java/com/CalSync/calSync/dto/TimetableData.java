package com.CalSync.calSync.dto;

import java.util.List;
import java.util.Map;

public class TimetableData {

    public record DayDefinition(String dayOrder, List<String> slots, List<String> time) {}

    private static final List<String> TIME_SLOTS = List.of(
        "08:00 AM - 08:50 AM", "08:50 AM - 09:40 AM", "09:45 AM - 10:35 AM",
        "10:40 AM - 11:30 AM", "11:35 AM - 12:25 PM", "12:30 PM - 01:20 PM",
        "01:25 PM - 02:15 PM", "02:20 PM - 03:10 PM", "03:10 PM - 04:00 PM",
        "04:00 PM - 04:50 PM"
    );

    public static final Map<Integer, List<DayDefinition>> BATCH_SLOTS = Map.of(
        1, List.of(
            new DayDefinition("Day 1", List.of("A", "A", "F", "F", "G", "P6", "P7", "P8", "P9", "P10"), TIME_SLOTS),
            new DayDefinition("Day 2", List.of("P11", "P12", "P13", "P14", "P15", "B", "B", "G", "G", "A"), TIME_SLOTS),
            new DayDefinition("Day 3", List.of("C", "C", "A", "D", "B", "P26", "P27", "P28", "P29", "P30"), TIME_SLOTS),
            new DayDefinition("Day 4", List.of("P31", "P32", "P33", "P34", "P35", "D", "D", "B", "E", "C"), TIME_SLOTS),
            new DayDefinition("Day 5", List.of("E", "E", "C", "F", "D", "P46", "P47", "P48", "P49", "P50"), TIME_SLOTS)
        ),
        2, List.of(
            new DayDefinition("Day 1", List.of("P1", "P2", "P3", "P4", "P5", "A", "A", "F", "F", "G"), TIME_SLOTS),
            new DayDefinition("Day 2", List.of("B", "B", "G", "G", "A", "P16", "P17", "P18", "P19", "P20"), TIME_SLOTS),
            new DayDefinition("Day 3", List.of("P21", "P22", "P23", "P24", "P25", "C", "C", "A", "D", "B"), TIME_SLOTS),
            new DayDefinition("Day 4", List.of("D", "D", "B", "E", "C", "P36", "P37", "P38", "P39", "P40"), TIME_SLOTS),
            new DayDefinition("Day 5", List.of("P41", "P42", "P43", "P44", "P45", "E", "E", "C", "F", "D"), TIME_SLOTS)
        )
    );
}