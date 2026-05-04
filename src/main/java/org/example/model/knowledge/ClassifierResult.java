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
public class ClassifierResult {
    private String action;          // CREATE | UPDATE | SKIP
    private String targetDoc;       // 目标文档路径（UPDATE 时必填）
    private String targetDocTitle;
    private double similarity;
    private String reason;
    private List<String> newInfoGaps;
    private String mergeStrategy;   // APPEND_SECTION | UPDATE_SECTION | REWRITE
}
