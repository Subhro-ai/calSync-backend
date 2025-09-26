package com.CalSync.calSync.dto;

import lombok.Data;
import java.util.List;

@Data
public class DaySchedule {
    private String dayOrder;
    private List<CourseSlot> classes;
}