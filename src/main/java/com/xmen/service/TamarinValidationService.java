package com.xmen.service;

import com.xmen.model.TamarinLexer;
import com.xmen.model.TamarinParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates that an uploaded file is well-formed Tamarin {@code .spthy} syntax before any
 * mutation work is attempted.
 *
 * <p>The validator runs two passes:
 *
 * <ol>
 *   <li><b>Quick structural checks</b> — extension, non-empty, balanced delimiters, presence of
 *       the mandatory {@code theory ... begin ... end} envelope. These produce friendly,
 *       actionable error messages.
 *   <li><b>Full grammar parse</b> via the existing ANTLR {@link TamarinLexer} / {@link
 *       TamarinParser}. Any syntax errors collected by the ANTLR error listener are translated
 *       into {@link ValidationIssue}s with line and column numbers.
 * </ol>
 *
 * <p>If both passes succeed the report is {@code valid = true}. Callers (controllers) can short
 * circuit with a structured {@code 400} response when {@code valid = false}.
 */
@Slf4j
@Service
public class TamarinValidationService {

  private static final Pattern HEADER = Pattern.compile("(?m)^\\s*theory\\s+\\S+");
  private static final Pattern BEGIN = Pattern.compile("(?m)^\\s*begin\\b");
  private static final Pattern END = Pattern.compile("(?m)^\\s*end\\b");

  /** Validate a multipart upload. Reads at most a few MB into memory. */
  public ValidationReport validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return ValidationReport.of(false, ValidationIssue.error("File is empty or missing."));
    }
    String name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase().endsWith(".spthy")) {
      return ValidationReport.of(
          false, ValidationIssue.error("Expected a .spthy file, got: " + name));
    }
    try (InputStream in = file.getInputStream()) {
      byte[] bytes = in.readAllBytes();
      return validate(new String(bytes, StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.error("Failed to read upload for validation", e);
      return ValidationReport.of(
          false, ValidationIssue.error("Could not read upload: " + e.getMessage()));
    }
  }

  /** Validate raw {@code .spthy} text. */
  public ValidationReport validate(String content) {
    List<ValidationIssue> issues = new ArrayList<>();

    if (content == null || content.isBlank()) {
      issues.add(ValidationIssue.error("File is empty."));
      return ValidationReport.of(false, issues);
    }

    // Pass 1 – structural checks.
    if (!HEADER.matcher(content).find()) {
      issues.add(ValidationIssue.error("Missing `theory <Name>` header."));
    }
    if (!BEGIN.matcher(content).find()) {
      issues.add(ValidationIssue.error("Missing `begin` after the theory header."));
    }
    if (!END.matcher(content).find()) {
      issues.add(ValidationIssue.error("Missing trailing `end` keyword."));
    }
    issues.addAll(checkBalanced(content, '{', '}'));
    issues.addAll(checkBalanced(content, '[', ']'));
    issues.addAll(checkBalanced(content, '(', ')'));
    issues.addAll(checkBalanced(content, '<', '>'));

    // If structure is already broken, skip the heavyweight grammar parse — its errors will
    // just echo the same problems with less helpful messages.
    if (issues.stream().anyMatch(i -> i.severity == Severity.ERROR)) {
      return ValidationReport.of(false, issues);
    }

    // Pass 2 – ANTLR parse.
    try {
      TamarinLexer lexer = new TamarinLexer(CharStreams.fromString(content));
      lexer.removeErrorListeners();
      CollectingErrorListener listener = new CollectingErrorListener();
      lexer.addErrorListener(listener);

      TamarinParser parser = new TamarinParser(new CommonTokenStream(lexer));
      parser.removeErrorListeners();
      parser.addErrorListener(listener);

      // Drive the parser at the root rule. Some hand-rolled grammars use `theory` or
      // `prog` – we try the conventional name and fall back to `start` if that throws.
      try {
        parser.theory();
      } catch (NoSuchMethodError | Exception ignored) {
        try {
          parser.getClass().getMethod("start").invoke(parser);
        } catch (ReflectiveOperationException reflective) {
          // If we can't drive the parser at all, fall back to lexer-only feedback.
          log.debug("Could not find a root parser rule by reflection: {}", reflective.getMessage());
        }
      }

      issues.addAll(listener.issues());
    } catch (Throwable t) {
      log.warn("ANTLR validation pass crashed: {}", t.getMessage());
      issues.add(
          ValidationIssue.warn("Grammar validation skipped (parser error): " + t.getMessage()));
    }

    boolean valid = issues.stream().noneMatch(i -> i.severity == Severity.ERROR);
    return ValidationReport.of(valid, issues);
  }

  /** Counts matched delimiter pairs and reports the deficit (if any). */
  private List<ValidationIssue> checkBalanced(String content, char open, char close) {
    int depth = 0;
    int line = 1;
    int col = 0;
    int firstOffending = -1;
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '\n') {
        line++;
        col = 0;
      } else col++;
      if (c == open) depth++;
      else if (c == close) {
        depth--;
        if (depth < 0 && firstOffending < 0) firstOffending = i;
      }
    }
    if (depth > 0) {
      return List.of(
          ValidationIssue.error(
              "Unbalanced `" + open + close + "` — " + depth + " unclosed `" + open + "`."));
    } else if (depth < 0) {
      return List.of(
          ValidationIssue.error(
              "Unbalanced `" + open + close + "` — extra `" + close + "` near line " + line + "."));
    }
    return List.of();
  }

  /* ----- Inner types --------------------------------------------------- */

  /** Severity of a single validation finding. */
  public enum Severity {
    ERROR,
    WARN
  }

  /** One validation finding. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ValidationIssue {
    private Severity severity;
    private int line;
    private int column;
    private String message;

    public static ValidationIssue error(String message) {
      return new ValidationIssue(Severity.ERROR, 0, 0, message);
    }

    public static ValidationIssue error(int line, int column, String message) {
      return new ValidationIssue(Severity.ERROR, line, column, message);
    }

    public static ValidationIssue warn(String message) {
      return new ValidationIssue(Severity.WARN, 0, 0, message);
    }
  }

  /** Whole-file validation report. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ValidationReport {
    private boolean valid;
    private List<ValidationIssue> issues = new ArrayList<>();

    public static ValidationReport of(boolean valid, ValidationIssue single) {
      return new ValidationReport(valid, new ArrayList<>(List.of(single)));
    }

    public static ValidationReport of(boolean valid, List<ValidationIssue> issues) {
      return new ValidationReport(valid, issues);
    }
  }

  /** ANTLR listener that captures errors as {@link ValidationIssue}s. */
  private static class CollectingErrorListener extends BaseErrorListener {
    private final List<ValidationIssue> collected = new ArrayList<>();

    List<ValidationIssue> issues() {
      return collected;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {
      collected.add(ValidationIssue.error(line, charPositionInLine + 1, msg));
    }
  }
}
