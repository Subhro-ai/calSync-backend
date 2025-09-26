package com.CalSync.calSync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UserLookupResponse {

    @JsonProperty("lookup")
    private LookupData lookupData;

    @Data
    public static class LookupData {
        private String identifier;
        private String digest;
    }
}