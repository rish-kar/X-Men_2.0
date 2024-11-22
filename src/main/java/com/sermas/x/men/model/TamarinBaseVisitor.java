package com.sermas.x.men.model;

import lombok.NoArgsConstructor;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

@NoArgsConstructor
public class TamarinBaseVisitor<T> extends AbstractParseTreeVisitor<T> implements TamarinVisitor<T> {

    public T visitTheory(TamarinParser.TheoryContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitComponent(TamarinParser.ComponentContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitBuilt_in(TamarinParser.Built_inContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitBuiltin_name(TamarinParser.Builtin_nameContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitEquations(TamarinParser.EquationsContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitEquation_symbol(TamarinParser.Equation_symbolContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitFunctions(TamarinParser.FunctionsContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitFunction_symbol(TamarinParser.Function_symbolContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitProtocolrule(TamarinParser.ProtocolruleContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitGenericrule(TamarinParser.GenericruleContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitLet_block(TamarinParser.Let_blockContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitFact(TamarinParser.FactContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitTerms(TamarinParser.TermsContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitFact_identifier(TamarinParser.Fact_identifierContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitMultterm(TamarinParser.MulttermContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitExpterm(TamarinParser.ExptermContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitTerm(TamarinParser.TermContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitGroup_of_terms(TamarinParser.Group_of_termsContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitNullary_fun(TamarinParser.Nullary_funContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitBinary_app(TamarinParser.Binary_appContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitBinary_fun(TamarinParser.Binary_funContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitNary_app(TamarinParser.Nary_appContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitLiteral(TamarinParser.LiteralContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitIdentifier(TamarinParser.IdentifierContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitNatural(TamarinParser.NaturalContext ctx) {
        return this.visitChildren(ctx);
    }

    public T visitDigit(TamarinParser.DigitContext ctx) {
        return this.visitChildren(ctx);
    }
}
