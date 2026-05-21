package com.sermas.x.men.integrationTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Forget -> Neglect Trigger Tests")
class ForgetNeglectTriggerTest {

  @Autowired
  private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @AfterEach
  void tearDown() throws Exception {
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

