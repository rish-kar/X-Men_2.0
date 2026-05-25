package com.sermas.x.men.service.chatbot;

import com.batiaev.aiml.bot.BotImpl;
import com.batiaev.aiml.chat.InMemoryChatContextStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Offline chat assistant backed by the AIML 2.0 engine
 * (com.github.AIMLang.aiml-java-interpreter — Program-AB lineage).
 *
 * <p>The AIML "brain" — pattern/template training data — lives under
 * {@code src/main/resources/aiml/xmen/}. Layout is what the engine expects:
 *
 * <pre>
 *   aiml/xmen/
 *     system/bot.properties         (bot identity)
 *     aiml/*.aiml                   (training categories)
 *     sets/*.txt                    (optional named sets)
 *     maps/*.txt                    (optional named maps)
 *     substitutions/*.txt           (optional preprocessing rules)
 * </pre>
 *
 * <p>On first access we extract those resources to a temporary directory so
 * the AIML library (which expects filesystem paths) can load them, then wake
 * the bot up. All matching is done in-memory after that; no network calls.
 *
 * <p>The class is a thread-safe lazy singleton, deliberately not a Spring
 * bean — the JavaFX dialog calls {@link #getInstance()} directly so we don't
 * have to hop through the HTTP API just to chat.
 *
 * <p>To extend training: edit {@code TRAINING-PLACEHOLDER.aiml} (or drop new
 * {@code .aiml} files) under {@code src/main/resources/aiml/xmen/aiml/} and
 * restart X-Men.
 */
@Slf4j
public final class ChatBotService {

  private static final String BRAIN_NAME = "xmen";
  private static final String CLASSPATH_ROOT = "classpath:/aiml/" + BRAIN_NAME + "/";
  private static final String DEFAULT_GREETING =
      "Hi — I'm the X-Men assistant. I can explain every mutation this tool "
          + "can apply, help you pick the one that fits your scenario, and "
          + "walk you through the analysis pipeline. What would you like to "
          + "start with?";
  private static final String FALLBACK_REPLY =
      "I do not have enough signal to answer that exact question well. "
          + "The useful next move is to narrow it to a mutation, a protocol step, "
          + "a generated file, or the analysis pipeline. Pick one of these and I "
          + "can give you a cleaner answer.";
  private static final List<String> FALLBACK_FOLLOWUPS = List.of(
      "What can you help me with?",
      "What mutations are available?",
      "Which mutation should I pick?",
      "How does the analysis pipeline work?");

  /** Marker that ends every authored template. Everything after it is parsed
   *  as a pipe-delimited list of suggested follow-up questions. */
  private static final String FOLLOWUP_MARKER = "[[FOLLOWUPS]]";

  private static volatile ChatBotService instance;

  private final BotImpl bot;
  private final boolean ready;
  private final String loadStatus;

  /**
   * Knowledge entries scraped from the AIML brain at startup. Each entry pairs
   * the &lt;pattern&gt; words (used as the searchable keyword bag) with the
   * &lt;template&gt; text the engine would have returned. We consult this when
   * AIML's exact-match returns its generic "no answer" reply, so a user who
   * phrases a question differently than the trained pattern still gets the
   * relevant trained answer instead of a fallback. This is the intelligence
   * layer on top of rigid AIML matching — no extra dependencies, all in-RAM.
   */
  private final List<KnowledgeEntry> knowledge = new ArrayList<>();

