package com.xmen.service;

import com.xmen.model.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Service to integrate with external Haskell derivation-tree microservice.
 * The Haskell service runs on port 9091 and provides derivation analysis.
 *
 * This service converts SPTHY protocol rules into Haskell-compatible format,
 * calls the external service, and formats the derivation tree response.
 */
@Service
@Slf4j
public class HaskellDerivationFetcher {

  private final WebClient webClient;

  @Autowired
  private HaskellFormatConverter haskellConverter;

  public HaskellDerivationFetcher(
      @Value("${derivation.service.url:http://localhost:9091}") String derivationServiceUrl) {
    this.webClient =
        WebClient.builder()
            .baseUrl(derivationServiceUrl)
            .defaultHeader("Content-Type", "text/plain")
            .build();
    log.info("HaskellDerivationFetcher initialized with base URL: {}", derivationServiceUrl);
  }

  /**
   * Analyzes protocol rules using Haskell derivation service.
   * Converts SPTHY rules to Haskell format, calls service, and returns formatted derivation tree.
   *
   * @param rules Parsed SPTHY protocol rules
   * @param theoryName Name of the protocol theory
   * @return Formatted derivation tree analysis
   */
  public String deriveAnalysisFromRules(ArrayList<Rule> rules, String theoryName) {
    log.info("Deriving analysis for theory '{}' with {} rules", theoryName, rules.size());

    try {
      // Convert SPTHY rules to Haskell-compatible format
      String haskellInput = haskellConverter.convertToHaskellFormat(rules, theoryName);
      log.debug("Converted rules to Haskell format (length: {})", haskellInput.length());

      // Call Haskell service with converted input
      String haskellResponse = callHaskellService(haskellInput);

      // Format the derivation tree response
      String formattedTree = haskellConverter.formatDerivationTree(haskellResponse);

      log.info("Derivation tree analysis completed successfully");
      return formattedTree;

    } catch (Exception e) {
      log.error("Error during derivation analysis: {}", e.getMessage(), e);
      return "Error: Unable to perform derivation analysis - " + e.getMessage();
    }
  }

  /**
   * Calls the external Haskell derivation service with the provided SPTHY model content.
   *
   * @param spthyContent The content of the .spthy file to analyze
   * @return Derivation tree analysis result as a String
   * @deprecated Use deriveAnalysisFromRules() for proper conversion
   */
  @Deprecated
  public String deriveAnalysis(String spthyContent) {
    log.debug("Calling derivation service with raw SPTHY content (length: {})", spthyContent.length());
    return callHaskellService(spthyContent);
  }


  /**
   * Internal method to call Haskell service HTTP endpoint.
   */
  private String callHaskellService(String content) {
    try {
      String result =
          webClient
              .post()
              .uri("/derive") // The Haskell service exposes a /derive endpoint
              .contentType(MediaType.TEXT_PLAIN)
              .bodyValue(content)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  response ->
                      response
                          .bodyToMono(String.class)
                          .flatMap(
                              error ->
                                  Mono.error(
                                      new RuntimeException(
                                          "Derivation service error: " + error))))
              .bodyToMono(String.class)
              .timeout(Duration.ofSeconds(30))
              .block();

      return result;

    } catch (Exception e) {
      log.error("Error calling derivation service: {}", e.getMessage(), e);
      throw new RuntimeException("Error calling Haskell derivation service", e);
    }
  }

  /**
   * Check if the derivation service is available.
   *
   * @return true if service responds, false otherwise
   */
  public boolean isServiceAvailable() {
    try {
      webClient
          .get()
          .uri("/health") // Assuming a health endpoint exists
          .retrieve()
          .bodyToMono(String.class)
          .timeout(Duration.ofSeconds(5))
          .block();
      return true;
    } catch (Exception e) {
      log.warn("Derivation service health check failed: {}", e.getMessage());
      return false;
    }
  }
}

