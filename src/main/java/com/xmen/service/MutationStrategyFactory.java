package com.xmen.service;

import com.xmen.model.Mutations;
import com.xmen.service.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MutationStrategyFactory class. This factory is responsible for providing the appropriate
 * MutationStrategy based on the specified mutation type.
 */
@Component
public class MutationStrategyFactory {

  private final Map<Mutations, MutationStrategy> strategies = new HashMap<>();

  /**
   * Constructor for MutationStrategyFactory. It initializes the factory with all available mutation
   * strategies.
   *
   * @param skipSendMutationStrategy SkipSendMutationStrategy
   * @param skipReceiveMutationStrategy SkipReceiveMutationStrategy
   * @param skipSendReceiveMutationStrategy SkipSendReceiveMutationStrategy
   * @param skipReceiveSendMutationStrategy SkipReceiveSendMutationStrategy
   * @param skipReceiveSendReceiveMutationStrategy SkipReceiveSendReceiveMutationStrategy
   * @param addMutationStrategy AddMutationStrategy
   * @param replaceSubMessagesStrategy ReplaceSubMessagesStrategy
   * @param replaceTypeStrategy ReplaceTypeStrategy
   */
  @Autowired
  public MutationStrategyFactory(
      SkipSendMutationStrategy skipSendMutationStrategy,
      SkipReceiveMutationStrategy skipReceiveMutationStrategy,
      SkipSendReceiveMutationStrategy skipSendReceiveMutationStrategy,
      SkipReceiveSendMutationStrategy skipReceiveSendMutationStrategy,
      SkipReceiveSendReceiveMutationStrategy skipReceiveSendReceiveMutationStrategy,
      AddMutationStrategy addMutationStrategy,
      ReplaceSubMessagesStrategy replaceSubMessagesStrategy,
      ReplaceTypeStrategy replaceTypeStrategy,
      ForgetMutationStrategy forgetMutationStrategy,
      NeglectMutationStrategy neglectMutationStrategy) {
    // Initialize strategies with Spring-managed beans
    strategies.put(Mutations.SKIP_SEND, skipSendMutationStrategy);
    strategies.put(Mutations.SKIP_RECEIVE, skipReceiveMutationStrategy);
    strategies.put(Mutations.SKIP_SEND_RECEIVE, skipSendReceiveMutationStrategy);
    strategies.put(Mutations.SKIP_RECEIVE_SEND, skipReceiveSendMutationStrategy);
    strategies.put(Mutations.SKIP_RECEIVE_SEND_RECEIVE, skipReceiveSendReceiveMutationStrategy);
    strategies.put(Mutations.ADD, addMutationStrategy);
    strategies.put(Mutations.REPLACE_SUB_MESSAGES, replaceSubMessagesStrategy);
    strategies.put(Mutations.REPLACE_TYPE, replaceTypeStrategy);
    strategies.put(Mutations.FORGET, forgetMutationStrategy);
    strategies.put(Mutations.NEGLECT, neglectMutationStrategy);
  }

  /**
   * Get the appropriate MutationStrategy based on the specified mutation type.
   *
   * @param mutation The mutation type for which the strategy is requested
   * @return MutationStrategy corresponding to the specified mutation type, or null if no strategy
   *     is found
   */
  public MutationStrategy getStrategy(Mutations mutation) {
    return strategies.get(mutation);
  }
}
