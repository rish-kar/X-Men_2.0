package com.sermas.x.men.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sermas.x.men.model.*;
import com.sermas.x.men.service.impl.SkipReceiveMutationStrategy;
import com.sermas.x.men.utilities.RulesModifier;
import com.sermas.x.men.utilities.UtilityFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

class SkipReceiveMutationStrategyTest {

//    @Mock
//    private UtilityFunctions utilityFunctions;
//
//    @Mock
//    private RulesModifier rulesModifier;
//
//    @InjectMocks
//    private SkipReceiveMutationStrategy skipReceiveMutationStrategy;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//    }

//    @Test
//    void applyMutation_withRcvPreconditionFact_shouldMutateRule() {
//        Rule originalRule = mock(Rule.class);
//        Rule clonedRule = mock(Rule.class);
//        ArrayList<Rule> rules = new ArrayList<>();
//        ParametersBundle parametersBundle = new ParametersBundle();
//
//        Fact rcvFact = mock(Fact.class);
//        Fact clonedRcvFact = mock(Fact.class);
//        when(originalRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(rcvFact);
//        when(rcvFact.getParameter(0)).thenReturn(mock(Value.class));
//        when(rcvFact.getParameter(2)).thenReturn(mock(Value.class));
//        when(originalRule.clone()).thenReturn(clonedRule);
//        when(clonedRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(clonedRcvFact);
//        when(utilityFunctions.cloneModel(any())).thenReturn(new ArrayList<>());
//
//        ParametersBundle result = skipReceiveMutationStrategy.applyMutation(originalRule, rules, parametersBundle);
//
//        assertNotNull(result);
//        verify(clonedRcvFact).setRemoved(true);
//        verify(clonedRule).setRule_name(anyString());
//    }
//
//    @Test
//    void applyMutation_withoutRcvPreconditionFact_shouldReturnOriginalParametersBundle() {
//        Rule originalRule = mock(Rule.class);
//        ArrayList<Rule> rules = new ArrayList<>();
//        ParametersBundle parametersBundle = new ParametersBundle();
//
//        when(originalRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(null);
//
//        ParametersBundle result = skipReceiveMutationStrategy.applyMutation(originalRule, rules, parametersBundle);
//
//        assertEquals(parametersBundle, result);
//    }
//
//    @Test
//    void applyMutation_withEmptyModifications_shouldAddMutatedRule() {
//        Rule originalRule = mock(Rule.class);
//        Rule clonedRule = mock(Rule.class);
//        ArrayList<Rule> rules = new ArrayList<>();
//        ParametersBundle parametersBundle = new ParametersBundle();
//
//        Fact rcvFact = mock(Fact.class);
//        Fact clonedRcvFact = mock(Fact.class);
//        when(originalRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(rcvFact);
//        when(rcvFact.getParameter(0)).thenReturn(mock(Value.class));
//        when(rcvFact.getParameter(2)).thenReturn(mock(Value.class));
//        when(originalRule.clone()).thenReturn(clonedRule);
//        when(clonedRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(clonedRcvFact);
//        when(utilityFunctions.cloneModel(any())).thenReturn(new ArrayList<>());
//
//        ParametersBundle result = skipReceiveMutationStrategy.applyMutation(originalRule, rules, parametersBundle);
//
//        assertNotNull(result);
//        verify(clonedRcvFact).setRemoved(true);
//        verify(clonedRule).setRule_name(anyString());
//    }
//
//    @Test
//    void applyMutation_withStatePostcondition_shouldRebuildState() {
//        Rule originalRule = mock(Rule.class);
//        Rule clonedRule = mock(Rule.class);
//        ArrayList<Rule> rules = new ArrayList<>();
//        ParametersBundle parametersBundle = new ParametersBundle();
//
//        Fact rcvFact = mock(Fact.class);
//        Fact stateFact = mock(Fact.class);
//        Fact clonedRcvFact = mock(Fact.class);
//        when(originalRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(rcvFact);
//        when(originalRule.getPreconditionFactByMatchingName("State")).thenReturn(stateFact);
//        when(originalRule.getPostconditionFactByMatchingName("State")).thenReturn(stateFact);
//        when(rcvFact.getParameter(0)).thenReturn(mock(Value.class));
//        when(rcvFact.getParameter(2)).thenReturn(mock(Value.class));
//        when(originalRule.clone()).thenReturn(clonedRule);
//        when(clonedRule.getPreconditionFactByMatchingName("Rcv")).thenReturn(clonedRcvFact);
//        when(utilityFunctions.cloneModel(any())).thenReturn(new ArrayList<>());
//
//        ParametersBundle result = skipReceiveMutationStrategy.applyMutation(originalRule, rules, parametersBundle);
//
//        assertNotNull(result);
//        verify(clonedRcvFact).setRemoved(true);
//        verify(utilityFunctions).buildNewState(any(), eq(stateFact));
//        verify(clonedRule).setRule_name(anyString());
//    }
}