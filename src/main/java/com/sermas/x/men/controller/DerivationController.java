package com.sermas.x.men.controller;

import com.sermas.x.men.service.DerivationTreeService;
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
@Slf4j
public class DerivationController {

  @Autowired private DerivationTreeService derivationTreeService;

  /**
   * Analyzes a SPTHY file and returns the derivation tree from the Haskell service.
   *
   * @param file The SPTHY file to analyze
   * @return Derivation tree analysis result
   */
  @PostMapping(value = "/derive", produces = MediaType.TEXT_PLAIN_VALUE)
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
      if (!derivationTreeService.isServiceAvailable()) {
        log.warn("Derivation service is not available");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                "Derivation service is currently unavailable. "
                    + "Please ensure the Haskell service is running on port 9091.");
      }

      // Call the derivation service
      String result = derivationTreeService.deriveAnalysis(spthyContent);

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
   * @return Service status
   */
  @GetMapping("/derive/health")
  public ResponseEntity<String> checkDerivationServiceHealth() {
    boolean available = derivationTreeService.isServiceAvailable();
    if (available) {
      return ResponseEntity.ok("Derivation service is available");
    } else {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("Derivation service is unavailable");
    }
  }
}

