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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@DisplayName("TrialCase Forget Mutation Integration Tests")
public class TrialCaseForgetMutationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;

  @BeforeEach
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @AfterEach
  public void tearDown() throws Exception {
    // Clean up generated files
    Files.deleteIfExists(Paths.get("TrialCase_M0.m"));
    Files.deleteIfExists(Paths.get("BankLogin_StateStyle_M0.m"));
  }

  @Test
  @DisplayName("Test TrialCase.spthy Parsing and Forget Mutation")
  public void testTrialCaseForgetMutation() throws Exception {
    // Read the TrialCase.spthy file
    Path trialCasePath = Paths.get("src/main/resources/TrialCase.spthy");

    // Check if file exists
    if (!Files.exists(trialCasePath)) {
      log.warn("TrialCase.spthy not found, skipping test");
      return;
    }

    byte[] fileContent = Files.readAllBytes(trialCasePath);

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "TrialCase.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            fileContent);

    MvcResult result = mockMvc
        .perform(multipart("/api/forget/mutations").file(file)
            .header("Derivation-Type", "LIMITED"))
        .andExpect(status().isOk())
        .andReturn();

    // Check the response
    log.info("Response status: {}", result.getResponse().getStatus());
    log.info("Response content type: {}", result.getResponse().getContentType());

    // The response should be a zip file or successful indication
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    log.info("TrialCase.spthy forget mutation test completed");
  }

  @Test
  @DisplayName("Test TrialCase.spthy via Multi Endpoint")
  public void testTrialCaseMultiEndpoint() throws Exception {
    // Read the TrialCase.spthy file
    Path trialCasePath = Paths.get("src/main/resources/TrialCase.spthy");

    if (!Files.exists(trialCasePath)) {
      log.warn("TrialCase.spthy not found, skipping test");
      return;
    }

    byte[] fileContent = Files.readAllBytes(trialCasePath);

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "TrialCase.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            fileContent);

    MvcResult result = mockMvc
        .perform(multipart("/api/generateMutations").file(file)
            .header("Forget-Mutation", "true")
            .header("Derivation-Type", "LIMITED"))
        .andExpect(status().isOk())
        .andReturn();

    log.info("TrialCase.spthy multi-endpoint test completed with status: {}",
        result.getResponse().getStatus());
  }
}

