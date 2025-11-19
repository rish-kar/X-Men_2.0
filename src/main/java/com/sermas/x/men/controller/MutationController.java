package com.sermas.x.men.controller;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.*;
import com.sermas.x.men.utilities.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Controller for mutation generation. */
@RestController
@RequestMapping("/api")
@Slf4j
public class MutationController {

  @Autowired public FileLoadingService fileLoadingService;

  @Autowired public TagSetter tagSetter;

  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  public MutationGeneratorService mutationGeneratorService;

  @Autowired private FileSplitterService fileSplitterService;

  @Autowired private UtilityFunctions utilityFunctions;

  @Autowired private SetupKnowledgeExtractor setupKnowledgeExtractor;

  @Autowired private ZipService zipService;

  @Autowired private DerivationTreeService derivationTreeService;

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
      @RequestHeader(value = "True-Replace", required = false) Boolean trueReplace,
      @RequestHeader(value = "Forget-Mutation", required = false) Boolean forgetMutation,
      @RequestHeader(value = "Neglect-Mutation", required = false) Boolean neglectMutation,
      @RequestHeader(value = "Haskell-Activate", required = false) Boolean haskellActivate,
      @RequestParam("file") MultipartFile file)
      throws Exception {

    Map<String, String> setupKnowledgeValues;
    boolean haskellWasEnabled = false;

    try {
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
        if (Boolean.TRUE.equals(forgetMutation)) {
            mutationSet.add(Mutations.FORGET);
        }
        if (Boolean.TRUE.equals(neglectMutation)) {
            mutationSet.add(Mutations.NEGLECT);
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
      parametersBundle.setFlags(new Flags());
      parametersBundle = tagSetter.setTags(parametersBundle, mutationSet);
      parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);

      // Store file sections
      parametersBundle.addExtraContent("preamble", sections.preamble());
      parametersBundle.addExtraContent("postamble", sections.postamble());

      // Generate mutations
      ArrayList<Rule> originalRules = parametersBundle.getCollections().get(0);

      // If Haskell-Activate header is true and Forget mutation is requested, enable Haskell derivation
      if (Boolean.TRUE.equals(haskellActivate) && Boolean.TRUE.equals(forgetMutation)) {
        log.info("Haskell-Activate header detected with Forget mutation, enabling Haskell derivation service");

        // Check if Haskell service is available
        if (!derivationTreeService.isServiceAvailable()) {
          log.warn("Haskell service requested but unavailable, using Java derivation");
        } else {
          try {
            // Extract theory name from filename
            String theoryName = file.getOriginalFilename().replace(".spthy", "");

            // Enable Haskell derivation in HybridDerivationService
            com.sermas.x.men.service.impl.HybridDerivationService.enableHaskellDerivation(
                originalRules, theoryName);

            haskellWasEnabled = true;

            log.info("Haskell derivation ENABLED for theory: {}", theoryName);
            log.info("Forget mutation will use Haskell service for derivability checks");

          } catch (Exception e) {
            log.error("Error enabling Haskell derivation: {}", e.getMessage());
          }
        }
      }

      // Continue with standard mutation processing
      // Only set flag if trueReplace header is available for random replacement for a similar value
      // from the knowledge
      // If true replacement is needed, then extract values from the setup knowledge
      if (trueReplace != null
          && trueReplace
          && mutationSet.contains(Mutations.REPLACE_SUB_MESSAGES)) {
        parametersBundle.getFlags().setTrueReplace(true);
        setupKnowledgeValues = setupKnowledgeExtractor.processProtocolModel(originalRules);
        parametersBundle.setExistingSetupKnowledge(setupKnowledgeValues);
      }

      if (forgetMutation != null && forgetMutation) {
        parametersBundle.getFlags().setTrueReplace(true);
        setupKnowledgeValues = setupKnowledgeExtractor.processProtocolModel(originalRules);
        parametersBundle.setExistingSetupKnowledge(setupKnowledgeValues);
        parametersBundle =
            ForgetMutationParser.parseForgetMutations(originalRules, parametersBundle);
        parametersBundle.getFlags().setForgetMutation(true);
      }

      parametersBundle.getCollections().clear();
      parametersBundle.setFileName(file.getOriginalFilename());

      mutationGeneratorService.generateMutation(originalRules, mutationSet, parametersBundle);

      // Extract base filename for ZIP creation
      String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
      return zipService.createZipResponse(baseFileName);
    } catch (IllegalArgumentException e) {
      log.error("Error generating mutations: " + e.getMessage(), e);
      return ResponseEntity.status(400).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error generating mutations: " + e.getMessage(), e);
      return ResponseEntity.status(500).body(e.getMessage());
    } finally {
      // Always disable Haskell derivation after processing
      if (haskellWasEnabled) {
        com.sermas.x.men.service.impl.HybridDerivationService.disableHaskellDerivation();
        log.info("Haskell derivation DISABLED after mutation processing");
      }
    }
  }
}
