package com.sermas.x.men.config;

import com.sermas.x.men.service.impl.SkipSendMutationStrategy;
import com.sermas.x.men.utilities.FileHandler;
import com.sermas.x.men.utilities.RulesModifier;
import com.sermas.x.men.utilities.UtilityFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Configuration class for the application. This class defines beans for various services and
 * utilities used in the application.
 */
@Configuration
public class AppConfig {

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
}
