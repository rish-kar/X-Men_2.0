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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class AddMutationTests {

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
    // Delete all generated files from Oyster_M0.m to Oyster_M91.m
    for (int i = 0; i <= 91; i++) {
      Path p = Paths.get("Oyster_M" + i + ".m");
      if (Files.exists(p)) {
        Files.delete(p);
      }
    }
  }

  @Test
  @DisplayName("Test Add Mutation - Multi Endpoint")
  public void testAddMutationMultiEndpoint() throws Exception {
    // Prepare the MultipartFile
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy")));

    // Perform the request
    mockMvc
        .perform(multipart("/api/generateMutations").file(file).header("Add-Mutation", "true"))
        .andExpect(status().isOk());

    // Generate a random number from 0 to 91
    Random random = new Random();
    int randomNumber = random.nextInt(92); // 0 to 91 inclusive

    // Verify the generated file against all Add_ files
    File generatedFile = new File("Oyster_M" + randomNumber + ".m");
    String generatedFileContent =
        normalizeContent(Files.readString(generatedFile.toPath(), StandardCharsets.UTF_8));
    boolean matchFound = false;
    for (int i = 0; i <= 91; i++) {
      File expectedFile = new File("src/test/resources/Add_" + i + ".m");
      if (Files.exists(expectedFile.toPath())) {
        String expectedFileContent =
            normalizeContent(Files.readString(expectedFile.toPath(), StandardCharsets.UTF_8));
        if (generatedFileContent.contains(expectedFileContent)) {
          matchFound = true;
          log.info("Match found with file: Add_{}.m", i);
          break;
        } else {
          log.info("No match with file: Add_{}.m", i);
        }
      }
    }
    assertThat(matchFound).isTrue();
  }

  private String normalizeContent(String content) {
    return content.replaceAll("\\r\\n", "\n").trim();
  }

  @Test
  @DisplayName("Test Add Mutation - Single Endpoint")
  public void testAddMutationSingleEndpoint() throws Exception {
    // Prepare the MultipartFile
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy")));

    // Perform the request
    mockMvc.perform(multipart("/api/addMutations").file(file)).andExpect(status().isOk());

    // Generate a random number from 0 to 91
    Random random = new Random();
    int randomNumber = random.nextInt(92); // 0 to 91 inclusive

    // Verify the generated file against all Add_ files
    File generatedFile = new File("Oyster_M" + randomNumber + ".m");
    String generatedFileContent =
        normalizeContent(Files.readString(generatedFile.toPath(), StandardCharsets.UTF_8));
    boolean matchFound = false;
    for (int i = 0; i <= 91; i++) {
      File expectedFile = new File("src/test/resources/Add_" + i + ".m");
      if (Files.exists(expectedFile.toPath())) {
        String expectedFileContent =
            normalizeContent(Files.readString(expectedFile.toPath(), StandardCharsets.UTF_8));
        if (generatedFileContent.contains(expectedFileContent)) {
          matchFound = true;
          log.info("Match found with file: Add_{}.m", i);
          break;
        } else {
          log.info("No match with file: Add_{}.m", i);
        }
      }
    }
    assertThat(matchFound).isTrue();
  }
}
