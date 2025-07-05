package com.sermas.x.men.utilities;

import com.sermas.x.men.model.Fact;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.model.Value;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ForgetMutationParser class is responsible for parsing rules to extract Forget facts. It processes
 * each rule to find Forget(...) facts, extracts the values, and stores them in a LinkedHashSet.
 */
@Component
@Slf4j
public class ForgetMutationParser {

  /**
   * Parses the list of rules to extract values from any Forget(...) facts. For each human rule that
   * contains a Forget fact, the value inside Forget() is extracted and added to a LinkedHashSet.
   * The Forget fact is then removed from the rule. The resulting set is stored in the
   * ParametersBundle.forgetMutationSet map with the rule name as the key.
   *
   * <p>Example: If rule H_1 contains: Forget($ccard) then the resulting map entry will be: key:
   * "H_1", value: {"$ccard"}
   *
   * <p>If a rule does not contain any Forget fact, no entry is added.
   *
   * @param rules The list of original rules.
   * @param parametersBundle The ParametersBundle instance to update.
   * @return The updated ParametersBundle with forgetMutationSet populated.
   */
  public static ParametersBundle parseForgetMutations(
      List<Rule> rules, ParametersBundle parametersBundle) {

    // Iterate over each rule.
    for (Rule rule : rules) {
      if (rule.isHuman()) {
        LinkedHashSet<String> forgetSet = new LinkedHashSet<>();

        // Process only the action facts for this rule.
        List<Fact> actions = rule.getActions();
        Iterator<Fact> iterator = actions.iterator();
        while (iterator.hasNext()) {
          Fact fact = iterator.next();
          if ("Forget".equals(fact.getF_name())) {
            if (fact.getParameters() != null && !fact.getParameters().isEmpty()) {
              for (Object param : fact.getParameters()) {
                if (param instanceof Value) {
                  String forgetVal = ((Value) param).getName();
                  forgetSet.add(forgetVal);
                }
              }
            }
            // Remove the Forget fact from the rule.
            iterator.remove();
          }
        }
        // If any Forget values were found, add them to the bundle.
        if (!forgetSet.isEmpty()) {
          parametersBundle.getForgetMutationSet().put(rule.getRule_name(), forgetSet);
        }
      }
    }

    if (parametersBundle.getForgetMutationSet().isEmpty()) {
      throw new IllegalArgumentException(
          "Forget function not found in any rule in the given input file.");
    }

    return parametersBundle;
  }
}
