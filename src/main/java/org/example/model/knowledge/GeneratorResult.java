package org.example.model.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratorResult {
    private String filename;
    private String content;
    private String action;   // CREATE | UPDATE
    private int contentLength;
}
