package com.xmen.model;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

/** This class is a visitor for a parse tree. Extends the TamarinBaseVisitor class. */
@NoArgsConstructor
@Slf4j
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
      try {
        Object result = this.visit(ctx.getChild(i));
        if (result instanceof Component) {
          theory.add((Component) result);
        }
      } catch (Exception e) {
        log.warn("Failed to parse component at index {}: {}", i, e.getMessage());
        // Continue parsing other components
      }
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
        Object item = facts.get(i);
        if (item instanceof Fact) {
          Fact x = (Fact) item;
          if (x.getType() != null) {
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
                log.warn("Unknown fact type: {} for fact: {}", x.getType(), x.getF_name());
            }
          } else {
            log.warn("Fact type is null for: {}", x.getF_name());
          }
        }
      }
    } else {
      lets = (ArrayList) this.visit(ctx.getChild(3));

      for (i = 0; i < lets.size(); ++i) {
        Object item = lets.get(i);
        if (item instanceof Fact) {
          Fact x = (Fact) item;
          if (x.getType() != null) {
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
                log.warn("Unknown fact type: {} for fact: {}", x.getType(), x.getF_name());
            }
          } else {
            log.warn("Fact type is null for: {}", x.getF_name());
          }
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
          try {
            Object result = this.visit(ctx.getChild(i));
            if (result instanceof Fact) {
              Fact ft = (Fact) result;
              ft.setType(t);
              array.add(ft);
            } else if (result != null) {
              // If we got something other than a Fact, create a placeholder fact
              log.warn("Expected Fact but got {} for: {}", result.getClass().getSimpleName(), ctx.getChild(i).getText());
            }
          } catch (Exception e) {
            log.warn("Failed to parse fact at index {}: {}", i, e.getMessage());
          }
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
   * Supports nested structures like <<m1,m2>,m2> and function applications like senc(m1,m2).
   *
   * @param ctx the group of term context
   * @return a Pspecial object
   */
  public Object visitGroup_of_terms(TamarinParser.Group_of_termsContext ctx) {
    PSpecial gop = new PSpecial();
    int v = ctx.getChildCount();
    int i = 0;

    while (i < v) {
      String childText = ctx.getChild(i).getText();
      switch (childText) {
        case "<":
        case ">":
        case ",":
          ++i;
          break;
        default:
          try {
            // Recursively visit the child to properly handle nested structures
            Object childResult = this.visit(ctx.getChild(i));
            if (childResult instanceof Value) {
              gop.addValue((Value) childResult);
            } else if (childResult instanceof PSpecial) {
              // Nested group - convert to Value with text representation for compatibility
              Value nestedValue = new Value(childText, false, false, false);
              gop.addValue(nestedValue);
            } else if (childResult instanceof FSpecial) {
              // Function application - convert to Value with text representation
              Value funcValue = new Value(childText, false, false, false);
              gop.addValue(funcValue);
            } else if (childResult != null) {
              // Fallback to text representation
              Value newParameter = new Value(childText, false, false, false);
              gop.addValue(newParameter);
            } else {
              // Null result - use text
              Value newParameter = new Value(childText, false, false, false);
              gop.addValue(newParameter);
            }
          } catch (Exception e) {
            // If recursive visit fails, use text representation
            log.debug("Using text representation for group element: {}", childText);
            Value newParameter = new Value(childText, false, false, false);
            gop.addValue(newParameter);
          }
          ++i;
          break;
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
          try {
            Object termsOfFact = this.visit(ctx.getChild(i));
            if (termsOfFact instanceof PSpecial) {
              factParameters.add(termsOfFact);
            } else if (termsOfFact instanceof Value) {
              factParameters.add(termsOfFact);
            } else if (termsOfFact instanceof FSpecial) {
              // Handle FSpecial (function applications like senc{x}k)
              factParameters.add(termsOfFact);
            } else if (termsOfFact instanceof Nary_app) {
              // Keep as Nary_app or convert to Value
              Value newParameter = new Value(ctx.getChild(i).getText(), false, false, false);
              factParameters.add(newParameter);
            } else if (termsOfFact instanceof Fact) {
              Value newParameter = new Value(ctx.getChild(i).getText(), false, false, false);
              factParameters.add(newParameter);
            } else if (termsOfFact != null) {
              // Fallback: convert to Value using text representation
              Value newParameter = new Value(ctx.getChild(i).getText(), false, false, false);
              factParameters.add(newParameter);
            }
          } catch (Exception e) {
            // If parsing fails, use the raw text as a Value
            String text = ctx.getChild(i).getText();
            if (text != null && !text.isEmpty() && !",".equals(text)) {
              log.debug("Using text representation for complex term: {}", text);
              Value newParameter = new Value(text, false, false, false);
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
   * Modified to handle comma-separated arguments like senc(m1,m2).
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public Object visitNary_app(TamarinParser.Nary_appContext ctx) {
    try {
      int v = ctx.getChildCount();
      Object funcName = this.visit(ctx.getChild(0));
      String fname = funcName instanceof String ? (String) funcName : ctx.getChild(0).getText();
      Nary_app newNary = new Nary_app(fname);

      // Parse arguments: func '(' arg1 ',' arg2 ',' ... ')'
      // Children: [0]=func, [1]='(', [2]=arg1, [3]=',', [4]=arg2, ..., [last]=')'
      for (int i = 2; i < v - 1; i++) {
        String childText = ctx.getChild(i).getText();
        if (",".equals(childText)) {
          continue; // skip commas
        }
        try {
          Object argResult = this.visit(ctx.getChild(i));
          if (argResult instanceof Value) {
            newNary.addValue((Value) argResult);
          } else if (argResult instanceof PSpecial) {
            // Handle nested pairs like <m1,m2>
            newNary.addValue(new Value(childText, false, false, false));
          } else if (argResult != null) {
            newNary.addValue(new Value(childText, false, false, false));
          }
        } catch (Exception e) {
          // Fallback to text representation
          newNary.addValue(new Value(childText, false, false, false));
        }
      }
      return newNary;
    } catch (Exception e) {
      // If parsing fails, return a Value with the full text
      log.debug("Failed to parse nary_app, using text: {}", ctx.getText());
      return new Value(ctx.getText(), false, false, false);
    }
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
