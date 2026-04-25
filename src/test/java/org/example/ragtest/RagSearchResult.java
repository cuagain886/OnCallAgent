package org.example.ragtest;

public class RagSearchResult {

    private String docId;
    private double score;

    public RagSearchResult() {
    }

    public RagSearchResult(String docId, double score) {
        this.docId = docId;
        this.score = score;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
