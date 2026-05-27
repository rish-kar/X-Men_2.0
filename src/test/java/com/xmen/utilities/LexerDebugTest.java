package com.xmen.utilities;

import com.xmen.model.TamarinLexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Disabled;
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
  @Disabled("Manual diagnostic; produces large stdout dumps. Enable locally when inspecting lexer output.")
  void debugLexerTokens() throws Exception {
    Path spthyPath = Paths.get("src/test/resources/Forget_Bank_Input.spthy");
    try (InputStream inputStream = Files.newInputStream(spthyPath)) {
      CharStream charStream = CharStreams.fromStream(inputStream);
      TamarinLexer lexer = new TamarinLexer(charStream);

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



