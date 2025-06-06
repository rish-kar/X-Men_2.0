package com.sermas.x.men.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Rule class represents a rule in the system, containing various properties such as rule name,
 * type, variables, preconditions, postconditions, and actions.
 */
@Getter
@Setter
public class Rule extends Component implements Cloneable {

    public int id;
    @NonNull public String rule_name;
    public boolean isChannel;
    public Type typo;
    public boolean hasMutation;
    public Rule associateMutation;
    public ArrayList<Variable> variables;
    public ArrayList<Fact> preconditions;
    public ArrayList<Fact> postconditions;
    public ArrayList<Fact> actions;
    public Rule previous;
    public Rule next;
    public static final AtomicInteger count = new AtomicInteger(0);

    /**
     * Constructor for Rule class.
     *
     * @param rule_name the name of the rule
     */
    public Rule(String rule_name) {
        this.id = count.incrementAndGet();
        this.rule_name = rule_name;
        this.hasMutation = false;
        this.variables = new ArrayList();
        this.preconditions = new ArrayList();
        this.postconditions = new ArrayList();
        this.actions = new ArrayList();
    }

    /**
     * Adds a precondition to the rule.
     *
     * @param x the Fact to be added as a precondition
     * @return true if the precondition was added successfully
     */
    public boolean addPrecondition(Fact x) {
        this.preconditions.add(x);
        return true;
    }

    /**
     * Adds an action to the rule.
     *
     * @param x the Fact to be added as an action
     * @return true if the action was added successfully
     */
    public boolean addAction(Fact x) {
        this.actions.add(x);
        return true;
    }

    /**
     * Adds a postcondition to the rule.
     *
     * @param x the Fact to be added as a postcondition
     * @return true if the postcondition was added successfully
     */
    public boolean addPostcondition(Fact x) {
        this.postconditions.add(x);
        return true;
    }

    /**
     * Adds a variable to the rule.
     *
     * @param x the Variable to be added
     * @return true if the variable was added successfully
     */
    public boolean addVariable(Variable x) {
        this.variables.add(x);
        return true;
    }

    /**
     * Checks if the rule has any variables defined.
     *
     * @return true if the rule has variables, false otherwise
     */
    public boolean hasVariables() {
        return this.variables.size() > 0;
    }

    /**
     * Returns the single precondition fact at the specified index.
     *
     * @param i the index of the precondition fact to retrieve
     * @return the Fact at the specified index
     */
    public Fact getSinglePreconditionFact(int i) {
        return this.preconditions.get(i);
    }

    /**
     * Returns string representation of the Rule object.
     *
     * @return a string in a certain format
     */
    public String toString() {
        String str = "rule " + this.rule_name + ":\n";
        int counter;
        if (!this.variables.isEmpty()) {
            str = str.concat("let");
            str = str.concat("\n");

            for (counter = 0; counter < this.variables.size(); ++counter) {
                Variable x = this.variables.get(counter);
                String ss = x.toString();
                if (!ss.equals("")) {
                    str = str.concat(ss);
                    str = str.concat("\n");
                }
            }

            str = str.concat("in");
            str = str.concat("\n");
        }

        if (str.equals("rule " + this.rule_name + ":\nlet\nin\n")) {
            str = "rule " + this.rule_name + ":\n";
        }

        str = str.concat("[ ");
        counter = 0;

        int cc;
        for (cc = 0; cc < this.preconditions.size(); ++cc) {
            if (!this.preconditions.get(cc).isRemoved()) {
                if (counter == 0) {
                    str = str.concat(this.preconditions.get(cc).toString());
                    ++counter;
                } else if (counter <= 0 && cc >= this.preconditions.size() - 1) {
                    if (cc == this.preconditions.size() - 1) {
                        str = str.concat(this.preconditions.get(cc).toString());
                    }
                } else {
                    str = str.concat("\n, ");
                    str = str.concat(this.preconditions.get(cc).toString());
                    ++counter;
                }
            }
        }

        str = str.concat("\n]\n");
        if (this.actions.size() > 0) {
            cc = 0;
            str = str.concat("--[ ");

            for (int po = 0; po < this.actions.size(); ++po) {
                if (!this.actions.get(po).isRemoved()) {
                    if (cc == 0) {
                        str = str.concat(this.actions.get(po).toString());
                        ++cc;
                    } else if (cc <= 0 && po >= this.actions.size() - 1) {
                        if (po == this.actions.size() - 1) {
                            str = str.concat(this.actions.get(po).toString());
                        }
                    } else {
                        str = str.concat("\n, ");
                        str = str.concat(this.actions.get(po).toString());
                        ++cc;
                    }
                }
            }

            str = str.concat("\n]->\n");
        } else {
            str = str.concat("-->\n");
        }

        Pattern word = Pattern.compile("\\[[^\\[]\\n\\]->");
        Matcher match = word.matcher(str);
        if (match.find()) {
            str = str.replaceFirst("\\[[^\\[]\\n\\]->", ">");
        }

        str = str.concat("[ ");

        for (int po = 0; po < this.postconditions.size(); ++po) {
            if (!this.postconditions.get(po).isRemoved()) {
                if (po == 0) {
                    str = str.concat(this.postconditions.get(po).toString());
                } else if (po <= 0 && po >= this.postconditions.size() - 1) {
                    if (po == this.postconditions.size() - 1) {
                        str = str.concat(this.postconditions.get(po).toString());
                    }
                } else {
                    str = str.concat("\n, ");
                    str = str.concat(this.postconditions.get(po).toString());
                }
            }
        }

        str = str.concat("\n]\n\n");
        return str;
    }

