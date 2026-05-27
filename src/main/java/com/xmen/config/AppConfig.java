package com.xmen.config;

import com.xmen.service.impl.SkipSendMutationStrategy;
import com.xmen.utilities.FileHandler;
import com.xmen.utilities.RulesModifier;
import com.xmen.utilities.UtilityFunctions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Random;

/**
 * Configuration class for the application. This class defines beans for various services and
 * utilities used in the application.
 */
@Configuration
public class AppConfig {

  // CORS basically whitelist domains to ensure security when the application is deployed.
  @Value("${app.cors.allowed-origins:}")
  private String corsAllowedOrigins; // Comma-separated origins from configuration


  /**
   * Bean for FileHandler. Ensures proper initialization and avoids any dependency injection issues.
   */
  @Bean
  public FileHandler fileHandler() {
    return new FileHandler();
  }


  /**
   * Bean for UtilityFunctions. Injects the FileHandler dependency to ensure UtilityFunctions is
   * properly configured.
   */
  @Bean
  public UtilityFunctions utilityFunctions(FileHandler fileHandler) {
    return new UtilityFunctions(fileHandler);
  }


  /** Bean for RulesModifier. No dependencies required for this bean as per the provided code. */
  @Bean
  public RulesModifier rulesModifier() {
    return new RulesModifier();
  }


  /**
   * Bean for SkipSendMutationStrategy. Injects UtilityFunctions and RulesModifier to ensure all
   * dependencies are properly wired.
   */
  @Bean
  public SkipSendMutationStrategy skipSendMutationStrategy(
      UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
    return new SkipSendMutationStrategy(utilityFunctions, rulesModifier);
  }


  /**
   * Bean for Random. Provides a random number generator instance that can be used throughout the
   * application.
   *
   * @return a new instance of Random
   */
  @Bean
  public Random random() {
    return new Random();
  }


  /**
   * CORS configuration to allow all types of requests from localhost ports 8082 and higher.
   * We replace the values in the environment variables when the application is deployed with the
   * actual front-end URL.
   * This enables cross-origin requests for development and testing purposes.
   */
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(@NonNull CorsRegistry registry) {
        // Parse configured origins; if none provided, use legacy defaults
        String[] configured =
            Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry
            .addMapping("/api/**")
            .allowedOrigins(configured)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders(
                "Content-Type",
                "Accept",
                "Origin",
                "Authorization",
                "Skip-Send",
                "Skip-Receive",
                "Skip-Send-Receive",
                "Skip-Receive-Send",
                "Skip-Receive-Send-Receive",
                "Add-Mutation",
                "Replace-Sub-Messages",
                "Replace-Type",
                "True-Replace",
                "Forget-Mutation")
            .exposedHeaders("Content-Disposition")
            .allowCredentials(true)
            .maxAge(3600);
      }
    };
  }
}
