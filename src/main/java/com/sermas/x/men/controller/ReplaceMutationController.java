package com.sermas.x.men.controller;

import com.sermas.x.men.model.InMemoryMultipartFile;
import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.FileLoadingService;
import com.sermas.x.men.service.FileSplitterService;
import com.sermas.x.men.service.MutationGeneratorService;
import com.sermas.x.men.utilities.TagSetter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Controller for replace mutations. */
@RestController
@RequestMapping("/api/replace")
public class ReplaceMutationController {

  @Autowired private FileLoadingService fileLoadingService;

  @Autowired public TagSetter tagSetter;

  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  MutationGeneratorService mutationGeneratorService;

  @Autowired private FileSplitterService fileSplitterService;

  /**
   * Trigger replacing of sub messages mutation.
   *
   * @param file The file to process.
   * @return A message indicating the result of the file processing.
   */
  @PostMapping("/subMessagesMutations")
  public ResponseEntity<?> replaceSubMessagesMutations(@RequestParam("file") MultipartFile file)
      throws Exception {

    Set<Mutations> mutationSet = EnumSet.noneOf(Mutations.class);
    mutationSet.add(Mutations.REPLACE_SUB_MESSAGES);

    ParametersBundle parametersBundle = new ParametersBundle();
    parametersBundle.setFlags(new com.sermas.x.men.model.Flags());

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

    // Set tags based on the mutation set
    parametersBundle = tagSetter.setTags(parametersBundle, mutationSet);

    // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
    parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);
    ArrayList<Rule> rules = parametersBundle.getCollections().get(0);
    parametersBundle.getCollections().clear();
    parametersBundle.setFileName(file.getOriginalFilename());

    mutationGeneratorService.generateMutation(
        rules, Collections.singleton(Mutations.REPLACE_SUB_MESSAGES), parametersBundle);

    return ResponseEntity.ok("Files generated successfully");
  }

  /**
   * Trigger replacing of type mutation.
   *
   * @param file The file to process.
   * @return A message indicating the result of the file processing.
   */
  @PostMapping("/typeMutations")
  public ResponseEntity<?> replaceTypeMutations(@RequestParam("file") MultipartFile file)
      throws Exception {

    Set<Mutations> mutationSet = EnumSet.noneOf(Mutations.class);
    mutationSet.add(Mutations.REPLACE_TYPE);

    ParametersBundle parametersBundle = new ParametersBundle();
    parametersBundle.setFlags(new com.sermas.x.men.model.Flags());

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

    // Set tags based on the mutation set
    parametersBundle = tagSetter.setTags(parametersBundle, mutationSet);

    // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
    parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);
    ArrayList<Rule> rules = parametersBundle.getCollections().get(0);
    parametersBundle.getCollections().clear();
    parametersBundle.setFileName(file.getOriginalFilename());

    mutationGeneratorService.generateMutation(
        rules, Collections.singleton(Mutations.REPLACE_TYPE), parametersBundle);

    return ResponseEntity.ok("Files generated successfully");
  }
}
