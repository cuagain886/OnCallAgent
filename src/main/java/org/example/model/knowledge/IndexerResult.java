package org.example.model.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexerResult {
    private boolean qualityPassed;
    private float qualityScore;
    private String filePath;
    private boolean indexed;
    private String errorMessage;
}
