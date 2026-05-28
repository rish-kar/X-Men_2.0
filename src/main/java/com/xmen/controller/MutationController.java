package com.xmen.controller;

import com.xmen.model.*;
import com.xmen.service.*;
import com.xmen.service.impl.DerivationModeContext;
import com.xmen.utilities.ForgetMutationParser;
import com.xmen.utilities.SetupKnowledgeExtractor;
import com.xmen.utilities.TagSetter;
import com.xmen.utilities.UtilityFunctions;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Controller for mutation generation. */
@RestController
@RequestMapping("/api")
@Tag(name = "Mutations")
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

  @Autowired private HaskellDerivationFetcher haskellDerivationFetcher;

  @Autowired private DerivationTreeCaptureService derivationTreeCaptureService;

  @Autowired private com.xmen.service.forget.ForgetDerivationChecker forgetDerivationChecker;

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
  @Operation(
      summary = "Generate mutations from a SPTHY file",
      description =
          "Generates a zipped bundle of mutation variants based on header flags. "
              + "Use headers to select skip, replace, forget, or neglect mutations. "
              + "Optionally enable Haskell derivation and set derivation type/depth.")
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
  @PostMapping("/generateMutations")
  public ResponseEntity<?> generateMutations(
      @Parameter(description = "Enable skip send mutation")
      @RequestHeader(value = "Skip-Send", required = false) Boolean skipSend,
      @Parameter(description = "Enable skip receive mutation")
      @RequestHeader(value = "Skip-Receive", required = false) Boolean skipReceive,
      @Parameter(description = "Enable skip send-receive mutation")
      @RequestHeader(value = "Skip-Send-Receive", required = false) Boolean skipSendReceive,
      @Parameter(description = "Enable skip receive-send mutation")
      @RequestHeader(value = "Skip-Receive-Send", required = false) Boolean skipReceiveSend,
      @Parameter(description = "Enable skip receive-send-receive mutation")
      @RequestHeader(value = "Skip-Receive-Send-Receive", required = false)
          Boolean skipReceiveSendReceive,
      @Parameter(description = "Enable add mutation")
      @RequestHeader(value = "Add-Mutation", required = false) Boolean addMutation,
      @Parameter(description = "Enable replace sub-messages mutation")
      @RequestHeader(value = "Replace-Sub-Messages", required = false) Boolean replaceSubMessages,
      @Parameter(description = "Enable replace type mutation")
      @RequestHeader(value = "Replace-Type", required = false) Boolean replaceType,
      @Parameter(description = "Enable true replacement (random replacement from setup knowledge)")
      @RequestHeader(value = "True-Replace", required = false) Boolean trueReplace,
      @Parameter(description = "Enable forget mutation")
      @RequestHeader(value = "Forget-Mutation", required = false) Boolean forgetMutation,
      @Parameter(description = "Enable neglect mutation")
      @RequestHeader(value = "Neglect-Mutation", required = false) Boolean neglectMutation,
      @Parameter(description = "Enable Haskell derivation for forget mutations if available")
      @RequestHeader(value = "Haskell-Activate", required = false) Boolean haskellActivate,
      @Parameter(description = "Derivation type (e.g., DEPTH_SPECIFIED)")
      @RequestHeader(value = "Derivation-Type", required = false) String derivationType,
      @Parameter(description = "Derivation depth when derivation type requires it")
      @RequestHeader(value = "Derivation-Depth", required = false) Integer derivationDepth,
      @Parameter(description = "SPTHY input file", required = true)
      @RequestParam("file") MultipartFile file)
      throws Exception {

    Map<String, String> setupKnowledgeValues;
    boolean haskellWasEnabled = false;
    String derivationTreeContent = null;

    try {
      // Start capturing derivation tree output
      derivationTreeCaptureService.startCapture();

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

      // If Haskell-Activate header is true and Forget mutation is requested, enable Haskell
      // derivation
      if (Boolean.TRUE.equals(haskellActivate) && Boolean.TRUE.equals(forgetMutation)) {
        log.info(
            "Haskell-Activate header detected with Forget mutation, "
                + "enabling HybridDerivationService");

        if (!haskellDerivationFetcher.isServiceAvailable()) {
          log.warn("Haskell service requested but unavailable, using Java derivation");
        } else {
          try {
            String theoryName = file.getOriginalFilename().replace(".spthy", "");
            com.xmen.service.impl.HybridDerivationService.enableHaskellDerivation(
                originalRules, theoryName);
            DerivationModeContext.enableHaskell();
            haskellWasEnabled = true;
          } catch (Exception e) {
            log.error("Error enabling Haskell derivation: {}", e.getMessage());
            com.xmen.service.impl.HybridDerivationService.disableHaskellDerivation();
            DerivationModeContext.disableHaskell();
          }
        }
      } else {
        com.xmen.service.impl.HybridDerivationService.disableHaskellDerivation();
        DerivationModeContext.disableHaskell();
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
            ForgetMutationParser.parseForgetMutations(originalRules, parametersBundle, fileContent);
        parametersBundle.getFlags().setForgetMutation(true);
      }

      // If derivation type/depth are specified, set them on the parameters bundle
      parametersBundle.setDerivationType(derivationType);
      if (DerivationType.DEPTH_SPECIFIED.name().equals(derivationType) && derivationDepth != null) {
        parametersBundle.setDerivationDepth(derivationDepth);
      }

      // Mirror Derivation-Type into the Forget pipeline so an "Infinite" or
      // "Specified Depth" selection actually lifts the ForgetDerivationChecker
      // caps (default depth=10 / max=50). Only meaningful when Forget is in
      // the requested mutation set, but it's harmless to set unconditionally.
      if (DerivationType.INFINITE.name().equalsIgnoreCase(derivationType)) {
        forgetDerivationChecker.overrideLimits(Integer.MAX_VALUE, Integer.MAX_VALUE);
      } else if (DerivationType.DEPTH_SPECIFIED.name().equalsIgnoreCase(derivationType)
          && derivationDepth != null
          && derivationDepth > 0) {
        forgetDerivationChecker.overrideLimits(derivationDepth, Integer.MAX_VALUE);
      }

      parametersBundle.getCollections().clear();
      parametersBundle.setFileName(file.getOriginalFilename());

      mutationGeneratorService.generateMutation(originalRules, mutationSet, parametersBundle);

      // Stop capturing and get derivation tree content
      derivationTreeContent = derivationTreeCaptureService.stopCaptureAndGet();
      if (derivationTreeContent != null) {
        log.info("Captured derivation tree output ({} characters)", derivationTreeContent.length());
      }

      // Extract base filename for ZIP creation
      String baseFileName = file.getOriginalFilename().split("\\.(?=[^\\.]+$)")[0];
      return zipService.createZipResponse(baseFileName, derivationTreeContent);
    } catch (IllegalArgumentException e) {
      log.error("Error generating mutations: " + e.getMessage(), e);
      return ResponseEntity.status(400).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error generating mutations: " + e.getMessage(), e);
      return ResponseEntity.status(500).body(e.getMessage());
    } finally {
      // Always disable Haskell derivation after processing
      if (haskellWasEnabled) {
        com.xmen.service.impl.HybridDerivationService.disableHaskellDerivation();
        DerivationModeContext.disableHaskell();
        log.info("Haskell derivation DISABLED after mutation processing");
      }
      // Restore the Forget pipeline's default caps so the next request isn't
      // accidentally still in INFINITE mode if this one tripped that path.
      forgetDerivationChecker.resetLimits();
      // Clear any remaining capture state
      derivationTreeCaptureService.clearCapture();
    }
  }
}
