package com.xmen.controller;

import com.xmen.model.InMemoryMultipartFile;
import com.xmen.model.Mutations;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import com.xmen.service.FileLoadingService;
import com.xmen.service.FileSplitterService;
import com.xmen.service.MutationGeneratorService;
import com.xmen.service.ZipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Collections;

/** Controller for add mutation. */
@RestController
@RequestMapping("/api")
@Tag(name = "Add Mutations")
public class AddMutationController {

  @Autowired private FileLoadingService fileLoadingService;

  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  MutationGeneratorService mutationGeneratorService;

  @Autowired private FileSplitterService fileSplitterService;

  @Autowired private ZipService zipService;

  /**
   * Trigger of add mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate add mutations",
      description = "Creates add-rule mutation variants from the uploaded SPTHY file.")
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
  @PostMapping("/addMutations")
  public ResponseEntity<?> addMutations(
      @Parameter(description = "SPTHY input file", required = true)
      @RequestParam("file") MultipartFile file)
      throws Exception {

    ParametersBundle parametersBundle = new ParametersBundle();

    // Process file content
    String fileContent = new String(file.getBytes());
    FileSplitterService.FileSections sections = fileSplitterService.splitFile(fileContent);

    // Create virtual MultipartFile for rules section
    MultipartFile rulesFile =
        new InMemoryMultipartFile(
            "rulesFile",
            file.getOriginalFilename().replace(".spthy", "_rules.spthy"), // Preserve extension
            "text/plain",
            sections.rules().getBytes(StandardCharsets.UTF_8));

    parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);
    ArrayList<Rule> rules = parametersBundle.getCollections().get(0);
    parametersBundle.getCollections().clear();
    parametersBundle.setFileName(file.getOriginalFilename());

    mutationGeneratorService.generateMutation(
        rules, Collections.singleton(Mutations.ADD), parametersBundle);

    // Extract base filename for ZIP creation
    String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
    return zipService.createZipResponse(baseFileName);
  }
}
