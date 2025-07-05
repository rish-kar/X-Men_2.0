package com.sermas.x.men.integrationTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
public class ForgetMutationTests {

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;

  @BeforeEach
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @AfterEach
  public void tearDown() throws Exception {
    // Delete generated files (adjust range/count as needed)
    Files.deleteIfExists(Paths.get("Oyster_M0.m"));
  }

  @Test
  @DisplayName("Test Forget Mutation - Multi Endpoint")
  public void testForgetMutationMultiEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster_Forget.spthy")));

    mockMvc
        .perform(multipart("/api/generateMutations").file(file).header("Forget-Mutation", "true"))
        .andExpect(status().isOk());

    // Compare output to reference files (you must create these)
    assertThat(Files.readString(Paths.get("Oyster_M0.m")).trim().replaceAll("\\r?\\n", "\n"))
        .contains(
            Files.readString(Paths.get("src/test/resources/ForgetMutation_0.m"))
                .trim()
                .replaceAll("\\r?\\n", "\n"));
    // Add more assertions for other output files if needed
  }

  @Test
  @DisplayName("Test Forget Mutation - Single Endpoint")
  public void testForgetMutationSingleEndpoint() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster_Forget.spthy")));

    mockMvc.perform(multipart("/api/forget/mutations").file(file)).andExpect(status().isOk());

    assertThat(Files.readString(Paths.get("Oyster_M0.m")).trim().replaceAll("\\r?\\n", "\n"))
        .contains(
            Files.readString(Paths.get("src/test/resources/ForgetMutation_0.m"))
                .trim()
                .replaceAll("\\r?\\n", "\n"));
    // Add more assertions for other output files if needed
  }
}