    /**
     * Clones the list of Variable objects.
     *
     * @param array the ArrayList of Variable objects to be cloned
     * @return a new ArrayList containing cloned Variable objects
     */
    private ArrayList<Variable> cloneListVariable(ArrayList<Variable> array) {
        ArrayList<Variable> clone = new ArrayList();
        Iterator var3 = this.variables.iterator();

        while (var3.hasNext()) {
            Variable v = (Variable) var3.next();
            clone.add(v.clone());
        }

        return clone;
    }

    /**
     * Arranges the variables in the rule based on their names and tags.
     *
     * @param list the list of Variable objects to be arranged
     * @param clone1 the cloned preconditions
     * @param clone2 the cloned postconditions
     */
    private void letArrangement(
            ArrayList<Variable> list, ArrayList<Fact> clone1, ArrayList<Fact> clone2) {
        Iterator var4 = list.iterator();

        while (var4.hasNext()) {
            Variable var = (Variable) var4.next();
            Fact rcv = this.getPreconditionFactByMatchingName(clone1, "Rcv");
            if (rcv != null && rcv.getParameter(2) instanceof Value val) {
                if (val.getName().equals(var.getName())) {
                    var.setTag(val.getTag());
                    rcv.getParameters().set(2, var);
                }
            }

            Fact snd = this.getPostconditionFactByMatchingName(clone2, "Snd");
            if (snd != null && snd.getParameter(2) instanceof Value val) {
                if (val.getName().equals(var.getName())) {
                    var.setTag(val.getTag());
                    snd.getParameters().set(2, var);
                }
            }
        }
    }

