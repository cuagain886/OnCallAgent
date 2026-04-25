package org.example.ragtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class RagTestCaseLoader {

    private RagTestCaseLoader() {
    }

    public static List<RagTestCase> loadFromClasspath(String classpathResource) {
        ObjectMapper objectMapper = new ObjectMapper();

        try (InputStream inputStream = RagTestCaseLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Cannot find resource: " + classpathResource);
            }
            return objectMapper.readValue(inputStream, new TypeReference<List<RagTestCase>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load test cases from " + classpathResource, e);
        }
    }
}
