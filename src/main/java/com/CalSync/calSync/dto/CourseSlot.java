package com.CalSync.calSync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CourseSlot {
    private String slot;
    @JsonProperty("isClass")
    private boolean isClass;
    private String courseTitle;
    private String courseCode;
    private String courseType;
    private String courseCategory;
    private String courseRoomNo;
    private String time;
}
