package com.xmen.model;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced by
 * {@link TamarinParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for operations with no return
 *     type.
 */
public interface TamarinVisitor<T> extends ParseTreeVisitor<T> {

  /**
   * Visit a parse tree produced by {@link TamarinParser#theory}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitTheory(TamarinParser.TheoryContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#component}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitComponent(TamarinParser.ComponentContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#built_in}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitBuilt_in(TamarinParser.Built_inContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#builtin_name}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitBuiltin_name(TamarinParser.Builtin_nameContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#equations}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitEquations(TamarinParser.EquationsContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#equation_symbol}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitEquation_symbol(TamarinParser.Equation_symbolContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#functions}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitFunctions(TamarinParser.FunctionsContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#function_symbol}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitFunction_symbol(TamarinParser.Function_symbolContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#protocolrule}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitProtocolrule(TamarinParser.ProtocolruleContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#genericrule}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitGenericrule(TamarinParser.GenericruleContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#let_block}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitLet_block(TamarinParser.Let_blockContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#fact}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitFact(TamarinParser.FactContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#terms}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitTerms(TamarinParser.TermsContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#fact_identifier}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitFact_identifier(TamarinParser.Fact_identifierContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#multterm}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitMultterm(TamarinParser.MulttermContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#expterm}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitExpterm(TamarinParser.ExptermContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#term}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitTerm(TamarinParser.TermContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#group_of_terms}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitGroup_of_terms(TamarinParser.Group_of_termsContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#nullary_fun}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitNullary_fun(TamarinParser.Nullary_funContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#binary_app}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitBinary_app(TamarinParser.Binary_appContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#binary_fun}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitBinary_fun(TamarinParser.Binary_funContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#nary_app}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitNary_app(TamarinParser.Nary_appContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#literal}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitLiteral(TamarinParser.LiteralContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#identifier}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitIdentifier(TamarinParser.IdentifierContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#natural}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitNatural(TamarinParser.NaturalContext var1);

  /**
   * Visit a parse tree produced by {@link TamarinParser#digit}.
   *
   * @param var1 the parse tree
   * @return the visitor result
   */
  T visitDigit(TamarinParser.DigitContext var1);
}
