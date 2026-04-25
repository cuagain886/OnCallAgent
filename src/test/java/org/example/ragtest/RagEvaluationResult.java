package org.example.ragtest;

public class RagEvaluationResult {

    private final String caseId;
    private final double recall;
    private final double precision;
    private final double reciprocalRank;

    public RagEvaluationResult(String caseId, double recall, double precision, double reciprocalRank) {
        this.caseId = caseId;
        this.recall = recall;
        this.precision = precision;
        this.reciprocalRank = reciprocalRank;
    }

    public String getCaseId() {
        return caseId;
    }

    public double getRecall() {
        return recall;
    }

    public double getPrecision() {
        return precision;
    }

    public double getReciprocalRank() {
        return reciprocalRank;
    }
}
