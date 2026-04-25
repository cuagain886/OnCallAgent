package org.example.ragtest;

import java.util.List;

public class RagTestCase {

    private String id;
    private String query;
    private List<String> expectedDocIds;
    private int topK;
    private String difficulty;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getExpectedDocIds() {
        return expectedDocIds;
    }

    public void setExpectedDocIds(List<String> expectedDocIds) {
        this.expectedDocIds = expectedDocIds;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
