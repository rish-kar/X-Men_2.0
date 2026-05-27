package com.xmen.utilities;

import com.xmen.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FileHandler class. This class is responsible for handling the arrangement of rules and variables,
 * merging tags and values, spreading tags, connecting variables, arranging persistent knowledge,
 * identifying roles, and de-merging tags and values in the theory.
 */
@org.springframework.stereotype.Component
@Slf4j
public class FileHandler {

  /**
   * Arrange the theory by connecting the rules.
   *
   * @param parametersBundle The parameters bundle to arrange.
   * @return The arranged list of rules.
   */
  public ParametersBundle arrangeTheory(ParametersBundle parametersBundle) {
    ArrayList<Rule> connectedRules = new ArrayList<>();
    ArrayList<Rule> rules = parametersBundle.getTheory();
    try {
      // Iterate through each rule in the rules list
      for (int index = 0; index < rules.size(); index++) {
        Rule currentRule = rules.get(index);

        // If it's the first rule, set its previous rule to null and next rule to the second rule
        if (index == 0) {
          currentRule.setPrevious(null);
          currentRule.setNext(rules.get(index + 1));
        } else if (index > 0 && index < rules.size() - 1) {
          // If it's a middle rule, set its previous rule to the one before it and next rule to the
          // one after it
          currentRule.setPrevious(rules.get(index - 1));
          currentRule.setNext(rules.get(index + 1));
        } else {
          // If it's the last rule, set its previous rule to the one before it and next rule to null

          currentRule.setPrevious(rules.get(index - 1));
          currentRule.setNext(null);
        }

        connectedRules.add(currentRule);
      }
      log.debug("Connecting the rules ended");
    } catch (IndexOutOfBoundsException e) {
      log.error("Error: Attempted to access an index that is out of bounds. {}", e.getMessage());
    } catch (Exception e) {
      log.error("An unexpected error occurred: {}", e.getMessage());
    }
    parametersBundle.setTheory(new ArrayList<>(connectedRules));
    return parametersBundle;
  }

