package org.example.model.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractorResult {
    private String alertName;
    private String alertLevel;
    private String category;
    private List<String> symptoms;
    private String rootCause;
    private String rootCauseType;
    private Solution solution;
    private List<String> keywords;
    private List<String> affectedServices;
    private float confidence;
    private List<String> missingInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Solution {
        private List<String> immediate;
        private List<String> shortTerm;
        private List<String> longTerm;
    }
}
