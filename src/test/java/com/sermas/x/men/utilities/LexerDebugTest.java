package com.sermas.x.men.utilities;

import com.sermas.x.men.model.TamarinLexer;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Debug test to see what tokens the lexer produces.
 */
class LexerDebugTest {

  @Test
  void debugLexerTokens() throws Exception {
    Path spthyPath = Paths.get("src/test/resources/Forget_Bank_Input.spthy");
    try (InputStream inputStream = Files.newInputStream(spthyPath)) {
      ANTLRInputStream antlrInputStream = new ANTLRInputStream(inputStream);
      TamarinLexer lexer = new TamarinLexer(antlrInputStream);

      System.out.println("=== ALL TOKENS FROM TrialCase.spthy ===");
      Token token;
      int count = 0;
      while ((token = lexer.nextToken()).getType() != Token.EOF) {
        // Show all tokens, even hidden ones
        String symbolicName = lexer.getVocabulary().getSymbolicName(token.getType());
        if (symbolicName == null) symbolicName = lexer.getVocabulary().getLiteralName(token.getType());
        System.out.printf("Token[%d]: type=%d (%s), text='%s', line=%d, col=%d, channel=%d%n",
            count++,
            token.getType(),
            symbolicName,
            token.getText().replace("\n", "\\n").replace("\r", "\\r"),
            token.getLine(),
            token.getCharPositionInLine(),
            token.getChannel());
        if (count >= 200) break;
      }
    }
  }
}



