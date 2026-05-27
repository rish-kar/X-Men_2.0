package com.xmen.model;

import com.xmen.service.forget.ForgetContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** ParametersBundle class represents a collection of parameters used in the application. */
@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class ParametersBundle {

  String fileName;
  ArrayList<Rule> theory;
  ArrayList<ArrayList<Rule>> collections;
  ArrayList<Function> functions;
  ArrayList<Builtins> builtins;
  ArrayList<Value> roles;
  Boolean modelWithTags = false;
  Flags flags;
  Map<String, String> existingSetupKnowledge = new HashMap<>();
  Map<String, LinkedHashSet<String>> forgetMutationSet = new HashMap<>();

  // Transition-aware forget context for a single mutation run (non-serialized).
  transient ForgetContext forgetContext;

  // Indicates whether variants were truncated due to per-rule cap.
  boolean variantsTruncated = false;

  // Special parameters to handle the derivation tree logic
  String derivationType;
  int derivationDepth;

  // New field to store additional content
  private Map<String, String> extraContent = new HashMap<>();

  /**
   * Adds extra content to the ParametersBundle.
   *
   * @param key the key for the extra content
   * @param value the value for the extra content
   */
  public void addExtraContent(String key, String value) {
    this.extraContent.put(key, value);
  }

  /**
   * Retrieves extra content from the ParametersBundle.
   *
   * @param key the key for the extra content
   * @return the value associated with the key, or an empty string if the key does not exist
   */
  public String getExtraContent(String key) {
    return this.extraContent.getOrDefault(key, "");
  }
}