    /**
     * Arranges the lets in the list of Variable objects.
     *
     * @param list the list of Variable objects to be arranged
     */
    private void arrangeLets(ArrayList<Variable> list) {
        for (int j = 0; j < list.size(); ++j) {
            Variable v = list.get(j);
            Special s = v.getValues();
            ArrayList obj;
            int k;
            Object o;
            String variableName;
            int l;
            if (s instanceof FSpecial) {
                obj = s.getGroup();

                for (k = 0; k < obj.size(); ++k) {
                    o = obj.get(k);
                    if (o instanceof Variable) {
                        variableName = ((Variable) o).getName();

                        for (l = 0; l < list.size(); ++l) {
                            if (l != j && variableName.equals(list.get(l).getName())) {
                                obj.set(k, list.get(l));
                                break;
                            }
                        }
                    }
                }
            } else if (s instanceof PSpecial) {
                obj = s.getGroup();

                for (k = 0; k < obj.size(); ++k) {
                    o = obj.get(k);
                    if (o instanceof Variable) {
                        variableName = ((Variable) o).getName();

                        for (l = 0; l < list.size(); ++l) {
                            if (l != j && variableName.equals(list.get(l).getName())) {
                                obj.set(k, list.get(l));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Clones the Rule object, creating a new instance with the same properties. This method ensures
     * that the cloned object has its own copy of the preconditions, postconditions, actions, and
     * variables lists,
     *
     * @return a new Rule object that is a clone of the current instance
     */
    public Rule clone() {
        try {
            Rule p = (Rule) super.clone();
            ArrayList<Fact> clone1 = cloneList(this.preconditions);
            ArrayList<Fact> clone2 = cloneList(this.postconditions);
            ArrayList clone3;
            if (this.hasVariables()) {
                clone3 = this.cloneListVariable(this.variables);
                p.arrangeLets(clone3);
                p.letArrangement(clone3, clone1, clone2);
                p.variables = clone3;
            }

            p.preconditions = clone1;
            p.postconditions = clone2;
            clone3 = cloneList(this.actions);
            p.actions = clone3;
            return p;
        } catch (CloneNotSupportedException var5) {
            CloneNotSupportedException ex = var5;
            throw new RuntimeException(ex);
        }
    }

    /**
     * Retrieves a precondition fact from the provided array that matches the specified name.
     *
     * @param array the ArrayList of Fact objects to search through
     * @param s the name to match against the Fact names
     * @return the first Fact that matches the name, or null if no match is found
     */
    public Fact getPreconditionFactByMatchingName(ArrayList<Fact> array, String s) {
        Iterator var3 = array.iterator();

        Fact x;
        do {
            if (!var3.hasNext()) {
                return null;
            }

            x = (Fact) var3.next();
        } while (!x.getF_name().startsWith(s));

        return x;
    }

    /**
     * Retrieves a precondition fact from the rule that matches the specified name.
     *
     * @param s the name to match against the Fact names
     * @return the first Fact that matches the name, or null if no match is found
     */
    public Fact getPreconditionFactByMatchingName(String s) {
        Iterator var2 = this.preconditions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact) var2.next();
        } while (!x.getF_name().startsWith(s));

        return x;
    }

    /**
     * Retrieves an action fact from the provided array that matches the specified name.
     *
     * @param s the name to match against the Fact names
     * @return the first Fact that matches the name, or null if no match is found
     */
    public Fact getActionFactByMatchingName(String s) {
        Iterator var2 = this.actions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact) var2.next();
        } while (!x.getF_name().startsWith(s));

        return x;
    }

    /**
     * Retrieves a postcondition fact from the provided array that matches the specified name.
     *
     * @param array the ArrayList of Fact objects to search through
     * @param s the name to match against the Fact names
     * @return the first Fact that matches the name, or null if no match is found
     */
    public Fact getPostconditionFactByMatchingName(ArrayList<Fact> array, String s) {
        Iterator var3 = array.iterator();

        Fact x;
        do {
            if (!var3.hasNext()) {
                return null;
            }

            x = (Fact) var3.next();
        } while (!x.getF_name().startsWith(s));

        return x;
    }

    /**
     * Retrieves a postcondition fact from the rule that matches the specified name.
     *
     * @param s the name to match against the Fact names
     * @return the first Fact that matches the name, or null if no match is found
     */
    public Fact getPostconditionFactByMatchingName(String s) {
        Iterator var2 = this.postconditions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact) var2.next();
        } while (!x.getF_name().startsWith(s));

        return x;
    }

    /**
     * Retrieves a postcondition fact from the rule that matches the specified state name, agent name,
     * and state.
     *
     * @param stateName the name of the state to match against the Fact names
     * @param agentName the name of the agent to match against the Fact parameters
     * @param state the state to match against the Fact parameters
     * @return the first Fact that matches the criteria, or null if no match is found
     */
    public Fact getPostconditionFactByMatchingNames(
            String stateName, String agentName, String state) {
        Iterator var4 = this.postconditions.iterator();

        Fact x;
        do {
            if (!var4.hasNext()) {
                return null;
            }

            x = (Fact) var4.next();
        } while (!x.getF_name().startsWith(stateName)
                || !((Value) x.getParameter(0))
                .getName()
                .replaceAll("[^a-zA-Z0-9]", "")
                .equals(agentName.replaceAll("[^a-zA-Z0-9]", ""))
                || !((Value) x.getParameter(1)).getName().equals(state));

        return x;
    }

    /**
     * Clones the provided list of Fact objects.
     *
     * @param list the ArrayList of Fact objects to be cloned
     * @return a new ArrayList containing cloned Fact objects
     */
    private static ArrayList<Fact> cloneList(ArrayList<Fact> list) {
        ArrayList<Fact> clone = new ArrayList(list.size());
        Iterator var2 = list.iterator();

        while (var2.hasNext()) {
            Fact item = (Fact) var2.next();
            clone.add(item.clone());
        }

        return clone;
    }

    /**
     * Checks if the rule is associated with a human agent.
     *
     * @return true if the first action in the rule is a human action, false otherwise
     */
    public boolean isHuman() {
        return !this.actions.isEmpty() && this.actions.get(0).getF_name().equals("H");
    }

    /**
     * Finds the next rule based on the agent name and state.
     *
     * @param agentName the name of the agent to match
     * @param state the state to match against the rule's preconditions
     * @return the Rule if it matches the criteria, or null if no match is found
     */
    public Rule findNextRule(String agentName, int state) {
        Value agent = (Value) this.preconditions.get(0).getParameter(0);
        Value stat = (Value) this.preconditions.get(0).getParameter(1);
        int statInt = Integer.parseInt(stat.getName().replaceAll("[^0-9]", ""));
        return agent.getName().equals(agentName) && statInt >= state ? this : null;
    }
}
