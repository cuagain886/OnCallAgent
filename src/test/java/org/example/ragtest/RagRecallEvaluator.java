package org.example.ragtest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class RagRecallEvaluator {

    private final Function<RagTestCase, List<RagSearchResult>> retriever;

    public RagRecallEvaluator(Function<RagTestCase, List<RagSearchResult>> retriever) {
        this.retriever = retriever;
    }

    public RagMetricsSummary evaluate(List<RagTestCase> testCases) {
        List<RagEvaluationResult> caseResults = new ArrayList<>();

        double totalRecall = 0.0;
        double totalPrecision = 0.0;
        double totalRR = 0.0;

        for (RagTestCase testCase : testCases) {
            List<RagSearchResult> results = retriever.apply(testCase);
            RagEvaluationResult caseResult = evaluateSingleCase(testCase, results);
            caseResults.add(caseResult);

            totalRecall += caseResult.getRecall();
            totalPrecision += caseResult.getPrecision();
            totalRR += caseResult.getReciprocalRank();
        }

        int size = Math.max(1, testCases.size());
        return new RagMetricsSummary(
                caseResults,
                totalRecall / size,
                totalPrecision / size,
                totalRR / size
        );
    }

    private RagEvaluationResult evaluateSingleCase(RagTestCase testCase, List<RagSearchResult> results) {
        Set<String> expected = new HashSet<>(testCase.getExpectedDocIds());

        int hit = 0;
        int firstRelevantRank = -1;

        for (int i = 0; i < results.size(); i++) {
            RagSearchResult searchResult = results.get(i);
            if (expected.contains(searchResult.getDocId())) {
                hit++;
                if (firstRelevantRank < 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }

        double recall = expected.isEmpty() ? 0.0 : (double) hit / expected.size();
        double precision = results.isEmpty() ? 0.0 : (double) hit / results.size();
        double reciprocalRank = firstRelevantRank < 0 ? 0.0 : 1.0 / firstRelevantRank;

        return new RagEvaluationResult(testCase.getId(), recall, precision, reciprocalRank);
    }
}
