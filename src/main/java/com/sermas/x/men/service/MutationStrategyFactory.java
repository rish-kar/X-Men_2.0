package com.sermas.x.men.service;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.service.impl.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MutationStrategyFactory {

    private static final Map<Mutations, MutationStrategy> strategies = new HashMap<>();

    // Initialize the strategies
    static {

        // Skip Mutation Strategies
        strategies.put(Mutations.SKIP_SEND, new SkipSendMutationStrategy());
        strategies.put(Mutations.SKIP_RECEIVE, new SkipReceiveMutationStrategy());
        strategies.put(Mutations.SKIP_SEND_RECEIVE, new SkipSendReceiveMutationStrategy());
        strategies.put(Mutations.SKIP_RECEIVE_SEND, new SkipReceiveSendMutationStrategy());
        strategies.put(Mutations.SKIP_RECEIVE_SEND_RECEIVE, new SkipReceiveSendReceiveMutationStrategy());

        // Add Mutation Strategies
        strategies.put(Mutations.ADD, new AddMutationStrategy());

        // Replace Mutation Strategies#
        strategies.put(Mutations.REPLACE_SUB_MESSAGES, new ReplaceSubMessagesStrategy());
        strategies.put(Mutations.REPLACE_TYPE, new ReplaceTypeStrategy());

        // Neglect Mutation Strategies
        strategies.put(Mutations.NEGLECT, new NeglectMutationStrategy());
    }

    // Get the strategy for the given mutation
    public static MutationStrategy getStrategy(Mutations mutation) {
        return strategies.get(mutation);
    }
}