  /**
   * Arrange the lets by connecting the variables.
   *
   * @param parametersBundle The parameters bundle to arrange.
   * @return The arranged list of rules.
   */
  public ParametersBundle arrangeLets(ParametersBundle parametersBundle) {
    ArrayList<Rule> rules = parametersBundle.getTheory();
    boolean didSomething = false;

    try {
      // Iterate through each rule in the rules list
      for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
        Rule currentRule = rules.get(ruleIndex);

        // Check if the rule has more than one variable
        if (currentRule.hasVariables() && currentRule.getVariables().size() > 1) {
          didSomething = true;

          // Iterate through each variable in the current rule
          for (int variableIndex = 0;
              variableIndex < currentRule.getVariables().size();
              variableIndex++) {
            Variable currentVariable = currentRule.getVariables().get(variableIndex);
            Special variableValues = currentVariable.getValues();

            // Handle FSpecial type variables
            if (variableValues instanceof FSpecial) {
              ArrayList<Value> group = variableValues.getGroup();
              for (int groupIndex = 0; groupIndex < group.size(); groupIndex++) {
                Value value = group.get(groupIndex);

                // Check for pattern of special characters
                Pattern pattern = Pattern.compile("[^A-Za-z0-9]");
                Matcher matcher = pattern.matcher(value.getName());
                boolean hasSpecialChars = matcher.find();

                // Check if the value has special characters
                if (!hasSpecialChars) {
                  String variableName = value.getName();
                  for (int otherVariableIndex = 0;
                      otherVariableIndex < currentRule.getVariables().size();
                      otherVariableIndex++) {
                    if (otherVariableIndex != variableIndex
                        && variableName.equals(
                            currentRule.getVariables().get(otherVariableIndex).getName())) {
                      group.set(groupIndex, currentRule.getVariables().get(otherVariableIndex));
                      break;
                    }
                  }
                }
              }
            } else if (variableValues instanceof PSpecial) {
              // Handle PSpecial type variables
              ArrayList<Value> group = variableValues.getGroup();
              for (int groupIndex = 0; groupIndex < group.size(); groupIndex++) {
                Value value = group.get(groupIndex);

                Pattern pattern = Pattern.compile("[^A-Za-z0-9]");
                Matcher matcher = pattern.matcher(value.getName());
                boolean hasSpecialChars = matcher.find();

                // If the value does not contain special characters, connect it to the other
                // variable
                if (!hasSpecialChars) {
                  String variableName = value.getName();
                  for (int otherVariableIndex = 0;
                      otherVariableIndex < currentRule.getVariables().size();
                      otherVariableIndex++) {
                    if (otherVariableIndex != variableIndex
                        && variableName.equals(
                            currentRule.getVariables().get(otherVariableIndex).getName())) {
                      group.set(groupIndex, currentRule.getVariables().get(otherVariableIndex));
                      break;
                    }
                  }
                }
              }
              // TODO: Handle nested FSpecial within PSpecial if needed
            }
          }
        }
      }
      if (didSomething) {
        log.debug("Connecting the variables ended");
      }
    } catch (Exception e) {
      log.error("An unexpected error occurred while arranging lets: {}", e.getMessage());
    }
    parametersBundle.setTheory(new ArrayList<>(rules));
    return parametersBundle;
  }

  /**
   * Merge the tags values: For the models that used the specification of tags while they send or
   * receive messages, we merge the tags with the values in order to have standard rules of
   * Sender,Receiver,Values instead of Sender, Receiver,Tags,Values. We take care to print out the
   * tags at the end.
   *
   * @param parametersBundle The parameters bundle to merge.
   * @return The merged list of rules.
   */
  public ParametersBundle mergeTagsValues(ParametersBundle parametersBundle) {
    ArrayList<Rule> rules = parametersBundle.getTheory();
    boolean didSomething = false;

    try {
      // Iterate through each rule in the rules list
      for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
        Rule currentRule = rules.get(ruleIndex);

        // Handle the 'Rcv' fact in the preconditions
        Fact receiveFact = currentRule.getPreconditionFactByMatchingName("Rcv");
        if (receiveFact != null && receiveFact.getParameters().size() == 4) {
          didSomething = true;

          if (!parametersBundle.getModelWithTags()) {
            parametersBundle.setModelWithTags(true);
          }

          // We have 'Rcv' with tags
          // 0 Receiver
          // 1 Sender
          // 2 tag/group of tags
          // 3 value/group of values

          if (receiveFact.getParameter(3) instanceof PSpecial values) {
            PSpecial tags = (PSpecial) receiveFact.getParameter(2);

              for (int valueIndex = 0; valueIndex < values.getGroup().size(); valueIndex++) {
              Value value = values.getValue(valueIndex);
              value.setTag(tags.getValue(valueIndex).getName());
            }
            receiveFact.setSingleParameter(values, 2);
            receiveFact.getParameters().remove(3);

          } else if (receiveFact.getParameter(3) instanceof Value value && !currentRule.hasVariables()) {
            Value tag = (Value) receiveFact.getParameter(2);
              value.setTag(tag.getName());
            receiveFact.setSingleParameter(value, 2);
            receiveFact.getParameters().remove(3);

          } else if (receiveFact.getParameter(3) instanceof Value value && currentRule.hasVariables()) {
            Value tag = (Value) receiveFact.getParameter(2);
              receiveFact.setSingleParameter(value, 2);
            receiveFact.getParameters().remove(3);
          }
        }

        // Handle the 'Snd' fact in the postconditions
        Fact sendFact = currentRule.getPostconditionFactByMatchingName("Snd");

        // Check if the send fact is not null and has 4 parameters
        if (sendFact != null && sendFact.getParameters().size() == 4) {
          // We have 'Snd' with tags
          // 0 Sender
          // 1 Receiver
          // 2 tag/group of tags
          // 3 value/group of values

          if (sendFact.getParameter(3) instanceof PSpecial values) {
            PSpecial tags = (PSpecial) sendFact.getParameter(2);

              for (int valueIndex = 0; valueIndex < values.getGroup().size(); valueIndex++) {
              Value value = values.getValue(valueIndex);
              value.setTag(tags.getValue(valueIndex).getName());
            }
            sendFact.setSingleParameter(values, 2);
            sendFact.getParameters().remove(3);

          } else if (sendFact.getParameter(3) instanceof Value value && !currentRule.hasVariables()) {

            // If the send fact has a single value and no variables
            Value tag = (Value) sendFact.getParameter(2);
              value.setTag(tag.getName());
            sendFact.setSingleParameter(value, 2);
            sendFact.getParameters().remove(3);

          } else if (sendFact.getParameter(3) instanceof Value value && currentRule.hasVariables()) {

            // If the send fact has a single value and variables
            Value tag = (Value) sendFact.getParameter(2);
              sendFact.setSingleParameter(value, 2);
            sendFact.getParameters().remove(3);
          }
        }
      }

      if (didSomething) {
        log.debug("Merging of tags and values ended");
      }
    } catch (CloneNotSupportedException e) {
      log.error("Error: Cloning not supported. {}", e.getMessage());
    } catch (Exception e) {
      log.error("An unexpected error occurred while merging tags and values: {}", e.getMessage());
    }
    parametersBundle.setTheory(new ArrayList<>(rules));
    return parametersBundle;
  }

  /**
   * Spread the tags in the theory.
   *
   * @param parametersBundle The parameters bundle to spread.
   * @return The spread list of rules.
   */
  public ParametersBundle spreadTags(ParametersBundle parametersBundle) {
    ArrayList<Value> persistentTags = new ArrayList<>();
    ArrayList<Rule> theory = parametersBundle.getTheory();

    boolean didSomething = false;

    try {
      // Iterate through each rule to find the 'humansetup' rule and collect persistent tags
      for (Rule rule : theory) {
        if (rule.getRule_name().equalsIgnoreCase("humansetup")) {
          for (Fact postcondition : rule.getPostconditions()) {
            if (postcondition.getF_name().equalsIgnoreCase("!Type")) {
              Object param2 = postcondition.getParameter(2);
              Object param1 = postcondition.getParameter(1);

              // Handle both Value and other types (PSpecial, etc.)
              if (param2 instanceof Value && param1 instanceof Value) {
                Value value = (Value) param2;
                value.setTag(((Value) param1).getName());
                persistentTags.add(value);
              } else if (param2 != null && param1 instanceof Value) {
                // For non-Value types like PSpecial, create a Value from their string representation
                Value value = new Value(param2.toString(), false, false, false);
                value.setTag(((Value) param1).getName());
                persistentTags.add(value);
              }
            }
          }
          break;
        }
      }

      // If persistent tags are found, spread them through all rules
      if (!persistentTags.isEmpty()) {
        didSomething = true;
        for (Rule rule : theory) {
          ArrayList<Value> valuesWithTags = new ArrayList<>(persistentTags);

          // Collect possible tags from 'Rcv' precondition
          Fact receiveFact = rule.getPreconditionFactByMatchingName("Rcv");
          if (receiveFact != null) {
            Object receivedValue = receiveFact.getParameter(2);
            if (receivedValue instanceof Value) {
              if (((Value) receivedValue).getTag() != null) {
                valuesWithTags.add((Value) receivedValue);
              }
            } else if (receivedValue instanceof PSpecial) {
              for (Value value : ((PSpecial) receivedValue).getGroup()) {
                if (value.getTag() != null) {
                  valuesWithTags.add(value);
                }
              }
            }
          }

          // Collect possible tags from 'Snd' postcondition
          Fact sendFact = rule.getPostconditionFactByMatchingName("Snd");
          if (sendFact != null) {
            Object sentValue = sendFact.getParameter(2);
            if (sentValue instanceof Value) {
              if (((Value) sentValue).getTag() != null) {
                valuesWithTags.add((Value) sentValue);
              }
            } else if (sentValue instanceof PSpecial) {
              for (Value value : ((PSpecial) sentValue).getGroup()) {
                if (value.getTag() != null) {
                  valuesWithTags.add(value);
                }
              }
            }
          }

          // Spread the tags in the 'State' precondition
          Fact statePrecondition = rule.getPreconditionFactByMatchingName("State");
          if (statePrecondition != null && !valuesWithTags.isEmpty() && statePrecondition.getParameters().size() > 2) {
            valuesWithTags = new ArrayList<>(new LinkedHashSet<>(valuesWithTags));
            Object stateParam = statePrecondition.getParameter(2);
            if (stateParam instanceof PSpecial) {
              PSpecial knowledge = (PSpecial) stateParam;
              for (Value tagValue : valuesWithTags) {
                for (Value knowledgeValue : knowledge.getGroup()) {
                  if (knowledgeValue.getTag() == null
                      && knowledgeValue.getName().equals(tagValue.getName())) {
                    knowledgeValue.setTag(tagValue.getTag());
                  }
                }
              }
            }
            // If it's not a PSpecial (e.g., a Value), we skip tag spreading for this fact
          }

          // Spread the tags in the 'State' postcondition
          Fact statePostcondition = rule.getPostconditionFactByMatchingName("State");
          if (statePostcondition != null && !valuesWithTags.isEmpty() && statePostcondition.getParameters().size() > 2) {
            Object statePostParam = statePostcondition.getParameter(2);
            if (statePostParam instanceof PSpecial) {
              PSpecial knowledge = (PSpecial) statePostParam;
              for (Value tagValue : valuesWithTags) {
                for (Value knowledgeValue : knowledge.getGroup()) {
                  if (knowledgeValue.getTag() == null
                      && knowledgeValue.getName().equals(tagValue.getName())) {
                    knowledgeValue.setTag(tagValue.getTag());
                  }
                }
              }
            }
            // If it's not a PSpecial (e.g., a Value), we skip tag spreading for this fact
          }
        }
      }
      if (didSomething) {
        log.debug("Spreading of tags ended");
      }
    } catch (Exception e) {
      log.error("An unexpected error occurred while spreading tags: {}", e.getMessage());
    }
    parametersBundle.setTheory(new ArrayList<>(theory));
    return parametersBundle;
  }

  /**
   * Connects the variables with each other.
   *
   * @param parametersBundle the parameters bundle to process.
   * @return the modified list of rules
   */
  public ParametersBundle letArrangement(ParametersBundle parametersBundle) {
    ArrayList<Rule> theory = parametersBundle.getTheory();
    boolean didSomething = false;

    /*
     * 0 Receiver
     * 1 Sender
     * 2 Value to be connected with Variable
     */
    try {
      // Iterate through each rule in the list
      for (Rule rule : theory) {
        // Check if the rule has variables
        if (rule.hasVariables()) {
          didSomething = true;

          // Iterate through each variable in the rule
          for (Variable variable : rule.getVariables()) {
            // Connect the variable with the value in the 'Rcv' precondition
            Fact receiveFact = rule.getPreconditionFactByMatchingName("Rcv");
            if (receiveFact != null) {
              // Check if the value in the 'Rcv' precondition matches the variable name
              if (receiveFact.getParameter(2) instanceof Value value) {
                  if (value.getName().equals(variable.getName())) {
                  variable.setTag(value.getTag());
                  receiveFact.getParameters().set(2, variable);
                }
              }
            }

            // Connect the variable with the value in the 'Snd' postcondition
            Fact sendFact = rule.getPostconditionFactByMatchingName("Snd");
            if (sendFact != null) {
              // Check if the value in the 'Snd' postcondition matches the variable name
              if (sendFact.getParameter(2) instanceof Value value) {
                  if (value.getName().equals(variable.getName())) {
                  variable.setTag(value.getTag());
                  sendFact.getParameters().set(2, variable);
                }
              }
            }
          }
        }
      }

      if (didSomething) {
        log.debug("Connecting the variables with each other ended");
      }
    } catch (Exception e) {
      log.error("An unexpected error occurred while connecting variables: {}", e.getMessage());
    }
    parametersBundle.setTheory(new ArrayList<>(theory));
    return parametersBundle;
  }

  /**
   * Arranges the persistent knowledge on all the rules based on the knowledge in each state.
   *
   * @param parametersBundle the parameters bundle to arrange.
   * @return the modified list of rules
   */
  public ParametersBundle arrangeValues(ParametersBundle parametersBundle) {
    ArrayList<Rule> theory = parametersBundle.getTheory();
    boolean didSomething = false;

    try {
      // Iterate through each rule in the list
      for (Rule rule : theory) {
        // Check if the rule has preconditions and the first precondition is 'State'
        if (!rule.getPreconditions().isEmpty()
            && rule.getPreconditions().get(0).getF_name().equals("State")) {
          didSomething = true;
          Fact stateFact = rule.getSinglePreconditionFact(0);
          Value stateNumber = (Value) stateFact.getParameter(1);

          // Check if the state number is '1'
          if (stateNumber.getName().equals("'1'")) {
            ArrayList<Value> storageBox = new ArrayList<>();

            // Save the persistent knowledge of the rule
            Object values = stateFact.getParameter(2);
            if (values instanceof Value) {
              storageBox.add(((Value) values).clone());
            } else {
              PSpecial specialValues = (PSpecial) values;
              for (Value value : specialValues.getGroup()) {
                storageBox.add(value.clone());
              }
            }

            // Add 'Fr' preconditions to the storage box
            for (Fact precondition : rule.getPreconditions()) {
              if (precondition.getF_name().equals("Fr")) {
                storageBox.add(((Value) precondition.getParameter(0)).clone());
              }
            }

            // Process rules with similar names
            String ruleName = rule.getRule_name().replaceAll("[^a-zA-Z]", "");
            for (Rule similarRule : theory) {
              if (similarRule.getRule_name().replaceAll("[^a-zA-Z]", "").equals(ruleName)) {
                processSimilarRule(similarRule, storageBox);
              }
            }
          }
        }
      }

      if (didSomething) {
        log.debug("Setting up the persistent knowledge through the rules ended");
      }
    } catch (Exception e) {
      log.error("An unexpected error occurred while arranging values: {}", e.getMessage());
    }

    parametersBundle.setTheory(new ArrayList<>(theory));
    return parametersBundle;
  }

  /**
   * Processes a rule with a similar name and updates the storage box with persistent knowledge.
   *
   * @param rule the rule to process
   * @param storageBox the storage box containing persistent knowledge
   */
  private void processSimilarRule(Rule rule, ArrayList<Value> storageBox) {
    try {
      // Process 'State' precondition
      Fact statePrecondition = rule.getPreconditionFactByMatchingName("State");
      if (statePrecondition != null) {
        Value stateAgent = (Value) statePrecondition.getParameter(0);
        stateAgent.persistentKnowledge();
        storageBox.add(stateAgent.clone());
        updateStorageBoxWithStateParameters(storageBox, statePrecondition.getParameter(2));
      }

      // Process 'Rcv' precondition
      Fact receivePrecondition = rule.getPreconditionFactByMatchingName("Rcv");
      if (receivePrecondition != null) {
        Value receiverAgent = (Value) receivePrecondition.getParameter(1);
        receiverAgent.persistentKnowledge();
        updateStorageBoxWithReceiveParameters(storageBox, receivePrecondition);
      }

      // Process 'State' postcondition
      Fact statePostcondition = rule.getPostconditionFactByMatchingName("State");
      if (statePostcondition != null) {
        Value stateAgentPost = (Value) statePostcondition.getParameter(0);
        stateAgentPost.persistentKnowledge();
        updateStorageBoxWithStateParameters(storageBox, statePostcondition.getParameter(2));
      }

      // Process 'Snd' postcondition
      Fact sendPostcondition = rule.getPostconditionFactByMatchingName("Snd");
      if (sendPostcondition != null) {
        Value senderAgent = (Value) sendPostcondition.getParameter(0);
        senderAgent.persistentKnowledge();
        updateStorageBoxWithSendParameters(storageBox, sendPostcondition);
      }
    } catch (Exception e) {
      log.error("An error occurred while processing a similar rule: {}", e.getMessage());
    }
  }

  /**
   * Updates the storage box with state parameters.
   *
   * @param storageBox the storage box containing persistent knowledge
   * @param stateParameters the state parameters to update
   */
  private void updateStorageBoxWithStateParameters(
      ArrayList<Value> storageBox, Object stateParameters) {
    if (stateParameters instanceof Value) {
      for (Value value : storageBox) {
        if (value
            .getName()
            .replaceAll("[^a-zA-Z0-9]", "")
            .equals(((Value) stateParameters).getName().replaceAll("[^a-zA-Z0-9]", ""))) {
          ((Value) stateParameters).persistentKnowledge();
        }
      }
    } else if (stateParameters instanceof PSpecial) {
      for (Value stateValue : ((PSpecial) stateParameters).getGroup()) {
        for (Value storageValue : storageBox) {
          if (storageValue
              .getName()
              .replaceAll("[^a-zA-Z0-9]", "")
              .equals(stateValue.getName().replaceAll("[^a-zA-Z0-9]", ""))) {
            stateValue.persistentKnowledge();
          }
        }
      }
    }
    // If neither Value nor PSpecial, do nothing - this handles other types gracefully
  }

  /**
   * Updates the storage box with receive parameters.
   *
   * @param storageBox the storage box containing persistent knowledge
   * @param receivePrecondition the receive precondition to update
   */
  private void updateStorageBoxWithReceiveParameters(
      ArrayList<Value> storageBox, Fact receivePrecondition) {
    Object receiveParameters = receivePrecondition.getParameter(2);
    updateStorageBoxWithParameters(storageBox, receiveParameters);
  }

  /**
   * Updates the storage box with send parameters.
   *
   * @param storageBox the storage box containing persistent knowledge
   * @param sendPostcondition the send postcondition to update
   */
  private void updateStorageBoxWithSendParameters(
      ArrayList<Value> storageBox, Fact sendPostcondition) {
    Object sendParameters = sendPostcondition.getParameter(2);
    updateStorageBoxWithParameters(storageBox, sendParameters);
  }

  /**
   * Updates the storage box with parameters.
   *
   * @param storageBox the storage box containing persistent knowledge
   * @param parameters the parameters to update
   */
  private void updateStorageBoxWithParameters(ArrayList<Value> storageBox, Object parameters) {
    if (parameters instanceof Variable) {
      Special special = ((Variable) parameters).getValues();
      if (special instanceof FSpecial) {
        for (Object obj : special.getGroup()) {
          if (obj instanceof Variable) {
            for (Value value : getValuesFromNestedSpecial(special)) {
              updateStorageBoxWithValue(storageBox, value);
            }
          } else if (obj instanceof Value) {
            updateStorageBoxWithValue(storageBox, (Value) obj);
          }
        }
      } else if (special instanceof PSpecial) {
        for (Value value : special.getGroup()) {
          updateStorageBoxWithValue(storageBox, value);
        }
      }
    } else if (parameters instanceof Value) {
      updateStorageBoxWithValue(storageBox, (Value) parameters);
    } else if (parameters instanceof PSpecial) {
      for (Value value : ((PSpecial) parameters).getGroup()) {
        updateStorageBoxWithValue(storageBox, value);
      }
    }
  }

  /**
   * Updates the storage box with a value.
   *
   * @param storageBox the storage box containing persistent knowledge
   * @param value the value to update
   */
  private void updateStorageBoxWithValue(ArrayList<Value> storageBox, Value value) {
    for (Value storageValue : storageBox) {
      if (storageValue
          .getName()
          .replaceAll("[^a-zA-Z0-9]", "")
          .equals(value.getName().replaceAll("[^a-zA-Z0-9]", ""))) {
        value.persistentKnowledge();
      }
    }
  }

  /**
   * Retrieves values from a nested special.
   *
   * @param special the special to retrieve values from
   * @return the list of values
   */
  private ArrayList<Value> getValuesFromNestedSpecial(Special special) {
    ArrayList<Value> values = new ArrayList<>();
    if (special instanceof FSpecial) {
      for (Object obj : special.getGroup()) {
        if (obj instanceof Value) {
          values.add((Value) obj);
        } else if (obj instanceof Variable) {
          values.addAll(getValuesFromNestedSpecial(((Variable) obj).getValues()));
        }
      }
    }
    return values;
  }

  /**
   * Identifies the roles in the theory.
   *
   * @param parametersBundle the parameters bundle to identify.
   * @return the modified list of rules
   */
  public ParametersBundle identifyRoles(ParametersBundle parametersBundle) {
    ArrayList<Rule> theory = parametersBundle.getTheory();
    boolean didSomething = false;

    try {

      // Ensure the roles list is initialized
      if (parametersBundle.getRoles() == null) {
        parametersBundle.setRoles(new ArrayList<>());
      }

      // Iterate through each rule in the list
      for (Rule rule : theory) {
        // Check if the rule name starts with "setup"
        if (rule.getRule_name().toLowerCase().startsWith("setup")) {
          // Get the Roles action fact
          Fact rolesFact = rule.getActionFactByMatchingName("Roles");
          if (rolesFact != null) {
            didSomething = true;
            // Add each parameter of the Roles fact to the roles list
            for (Object parameter : rolesFact.getParameters()) {
              if (parameter instanceof Value) {
                parametersBundle.getRoles().add((Value) parameter);
              } else {
                log.error("Parameter is not an instance of Value: {}", parameter);
              }
            }
            break;
          }
        }
      }

      if (didSomething) {
        log.debug("Extracting the roles ended");
      }
    } catch (Exception e) {
      log.error("An unexpected error occurred while identifying roles: {}", e.getMessage());
    }

    parametersBundle.setTheory(new ArrayList<>(theory));
    return parametersBundle;
  }

  /**
   * De-merges tags and values in the given theory.
   *
   * @param theory The list of rules to process.
   * @return The modified list of rules.
   */
  public ArrayList<com.xmen.model.Rule> demergeTagsValues(
      ArrayList<com.xmen.model.Rule> theory, ParametersBundle parametersBundle) {
    if (parametersBundle.getModelWithTags()) {
      boolean didSomething = false;
      for (int i = 0; i < theory.size(); i++) {
        com.xmen.model.Rule rule = theory.get(i);
        Fact rcv = rule.getPreconditionFactByMatchingName("Rcv");
        if (rcv != null) {
          didSomething = true;
          /*We have Rcv with tags
           *
           * 0 Receiver
           * 1 Sender
           * 2 tag/group of tags
           * 3 value/group of values
           */

          if (rcv.getParameter(2) instanceof PSpecial valuesAndTags) {

              PSpecial tags = new PSpecial();
            PSpecial values = new PSpecial();

            for (int j = 0; j < valuesAndTags.getGroup().size(); j++) {
              Value value = valuesAndTags.getValue(j);
              Value tag = new Value(value.getTag());
              tag.setRemoved(value.isRemoved());
              tags.addValue(tag);
              values.addValue(value);
            }

            rcv.getParameters().set(2, tags);
            rcv.getParameters().add(values);

          } else if (rcv.getParameter(2) instanceof Value value && !rule.hasVariables()) {
            PSpecial tags = new PSpecial();
            PSpecial values = new PSpecial();

              Value tag = new Value(value.getTag());
            tag.setRemoved(value.isRemoved());

            values.addValue(value);
            tags.addValue(tag);

            rcv.getParameters().set(2, tags);
            rcv.getParameters().add(values);

          } else if (rcv.getParameter(3) instanceof Value && rule.hasVariables()) {
            /*
             * 2 tag for the variable (never used so far)
             * 3 variable
             */
            Value value = (Value) rcv.getParameter(2);
            Value tag = new Value(value.getTag());

            rcv.getParameters().set(2, tag);
            rcv.getParameters().add(value);

            // Method to connect the variable with the messages!!!!
          }
        }
        Fact snd = rule.getPostconditionFactByMatchingName("Snd");
        if (snd != null) {
          /*We have Snd with tags
           *
           * 0 Sender
           * 1 Receiver
           * 2 tag/group of tags
           * 3 value/group of values
           */

          if (snd.getParameter(2) instanceof PSpecial valuesAndTags) {

              PSpecial tags = new PSpecial();
            PSpecial values = new PSpecial();

            for (int j = 0; j < valuesAndTags.getGroup().size(); j++) {
              Value value = valuesAndTags.getValue(j);
              Value tag = new Value(value.getTag());
              tag.setRemoved(value.isRemoved());
              tags.addValue(tag);
              values.addValue(value);
            }

            snd.getParameters().set(2, tags);
            snd.getParameters().add(values);

          } else if (snd.getParameter(2) instanceof Value value && !rule.hasVariables()) {
            PSpecial tags = new PSpecial();
            PSpecial values = new PSpecial();

              Value tag = new Value(value.getTag());
            tag.setRemoved(value.isRemoved());

            values.addValue(value);
            tags.addValue(tag);

            snd.getParameters().set(2, tags);
            snd.getParameters().add(values);

          } else if (snd.getParameter(3) instanceof Value && rule.hasVariables()) {
            /*
             * 2 tag for the variable (never used so far)
             * 3 variable
             */
            Value value = (Value) snd.getParameter(2);
            Value tag = new Value(value.getTag());

            snd.getParameters().set(2, tag);
            snd.getParameters().add(value);

            // Method to connect the variable with the messages!!!!
          }
        }
      }
      if (didSomething) {
        log.debug(
            "--------------------------------\nDEMerging of tags "
                    + "and values ended\n--------------------------------");
      }
    }
    return theory;
  }
}
