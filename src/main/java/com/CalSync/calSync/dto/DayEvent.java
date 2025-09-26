package com.CalSync.calSync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DayEvent {
    private String date;
    private String day; 
    private String event; 
    private String dayOrder;
}