  /** Stop-words ignored when scoring user input against trained patterns. */
  private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
      "a","an","the","is","are","was","were","be","been","being","am",
      "do","does","did","doing","done",
      "have","has","had","having",
      "i","you","he","she","it","we","they","me","my","your","our","their",
      "this","that","these","those","there","here",
      "and","or","but","if","then","else","than","so","as","of","at","by",
      "for","from","in","into","on","onto","to","with","without","about",
      "can","could","should","would","may","might","will","shall","just",
      "what","which","who","whom","whose","when","where","why","how",
      "tell","explain","show","say","talk","know","want","need",
      "please","kindly","help","me","us","yourself"
  ));

  private ChatBotService() {
    BotImpl b = null;
    boolean ok = false;
    String status = "not initialised";
    try {
      Path brainDir = extractBrain(knowledge);
      // BotImpl appends sub-folder names directly to rootDir, so it must end
      // with the platform separator (e.g. "/tmp/xmen-aiml-xyz/xmen/").
      Path botFolder = brainDir.resolve(BRAIN_NAME);
      String rootDir = botFolder.toAbsolutePath().toString();
      if (!rootDir.endsWith(java.io.File.separator)) rootDir += java.io.File.separator;

      log.info("Loading X-Men AIML brain from {}", rootDir);
      b = new BotImpl(BRAIN_NAME, rootDir, new InMemoryChatContextStorage());
      ok = b.wakeUp();
      status = ok
          ? "AIML brain loaded from " + rootDir
          : "AIML wakeUp() returned false — check that aiml/ folder is populated";
      log.info("ChatBotService status: {}", status);
    } catch (Throwable t) {
      status = "AIML brain failed to load: " + t.getClass().getSimpleName()
          + " — " + t.getMessage();
      log.warn("ChatBotService init failed", t);
      ok = false;
    }
    this.bot = b;
    this.ready = ok;
    this.loadStatus = status;
  }

  /** Lazy, thread-safe singleton accessor. */
  public static ChatBotService getInstance() {
    ChatBotService local = instance;
    if (local == null) {
      synchronized (ChatBotService.class) {
        local = instance;
        if (local == null) {
          local = new ChatBotService();
          instance = local;
        }
      }
    }
    return local;
  }

  /**
   * Return the bot's reply to {@code userMessage} as plain text. Use
   * {@link #respondReply(String)} when you also want the authored follow-up
   * suggestions for that answer.
   */
  public String respond(String userMessage) {
    return respondReply(userMessage).text;
  }

  /**
   * Return the bot's reply together with the authored follow-up suggestions
   * parsed from the {@code [[FOLLOWUPS]]} marker in the template. Never
   * returns null; falls back to the default greeting / fallback as needed.
   */
  public Reply respondReply(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
      return splitReply(DEFAULT_GREETING + "\n[[FOLLOWUPS]] "
          + "What mutations are available? | Which mutation should I pick? | "
          + "How does the analysis pipeline work?");
    }
    if (!ready || bot == null) {
      // Even when the AIML engine failed to start, the knowledge index built
      // during extractBrain() is still available — so the assistant degrades
      // to keyword search rather than going dark.
      String guess = bestKnowledgeMatch(userMessage);
      if (guess != null) return splitReply(guess);
      return new Reply("(Assistant offline — " + loadStatus + ")\n\n"
          + FALLBACK_REPLY, FALLBACK_FOLLOWUPS);
    }
    try {
      String reply = bot.getRespond(userMessage);
      String trimmed = reply == null ? "" : reply.trim();

      if (trimmed.isEmpty()
          || trimmed.equalsIgnoreCase("Sorry, I don't understand.")
          || trimmed.startsWith("Internal Error:")
          || trimmed.equalsIgnoreCase("I have no answer for that.")) {
        // Fall through to the keyword-search index — the bot's intelligence
        // layer. If it cannot find a strong-enough match, surface the
        // friendly fallback with follow-up chips so suggestions keep showing.
        String guess = bestKnowledgeMatch(userMessage);
        return guess != null ? splitReply(guess) : fallbackReply();
      }
      return splitReply(trimmed);
    } catch (Exception e) {
      log.warn("AIML respond() threw", e);
      return fallbackReply();
    }
  }

  private static Reply fallbackReply() {
    return new Reply(FALLBACK_REPLY, FALLBACK_FOLLOWUPS);
  }

  /**
   * Pull the optional {@code [[FOLLOWUPS]]} suffix off a template's text and
   * return the text + parsed chip list. The marker is convention-only; any
   * template without it returns an empty chip list.
   */
  private static Reply splitReply(String raw) {
    int idx = raw.indexOf(FOLLOWUP_MARKER);
    if (idx < 0) return new Reply(raw.trim(), List.of());
    String text = raw.substring(0, idx).trim();
    String tail = raw.substring(idx + FOLLOWUP_MARKER.length()).trim();
    if (tail.isEmpty()) return new Reply(text, List.of());
    List<String> chips = new ArrayList<>();
    for (String part : tail.split("\\|")) {
      String chip = part.trim();
      if (!chip.isEmpty()) chips.add(chip);
    }
    return new Reply(text, chips);
  }

  /** Reply payload: bot's text answer plus any authored follow-up chips. */
  public static final class Reply {
    public final String text;
    public final List<String> followups;
    public Reply(String text, List<String> followups) {
      this.text = text;
      this.followups = followups == null ? List.of() : List.copyOf(followups);
    }
  }

  public String greeting() {
    return DEFAULT_GREETING;
  }

  /** Suggested follow-up chips shown beneath the opening greeting. */
  public List<String> initialFollowups() {
    return List.of(
        "What mutations are available?",
        "Which mutation should I pick?",
        "How does the analysis pipeline work?",
        "What case studies do you support?");
  }

  public boolean isReady() {
    return ready;
  }

  /** Human-readable status string — surfaced in the chat header when loading fails. */
  public String status() {
    return loadStatus;
  }

  // ---------------------------------------------------------------------- //
  //  Brain extraction                                                      //
  // ---------------------------------------------------------------------- //

  /**
   * Copy every classpath resource under {@code /aiml/xmen/} into a temp dir,
   * preserving the relative folder layout the AIML library expects. Empty
   * sub-folders ({@code sets}, {@code maps}, {@code substitutions}) are
   * still created on disk so the loaders don't log "folder not found".
   */
  private static Path extractBrain(List<KnowledgeEntry> knowledgeSink) throws IOException {
    Path target = Files.createTempDirectory("xmen-aiml-");
    target.toFile().deleteOnExit();

    Path botRoot = target.resolve(BRAIN_NAME);
    Files.createDirectories(botRoot);
    // Pre-create every folder the engine looks at so empty ones still resolve.
    Files.createDirectories(botRoot.resolve("aiml"));
    Files.createDirectories(botRoot.resolve("sets"));
    Files.createDirectories(botRoot.resolve("maps"));
    Files.createDirectories(botRoot.resolve("substitutions"));
    Files.createDirectories(botRoot.resolve("system"));

    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources(CLASSPATH_ROOT + "**/*");

    String marker = "/aiml/" + BRAIN_NAME + "/";
    int copied = 0;
    for (Resource r : resources) {
      if (!r.isReadable()) continue;
      String url;
      try {
        url = r.getURL().toString();
      } catch (IOException ioe) {
        continue;
      }
      int idx = url.lastIndexOf(marker);
      if (idx < 0) continue;
      String relative = url.substring(idx + marker.length());
      if (relative.isEmpty() || relative.endsWith("/")) continue;
      Path dest = botRoot.resolve(relative.replace('/', java.io.File.separatorChar));
      Files.createDirectories(dest.getParent());
      if (relative.toLowerCase().endsWith(".aiml")) {
        // The AIML library we use doesn't read CDATA template bodies — it
        // sees them as empty text and matching silently returns the default
        // "no answer" reply for every input. Strip CDATA wrappers as we
        // extract so authors can keep using them in source for readability.
        try (InputStream in = r.getInputStream()) {
          byte[] raw = in.readAllBytes();
          String source = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
          // Build the keyword-search index from the source — CDATA still
          // intact, so we capture human-readable template text without the
          // entity-escaped noise that the engine needs on disk.
          if (knowledgeSink != null) indexCategories(source, knowledgeSink);
          String rewritten = unwrapCdata(source);
          Files.writeString(dest, rewritten, java.nio.charset.StandardCharsets.UTF_8);
        }
      } else {
        try (InputStream in = r.getInputStream()) {
          Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
      }
      copied++;
      dest.toFile().deleteOnExit();
    }
    log.info("Extracted {} AIML brain resources into {}", copied, botRoot);
    return target;
  }

  /**
   * Replace every {@code <![CDATA[...]]>} block with its content XML-escaped
   * in place. The AIML loader walks the DOM but doesn't pull text out of
   * CDATA nodes, so what used to read as markdown-rich training data now
   * reads back as the plain text the engine expects.
   *
   * <p>Only {@code <}, {@code >} and {@code &} are escaped — that's enough
   * to keep the surrounding XML valid without disturbing any AIML tag
   * (which never appears inside a CDATA block by definition).
   */
  static String unwrapCdata(String xml) {
    final String open = "<![CDATA[";
    final String close = "]]>";
    StringBuilder out = new StringBuilder(xml.length());
    int i = 0;
    while (true) {
      int o = xml.indexOf(open, i);
      if (o < 0) {
        out.append(xml, i, xml.length());
        return out.toString();
      }
      out.append(xml, i, o);
      int c = xml.indexOf(close, o + open.length());
      if (c < 0) {
        // Unterminated CDATA — leave the rest of the file alone.
        out.append(xml, o, xml.length());
        return out.toString();
      }
      String inner = xml.substring(o + open.length(), c);
      out.append(escapeXml(inner));
      i = c + close.length();
    }
  }

  // ---------------------------------------------------------------------- //
  //  Keyword-search intelligence layer                                     //
  // ---------------------------------------------------------------------- //

  private static final Pattern CATEGORY_RE = Pattern.compile(
      "<category\\b[^>]*>(.*?)</category>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern PATTERN_RE = Pattern.compile(
      "<pattern\\b[^>]*>(.*?)</pattern>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern TEMPLATE_RE = Pattern.compile(
      "<template\\b[^>]*>(.*?)</template>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern CDATA_RE = Pattern.compile(
      "<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL);
  private static final Pattern SRAI_RE = Pattern.compile(
      "<srai\\b[^>]*>(.*?)</srai>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern TAG_RE = Pattern.compile("<[^>]+>");
  private static final Pattern WORD_RE = Pattern.compile("[A-Za-z][A-Za-z0-9'\\-]*");

  /**
   * Walk every &lt;category&gt; in an AIML file and add a knowledge entry per
   * non-srai template. srai (redirect) templates point at another pattern;
   * those are resolved in a second pass after the whole corpus is indexed.
   */
  private static void indexCategories(String aimlSource, List<KnowledgeEntry> sink) {
    Map<String, String> sraiRedirects = new LinkedHashMap<>();
    Matcher cm = CATEGORY_RE.matcher(aimlSource);
    while (cm.find()) {
      String body = cm.group(1);
      Matcher pm = PATTERN_RE.matcher(body);
      Matcher tm = TEMPLATE_RE.matcher(body);
      if (!pm.find() || !tm.find()) continue;
      String pattern = stripTags(pm.group(1)).trim();
      String templateXml = tm.group(1);

      Matcher sr = SRAI_RE.matcher(templateXml);
      if (sr.find()) {
        sraiRedirects.put(pattern.toUpperCase(), stripTags(sr.group(1)).trim().toUpperCase());
        continue;
      }
      // Pull text from CDATA blocks if present; otherwise strip tags.
      String body2 = templateXml;
      Matcher cd = CDATA_RE.matcher(templateXml);
      if (cd.find()) {
        StringBuilder sb = new StringBuilder();
        cd.reset();
        while (cd.find()) {
          if (sb.length() > 0) sb.append('\n');
          sb.append(cd.group(1));
        }
        body2 = sb.toString();
      }
      String text = stripTags(body2).trim();
      if (text.isEmpty() || pattern.isEmpty()) continue;
      // Index text *with* the [[FOLLOWUPS]] tail attached — bestKnowledgeMatch
      // returns the whole string and respondReply()'s splitReply() peels the
      // chips off afterwards, so the keyword path produces the same
      // text + chips shape as the AIML-matched path.
      sink.add(new KnowledgeEntry(pattern, tokenize(pattern + " " + text), text));
    }
    // Resolve srai chains: any redirect's target becomes the same answer.
    for (Map.Entry<String, String> e : sraiRedirects.entrySet()) {
      String target = e.getValue();
      for (KnowledgeEntry k : sink) {
        if (k.pattern.equalsIgnoreCase(target)) {
          sink.add(new KnowledgeEntry(e.getKey(),
              tokenize(e.getKey() + " " + k.text), k.text));
          break;
        }
      }
    }
  }

  private static String stripTags(String s) {
    return TAG_RE.matcher(s).replaceAll(" ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
  }

  /** Lower-case content words with stop-words removed. */
  private static Set<String> tokenize(String s) {
    if (s == null) return java.util.Collections.emptySet();
    Set<String> out = new HashSet<>();
    Matcher m = WORD_RE.matcher(s.toLowerCase());
    while (m.find()) {
      String w = m.group();
      if (w.length() < 2) continue;
      if (STOP_WORDS.contains(w)) continue;
      out.add(w);
    }
    return out;
  }

  /**
   * Score every indexed entry against the user's tokens and return the best
   * trained answer if the match is strong enough. Score is overlap weighted
   * toward the pattern phrase — so "tell me about forget" finds the
   * "WHAT IS FORGET" category even though the surface words differ.
   *
   * <p>Returns {@code null} if no entry clears a confidence floor; the caller
   * uses the standard fallback in that case.
   */
  private String bestKnowledgeMatch(String userMessage) {
    Set<String> q = tokenize(userMessage);
    if (q.isEmpty() || knowledge.isEmpty()) return null;

    KnowledgeEntry best = null;
    double bestScore = 0.0;
    for (KnowledgeEntry e : knowledge) {
      // Overlap between query and entry's full token bag.
      int hits = 0;
      for (String t : q) if (e.tokens.contains(t)) hits++;
      if (hits == 0) continue;
      // Weight pattern-word hits 3x — they're the curated topic keywords.
      Set<String> patternTokens = tokenize(e.pattern);
      int patternHits = 0;
      for (String t : q) if (patternTokens.contains(t)) patternHits++;
      double score = hits + 3.0 * patternHits;
      // Normalise by query length so longer questions don't always win.
      score = score / Math.sqrt(q.size());
      if (score > bestScore) {
        bestScore = score;
        best = e;
      }
    }
    // Confidence floor: at least one pattern-word hit OR two general hits.
    if (best == null || bestScore < 1.4) return null;
    return best.text;
  }

  /** A single trained &lt;category&gt; flattened for keyword search. */
  private static final class KnowledgeEntry {
    final String pattern;
    final Set<String> tokens;
    final String text;
    KnowledgeEntry(String pattern, Set<String> tokens, String text) {
      this.pattern = pattern;
      this.tokens = tokens;
      this.text = text;
    }
  }

  private static String escapeXml(String s) {
    StringBuilder b = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '&' -> b.append("&amp;");
        case '<' -> b.append("&lt;");
        case '>' -> b.append("&gt;");
        default -> b.append(ch);
      }
    }
    return b.toString();
  }
}
