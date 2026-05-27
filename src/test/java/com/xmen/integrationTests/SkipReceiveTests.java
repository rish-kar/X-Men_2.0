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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class SkipReceiveTests {

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
  }

  /**
   * Test Skip Receive Mutation - Multi Endpoint.
   *
   * @throws Exception Exception Object
   */
  @Test
  @DisplayName("Test Skip Receive Mutation - Multi Endpoint")
  public void testSkipReceiveMultiEndpoint() throws Exception {
    // Prepare the MultipartFile
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy")));

    // Perform the request
    mockMvc
        .perform(multipart("/api/generateMutations").file(file).header("Skip-Receive", "true"))
        .andExpect(status().isOk());

    assertThat(Files.readString(Paths.get("Oyster_M0.m")).trim().replaceAll("\\r?\\n", "\n"))
        .contains(
            Files.readString(Paths.get("src/test/resources/SkipReceive_0.m"))
                .trim()
                .replaceAll("\\r?\\n", "\n"));
    assertThat(Files.readString(Paths.get("Oyster_M1.m")).trim().replaceAll("\\r?\\n", "\n"))
        .contains(
            Files.readString(Paths.get("src/test/resources/SkipReceive_1.m"))
                .trim()
                .replaceAll("\\r?\\n", "\n"));
  }

  /**
   * Test Skip Receive Mutation - Single Endpoint.
   *
   * @throws Exception Exception Object
   */
  @Test
  @DisplayName("Test Skip Receive Mutation - Single Endpoint")
  public void testSkipReceiveSingleEndpoint() throws Exception {
    // Prepare the MultipartFile
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Oyster.spthy",
            MediaType.TEXT_PLAIN_VALUE,
            Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy")));

    // Perform the request
    mockMvc.perform(multipart("/api/skip/receiveMutations").file(file)).andExpect(status().isOk());

    assertThat(Files.readString(Paths.get("Oyster_M0.m")).trim().replaceAll("\\r?\\n", "\n"))
        .contains(
            Files.readString(Paths.get("src/test/resources/SkipReceive_0.m"))
                .trim()
                .replaceAll("\\r?\\n", "\n"));
    assertThat(Files.readString(Paths.get("Oyster_M1.m")).trim().replaceAll("\\r?\\n", "\n"))
        .contains(
            Files.readString(Paths.get("src/test/resources/SkipReceive_1.m"))
                .trim()
                .replaceAll("\\r?\\n", "\n"));
  }
}
