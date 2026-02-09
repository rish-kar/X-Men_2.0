package com.sermas.x.men.model;

import java.util.ArrayList;
import lombok.*;

/** This class is a visitor for a parse tree. Extends the TamarinBaseVisitor class. */
@NoArgsConstructor
public class TamVisitor extends TamarinBaseVisitor<Object> {

  /**
   * Visits the theory context and returns a list of components.
   *
   * @param ctx the theory context
   * @return a list of components
   */
  public Object visitTheory(TamarinParser.TheoryContext ctx) {
    ArrayList<Component> theory = new ArrayList();
    int v = ctx.getChildCount() - 1;

    for (int i = 0; i < v; ++i) {
      Component tmp = (Component) this.visit(ctx.getChild(i));
      theory.add(tmp);
    }

    return theory;
  }

  /**
   * Visits the component context and returns a Component object.
   *
   * @param ctx the component context
   * @return a Component object
   */
  public Object visitComponent(TamarinParser.ComponentContext ctx) {
    Component newr = (Component) this.visit(ctx.getChild(0));
    return newr;
  }

  /**
   * Visits the protocol rule context and returns a Rule object.
   *
   * @param ctx the protocol rule context
   * @return a Rule object
   */
  public Object visitProtocolrule(TamarinParser.ProtocolruleContext ctx) {
    int v = ctx.getChildCount();
    String nameRule = ctx.getChild(1).getText();
    Rule newRule = new Rule(nameRule);
    ArrayList lets;
    int i;
    if (v > 4) {
      lets = (ArrayList) this.visit(ctx.getChild(3));

      for (i = 0; i < lets.size(); ++i) {
        Variable x = (Variable) lets.get(i);
        newRule.addVariable(x);
      }

      ArrayList facts = (ArrayList) this.visit(ctx.getChild(4));

      for (i = 0; i < facts.size(); ++i) {
        Fact x = (Fact) facts.get(i);
        switch (x.getType()) {
          case PRE:
            newRule.addPrecondition(x);
            break;
          case ACTION:
            newRule.addAction(x);
            break;
          case POST:
            newRule.addPostcondition(x);
            break;
          default:
            throw new IllegalArgumentException("Unknown fact type: " + x.getType());
        }
      }
    } else {
      lets = (ArrayList) this.visit(ctx.getChild(3));

      for (i = 0; i < lets.size(); ++i) {
        Fact x = (Fact) lets.get(i);
        switch (x.getType()) {
          case PRE:
            newRule.addPrecondition(x);
            break;
          case ACTION:
            newRule.addAction(x);
            break;
          case POST:
            newRule.addPostcondition(x);
            break;
          default:
            throw new IllegalArgumentException("Unknown fact type: " + x.getType());
        }
      }
    }

    return newRule;
  }

  /**
   * Visits the generic rule context and returns an ArrayList of Fact objects.
   *
   * @param ctx the generic rule context
   * @return an ArrayList of Fact objects
   */
  public Object visitGenericrule(TamarinParser.GenericruleContext ctx) {
    ArrayList<Fact> array = new ArrayList();
    int v = ctx.getChildCount();
    TypeFact t = TypeFact.PRE;

    for (int i = 0; i < v; ++i) {
      switch (ctx.getChild(i).getText()) {
        case "[":
        case "]":
        case ",":
          break;
        case "-->":
          t = TypeFact.POST;
          break;
        case "--[":
          t = TypeFact.ACTION;
          break;
        case "]->":
          t = TypeFact.POST;
          break;
        default:
          Fact ft = (Fact) this.visit(ctx.getChild(i));
          ft.setType(t);
          array.add(ft);
      }
    }

    return array;
  }

  /**
   * Visits the let block context and returns an ArrayList of Variable objects.
   *
   * @param ctx the let block context
   * @return an ArrayList of Variable objects
   */
  public Object visitLet_block(TamarinParser.Let_blockContext ctx) {
    ArrayList<Variable> variables = new ArrayList();
    int x = ctx.getChildCount();
    String strLet = ctx.getChild(0).getText();

    for (int i = 1; i < x - 1; i += 3) {
      Variable v = new Variable(ctx.getChild(i).getText());
      Object c = this.visit(ctx.getChild(i + 2));
      v.setValues((Special) c);
      variables.add(v);
    }

    return variables;
  }

  /**
   * Visits the fact context and returns a Fact object.
   *
   * @param ctx the fact context
   * @return a Fact object
   */
  public Object visitFact(TamarinParser.FactContext ctx) {
    if (ctx == null || ctx.getChildCount() == 0 || ctx.getChild(0) == null) {
      throw new IllegalArgumentException("Malformed fact node (parser recovery produced empty FactContext).");
    }
    int x = ctx.getChildCount();
    Fact newf = new Fact(ctx.getChild(0).getText());
    if (x >= 4) {
      ArrayList factParam = (ArrayList) this.visit(ctx.getChild(2));
      newf.setArrayListParameters(factParam);
    } else if (x == 3) {
      return newf;
    }

    return newf;
  }

