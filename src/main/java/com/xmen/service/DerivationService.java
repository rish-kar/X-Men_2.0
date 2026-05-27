package com.xmen.service;

import com.xmen.model.Derivation;
import com.xmen.model.Message;

import java.util.Set;

/**
 * DerivationService interface provides methods to derive a target message from a set of knowledge
 * messages and to print the derivation tree.
 */
public interface DerivationService {

  /**
   * Derives a target message from a set of knowledge messages up to a specified depth limit
   * according to Dolev-Yao Model.
   *
   * @param target the target message to derive
   * @param knowledge the set of knowledge messages
   * @param depthLimit the maximum depth for derivation
   * @return a set of strings representing the derived messages
   */
  Set<String> deriveLimited(Message target, Set<Message> knowledge, int depthLimit);


  Set<Derivation> deriveToDepth(Message target, Set<Message> knowledge, int depthLimit);
  Set<Derivation> deriveToInfinity(Message target, Set<Message> knowledge);

  void printAllDerivationTrees(Set<Derivation> trees);

  /**
   * Prints the derivation tree for a target message based on a set of knowledge messages and a
   * specified depth limit.
   *
   * @param target the target message to derive
   * @param knowledge the set of knowledge messages
   * @param depthLimit the maximum depth for the derivation tree
   */
  void printDerivationTree(Message target, Set<Message> knowledge, int depthLimit);
}
