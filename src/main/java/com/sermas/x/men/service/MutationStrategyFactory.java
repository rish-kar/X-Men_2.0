package com.sermas.x.men.service;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.service.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MutationStrategyFactory {

    private final Map<Mutations, MutationStrategy> strategies = new HashMap<>();

    @Autowired
    public MutationStrategyFactory(
            SkipSendMutationStrategy skipSendMutationStrategy,
            SkipReceiveMutationStrategy skipReceiveMutationStrategy,
            SkipSendReceiveMutationStrategy skipSendReceiveMutationStrategy,
            SkipReceiveSendMutationStrategy skipReceiveSendMutationStrategy,
            SkipReceiveSendReceiveMutationStrategy skipReceiveSendReceiveMutationStrategy,
            AddMutationStrategy addMutationStrategy,
            ReplaceSubMessagesStrategy replaceSubMessagesStrategy,
            ReplaceTypeStrategy replaceTypeStrategy
    ) {
        // Initialize strategies with Spring-managed beans
        strategies.put(Mutations.SKIP_SEND, skipSendMutationStrategy);
        strategies.put(Mutations.SKIP_RECEIVE, skipReceiveMutationStrategy);
        strategies.put(Mutations.SKIP_SEND_RECEIVE, skipSendReceiveMutationStrategy);
        strategies.put(Mutations.SKIP_RECEIVE_SEND, skipReceiveSendMutationStrategy);
        strategies.put(Mutations.SKIP_RECEIVE_SEND_RECEIVE, skipReceiveSendReceiveMutationStrategy);
        strategies.put(Mutations.ADD, addMutationStrategy);
        strategies.put(Mutations.REPLACE_SUB_MESSAGES, replaceSubMessagesStrategy);
        strategies.put(Mutations.REPLACE_TYPE, replaceTypeStrategy);
    }

    // Get the strategy for the given mutation
    public MutationStrategy getStrategy(Mutations mutation) {
        return strategies.get(mutation);
    }
}
