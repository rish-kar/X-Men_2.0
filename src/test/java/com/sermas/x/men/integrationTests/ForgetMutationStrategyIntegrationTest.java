package com.sermas.x.men.integrationTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@DisplayName("Forget Mutation Integration Tests")
public class ForgetMutationStrategyIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;

  @BeforeEach
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @AfterEach
  public void tearDown() throws Exception {
    // Clean up generated files - Bank protocol generates Bank_M0.m
    Files.deleteIfExists(Paths.get("Bank_M0.m"));
    Files.deleteIfExists(Paths.get("Oyster_M0.m"));
  }

  @Test
  @DisplayName("Test Forget Mutation - Multi Endpoint")
  public void testForgetMutationMultiEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(multipart("/api/generateMutations").file(file).header("Forget-Mutation", "true"))
        .andExpect(status().isOk());

    // Bank protocol generates Bank_M0.m
    String generatedContent = Files.readString(Paths.get("Bank_M0.m"), StandardCharsets.UTF_8);

    // Accept either explicit mutated rule names or in-place mutation content
    boolean hasUser2Suffix = generatedContent.contains("rule User_2_M:");
    boolean hasIntruder4Suffix = generatedContent.contains("rule Intruder_4_M:");
    boolean user2MutatedContent =
        generatedContent.contains(
            "SndS($User,$Intruder,<'password','nonce','nonce'>,<p2,~nh,~nb>)");
    boolean intruder4MutatedContent =
        generatedContent.contains("RcvS($Bank2,$Intruder,<'access'>,<'Granted'>)");

    assertThat(hasUser2Suffix || user2MutatedContent).isTrue();
    assertThat(hasIntruder4Suffix || intruder4MutatedContent).isTrue();

    // Verify the basic structure is maintained
    assertThat(generatedContent).contains("theory Bank");
    assertThat(generatedContent).contains("/****MODEL****/");
    assertThat(generatedContent).contains("/****ENDOFMODEL****/");

    log.info("Bank protocol forget mutation test passed");
  }

  @Test
  @DisplayName("Test Forget Mutation - Single Endpoint")
  public void testForgetMutationSingleEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file)).andExpect(status().isOk());

    // Bank protocol generates Bank_M0.m
    String generatedContent = Files.readString(Paths.get("Bank_M0.m"), StandardCharsets.UTF_8);

    boolean hasUser2Suffix = generatedContent.contains("rule User_2_M:");
    boolean hasIntruder4Suffix = generatedContent.contains("rule Intruder_4_M:");
    boolean user2MutatedContent =
        generatedContent.contains(
            "SndS($User,$Intruder,<'password','nonce','nonce'>,<p2,~nh,~nb>)");
    boolean intruder4MutatedContent =
        generatedContent.contains("RcvS($Bank2,$Intruder,<'access'>,<'Granted'>)");

    assertThat(hasUser2Suffix || user2MutatedContent)
        .as("User_2 must be mutated by name or content")
        .isTrue();
    assertThat(hasIntruder4Suffix || intruder4MutatedContent)
        .as("Intruder_4 must be mutated by name or content")
        .isTrue();

    // Verify the basic structure is maintained
    assertThat(generatedContent).contains("theory Bank");
    assertThat(generatedContent).contains("/****MODEL****/");
    assertThat(generatedContent).contains("/****ENDOFMODEL****/");

    log.info("Single endpoint forget mutation test passed");
  }

  @Test
  @DisplayName("Test Forget Mutation Rule Naming Convention (suffix or in-place)")
  public void testForgetMutationRuleNamingConvention() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file)).andExpect(status().isOk());

    String generatedContent = Files.readString(Paths.get("Bank_M0.m"), StandardCharsets.UTF_8);

    boolean hasUser2Suffix = generatedContent.contains("rule User_2_M:");
    boolean hasIntruder4Suffix = generatedContent.contains("rule Intruder_4_M:");
    boolean user2MutatedContent =
        generatedContent.contains(
            "SndS($User,$Intruder,<'password','nonce','nonce'>,<p2,~nh,~nb>)");
    boolean intruder4MutatedContent =
        generatedContent.contains("RcvS($Bank2,$Intruder,<'access'>,<'Granted'>)");

    // Accept either naming convention with _M or verified in-place mutation content
    assertThat(hasUser2Suffix || user2MutatedContent).isTrue();
    assertThat(hasIntruder4Suffix || intruder4MutatedContent).isTrue();

    log.info("Rule naming convention satisfied by suffix or content-based mutation");
  }

  @Test
  @DisplayName("Test Forget Mutation Output File Generation")
  public void testForgetMutationOutputFileGeneration() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file)).andExpect(status().isOk());

    // Verify output file is generated
    assertThat(Files.exists(Paths.get("Bank_M0.m"))).isTrue();

    String generatedContent = Files.readString(Paths.get("Bank_M0.m"), StandardCharsets.UTF_8);

    // Verify basic structure
    assertThat(generatedContent).contains("/****MODEL****/");
    assertThat(generatedContent).contains("theory Bank");
    assertThat(generatedContent).contains("begin");
    assertThat(generatedContent).contains("end");
    assertThat(generatedContent).contains("/****ENDOFMODEL****/");

    log.info("Output file generation test passed");
  }

  @Test
  @DisplayName("Test Forget Mutation Content Validation")
  public void testForgetMutationContentValidation() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file)).andExpect(status().isOk());

    String generatedContent = Files.readString(Paths.get("Bank_M0.m"), StandardCharsets.UTF_8);

    // Verify that State facts are properly handled in mutated rules
    assertThat(generatedContent).contains("State(");

    // Verify that mutated rules contain expected transformations
    assertThat(generatedContent).contains("RcvS($Bank2,$Intruder,<'access'>,<'Granted'>)");

    // Verify channel rules are preserved
    assertThat(generatedContent).contains("rule ChanSndS:");
    assertThat(generatedContent).contains("rule ChanRcvS:");

    log.info("Content validation test passed - State facts handled correctly");
  }

  @Test
  @DisplayName("Test Forget Mutation with Invalid Input")
  public void testForgetMutationWithInvalidInput() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "invalid.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            "invalid content".getBytes(StandardCharsets.UTF_8));

    // The application may throw an exception or return an error status
    try {
      MvcResult result = mockMvc
          .perform(multipart("/api/forget/mutations").file(file))
          .andReturn();

      int status = result.getResponse().getStatus();
      // Accept any status >= 400 for invalid input
      assertThat(status).isGreaterThanOrEqualTo(400);
    } catch (Exception e) {
      // Exception is acceptable for invalid input - this means validation is working
      assertThat(e).isNotNull();
      log.info("Invalid input correctly caused exception: {}", e.getMessage());
    }
  }

  @Test
  @DisplayName("Test Forget Mutation with Empty File")
  public void testForgetMutationWithEmptyFile() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "empty.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            new byte[0]);

    // The application may throw an exception or return an error status
    try {
      MvcResult result = mockMvc
          .perform(multipart("/api/forget/mutations").file(file))
          .andReturn();

      int status = result.getResponse().getStatus();
      // Accept any status >= 400 for empty file
      assertThat(status).isGreaterThanOrEqualTo(400);
    } catch (Exception e) {
      // Exception is acceptable for empty file - this means validation is working
      assertThat(e).isNotNull();
      log.info("Empty file correctly caused exception: {}", e.getMessage());
    }
  }

  @Test
  @DisplayName("Test Forget Mutation API Headers")
  public void testForgetMutationAPIHeaders() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    // Test with header - should return 200 OK and generate mutations
    mockMvc
        .perform(multipart("/api/generateMutations").file(file).header("Forget-Mutation", "true"))
        .andExpect(status().isOk());

    assertThat(Files.exists(Paths.get("Bank_M0.m"))).isTrue();

    // Clean up for next test
    Files.deleteIfExists(Paths.get("Bank_M0.m"));

    // Without header, some builds respond 200 OK or 204 No Content depending on implementation.
    MvcResult res = mockMvc
        .perform(multipart("/api/generateMutations").file(file))
        .andReturn();
    int status = res.getResponse().getStatus();
    assertThat(Arrays.asList(200, 204)).contains(status);

    log.info("API headers test passed (status={})", status);
  }

  @SuppressWarnings("unused")
  private String normalizeContent(String content) {
    return content.replaceAll("\\r\\n", "\n").replaceAll("\\s+", " ").trim();
  }
}
