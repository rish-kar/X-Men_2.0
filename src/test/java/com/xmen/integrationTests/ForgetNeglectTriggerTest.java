package com.xmen.integrationTests;

import com.xmen.config.CeremonyVocabulary;
import com.xmen.service.impl.ForgetMutationStrategy;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Forget -> Neglect Trigger Tests")
class ForgetNeglectTriggerTest {

  @Autowired
  private WebApplicationContext webApplicationContext;
  @Autowired
  private CeremonyVocabulary vocabulary;
  @Autowired
  private ForgetMutationStrategy forgetMutationStrategy;
  private MockMvc mockMvc;
  private CeremonyVocabulary savedVocabulary;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    // The live CeremonyVocabulary bean is shared across all integration tests AND
    // is hydrated from ~/.xmen/settings.json at startup, so a developer who has
    // ever switched profiles (e.g. to Bank) will silently break these tests by
    // classifying PasswordAttempt as a non-internal "core" action and dropping
    // SndS from the outbound-channel list. Snapshot the live state, then reset
    // to the built-in defaults so this fixture's SPTHY files (which use the
    // default H()/Forget()/SndS/State naming) are interpreted as intended.
    savedVocabulary = new CeremonyVocabulary();
    savedVocabulary.copyFrom(vocabulary);
    vocabulary.copyFrom(new CeremonyVocabulary());
    // The strategy keeps its own non-internal-actions override which persists
    // across requests once any earlier test has supplied a Witness-Actions
    // header. Clear it so this fixture sees a clean baseline.
    forgetMutationStrategy.setNonInternalActions(null);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (savedVocabulary != null) {
      vocabulary.copyFrom(savedVocabulary);
    }
    deleteIfExists("Forget_Neglect_NoInternal_M0.m");
    deleteIfExists("Forget_Neglect_Internal_M0.m");
    deleteIfExists("Forget_Neglect_Mixed_M0.m");
  }

  @Test
  @DisplayName("No internal action: Neglect is not triggered")
  void noInternalActionDoesNotTriggerNeglect() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "Forget_Neglect_NoInternal.spthy",
        MediaType.TEXT_PLAIN_VALUE,
        Files.readAllBytes(Paths.get("src/test/resources/Forget_Neglect_NoInternal.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file))
        .andExpect(status().isOk());

    String generated = readFile("Forget_Neglect_NoInternal_M0.m");
    assertThat(generated).contains("PasswordAttempt($User,p2)");
  }

  @Test
  @DisplayName("Internal action present: Neglect removes internal action")
  void internalActionIsRemoved() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "Forget_Neglect_Internal.spthy",
        MediaType.TEXT_PLAIN_VALUE,
        Files.readAllBytes(Paths.get("src/test/resources/Forget_Neglect_Internal.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file))
        .andExpect(status().isOk());

    String generated = readFile("Forget_Neglect_Internal_M0.m");
    assertThat(generated).doesNotContain("PasswordAttempt(");
  }

  @Test
  @DisplayName("Mixed: Neglect removes internal action and send is substituted")
  void mixedNeglectAndVariant() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "Forget_Neglect_Mixed.spthy",
        MediaType.TEXT_PLAIN_VALUE,
        Files.readAllBytes(Paths.get("src/test/resources/Forget_Neglect_Mixed.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file))
        .andExpect(status().isOk());

    String generated = readFile("Forget_Neglect_Mixed_M0.m");
    assertThat(generated).doesNotContain("PasswordAttempt(");
    assertThat(generated).contains("SndS($User,$Server,<'password'>,<p2>)");
  }

  private String readFile(String path) throws Exception {
    return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
  }

  private void deleteIfExists(String path) throws Exception {
    Path file = Paths.get(path);
    Files.deleteIfExists(file);
  }
}

