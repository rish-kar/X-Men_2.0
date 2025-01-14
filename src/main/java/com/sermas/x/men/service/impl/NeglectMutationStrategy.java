package com.sermas.x.men.service.impl;

import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.MutationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@Slf4j
public class NeglectMutationStrategy implements MutationStrategy {
    @Override
    public void applyMutation(ArrayList<Rule> rules) {
        // Implement skip send mutation logic
    }
}