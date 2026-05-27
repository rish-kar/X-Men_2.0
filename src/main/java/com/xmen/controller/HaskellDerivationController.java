package com.xmen.controller;

import com.xmen.service.HaskellDerivationFetcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Controller for derivation tree analysis using external Haskell microservice.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Derivation")
@Slf4j
public class HaskellDerivationController {

  @Autowired private HaskellDerivationFetcher haskellDerivationFetcher;

  /**
   * Analyzes a SPTHY file and returns the derivation tree from the Haskell service.
   *
   * @param file The SPTHY file to analyze
   * @return Derivation tree analysis result
   */
  @Operation(
      summary = "Analyze derivation tree",
      description =
          "Sends the SPTHY file to the Haskell derivation service and returns the tree output.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Derivation tree output",
        content = @Content(mediaType = "text/plain")),
    @ApiResponse(responseCode = "400", description = "Invalid file or input"),
    @ApiResponse(responseCode = "503", description = "Derivation service unavailable"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @PostMapping(value = "/derive", produces = MediaType.TEXT_PLAIN_VALUE)
  @SuppressWarnings("deprecation") // calls deriveAnalysis(String) intentionally; migration to deriveAnalysisFromRules tracked separately
  public ResponseEntity<String> deriveAnalysis(
      @Parameter(description = "SPTHY input file", required = true)
      @RequestParam("file") MultipartFile file) {
    try {
      // Validate file
      if (file == null || file.isEmpty()) {
        return ResponseEntity.badRequest().body("File is required and cannot be empty");
      }

      String filename = file.getOriginalFilename();
      if (filename == null || !filename.endsWith(".spthy")) {
        return ResponseEntity.badRequest()
            .body("Invalid file extension. Only .spthy files are supported");
      }

      // Read file content
      String spthyContent = new String(file.getBytes(), StandardCharsets.UTF_8);
      log.info("Received derivation request for file: {}", filename);

      // Check if derivation service is available
      if (!haskellDerivationFetcher.isServiceAvailable()) {
        log.warn("Derivation service is not available");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                "Derivation service is currently unavailable. "
                    + "Please ensure the Haskell service is running on port 9091.");
      }

      // Call the derivation service
      String result = haskellDerivationFetcher.deriveAnalysis(spthyContent);

      return ResponseEntity.ok(result);

    } catch (IllegalArgumentException e) {
      log.error("Invalid argument: {}", e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      log.error("Error processing derivation request: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error: " + e.getMessage());
    }
  }

  /**
   * Health check for the derivation service.
   *
   * @return Service status of Haskell Service
   */
  @Operation(summary = "Check derivation service health")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Derivation service is available"),
    @ApiResponse(responseCode = "503", description = "Derivation service is unavailable")
  })
  @GetMapping("/derive/health")
  public ResponseEntity<String> checkDerivationServiceHealth() {
    boolean available = haskellDerivationFetcher.isServiceAvailable();
    if (available) {
      return ResponseEntity.ok("Derivation service is available");
    } else {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("Derivation service is unavailable");
    }
  }
}
