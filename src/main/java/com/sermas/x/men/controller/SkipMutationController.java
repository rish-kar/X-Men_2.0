package com.sermas.x.men.controller;

import com.sermas.x.men.model.InMemoryMultipartFile;
import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.FileLoadingService;
import com.sermas.x.men.service.FileSplitterService;
import com.sermas.x.men.service.MutationGeneratorService;
import com.sermas.x.men.service.ZipService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Controller for skipping mutation. */
@RestController
@RequestMapping("/api/skip")
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
  @PostMapping("/sendMutations")
  public ResponseEntity<?> skipSendMutation(@RequestParam("file") MultipartFile file)
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
  @PostMapping("/receiveMutations")
  public ResponseEntity<?> skipReceiveMutation(@RequestParam("file") MultipartFile file)
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
  @PostMapping("/sendReceiveMutations")
  public ResponseEntity<?> skipSendReceiveMutation(@RequestParam("file") MultipartFile file)
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
  @PostMapping("/receiveSendMutations")
  public ResponseEntity<?> skipReceiveSendMutation(@RequestParam("file") MultipartFile file)
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
  @PostMapping("/receiveSendReceiveMutations")
  public ResponseEntity<?> skipReceiveSendReceiveMutation(@RequestParam("file") MultipartFile file)
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
