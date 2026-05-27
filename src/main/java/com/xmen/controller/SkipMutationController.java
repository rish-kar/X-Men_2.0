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

/** Controller for skipping mutation. */
@RestController
@RequestMapping("/api/skip")
@Tag(name = "Skip Mutations")
public class SkipMutationController {

  @Autowired private FileLoadingService fileLoadingService;

  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  MutationGeneratorService mutationGeneratorService;

  @Autowired private FileSplitterService fileSplitterService;

  @Autowired private ZipService zipService;

  /**
   * Trigger skipping of send mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate skip send mutations",
      description = "Creates skip send mutation variants from the uploaded SPTHY file.")
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
  @PostMapping("/sendMutations")
  public ResponseEntity<?> skipSendMutation(
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
        rules, Collections.singleton(Mutations.SKIP_SEND), parametersBundle);

    // Extract base filename for ZIP creation
    String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
    return zipService.createZipResponse(baseFileName);
  }

  /**
   * Trigger skipping of receive mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate skip receive mutations",
      description = "Creates skip receive mutation variants from the uploaded SPTHY file.")
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
  @PostMapping("/receiveMutations")
  public ResponseEntity<?> skipReceiveMutation(
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
        rules, Collections.singleton(Mutations.SKIP_RECEIVE), parametersBundle);

    // Extract base filename for ZIP creation
    String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
    return zipService.createZipResponse(baseFileName);
  }

  /**
   * Trigger skipping of send receive mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate skip send-receive mutations",
      description = "Creates skip send-receive mutation variants from the uploaded SPTHY file.")
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
  @PostMapping("/sendReceiveMutations")
  public ResponseEntity<?> skipSendReceiveMutation(
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
        rules, Collections.singleton(Mutations.SKIP_SEND_RECEIVE), parametersBundle);

    // Extract base filename for ZIP creation
    String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
    return zipService.createZipResponse(baseFileName);
  }

  /**
   * Trigger skipping of receive send mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate skip receive-send mutations",
      description = "Creates skip receive-send mutation variants from the uploaded SPTHY file.")
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
  @PostMapping("/receiveSendMutations")
  public ResponseEntity<?> skipReceiveSendMutation(
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
        rules, Collections.singleton(Mutations.SKIP_RECEIVE_SEND), parametersBundle);

    // Extract base filename for ZIP creation
    String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
    return zipService.createZipResponse(baseFileName);
  }

  /**
   * Trigger skipping of receive send receive mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate skip receive-send-receive mutations",
      description = "Creates skip receive-send-receive mutation variants from the uploaded SPTHY file.")
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
  @PostMapping("/receiveSendReceiveMutations")
  public ResponseEntity<?> skipReceiveSendReceiveMutation(
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
        rules, Collections.singleton(Mutations.SKIP_RECEIVE_SEND_RECEIVE), parametersBundle);

    // Extract base filename for ZIP creation
    String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
    return zipService.createZipResponse(baseFileName);
  }
}
