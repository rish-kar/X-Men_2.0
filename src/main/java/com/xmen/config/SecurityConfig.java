package com.xmen.config;

import com.xmen.security.OriginRestrictionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Web security configuration for the application.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Comma-separated list of allowed CORS origins (from application properties).
   */
  @Value("${app.cors.allowed-origins:}")
  private String corsAllowedOrigins;

  /**
   * Configure the security filter chain.
   *
   * @param http the HttpSecurity to configure
   * @return the configured SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> {})
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/css/**",
                        "/images/**",
                        "/js/**",
                        "/actuator/health/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(
            new OriginRestrictionFilter(corsAllowedOrigins),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * Build a CorsConfigurationSource from configured origins.
   *
   * @return a CorsConfigurationSource for API endpoints
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // Align with AppConfig
    List<String> origins =
        Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(
        Arrays.asList(
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
            "Forget-Mutation",
            "Neglect-Mutation",
            "Haskell-Activate",
            "Derivation-Type",
            "Derivation-Depth"));
    config.setExposedHeaders(List.of("Content-Disposition"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
