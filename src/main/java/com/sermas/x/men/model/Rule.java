package com.sermas.x.men.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
public class Rule extends Component implements Cloneable {
    public int id;

    @NonNull
    public String rule_name;
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

    public Rule(String rule_name) {
        this.id = count.incrementAndGet();
        this.rule_name = rule_name;
        this.hasMutation = false;
        this.variables = new ArrayList();
        this.preconditions = new ArrayList();
        this.postconditions = new ArrayList();
        this.actions = new ArrayList();
    }

    public void mutatePostconditions(ArrayList<Fact> mutants) throws CloneNotSupportedException {
        this.postconditions.clear();
        Iterator var2 = mutants.iterator();

        while(var2.hasNext()) {
            Fact p = (Fact)var2.next();
            this.postconditions.add(p.clone());
        }

    }

    public void mutateActions(ArrayList<Fact> mutants) throws CloneNotSupportedException {
        this.actions.clear();
        Iterator var2 = mutants.iterator();

        while(var2.hasNext()) {
            Fact p = (Fact)var2.next();
            this.actions.add(p.clone());
        }

    }

    public void mutatePrecondition(ArrayList<Fact> mutants) throws CloneNotSupportedException {
        this.preconditions.clear();
        Iterator var2 = mutants.iterator();

        while(var2.hasNext()) {
            Fact p = (Fact)var2.next();
            this.preconditions.add(p.clone());
        }

    }

    public boolean addPrecondition(Fact x) {
        this.preconditions.add(x);
        return true;
    }

    public void replaceActions(ArrayList<Fact> newActions) {
        this.actions.clear();

        for(int i = 0; i < newActions.size(); ++i) {
            this.actions.add(((Fact)newActions.get(i)).clone());
        }

    }

    public boolean addAction(Fact x) {
        this.actions.add(x);
        return true;
    }

    public boolean addAction(Fact x, int i) {
        this.actions.set(i, x);
        return true;
    }

    public boolean addPostcondition(Fact x) {
        this.postconditions.add(x);
        return true;
    }

    public boolean addVariable(Variable x) {
        this.variables.add(x);
        return true;
    }

    public boolean hasVariables() {
        return this.variables.size() > 0;
    }

    public Fact getSinglePreconditionFact(int i) {
        return (Fact)this.preconditions.get(i);
    }

    public Fact getSinglePostconditionFact(int i) {
        return (Fact)this.postconditions.get(i);
    }

    public Fact getSingleActionFact(int i) {
        return (Fact)this.actions.get(i);
    }

