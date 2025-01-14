package com.sermas.x.men.model;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

public interface TamarinVisitor<T> extends ParseTreeVisitor<T> {
    T visitTheory(TamarinParser.TheoryContext var1);

    T visitComponent(TamarinParser.ComponentContext var1);

    T visitBuilt_in(TamarinParser.Built_inContext var1);

    T visitBuiltin_name(TamarinParser.Builtin_nameContext var1);

    T visitEquations(TamarinParser.EquationsContext var1);

    T visitEquation_symbol(TamarinParser.Equation_symbolContext var1);

    T visitFunctions(TamarinParser.FunctionsContext var1);

    T visitFunction_symbol(TamarinParser.Function_symbolContext var1);

    T visitProtocolrule(TamarinParser.ProtocolruleContext var1);

    T visitGenericrule(TamarinParser.GenericruleContext var1);

    T visitLet_block(TamarinParser.Let_blockContext var1);

    T visitFact(TamarinParser.FactContext var1);

    T visitTerms(TamarinParser.TermsContext var1);

    T visitFact_identifier(TamarinParser.Fact_identifierContext var1);

    T visitMultterm(TamarinParser.MulttermContext var1);

    T visitExpterm(TamarinParser.ExptermContext var1);

    T visitTerm(TamarinParser.TermContext var1);

    T visitGroup_of_terms(TamarinParser.Group_of_termsContext var1);

    T visitNullary_fun(TamarinParser.Nullary_funContext var1);

    T visitBinary_app(TamarinParser.Binary_appContext var1);

    T visitBinary_fun(TamarinParser.Binary_funContext var1);

    T visitNary_app(TamarinParser.Nary_appContext var1);

    T visitLiteral(TamarinParser.LiteralContext var1);

    T visitIdentifier(TamarinParser.IdentifierContext var1);

    T visitNatural(TamarinParser.NaturalContext var1);

    T visitDigit(TamarinParser.DigitContext var1);
}
