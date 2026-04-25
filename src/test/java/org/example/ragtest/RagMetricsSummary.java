package org.example.ragtest;

import java.util.List;

public class RagMetricsSummary {

    private final List<RagEvaluationResult> caseResults;
    private final double avgRecall;
    private final double avgPrecision;
    private final double mrr;

    public RagMetricsSummary(List<RagEvaluationResult> caseResults, double avgRecall, double avgPrecision, double mrr) {
        this.caseResults = caseResults;
        this.avgRecall = avgRecall;
        this.avgPrecision = avgPrecision;
        this.mrr = mrr;
    }

    public List<RagEvaluationResult> getCaseResults() {
        return caseResults;
    }

    public double getAvgRecall() {
        return avgRecall;
    }

    public double getAvgPrecision() {
        return avgPrecision;
    }

    public double getMrr() {
        return mrr;
    }
}