    public String toString() {
        String str = "rule " + this.rule_name + ":\n";
        int counter;
        if (!this.variables.isEmpty()) {
            str = str.concat("let");
            str = str.concat("\n");

            for(counter = 0; counter < this.variables.size(); ++counter) {
                Variable x = (Variable)this.variables.get(counter);
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
        for(cc = 0; cc < this.preconditions.size(); ++cc) {
            if (!((Fact)this.preconditions.get(cc)).isRemoved()) {
                if (counter == 0) {
                    str = str.concat(((Fact)this.preconditions.get(cc)).toString());
                    ++counter;
                } else if (counter <= 0 && cc >= this.preconditions.size() - 1) {
                    if (cc == this.preconditions.size() - 1) {
                        str = str.concat(((Fact)this.preconditions.get(cc)).toString());
                    }
                } else {
                    str = str.concat("\n, ");
                    str = str.concat(((Fact)this.preconditions.get(cc)).toString());
                    ++counter;
                }
            }
        }

        str = str.concat("\n]\n");
        if (this.actions.size() > 0) {
            cc = 0;
            str = str.concat("--[ ");

            for(int po = 0; po < this.actions.size(); ++po) {
                if (!((Fact)this.actions.get(po)).isRemoved()) {
                    if (cc == 0) {
                        str = str.concat(((Fact)this.actions.get(po)).toString());
                        ++cc;
                    } else if (cc <= 0 && po >= this.actions.size() - 1) {
                        if (po == this.actions.size() - 1) {
                            str = str.concat(((Fact)this.actions.get(po)).toString());
                        }
                    } else {
                        str = str.concat("\n, ");
                        str = str.concat(((Fact)this.actions.get(po)).toString());
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

        for(int po = 0; po < this.postconditions.size(); ++po) {
            if (!((Fact)this.postconditions.get(po)).isRemoved()) {
                if (po == 0) {
                    str = str.concat(((Fact)this.postconditions.get(po)).toString());
                } else if (po <= 0 && po >= this.postconditions.size() - 1) {
                    if (po == this.postconditions.size() - 1) {
                        str = str.concat(((Fact)this.postconditions.get(po)).toString());
                    }
                } else {
                    str = str.concat("\n, ");
                    str = str.concat(((Fact)this.postconditions.get(po)).toString());
                }
            }
        }

        str = str.concat("\n]\n\n");
        return str;
    }

    private ArrayList<Variable> cloneListVariable(ArrayList<Variable> array) {
        ArrayList<Variable> clone = new ArrayList();
        Iterator var3 = this.variables.iterator();

        while(var3.hasNext()) {
            Variable v = (Variable)var3.next();
            clone.add(v.clone());
        }

        return clone;
    }

    private void letArrangement(ArrayList<Variable> list, ArrayList<Fact> clone1, ArrayList<Fact> clone2) {
        Iterator var4 = list.iterator();

        while(var4.hasNext()) {
            Variable var = (Variable)var4.next();
            Fact rcv = this.getPreconditionFactByMatchingName(clone1, "Rcv");
            if (rcv != null && rcv.getParameter(2) instanceof Value) {
                Value val = (Value)rcv.getParameter(2);
                if (val.getName().equals(var.getName())) {
                    var.setTag(val.getTag());
                    rcv.getParameters().set(2, var);
                }
            }

            Fact snd = this.getPostconditionFactByMatchingName(clone2, "Snd");
            if (snd != null && snd.getParameter(2) instanceof Value) {
                Value val = (Value)snd.getParameter(2);
                if (val.getName().equals(var.getName())) {
                    var.setTag(val.getTag());
                    snd.getParameters().set(2, var);
                }
            }
        }

    }

    private void arrangeLets(ArrayList<Variable> list) {
        for(int j = 0; j < list.size(); ++j) {
            Variable v = (Variable)list.get(j);
            Special s = v.getValues();
            ArrayList obj;
            int k;
            Object o;
            String variableName;
            int l;
            if (s instanceof FSpecial) {
                obj = ((FSpecial)s).getGroup();

                for(k = 0; k < obj.size(); ++k) {
                    o = obj.get(k);
                    if (o instanceof Variable) {
                        variableName = ((Variable)o).getName();

                        for(l = 0; l < list.size(); ++l) {
                            if (l != j && variableName.equals(((Variable)list.get(l)).getName())) {
                                obj.set(k, list.get(l));
                                break;
                            }
                        }
                    }
                }
            } else if (s instanceof PSpecial) {
                obj = ((PSpecial)s).getGroup();

                for(k = 0; k < obj.size(); ++k) {
                    o = obj.get(k);
                    if (o instanceof Variable) {
                        variableName = ((Variable)o).getName();

                        for(l = 0; l < list.size(); ++l) {
                            if (l != j && variableName.equals(((Variable)list.get(l)).getName())) {
                                obj.set(k, (Value)list.get(l));
                                break;
                            }
                        }
                    }
                }
            }
        }

    }

    public Rule clone() {
        try {
            Rule p = (Rule)super.clone();
            ArrayList<Fact> clone1 = cloneList(this.preconditions);
            ArrayList<Fact> clone2 = cloneList(this.postconditions);
            ArrayList clone3;
            if (this.hasVariables()) {
                clone3 = this.cloneListVariable(this.variables);
                this.arrangeLets(clone3);
                this.letArrangement(clone3, clone1, clone2);
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

    public Rule findFact(Fact x) {
        Iterator var2 = this.preconditions.iterator();

        Fact ft;
        do {
            if (!var2.hasNext()) {
                var2 = this.postconditions.iterator();

                do {
                    if (!var2.hasNext()) {
                        var2 = this.actions.iterator();

                        do {
                            if (!var2.hasNext()) {
                                return null;
                            }

                            ft = (Fact)var2.next();
                        } while(ft.compareTo(x) != 1);

                        return this;
                    }

                    ft = (Fact)var2.next();
                } while(ft.compareTo(x) != 1);

                return this;
            }

            ft = (Fact)var2.next();
        } while(ft.compareTo(x) != 1);

        return this;
    }

    public Fact getFactByName(String s) {
        Iterator var2 = this.preconditions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact)var2.next();
        } while(!x.getF_name().equals(s));

        return x;
    }

    public Fact getPreconditionFactByMatchingName(ArrayList<Fact> array, String s) {
        Iterator var3 = array.iterator();

        Fact x;
        do {
            if (!var3.hasNext()) {
                return null;
            }

            x = (Fact)var3.next();
        } while(!x.getF_name().startsWith(s));

        return x;
    }

    public Fact getPreconditionFactByMatchingName(String s) {
        Iterator var2 = this.preconditions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact)var2.next();
        } while(!x.getF_name().startsWith(s));

        return x;
    }

    public Fact getActionFactByMatchingName(String s) {
        Iterator var2 = this.actions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact)var2.next();
        } while(!x.getF_name().startsWith(s));

        return x;
    }

    public Fact getPostconditionFactByMatchingName(ArrayList<Fact> array, String s) {
        Iterator var3 = array.iterator();

        Fact x;
        do {
            if (!var3.hasNext()) {
                return null;
            }

            x = (Fact)var3.next();
        } while(!x.getF_name().startsWith(s));

        return x;
    }

    public Fact getPostconditionFactByMatchingName(String s) {
        Iterator var2 = this.postconditions.iterator();

        Fact x;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            x = (Fact)var2.next();
        } while(!x.getF_name().startsWith(s));

        return x;
    }

    public Fact getPostconditionFactByMatchingNames(String stateName, String agentName, String state) {
        Iterator var4 = this.postconditions.iterator();

        Fact x;
        do {
            if (!var4.hasNext()) {
                return null;
            }

            x = (Fact)var4.next();
        } while(!x.getF_name().startsWith(stateName) || !((Value)x.getParameter(0)).getName().replaceAll("[^a-zA-Z0-9]", "").equals(agentName.replaceAll("[^a-zA-Z0-9]", "")) || !((Value)x.getParameter(1)).getName().equals(state));

        return x;
    }

    public Fact getPreconditionFactByMatchingNames(String stateName, String agentName, String state) {
        Iterator var4 = this.getPreconditions().iterator();

        Fact x;
        do {
            if (!var4.hasNext()) {
                return null;
            }

            x = (Fact)var4.next();
        } while(!x.getF_name().startsWith(stateName) || !((Value)x.getParameter(0)).getName().replaceAll("[^a-zA-Z0-9]", "").equals(agentName.replaceAll("[^a-zA-Z0-9]", "")) || !((Value)x.getParameter(1)).getName().equals(state));

        return x;
    }

    public ArrayList<Mutants> extractRemovedValues() {
        ArrayList<Mutants> mutations = new ArrayList();
        Iterator var2 = this.preconditions.iterator();

        label40:
        while(true) {
            Fact x;
            do {
                if (!var2.hasNext()) {
                    return new ArrayList(new LinkedHashSet(mutations));
                }

                x = (Fact)var2.next();
            } while(!x.isRemoved());

            Iterator var4 = x.getParameters().iterator();

            while(true) {
                while(true) {
                    if (!var4.hasNext()) {
                        continue label40;
                    }

                    Object o = var4.next();
                    if (o instanceof Value) {
                        Mutants m = new Mutants((Value)o, (Value)null);
                        mutations.add(m);
                    } else if (o instanceof PSpecial) {
                        Iterator var6 = ((PSpecial)o).getGroup().iterator();

                        while(var6.hasNext()) {
                            Value v = (Value)var6.next();
                            Mutants m = new Mutants(v, (Value)null);
                            mutations.add(m);
                        }
                    }
                }
            }
        }
    }

    public ArrayList<Mutants> extractMutatedValue() {
        ArrayList<Mutants> mutations = new ArrayList();
        Iterator var2 = this.preconditions.iterator();

        while(true) {
            Value l;
            Value v;
            do {
                do {
                    while(true) {
                        Fact x;
                        do {
                            if (!var2.hasNext()) {
                                return new ArrayList(new LinkedHashSet(mutations));
                            }

                            x = (Fact)var2.next();
                        } while(x.getParameters().size() < 4);

                        if (x.getParameter(2) instanceof Value && x.getParameter(3) instanceof Value) {
                            l = (Value)x.getParameter(2);
                            v = (Value)x.getParameter(3);
                            break;
                        }

                        PSpecial l1 = (PSpecial)x.getParameter(2);
                        PSpecial v1 = (PSpecial)x.getParameter(3);

                        for(int q = 0; q < x.getParameters().size(); ++q) {
                            if ((l1.getValue(q).isAdded() || l1.getValue(q).isModified() || l1.getValue(q).isRemoved()) && (v1.getValue(q).isAdded() || v1.getValue(q).isModified() || v1.getValue(q).isRemoved())) {
                                Mutants m = new Mutants(l1.getValue(q), v1.getValue(q));
                                mutations.add(m);
                            }
                        }
                    }
                } while(!l.isAdded() && !l.isModified() && !l.isRemoved());
            } while(!v.isAdded() && !v.isModified() && !v.isRemoved());

            Mutants m = new Mutants(l, v);
            mutations.add(m);
        }
    }

    private static ArrayList<Fact> cloneList(ArrayList<Fact> list) {
        ArrayList<Fact> clone = new ArrayList(list.size());
        Iterator var2 = list.iterator();

        while(var2.hasNext()) {
            Fact item = (Fact)var2.next();
            clone.add(item.clone());
        }

        return clone;
    }

    public boolean hasMutation() {
        return this.hasMutation;
    }

    public boolean isHuman() {
        return !this.actions.isEmpty() && ((Fact)this.actions.get(0)).getF_name().equals("H");
    }

    public void consistencyCheck() {
        Value receiver = (Value)((Fact)this.postconditions.get(1)).getParameter(1);
        if (receiver.isRemoved()) {
            ((Fact)this.postconditions.get(1)).setRemoved(true);
        }

    }

    public Rule findNextRule(String agentName, int state) {
        Value agent = (Value)((Fact)this.preconditions.get(0)).getParameter(0);
        Value stat = (Value)((Fact)this.preconditions.get(0)).getParameter(1);
        int statInt = Integer.parseInt(stat.getName().replaceAll("[^0-9]", ""));
        return agent.getName().equals(agentName) && statInt >= state ? this : null;
    }
}