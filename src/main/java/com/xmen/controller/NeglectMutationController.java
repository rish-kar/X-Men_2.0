package com.xmen.controller;

import com.xmen.model.*;
import com.xmen.service.FileLoadingService;
import com.xmen.service.FileSplitterService;
import com.xmen.service.MutationGeneratorService;
import com.xmen.service.ZipService;
import com.xmen.utilities.TagSetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/** Controller for neglect mutation. */
@RestController
@RequestMapping("/api")
@Tag(name = "Neglect Mutations")
@Slf4j
public class NeglectMutationController {

  @Autowired private FileLoadingService fileLoadingService;
  @Autowired private TagSetter tagSetter;

  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  MutationGeneratorService mutationGeneratorService;

  @Autowired private FileSplitterService fileSplitterService;
  @Autowired private ZipService zipService;

  /**
   * Trigger of neglect mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate neglect mutations",
      description = "Creates neglect mutation variants from the uploaded SPTHY file.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Zipped mutation bundle",
        content =
            @Content(
                mediaType = "application/zip",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @PostMapping("/neglect/mutations")
  public ResponseEntity<?> neglectMutations(
      @Parameter(description = "SPTHY input file", required = true)
      @RequestParam("file") MultipartFile file)
      throws Exception {
    try {

      Set<Mutations> mutationSet = EnumSet.of(Mutations.NEGLECT);

      // Process file content
      String fileContent = new String(file.getBytes());
      FileSplitterService.FileSections sections = fileSplitterService.splitFile(fileContent);

      // Create virtual MultipartFile for rules section
      MultipartFile rulesFile =
          new InMemoryMultipartFile(
              "rulesFile",
              file.getOriginalFilename().replace(".spthy", "_rules.spthy"),
              "text/plain",
              sections.rules().getBytes(StandardCharsets.UTF_8));

      // Set tags and load rules
      ParametersBundle parametersBundle = new ParametersBundle();
      parametersBundle.setFlags(new Flags());
      parametersBundle = tagSetter.setTags(parametersBundle, mutationSet);
      parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);

      // Store file sections
      parametersBundle.addExtraContent("preamble", sections.preamble());
      parametersBundle.addExtraContent("postamble", sections.postamble());

      // Generate neglect mutations
      ArrayList<Rule> originalRules = parametersBundle.getCollections().get(0);
      parametersBundle.getCollections().clear();
      parametersBundle.setFileName(file.getOriginalFilename());

      mutationGeneratorService.generateMutation(originalRules, mutationSet, parametersBundle);

      // Extract base filename for ZIP creation
      String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
      return zipService.createZipResponse(baseFileName);
    } catch (IllegalArgumentException e) {
      log.error("Error generating neglect mutations: " + e.getMessage(), e);
      return ResponseEntity.status(400).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error generating neglect mutations: " + e.getMessage(), e);
      return ResponseEntity.status(500).body(e.getMessage());
    }
  }
}
