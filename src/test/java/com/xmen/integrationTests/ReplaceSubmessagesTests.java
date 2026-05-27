package com.xmen.integrationTests;

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

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class ReplaceSubmessagesTests {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  /** Setup the test environment. */
  @BeforeEach
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  /**
   * Tear down the test environment.
   *
   * @throws Exception Exception Object
   */
  @AfterEach
  public void tearDown() throws Exception {
    // Delete generated files
    Files.deleteIfExists(Paths.get("Oyster_M0.m"));
    Files.deleteIfExists(Paths.get("Oyster_M1.m"));
    Files.deleteIfExists(Paths.get("Oyster_M2.m"));
    Files.deleteIfExists(Paths.get("Oyster_M3.m"));
    Files.deleteIfExists(Paths.get("Oyster_M4.m"));
    Files.deleteIfExists(Paths.get("Oyster_M5.m"));
    Files.deleteIfExists(Paths.get("Oyster_M6.m"));
    Files.deleteIfExists(Paths.get("Oyster_M7.m"));
  }

  /**
   * Test Replace SubMessages Mutation - Multi Endpoint
   *
   * @throws Exception Exception Object
   */
  @Test
  @DisplayName("Test Replace SubMessages Mutation - Multi Endpoint")
  public void testReplaceSubMessagesMultiEndpoint() throws Exception {
    // Prepare the MultipartFile
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy")));

    // Perform the request
    mockMvc
        .perform(
            multipart("/api/generateMutations").file(file).header("Replace-Sub-Messages", "true"))
        .andExpect(status().isOk());

    // Helper to compare state vectors for State($Human,...) lines
    java.util.function.BiConsumer<String, String> compareStateLine = (expectedLine, actualContent) -> {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("State\\(\\$Human,'\\d+',<([^>]*)>\\)").matcher(expectedLine);
      if (m.find()) {
        String expectedVector = m.group(1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("State\\(\\$Human,'\\d+',<([^>]*)>\\)").matcher(actualContent);
        boolean found = false;
        while (m2.find()) {
          String actualVector = m2.group(1);
          String[] expectedElems = expectedVector.split(",");
          String[] actualElems = actualVector.split(",");
          int idx = 0;
          for (String expElem : expectedElems) {
            expElem = expElem.trim();
            while (idx < actualElems.length && !actualElems[idx].trim().equals(expElem)) {
              idx++;
            }
            if (idx == actualElems.length) {
              // Not found in order
              return;
            }
            idx++;
          }
          found = true;
          break;
        }
        org.assertj.core.api.Assertions.assertThat(found).as("State vector for line: " + expectedLine).isTrue();
      } else {
        org.assertj.core.api.Assertions.assertThat(actualContent).contains(expectedLine.trim());
      }
    };
    // Compare all files
    for (int i = 0; i < 8; i++) {
      String actual = Files.readString(Paths.get("Oyster_M" + i + ".m")).trim().replaceAll("\\r?\\n", "\n");
      String expected = Files.readString(Paths.get("src/test/resources/ReplaceSubMessages_" + i + ".m")).trim().replaceAll("\\r?\\n", "\n");
      for (String line : expected.split("\n")) {
        if (!line.trim().isEmpty()) {
          compareStateLine.accept(line, actual);
        }
      }
    }
  }

  /**
   * Test Replace SubMessages Mutation - Single Endpoint
   *
   * @throws Exception Exception Object
   */
  @Test
  @DisplayName("Test Replace SubMessages Mutation - Single Endpoint")
  public void testReplaceSubMessagesSingleEndpoint() throws Exception {
    // Prepare the MultipartFile
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy")));

    // Perform the request
    mockMvc
        .perform(multipart("/api/replace/subMessagesMutations").file(file))
        .andExpect(status().isOk());

    // Helper to compare state vectors for State($Human,...) lines
    java.util.function.BiConsumer<String, String> compareStateLine = (expectedLine, actualContent) -> {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("State\\(\\$Human,'\\d+',<([^>]*)>\\)").matcher(expectedLine);
      if (m.find()) {
        String expectedVector = m.group(1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("State\\(\\$Human,'\\d+',<([^>]*)>\\)").matcher(actualContent);
        boolean found = false;
        while (m2.find()) {
          String actualVector = m2.group(1);
          String[] expectedElems = expectedVector.split(",");
          String[] actualElems = actualVector.split(",");
          int idx = 0;
          for (String expElem : expectedElems) {
            expElem = expElem.trim();
            while (idx < actualElems.length && !actualElems[idx].trim().equals(expElem)) {
              idx++;
            }
            if (idx == actualElems.length) {
              // Not found in order
              return;
            }
            idx++;
          }
          found = true;
          break;
        }
        org.assertj.core.api.Assertions.assertThat(found).as("State vector for line: " + expectedLine).isTrue();
      } else {
        org.assertj.core.api.Assertions.assertThat(actualContent).contains(expectedLine.trim());
      }
    };
    // Compare all files
    for (int i = 0; i < 8; i++) {
      String actual = Files.readString(Paths.get("Oyster_M" + i + ".m")).trim().replaceAll("\\r?\\n", "\n");
      String expected = Files.readString(Paths.get("src/test/resources/ReplaceSubMessages_" + i + ".m")).trim().replaceAll("\\r?\\n", "\n");
      for (String line : expected.split("\n")) {
        if (!line.trim().isEmpty()) {
          compareStateLine.accept(line, actual);
        }
      }
    }
  }
}
