package com.sermas.x.men.controller;

import com.sermas.x.men.service.HaskellDerivationFetcher;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for derivation tree analysis using external Haskell microservice.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class HaskellDerivationController {

  @Autowired private HaskellDerivationFetcher haskellDerivationFetcher;

  /**
   * Analyzes a SPTHY file and returns the derivation tree from the Haskell service.
   *
   * @param file The SPTHY file to analyze
   * @return Derivation tree analysis result
   */
  @PostMapping(value = "/derive", produces = MediaType.TEXT_PLAIN_VALUE)
  @SuppressWarnings("deprecation") // calls deriveAnalysis(String) intentionally; migration to deriveAnalysisFromRules tracked separately
  public ResponseEntity<String> deriveAnalysis(@RequestParam("file") MultipartFile file) {
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
