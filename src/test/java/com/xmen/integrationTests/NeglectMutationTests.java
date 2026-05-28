package com.xmen.integrationTests;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class NeglectMutationTests {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  public void setup() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    // Clean up any pre-existing generated files to ensure a clean slate for each test
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "CoachService_*.m")) {
      for (Path p : stream) {
        Files.deleteIfExists(p);
      }
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "CoachService_M*.m")) {
      for (Path p : stream) {
        Files.deleteIfExists(p);
      }
    }
  }

  @AfterEach
  public void tearDown() throws Exception {
    // Delete all generated CoachService_*.m and CoachService_M*.m files (no hardcoding)
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "CoachService_*.m")) {
      for (Path p : stream) {
        Files.deleteIfExists(p);
      }
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "CoachService_M*.m")) {
      for (Path p : stream) {
        Files.deleteIfExists(p);
      }
    }
  }

  @Test
  @DisplayName("Test Neglect Mutation - Multi Endpoint")
  public void testNeglectMutationMultiEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "CoachService.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/CoachService.spthy")));

    // Multi-endpoint: header-driven generation (Neglect)
    mockMvc
        .perform(multipart("/api/generateMutations").file(file).header("Neglect-Mutation", "true"))
        .andExpect(status().isOk());

    assertAtLeastOneGeneratedMatchesAnySample();
  }

  @Test
  @DisplayName("Test Neglect Mutation - Single Endpoint")
  public void testNeglectMutationSingleEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "CoachService.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/CoachService.spthy")));

    // Single-endpoint: dedicated neglect endpoint
    mockMvc.perform(multipart("/api/neglect/mutations").file(file)).andExpect(status().isOk());

    assertAtLeastOneGeneratedMatchesAnySample();
  }

  @Test
  @DisplayName("Neglect Multi - invalid extension returns 400")
  public void testNeglectMultiEndpoint_InvalidExtension_returns400() throws Exception {
    String content = Files.readString(Paths.get("src/test/resources/CoachService.spthy"), StandardCharsets.UTF_8);
    MockMultipartFile badExt =
        new MockMultipartFile("file", "CoachService.txt", MediaType.TEXT_PLAIN_VALUE, content.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/api/generateMutations").file(badExt).header("Neglect-Mutation", "true"))
        .andExpect(status().isBadRequest()); // 400
  }

  @Test
  @DisplayName("Neglect Single - invalid extension returns 400")
  public void testNeglectSingleEndpoint_InvalidExtension_returns400() throws Exception {
    String content = Files.readString(Paths.get("src/test/resources/CoachService.spthy"), StandardCharsets.UTF_8);
    MockMultipartFile badExt =
        new MockMultipartFile("file", "CoachService.txt", MediaType.TEXT_PLAIN_VALUE, content.getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/api/neglect/mutations").file(badExt)).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Neglect Multi - empty file returns 204 (no content)")
  public void testNeglectMultiEndpoint_EmptyFile_returns500() throws Exception {
    MockMultipartFile empty =
        new MockMultipartFile("file", "CoachService.spthy", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

    // FileSplitterService appends "\nend\n", parser succeeds with no rules, no mutations
    // generated -> ZipService returns 204 No Content
    mockMvc
        .perform(multipart("/api/generateMutations").file(empty).header("Neglect-Mutation", "true"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Neglect Single - empty file returns 204 (no content)")
  public void testNeglectSingleEndpoint_EmptyFile_returns500() throws Exception {
    MockMultipartFile empty =
        new MockMultipartFile("file", "CoachService.spthy", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

    mockMvc.perform(multipart("/api/neglect/mutations").file(empty)).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Neglect Multi - no RULES markers returns 400 (syntax error)")
  public void testNeglectMultiEndpoint_NoRulesMarkers_returns500() throws Exception {
    String noRules = "/****MODEL****/ theory X begin\n builtins: signing\n rule setup: [ Fr(~x) ] --> [ ]\n /****ENDOFMODEL****/";
    MockMultipartFile bad =
        new MockMultipartFile("file", "CoachService.spthy", MediaType.TEXT_PLAIN_VALUE, noRules.getBytes(StandardCharsets.UTF_8));

    // Without RULES markers, splitter treats whole content as rules; parser flags syntax
    // errors and the visitor yields no usable rules, so ModelLoader surfaces a 400 Bad
    // Request rather than letting the request silently complete with no output.
    mockMvc
        .perform(multipart("/api/generateMutations").file(bad).header("Neglect-Mutation", "true"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Neglect Single - malformed rules returns 400 (syntax error)")
  public void testNeglectSingleEndpoint_MalformedRules_returns500() throws Exception {
    // Include RULES markers but broken content inside
    String malformed = "/****RULES****/\n rule bad: [ X ] --> [ Y\n /****ENDOFRULES****/"; // missing closing bracket
    MockMultipartFile bad =
        new MockMultipartFile("file", "CoachService.spthy", MediaType.TEXT_PLAIN_VALUE, malformed.getBytes(StandardCharsets.UTF_8));

    // ModelLoader wraps unrecoverable parser errors as IllegalArgumentException, which the
    // controller maps to HTTP 400 Bad Request (instead of the previous opaque 500).
    mockMvc.perform(multipart("/api/neglect/mutations").file(bad)).andExpect(status().isBadRequest());
  }

  private void assertAtLeastOneGeneratedMatchesAnySample() throws Exception {
    // Collect generated files of both naming patterns
    List<Path> generated = new ArrayList<>();
    try (DirectoryStream<Path> gen = Files.newDirectoryStream(Paths.get("."), "CoachService_*.m")) {
      for (Path p : gen) generated.add(p);
    }
    try (DirectoryStream<Path> gen = Files.newDirectoryStream(Paths.get("."), "CoachService_M*.m")) {
      for (Path p : gen) generated.add(p);
    }

    assertThat(generated)
        .withFailMessage("No generated CoachService files found after mutation")
        .isNotEmpty();

    // Log generated files
    StringBuilder genList = new StringBuilder();
    for (Path p : generated) {
      if (genList.length() > 0) genList.append(", ");
      genList.append(p.getFileName());
    }
    log.info("Generated files ({}): {}", generated.size(), genList);

    // Load sample files from src/test/resources (underscore naming)
    List<Path> samples = new ArrayList<>();
    try (DirectoryStream<Path> samp = Files.newDirectoryStream(Paths.get("src/test/resources/"), "CoachService_*.m")) {
      for (Path p : samp) samples.add(p);
    }
    assertThat(samples)
        .withFailMessage("No sample files found under src/test/resources matching CoachService_*.m")
        .isNotEmpty();

    // Log sample files
    StringBuilder sampList = new StringBuilder();
    for (Path p : samples) {
      if (sampList.length() > 0) sampList.append(", ");
      sampList.append(p.getFileName());
    }
    log.info("Sample files ({}): {}", samples.size(), sampList);

    boolean anySampleMatched = false;
    int matchedCount = 0;

    // Iterate over each sample and try to find a matching generated file (RULES section only)
    for (Path s : samples) {
      String expContent = normalize(extractRulesSection(Files.readString(s, StandardCharsets.UTF_8)));
      boolean sampleMatched = false;
      for (Path g : generated) {
        String genContent = normalize(extractRulesSection(Files.readString(g, StandardCharsets.UTF_8)));
        if (genContent.contains(expContent)) {
          log.warn("Match: {} contains sample {} (RULES only)", g.getFileName(), s.getFileName());
          System.out.println("[NeglectMutationTests] MATCH -> generated=" + g.getFileName() + ", sample=" + s.getFileName());
          sampleMatched = true;
          anySampleMatched = true;
          matchedCount++;
          break; // stop at first generated match for this sample
        }
      }
      if (!sampleMatched) {
        log.warn("No generated file matched sample {} (RULES only)", s.getFileName());
        System.out.println("[NeglectMutationTests] NO-MATCH -> sample=" + s.getFileName());
      }
    }

    log.info("Total samples matched: {}/{}", matchedCount, samples.size());
    assertThat(anySampleMatched)
        .withFailMessage("None of the generated CoachService files matched any sample under src/test/resources")
        .isTrue();
  }

  private String extractRulesSection(String content) {
    String start = "/****RULES****/";
    String end = "/****ENDOFRULES****/";
    int sIdx = content.indexOf(start);
    int eIdx = content.indexOf(end);
    if (sIdx >= 0 && eIdx > sIdx) {
      return content.substring(sIdx + start.length(), eIdx);
    }
    return content; // fallback to full content if markers not present
  }

  private String normalize(String s) {
    // Normalize line endings and collapse all whitespace to a single space for robust contains()
    String unified = s.replaceAll("\\r\\n", "\n").trim();
    return unified.replaceAll("\\s+", " ");
  }
}
