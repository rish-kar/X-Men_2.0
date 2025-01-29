package com.sermas.x.men.integration.tests;

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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class ReplaceSubmessagesTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    /**
     * Setup the test environment.
     */
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
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Oyster.spthy",
                MediaType.TEXT_PLAIN_VALUE,
                Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy"))
        );

        // Perform the request
        mockMvc.perform(multipart("/api/generateMutations")
                        .file(file)
                        .header("Replace-Sub-Messages", "true"))
                .andExpect(status().isOk());

        // Verify the generated files
        assertThat(new File("Oyster_M0.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_0.m"));
        assertThat(new File("Oyster_M1.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_1.m"));
        assertThat(new File("Oyster_M2.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_2.m"));
        assertThat(new File("Oyster_M3.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_3.m"));
        assertThat(new File("Oyster_M4.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_4.m"));
        assertThat(new File("Oyster_M5.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_5.m"));
        assertThat(new File("Oyster_M6.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_6.m"));
        assertThat(new File("Oyster_M7.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_7.m"));
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
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Oyster.spthy",
                MediaType.TEXT_PLAIN_VALUE,
                Files.readAllBytes(Paths.get("src/test/resources/Oyster.spthy"))
        );

        // Perform the request
        mockMvc.perform(multipart("/api/replace/subMessagesMutations")
                        .file(file))
                .andExpect(status().isOk());

        // Verify the generated files
        assertThat(new File("Oyster_M0.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_0.m"));
        assertThat(new File("Oyster_M1.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_1.m"));
        assertThat(new File("Oyster_M2.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_2.m"));
        assertThat(new File("Oyster_M3.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_3.m"));
        assertThat(new File("Oyster_M4.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_4.m"));
        assertThat(new File("Oyster_M5.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_5.m"));
        assertThat(new File("Oyster_M6.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_6.m"));
        assertThat(new File("Oyster_M7.m")).hasSameContentAs(new File("src/test/resources/ReplaceSubMessages_7.m"));
    }
}