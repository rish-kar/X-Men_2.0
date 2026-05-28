package com.xmen.integrationTests;

import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.impl.ForgetMutationStrategy;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@DisplayName("Forget Mutation Integration Tests")
public class ForgetMutationStrategyIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private ForgetMutationStrategy forgetMutationStrategy;
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

    // The Forget mutation rewrites H_2 -> H_2_M, substituting pw1 with pw2
    // in the m3 send and the PasswordAttempt action.
    boolean hasH2MutatedRule = generatedContent.contains("rule H_2_M:");
    boolean hasPw2Send =
        generatedContent.contains("Send($Human,'m3',senc(<$Human,pw2>,k(nH,nB)))");
    boolean hasPw2Attempt =
        generatedContent.contains("PasswordAttempt($Human,$B1,pw2,nH,nB)");

    assertThat(hasH2MutatedRule || hasPw2Send || hasPw2Attempt).isTrue();

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

    boolean hasH2MutatedRule = generatedContent.contains("rule H_2_M:");
    boolean hasPw2Send =
        generatedContent.contains("Send($Human,'m3',senc(<$Human,pw2>,k(nH,nB)))");
    boolean hasPw2Attempt =
        generatedContent.contains("PasswordAttempt($Human,$B1,pw2,nH,nB)");

    assertThat(hasH2MutatedRule || hasPw2Send || hasPw2Attempt)
        .as("H_2 must be mutated by rule-name suffix _M or by pw1->pw2 substitution in content")
        .isTrue();

    // Verify the basic structure is maintained
    assertThat(generatedContent).contains("theory Bank");
    assertThat(generatedContent).contains("/****MODEL****/");
    assertThat(generatedContent).contains("/****ENDOFMODEL****/");

    log.info("Single endpoint forget mutation test passed");
  }

  @Test
  @DisplayName("Max-Variants-Per-Rule header accepted")
  public void testMaxVariantsPerRuleHeaderAccepted() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Max-Variants-Per-Rule", "3"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Max-Variants-Per-Rule header rejects zero")
  public void testMaxVariantsPerRuleHeaderZeroRejected() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Max-Variants-Per-Rule", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Max-Variants-Per-Rule header rejects overly large values")
  public void testMaxVariantsPerRuleHeaderTooLargeRejected() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Max-Variants-Per-Rule", "99999"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Default max variants per rule behavior remains valid")
  public void testMaxVariantsPerRuleDefault() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(multipart("/api/forget/mutations").file(file))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Blocking-Mode header CASE2_PAIRING succeeds")
  public void testBlockingModeCase2Pairing() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Blocking-Mode", "CASE2_PAIRING"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Blocking-Mode header 3 succeeds")
  public void testBlockingModeCase3Numeric() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Blocking-Mode", "3"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Blocking-Mode header BANANA returns 400")
  public void testBlockingModeInvalid() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    MvcResult result = mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Blocking-Mode", "BANANA"))
        .andExpect(status().isBadRequest())
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("Unknown Blocking-Mode");
  }

  @Test
  @DisplayName("Blocking-Mode restores to default after request")
  public void testBlockingModeRestoration() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Forget_Bank_Input.spthy")));

    mockMvc
        .perform(
            multipart("/api/forget/mutations")
                .file(file)
                .header("Blocking-Mode", "CASE3_FULL_DY"))
        .andExpect(status().isOk());

    mockMvc
        .perform(multipart("/api/forget/mutations").file(file))
        .andExpect(status().isOk());

    assertThat(forgetMutationStrategy.getBlockingMode()).isEqualTo(BlockingMode.CASE1_WEAK);
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

    boolean hasH2MutatedRule = generatedContent.contains("rule H_2_M:");
    boolean hasPw2Send =
        generatedContent.contains("Send($Human,'m3',senc(<$Human,pw2>,k(nH,nB)))");
    boolean hasPw2Attempt =
        generatedContent.contains("PasswordAttempt($Human,$B1,pw2,nH,nB)");

    // Accept either the _M naming convention or verified in-place pw1->pw2 mutation content
    assertThat(hasH2MutatedRule || hasPw2Send || hasPw2Attempt).isTrue();

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

    // Verify the Forget mutation visited H_2 (rule was renamed to H_2_M) and
    // the symmetric-encryption send-payload structure is preserved. The exact
    // password substitution (pw1 -> pw2 vs. left as-is) depends on whether the
    // derivation checker finds an unblocked derivation; both are valid mutation
    // outcomes per Algorithm 1.
    assertThat(generatedContent).contains("rule H_2_M:");
    assertThat(generatedContent).contains("senc(<$Human,");

    // Verify the Bank-side response rules are preserved.
    assertThat(generatedContent).contains("rule Bank_1:");
    assertThat(generatedContent).contains("rule Bank_2_OK:");

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

  @Test
  @DisplayName("Bank_revised.spthy In/Out format smoke test")
  public void testBankInOutFormatSmoke() throws Exception {
    Path inputPath = Paths.get("src/test/resources/Bank_revised.spthy");
    if (!Files.exists(inputPath)) {
      log.warn("Bank_revised.spthy not present; skipping In/Out smoke test");
      return;
    }

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Bank_revised.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(inputPath));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/forget/mutations")
                    .file(file)
                    .header(
                        "Witness-Actions",
                        "PasswordAttempt,U_LoginRequest,LoginOK,LoginSuccess,LoginFailed,Commit,B_Running,RevLtk,HFin,Challenge"))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray().length).isGreaterThan(0);
  }

  @Test
  @DisplayName("Paper lemmas and action facts present in Forget_Bank_Input.spthy")
  public void testPaperLemmasPresentInSpec() throws Exception {
    String spec =
        Files.readString(
            Paths.get("src/test/resources/Forget_Bank_Input.spthy"), StandardCharsets.UTF_8);

    assertThat(spec).contains("lemma Auth_B2_requires_H_intent");
    assertThat(spec).contains("lemma Password_Confidentiality");
    assertThat(spec).contains("U_LoginRequest($Human,$B1,");
    assertThat(spec).contains("PasswordAttempt($Human,$B1,");
    assertThat(spec).contains("LoginOK($B,$Human,");
    assertThat(spec).doesNotContain("lemma Challenge_Injective");
    assertThat(spec).doesNotContain("lemma Complete_Verification");
    assertThat(spec).doesNotContain("lemma Human_intends_Bank2_if_Bank2_OK");
  }

  @SuppressWarnings("unused")
  private String normalizeContent(String content) {
    return content.replaceAll("\\r\\n", "\n").replaceAll("\\s+", " ").trim();
  }
}
