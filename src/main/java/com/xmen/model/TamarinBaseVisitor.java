package com.xmen.model;

import lombok.NoArgsConstructor;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

/**
 * This class provides an empty implementation of {@link TamarinVisitor}, which can be extended to
 * create a visitor which only needs to handle a subset of the available methods.
 *
 * @param <T> the return type of the visit operation. Use {@link Void} for operations with no return
 *     type.
 */
@NoArgsConstructor
public class TamarinBaseVisitor<T> extends AbstractParseTreeVisitor<T>
    implements TamarinVisitor<T> {

  /**
   * Visit a parse tree produced by {@link TamarinParser#theory}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitTheory(TamarinParser.TheoryContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#component}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitComponent(TamarinParser.ComponentContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#built_in}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitBuilt_in(TamarinParser.Built_inContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#builtin_name}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitBuiltin_name(TamarinParser.Builtin_nameContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#equations}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitEquations(TamarinParser.EquationsContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#equation_symbol}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitEquation_symbol(TamarinParser.Equation_symbolContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#functions}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitFunctions(TamarinParser.FunctionsContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#function_symbol}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitFunction_symbol(TamarinParser.Function_symbolContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#protocolrule}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitProtocolrule(TamarinParser.ProtocolruleContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#genericrule}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitGenericrule(TamarinParser.GenericruleContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#let_block}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitLet_block(TamarinParser.Let_blockContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#fact}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitFact(TamarinParser.FactContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#terms}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitTerms(TamarinParser.TermsContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#fact_identifier}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitFact_identifier(TamarinParser.Fact_identifierContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#multterm}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitMultterm(TamarinParser.MulttermContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#expterm}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitExpterm(TamarinParser.ExptermContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#term}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitTerm(TamarinParser.TermContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#group_of_terms}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitGroup_of_terms(TamarinParser.Group_of_termsContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#nullary_fun}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitNullary_fun(TamarinParser.Nullary_funContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#binary_app}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitBinary_app(TamarinParser.Binary_appContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#binary_fun}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitBinary_fun(TamarinParser.Binary_funContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#nary_app}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitNary_app(TamarinParser.Nary_appContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#literal}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitLiteral(TamarinParser.LiteralContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#identifier}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitIdentifier(TamarinParser.IdentifierContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#natural}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitNatural(TamarinParser.NaturalContext ctx) {
    return this.visitChildren(ctx);
  }

  /**
   * Visit a parse tree produced by {@link TamarinParser#digit}.
   *
   * @param ctx the parse tree
   * @return the visitor result
   */
  public T visitDigit(TamarinParser.DigitContext ctx) {
    return this.visitChildren(ctx);
  }
}
