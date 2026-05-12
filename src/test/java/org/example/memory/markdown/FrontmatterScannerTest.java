package org.example.memory.markdown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FrontmatterScanner 单元测试
 */
class FrontmatterScannerTest {

    @TempDir
    Path tempDir;

    private MemoryFileWriter fileWriter;
    private FrontmatterScanner scanner;

    @BeforeEach
    void setUp() {
        fileWriter = new MemoryFileWriter();
        scanner = new FrontmatterScanner(fileWriter);
    }

    @Test
    void testScanAllWithValidFiles() throws Exception {
        // Given - 创建测试文件
        createUserMemory("user_pref", "User Preferences", "User preferences content");
        createUserMemory("user_style", "Work Style", "Work style content");
        createProjectMemory("proj_arch", "Project Architecture", "Architecture content");

        // When
        List<FrontmatterScanner.ScannedMemory> results = scanner.scanAll(tempDir);

        // Then
        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(m -> m.getName().equals("User Preferences")));
        assertTrue(results.stream().anyMatch(m -> m.getName().equals("Work Style")));
        assertTrue(results.stream().anyMatch(m -> m.getName().equals("Project Architecture")));
    }

    @Test
    void testScanAllExcludesMemoryMd() throws Exception {
        // Given - 创建 MEMORY.md 和其他文件
        Files.writeString(tempDir.resolve("MEMORY.md"), "# Memory Index");
        createUserMemory("user_pref", "User Preferences", "content");

        // When
        List<FrontmatterScanner.ScannedMemory> results = scanner.scanAll(tempDir);

        // Then
        assertEquals(1, results.size());
        assertEquals("User Preferences", results.get(0).getName());
    }

    @Test
    void testScanAllWithEmptyDirectory() {
        // When
        List<FrontmatterScanner.ScannedMemory> results = scanner.scanAll(tempDir);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    void testScanAllWithInvalidFile() throws Exception {
        // Given - 创建一个没有 frontmatter 的文件
        Path userDir = tempDir.resolve("user");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("invalid.md"), "No frontmatter here");

        // When
        List<FrontmatterScanner.ScannedMemory> results = scanner.scanAll(tempDir);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    void testScanFileWithValidFrontmatter() throws Exception {
        // Given
        Path filePath = createUserMemory("test", "Test Memory", "Test content");

        // When
        FrontmatterScanner.ScannedMemory result = scanner.scanFile(filePath, tempDir);

        // Then
        assertNotNull(result);
        assertEquals("Test Memory", result.getName());
        assertEquals(MemoryType.USER, result.getType());
        assertEquals("Test content", result.getDescription());
    }

    @Test
    void testScanFileWithRelativePath() throws Exception {
        // Given
        Path filePath = createProjectMemory("proj", "Project", "Project content");

        // When
        FrontmatterScanner.ScannedMemory result = scanner.scanFile(filePath, tempDir);

        // Then
        assertNotNull(result);
        assertEquals("project/proj.md", result.getRelativePath().toString().replace("\\", "/"));
    }

    @Test
    void testScannedMemoryToString() throws Exception {
        // Given
        Path filePath = createUserMemory("test", "Test Memory", "Description");

        // When
        FrontmatterScanner.ScannedMemory result = scanner.scanFile(filePath, tempDir);

        // Then
        assertNotNull(result);
        String str = result.toString();
        assertTrue(str.contains("user"));
        assertTrue(str.contains("Test Memory"));
        assertTrue(str.contains("Description"));
    }

    private Path createUserMemory(String fileName, String name, String description) throws Exception {
        Path userDir = tempDir.resolve("user");
        Files.createDirectories(userDir);
        Path filePath = userDir.resolve(fileName + ".md");

        String content = String.format("""
                ---
                name: %s
                description: %s
                type: user
                created: 2026-05-12
                updated: 2026-05-12
                ---

                Content here
                """, name, description);

        Files.writeString(filePath, content);
        return filePath;
    }

    private Path createProjectMemory(String fileName, String name, String description) throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Path filePath = projectDir.resolve(fileName + ".md");

        String content = String.format("""
                ---
                name: %s
                description: %s
                type: project
                created: 2026-05-12
                updated: 2026-05-12
                ---

                Content here
                """, name, description);

        Files.writeString(filePath, content);
        return filePath;
    }
}
