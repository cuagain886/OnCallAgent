package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "knowledge-maintenance")
public class KnowledgeMaintenanceConfig {
    private boolean enabled = true;
    private boolean autoTrigger = true;
    private int maxRetry = 1;
    private double qualityThreshold = 0.7;
    private double confidenceThreshold = 0.5;
    private String reportStoragePath = "aiops-reports";
    private int retentionDays = 30;
}
