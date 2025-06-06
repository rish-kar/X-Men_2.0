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
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Controller for mutation generation. */
@RestController
@RequestMapping("/api")
public class MutationController {

  @Autowired public FileLoadingService fileLoadingService;

  @Autowired public TagSetter tagSetter;

  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  public MutationGeneratorService mutationGeneratorService;

  @Autowired private FileSplitterService fileSplitterService;

  /**
   * Generates mutations based on the provided file and mutation options.
   *
   * @param skipSend Skip sending messages
   * @param skipReceive Skip receiving messages
   * @param skipSendReceive Skip both sending and receiving messages
   * @param skipReceiveSend Skip receiving and then sending messages
   * @param skipReceiveSendReceive Skip receiving, sending, and then receiving messages
   * @param addMutation Add a mutation to the rules
   * @param replaceSubMessages Replace sub-messages in the rules
   * @param replaceType Replace the type of messages in the rules
   * @param file The input file containing rules in .spthy format
   * @return ResponseEntity indicating success or failure
   * @throws Exception if an error occurs during processing
   */
  @PostMapping("/generateMutations")
  public ResponseEntity<?> generateMutations(
      @RequestHeader(value = "Skip-Send", required = false) Boolean skipSend,
      @RequestHeader(value = "Skip-Receive", required = false) Boolean skipReceive,
      @RequestHeader(value = "Skip-Send-Receive", required = false) Boolean skipSendReceive,
      @RequestHeader(value = "Skip-Receive-Send", required = false) Boolean skipReceiveSend,
      @RequestHeader(value = "Skip-Receive-Send-Receive", required = false)
          Boolean skipReceiveSendReceive,
      @RequestHeader(value = "Add-Mutation", required = false) Boolean addMutation,
      @RequestHeader(value = "Replace-Sub-Messages", required = false) Boolean replaceSubMessages,
      @RequestHeader(value = "Replace-Type", required = false) Boolean replaceType,
      @RequestParam("file") MultipartFile file)
      throws Exception {

    // Create mutation set from headers
    Set<Mutations> mutationSet = EnumSet.noneOf(Mutations.class);
    if (Boolean.TRUE.equals(skipSend)) {
      mutationSet.add(Mutations.SKIP_SEND);
    }
    if (Boolean.TRUE.equals(skipReceive)) {
      mutationSet.add(Mutations.SKIP_RECEIVE);
    }
    if (Boolean.TRUE.equals(skipSendReceive)) {
      mutationSet.add(Mutations.SKIP_SEND_RECEIVE);
    }
    if (Boolean.TRUE.equals(skipReceiveSend)) {
      mutationSet.add(Mutations.SKIP_RECEIVE_SEND);
    }
    if (Boolean.TRUE.equals(skipReceiveSendReceive)) {
      mutationSet.add(Mutations.SKIP_RECEIVE_SEND_RECEIVE);
    }
    if (Boolean.TRUE.equals(addMutation)) {
      mutationSet.add(Mutations.ADD);
    }
    if (Boolean.TRUE.equals(replaceSubMessages)) {
      mutationSet.add(Mutations.REPLACE_SUB_MESSAGES);
    }
    if (Boolean.TRUE.equals(replaceType)) {
      mutationSet.add(Mutations.REPLACE_TYPE);
    }

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

    // Set tags and load rules
    ParametersBundle parametersBundle = new ParametersBundle();
    parametersBundle = tagSetter.setTags(parametersBundle, mutationSet);
    parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);

    // Store file sections
    parametersBundle.addExtraContent("preamble", sections.preamble());
    parametersBundle.addExtraContent("postamble", sections.postamble());

    // Generate mutations
    ArrayList<Rule> originalRules = parametersBundle.getCollections().get(0);
    parametersBundle.getCollections().clear();
    parametersBundle.setFileName(file.getOriginalFilename());

    mutationGeneratorService.generateMutation(originalRules, mutationSet, parametersBundle);

    return ResponseEntity.ok("Files generated successfully");
  }
}
