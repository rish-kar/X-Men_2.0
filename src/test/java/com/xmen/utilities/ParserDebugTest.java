package com.xmen.utilities;

import com.xmen.model.TamarinLexer;
import com.xmen.model.TamarinParser;
import com.xmen.service.FileSplitterService;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Debug test to see parse errors.
 */
@Disabled("Manual diagnostic; produces large stdout dumps. Enable locally when inspecting parser output.")
class ParserDebugTest {

  private final FileSplitterService fileSplitterService = new FileSplitterService();

  @Test
  void debugParseTrialCase() throws Exception {
    Path spthyPath = Paths.get("src/main/resources/TrialCase.spthy");
    String content = Files.readString(spthyPath, StandardCharsets.UTF_8);

    // Extract just the rules section like the real code does
    FileSplitterService.FileSections sections = fileSplitterService.splitFile(content);
    String rulesContent = sections.rules();

    System.out.println("=== RULES SECTION ===");
    System.out.println(rulesContent.substring(0, Math.min(500, rulesContent.length())));
    System.out.println("...");
    System.out.println("=== END RULES SECTION (first 500 chars) ===\n");

    CharStream charStream = CharStreams.fromString(rulesContent);
    TamarinLexer lexer = new TamarinLexer(charStream);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    TamarinParser parser = new TamarinParser(tokenStream);

    // Add error listener to capture errors
    parser.removeErrorListeners();
    parser.addErrorListener(new BaseErrorListener() {
      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                             int line, int charPositionInLine, String msg, RecognitionException e) {
        System.err.printf("PARSE ERROR at line %d:%d - %s%n", line, charPositionInLine, msg);
        if (offendingSymbol instanceof Token) {
          Token t = (Token) offendingSymbol;
          System.err.printf("  Offending token: type=%d, text='%s'%n", t.getType(), t.getText());
        }
      }
    });

    System.out.println("=== PARSING TrialCase.spthy RULES SECTION ===");
    ParseTree tree = parser.theory();
    System.out.println("Parse complete. Syntax errors: " + parser.getNumberOfSyntaxErrors());
    if (parser.getNumberOfSyntaxErrors() == 0) {
      System.out.println("SUCCESS! File parsed without errors.");
    }
  }

  @Test
  void debugParseBankFile() throws Exception {
    Path spthyPath = Paths.get("src/test/resources/Forget_Bank_Input.spthy");
    String content = Files.readString(spthyPath, StandardCharsets.UTF_8);

    // Extract just the rules section
    FileSplitterService.FileSections sections = fileSplitterService.splitFile(content);
    String rulesContent = sections.rules();

    CharStream charStream = CharStreams.fromString(rulesContent);
    TamarinLexer lexer = new TamarinLexer(charStream);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    TamarinParser parser = new TamarinParser(tokenStream);

    parser.removeErrorListeners();
    parser.addErrorListener(new BaseErrorListener() {
      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                             int line, int charPositionInLine, String msg, RecognitionException e) {
        System.err.printf("PARSE ERROR at line %d:%d - %s%n", line, charPositionInLine, msg);
      }
    });

    System.out.println("=== PARSING Forget_Bank_Input.spthy RULES SECTION ===");
    ParseTree tree = parser.theory();
    System.out.println("Parse complete. Syntax errors: " + parser.getNumberOfSyntaxErrors());
    if (parser.getNumberOfSyntaxErrors() == 0) {
      System.out.println("SUCCESS! File parsed without errors.");
    }
  }
}


