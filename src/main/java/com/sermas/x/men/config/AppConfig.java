package com.sermas.x.men.config;

import com.sermas.x.men.service.impl.SkipSendMutationStrategy;
import com.sermas.x.men.utilities.FileHandler;
import com.sermas.x.men.utilities.RulesModifier;
import com.sermas.x.men.utilities.UtilityFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Bean for FileHandler.
     * Ensures proper initialization and avoids any dependency injection issues.
     */
    @Bean
    public FileHandler fileHandler() {
        return new FileHandler();
    }

    /**
     * Bean for UtilityFunctions.
     * Injects the FileHandler dependency to ensure UtilityFunctions is properly configured.
     */
    @Bean
    public UtilityFunctions utilityFunctions(FileHandler fileHandler) {
        return new UtilityFunctions(fileHandler);
    }

    /**
     * Bean for RulesModifier.
     * No dependencies required for this bean as per the provided code.
     */
    @Bean
    public RulesModifier rulesModifier() {
        return new RulesModifier();
    }

    /**
     * Bean for SkipSendMutationStrategy.
     * Injects UtilityFunctions and RulesModifier to ensure all dependencies are properly wired.
     */
    @Bean
    public SkipSendMutationStrategy skipSendMutationStrategy(UtilityFunctions utilityFunctions, RulesModifier rulesModifier) {
        return new SkipSendMutationStrategy(utilityFunctions, rulesModifier);
    }
}