  /**
   * Visits the group of term context and returns a Pspecial object.
   *
   * @param ctx the group of term context
   * @return a Pspecial object
   */
  public Object visitGroup_of_terms(TamarinParser.Group_of_termsContext ctx) {
    PSpecial gop = new PSpecial();
    int v = ctx.getChildCount();
    int i = 0;

    while (i < v) {
      switch (ctx.getChild(i).getText()) {
        default:
          Value newParameter = new Value(ctx.getChild(i).getText(), false, false, false);
          gop.addValue(newParameter);
          // fall through
        case "<":
        case ">":
        case ",":
          ++i;
      }
    }

    return gop;
  }

  /**
   * Visits the terms context and returns an ArrayList of fact parameters.
   *
   * @param ctx the parse tree
   * @return an ArrayList of fact parameters
   */
  public Object visitTerms(TamarinParser.TermsContext ctx) {
    ArrayList factParameters = new ArrayList();
    int v = ctx.getChildCount();
    int i = 0;

    while (i < v) {
      switch (ctx.getChild(i).getText()) {
        default:
          Object termsOfFact = this.visit(ctx.getChild(i));
          if (termsOfFact instanceof PSpecial) {
            factParameters.add(termsOfFact);
          } else if (termsOfFact instanceof Value) {
            factParameters.add(termsOfFact);
          } else {
            Value newParameter;
            if (termsOfFact instanceof Fact) {
              newParameter = new Value(ctx.getChild(i).getText(), false, false, false);
              factParameters.add(newParameter);
            } else if (termsOfFact instanceof Nary_app) {
              newParameter = new Value(ctx.getChild(i).getText(), false, false, false);
              factParameters.add(newParameter);
            }
          }
          // fall through
        case ",":
          ++i;
      }
    }

    return factParameters;
  }

  /**
   * Visit Binary application context and returns a FSpecial object.
   *
   * @param ctx the parse tree
   * @return a FSpecial object
   */
  public Object visitBinary_app(TamarinParser.Binary_appContext ctx) {
    FSpecial value = new FSpecial(ctx.getChild(0).getText());
    int v = ctx.getChildCount();
    if (v == 5) {
      value.addValue((Value) this.visit(ctx.getChild(2)));
    } else {
      for (int i = 2; i < v - 2; i += 2) {
        value.addValue((Value) this.visit(ctx.getChild(i)));
      }
    }

    value.setKey((Abs_Value) this.visit(ctx.getChild(v - 1)));
    return value;
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#binary_fun}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitBinary_fun(TamarinParser.Binary_funContext ctx) {
    String x = ctx.getChild(0).getText();
    return x;
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#terms}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitTerm(TamarinParser.TermContext ctx) {
    int v = ctx.getChildCount();
    return v > 2
        ? new Value((String) this.visit(ctx.getChild(1)), false, false, false)
        : this.visit(ctx.getChild(0));
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#nullary_fun}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitNullary_fun(TamarinParser.Nullary_funContext ctx) {
    return ctx.getChild(0).getText();
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#nary_app}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitNary_app(TamarinParser.Nary_appContext ctx) {
    int v = ctx.getChildCount();
    Nary_app newNary = new Nary_app((String) this.visit(ctx.getChild(0)));
    newNary.addValue((Value) this.visit(ctx.getChild(2)));
    return newNary;
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#literal}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitLiteral(TamarinParser.LiteralContext ctx) {
    return new Value(ctx.getText(), false, false, false);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#built_in}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitBuilt_in(TamarinParser.Built_inContext ctx) {
    int v = ctx.getChildCount();
    String x = ctx.getChild(0).getText();
    Builtins value = new Builtins(x);

    for (int i = 2; i < v; i += 2) {
      value.addValue((String) this.visit(ctx.getChild(i)));
    }

    return value;
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#builtin_name}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitBuiltin_name(TamarinParser.Builtin_nameContext ctx) {
    String x = ctx.getChild(0).getText();
    return x;
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#functions}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitFunctions(TamarinParser.FunctionsContext ctx) {
    return this.visit(ctx.getChild(2));
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#function_symbol}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitFunction_symbol(TamarinParser.Function_symbolContext ctx) {
    String name = ctx.getChild(0).getText();
    int numberOfValues = Integer.parseInt(ctx.getChild(2).getText());
    return new Function(name, numberOfValues);
  }
}
