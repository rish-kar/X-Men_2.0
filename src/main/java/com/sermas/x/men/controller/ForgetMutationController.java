package com.sermas.x.men.controller;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.*;
import com.sermas.x.men.utilities.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Controller for forget mutation. */
@RestController
@RequestMapping("/api")
@Slf4j
public class ForgetMutationController {

  @Autowired private FileLoadingService fileLoadingService;
  @Autowired private TagSetter tagSetter;
  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  MutationGeneratorService mutationGeneratorService;
  @Autowired private FileSplitterService fileSplitterService;
  @Autowired private SetupKnowledgeExtractor setupKnowledgeExtractor;
  @Autowired private ZipService zipService;

  /**
   * Trigger of forget mutation.
   *
   * @param file The file to process.
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @PostMapping("/forget/mutations")
  public ResponseEntity<?> forgetMutations(@RequestParam("file") MultipartFile file)
      throws Exception {
    try {
      // Only FORGET mutation
      Set<Mutations> mutationSet = EnumSet.of(Mutations.FORGET);

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

      // Parse forget mutations and set flag (mirror MutationController behavior)
      ArrayList<Rule> originalRules = parametersBundle.getCollections().get(0);
      // Extract setup knowledge to support propagation where needed
      parametersBundle.getFlags().setTrueReplace(true);
      Map<String, String> setupKnowledgeValues =
          setupKnowledgeExtractor.processProtocolModel(originalRules);
      parametersBundle.setExistingSetupKnowledge(setupKnowledgeValues);
      parametersBundle = ForgetMutationParser.parseForgetMutations(originalRules, parametersBundle);
      parametersBundle.getFlags().setForgetMutation(true);

      parametersBundle.getCollections().clear();
      parametersBundle.setFileName(file.getOriginalFilename());

      mutationGeneratorService.generateMutation(originalRules, mutationSet, parametersBundle);

      // Extract base filename for ZIP creation
      String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
      return zipService.createZipResponse(baseFileName);
    } catch (IllegalArgumentException e) {
      log.error("Error generating forget mutations: " + e.getMessage(), e);
      return ResponseEntity.status(400).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error generating forget mutations: " + e.getMessage(), e);
      return ResponseEntity.status(500).body(e.getMessage());
    }
  }
}