package com.xmen.service;

import com.xmen.model.Message;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DerivationCheckService interface provides methods to check if a target message can be derived
 * from a set of knowledge messages, extract target messages from rules, and extract knowledge from
 * parameters bundles and rules.
 */
public interface DerivationCheckService {

  /**
   * Checks if a target message can be derived from a set of knowledge messages.
   *
   * @param target the target message to check
   * @param knowledge the set of knowledge messages
   * @return true if the target can be derived from the knowledge, false otherwise
   */
  boolean isDerivable(Message target, Set<Message> knowledge, String derivationType, int derivationDepth);

  /**
   * Extracts the target message from a rule.
   *
   * @param rule the rule from which to extract the target message
   * @return the target message extracted from the rule
   */
  Message extractTargetFromRule(Rule rule);

  /**
   * Extracts knowledge messages from a parameters bundle.
   *
   * @param parametersBundle the parameters bundle from which to extract knowledge
   * @return a set of knowledge messages extracted from the parameters bundle
   */
  Set<Message> extractKnowledge(ParametersBundle parametersBundle);

  /**
   * Extracts knowledge messages from a rule.
   *
   * @param rule the rule from which to extract knowledge
   * @return a set of knowledge messages extracted from the rule
   */
  Set<Message> extractKnowledgeFromRule(Rule rule);

  /**
   * Extracts state knowledge from a list of human-readable rules.
   *
   * @param humanRules the list of human-readable rules
   * @return a map where keys are state IDs and values are sets of messages representing the state
   *     knowledge
   */
  Map<String, Set<Message>> extractStateKnowledgeFromRules(List<Rule> humanRules);

  /**
   * Extracts the state ID from a rule.
   *
   * @param rule the rule from which to extract the state ID
   * @return the state ID extracted from the rule
   */
  String extractStateIdFromRule(Rule rule);

  /**
   * Extracts knowledge messages from a parameters bundle WITHOUT removing forgotten items.
   * This is used for the Algorithm 1 forget mutation where K is monotonic (never shrinks)
   * and Forget is tracked separately.
   *
   * @param parametersBundle the parameters bundle from which to extract knowledge
   * @return a set of knowledge messages (full K, not K minus Forget)
   */
  Set<Message> extractKnowledgeWithoutForgetRemoval(ParametersBundle parametersBundle);
}
