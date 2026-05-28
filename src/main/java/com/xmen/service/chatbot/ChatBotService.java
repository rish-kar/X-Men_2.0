package com.xmen.service.chatbot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline chat helper backed by a small structured knowledge base.
 *
 * <p>The old phrase matcher could return a large, unrelated answer blob on a
 * near miss. This service is concept-driven instead. It loads
 * curated YAML entries, detects the user's intent, ranks concepts, then
 * composes a concise answer from fields such as definition, use cases, checks,
 * examples, and common mistakes.
 */
@Slf4j
public final class ChatBotService {

  private static final List<String> KNOWLEDGE_RESOURCES = List.of(
      "chatbot/knowledge/chatbot-knowledge.yaml",
      "chatbot/knowledge/previous-paper.yaml",
      "chatbot/knowledge/forget-paper.yaml",
      "chatbot/knowledge/xmen-manual.yaml");

  private static final String DEFAULT_GREETING =
      "Hi - I'm the X-Men assistant. I can explain Tamarin concepts, X-Men "
          + "mutations, mutation selection, generated outputs, and debugging steps.";

  private static final String DEFAULT_FALLBACK =
      "I do not have enough signal to answer that exact question well. Try asking "
          + "about a Tamarin concept, a mutation, the analysis pipeline, or a result.";

  private static final List<String> DEFAULT_FOLLOWUPS = List.of(
      "What is Tamarin?",
      "What mutations are available?",
      "Which mutation should I pick?",
      "How do I read Tamarin results?");

  private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
      "a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "am",
      "do", "does", "did", "doing", "done", "have", "has", "had", "having",
      "i", "you", "he", "she", "it", "we", "they", "me", "my", "your", "our",
      "their", "this", "that", "these", "those", "there", "here", "and", "or",
      "but", "if", "then", "else", "than", "so", "as", "of", "at", "by", "for",
      "from", "in", "into", "on", "onto", "to", "with", "without", "about",
      "topic", "topics",
      "can", "could", "should", "would", "may", "might", "will", "shall", "just",
      "what", "which", "who", "whom", "whose", "when", "where", "why", "how",
      "tell", "explain", "show", "say", "talk", "know", "want", "need",
      "please", "kindly", "help", "me", "us", "yourself"));

  private static final Map<String, List<String>> TOKEN_EXPANSIONS = Map.ofEntries(
      Map.entry("spthy", List.of("theory", "file", "syntax")),
      Map.entry("theory", List.of("spthy", "model")),
      Map.entry("lemma", List.of("property", "proof")),
      Map.entry("property", List.of("lemma", "trace")),
      Map.entry("rule", List.of("rewrite", "transition")),
      Map.entry("trace", List.of("execution", "counterexample")),
      Map.entry("attack", List.of("counterexample", "trace")),
      Map.entry("fact", List.of("state", "premise", "conclusion")),
      Map.entry("builtin", List.of("function", "equation", "cryptographic")),
      Map.entry("builtins", List.of("function", "equation", "cryptographic")),
      Map.entry("dh", List.of("diffie", "hellman")),
      Map.entry("diffie", List.of("dh", "hellman")),
      Map.entry("hellman", List.of("dh", "diffie")),
      Map.entry("forget", List.of("knowledge", "state")),
      Map.entry("neglect", List.of("check", "unused")),
      Map.entry("skip", List.of("remove", "step")),
      Map.entry("send", List.of("snd")),
      Map.entry("snd", List.of("send")),
      Map.entry("receive", List.of("recv", "rcv")),
      Map.entry("recv", List.of("receive", "rcv")),
      Map.entry("rcv", List.of("receive", "recv")),
      Map.entry("replace", List.of("substitute", "swap")),
      Map.entry("add", List.of("inject", "message")),
      Map.entry("combine", List.of("piggyback", "message")),
      Map.entry("ceremony", List.of("protocol", "human", "mutation")),
      Map.entry("human", List.of("ceremony", "mistake", "mutation")),
      Map.entry("matching", List.of("propagation", "mutation", "executable")),
      Map.entry("propagation", List.of("matching", "mutation", "trace")),
      Map.entry("oyster", List.of("card", "touch", "journey")),
      Map.entry("card", List.of("oyster", "contactless", "journey")),
      Map.entry("sso", List.of("saml", "identity", "provider")),
      Map.entry("saml", List.of("sso", "identity", "provider")),
      Map.entry("idp", List.of("identity", "provider", "sso")),
      Map.entry("sp", List.of("service", "provider", "sso")),
      Map.entry("coach", List.of("ticket", "driver", "journey")),
      Map.entry("ticket", List.of("coach", "valid", "date")),
      Map.entry("xavier", List.of("analysis", "report", "tamarin")),
      Map.entry("wolverine", List.of("preprocessing", "slice", "join")));

  private static final Pattern WORD_RE = Pattern.compile("[A-Za-z][A-Za-z0-9']*|[0-9]+");

  private static volatile ChatBotService instance;

  private final KnowledgeBase knowledgeBase;
  private final Map<String, KnowledgeEntry> entriesById;
  private final Map<String, List<KnowledgeEntry>> searchIndex;
  private final boolean ready;
  private final String loadStatus;

  private ChatBotService() {
    KnowledgeBase loaded = null;
    Map<String, KnowledgeEntry> index = Map.of();
    Map<String, List<KnowledgeEntry>> search = Map.of();
    boolean ok = false;
    String status;
    try {
      loaded = loadKnowledgeBase();
      index = indexEntries(loaded.entries);
      search = buildSearchIndex(loaded.entries);
      ok = loaded.entries != null && !loaded.entries.isEmpty();
      status = ok
          ? "YAML knowledge base loaded: " + loaded.entries.size() + " entries"
          : "YAML knowledge base loaded but contains no entries";
      log.info("ChatBotService status: {}", status);
    } catch (Exception e) {
      status = "YAML knowledge base failed to load: " + e.getClass().getSimpleName()
          + " - " + e.getMessage();
      log.warn("ChatBotService init failed", e);
    }
    this.knowledgeBase = loaded == null ? new KnowledgeBase() : loaded;
    this.entriesById = index;
    this.searchIndex = search;
    this.ready = ok;
    this.loadStatus = status;
  }

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

  public String respond(String userMessage) {
    return respondReply(userMessage).text;
  }

  public Reply respondReply(String userMessage) {
    String question = userMessage == null ? "" : userMessage.trim();
    if (question.isEmpty()) {
      return new Reply(greeting(), initialFollowups());
    }
    if (!ready) {
      return new Reply("(Assistant offline - " + loadStatus + ")\n\n" + fallbackText(),
          fallbackFollowups());
    }

    Intent intent = detectIntent(question);
    List<Match> matches = rank(question);

    if (intent == Intent.GREETING) {
      KnowledgeEntry greeting = entriesById.get("conversation.greeting");
      return replyFor(greeting, Intent.DEFINITION);
    }

    if (intent == Intent.THANKS) {
      KnowledgeEntry thanks = entriesById.get("conversation.thanks");
      return replyFor(thanks, Intent.DEFINITION);
    }

    if (matches.isEmpty() || matches.get(0).score < 2.8) {
      return new Reply(fallbackText(), fallbackFollowups());
    }

    if (intent == Intent.COMPARE) {
      return composeComparison(matches, question);
    }

    return replyFor(matches.get(0).entry, intent);
  }

  public String greeting() {
    String configured = knowledgeBase.greeting;
    return configured == null || configured.isBlank() ? DEFAULT_GREETING : configured;
  }

  public List<String> initialFollowups() {
    return knowledgeBase.initialFollowups == null || knowledgeBase.initialFollowups.isEmpty()
        ? DEFAULT_FOLLOWUPS
        : List.copyOf(knowledgeBase.initialFollowups);
  }

  public boolean isReady() {
    return ready;
  }

  public String status() {
    return loadStatus;
  }

  private static KnowledgeBase loadKnowledgeBase() throws Exception {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    KnowledgeBase merged = new KnowledgeBase();
    List<KnowledgeEntry> entries = new ArrayList<>();

    for (String resourcePath : KNOWLEDGE_RESOURCES) {
      ClassPathResource resource = new ClassPathResource(resourcePath);
      if (!resource.exists()) {
        log.warn("Chatbot knowledge resource not found: {}", resourcePath);
        continue;
      }
      try (InputStream in = resource.getInputStream()) {
        KnowledgeBase kb = mapper.readValue(in, KnowledgeBase.class);
        if (merged.greeting == null) merged.greeting = kb.greeting;
        if (merged.fallback == null) merged.fallback = kb.fallback;
        if (merged.initialFollowups.isEmpty() && kb.initialFollowups != null) {
          merged.initialFollowups = kb.initialFollowups;
        }
        if (merged.fallbackFollowups.isEmpty() && kb.fallbackFollowups != null) {
          merged.fallbackFollowups = kb.fallbackFollowups;
        }
        if (kb.entries != null) entries.addAll(kb.entries);
      }
    }

    merged.entries = entries;
    return merged;
  }

  private static Map<String, KnowledgeEntry> indexEntries(List<KnowledgeEntry> entries) {
    Map<String, KnowledgeEntry> out = new HashMap<>();
    if (entries == null) return out;
    for (KnowledgeEntry entry : entries) {
      if (entry == null || entry.id == null || entry.id.isBlank()) continue;
      entry.prepare();
      out.put(entry.id, entry);
    }
    return out;
  }

  private static Map<String, List<KnowledgeEntry>> buildSearchIndex(List<KnowledgeEntry> entries) {
    Map<String, List<KnowledgeEntry>> out = new HashMap<>();
    if (entries == null) return out;
    for (KnowledgeEntry entry : entries) {
      if (entry == null) continue;
      for (String token : entry.indexTokens) {
        out.computeIfAbsent(token, ignored -> new ArrayList<>()).add(entry);
      }
    }
    return out;
  }

  private String fallbackText() {
    return knowledgeBase.fallback == null || knowledgeBase.fallback.isBlank()
        ? DEFAULT_FALLBACK
        : knowledgeBase.fallback;
  }

  private List<String> fallbackFollowups() {
    return knowledgeBase.fallbackFollowups == null || knowledgeBase.fallbackFollowups.isEmpty()
        ? DEFAULT_FOLLOWUPS
        : List.copyOf(knowledgeBase.fallbackFollowups);
  }

  private List<Match> rank(String question) {
    Set<String> queryTokens = tokenize(question);
    String normalizedQuestion = normalizeText(question);
    List<Match> matches = new ArrayList<>();

    for (KnowledgeEntry entry : candidatesFor(queryTokens)) {
      double score = 0.0;
      for (int i = 0; i < entry.normalizedTerms.size(); i++) {
        String normalizedTerm = entry.normalizedTerms.get(i);
        if (normalizedTerm.isBlank()) continue;
        if (containsPhrase(normalizedQuestion, normalizedTerm)) {
          int termTokenCount = entry.termTokenSets.get(i).size();
          score += normalizedTerm.length() <= 4 ? 7.5 : 9.0;
          score += termTokenCount * termTokenCount;
          if (termTokenCount >= 2) score += 8.0 * termTokenCount;
        }
        score += 3.0 * overlap(queryTokens, entry.termTokenSets.get(i));
      }
      String normalizedTitle = normalizeText(entry.title);
      if (!normalizedTitle.isBlank() && containsPhrase(normalizedQuestion, normalizedTitle)) {
        int titleTokenCount = entry.titleTokens.size();
        score += 10.0 + (titleTokenCount * titleTokenCount);
        if (titleTokenCount >= 2) score += 8.0 * titleTokenCount;
      }
      score += 2.0 * overlap(queryTokens, entry.titleTokens);
      score += 0.6 * overlap(queryTokens, entry.contentTokens);

      if (score > 0) {
        matches.add(new Match(entry, score));
      }
    }
    matches.sort(Comparator.comparingDouble((Match m) -> m.score).reversed());
    return matches;
  }

  private List<KnowledgeEntry> candidatesFor(Set<String> queryTokens) {
    if (queryTokens == null || queryTokens.isEmpty() || searchIndex.isEmpty()) {
      return knowledgeBase.entries;
    }
    LinkedHashSet<KnowledgeEntry> candidates = new LinkedHashSet<>();
    for (String token : queryTokens) {
      List<KnowledgeEntry> entries = searchIndex.get(token);
      if (entries != null) candidates.addAll(entries);
    }
    return candidates.isEmpty() ? knowledgeBase.entries : new ArrayList<>(candidates);
  }

  private Reply replyFor(KnowledgeEntry entry, Intent intent) {
    if (entry == null) return new Reply(fallbackText(), fallbackFollowups());

    StringBuilder answer = new StringBuilder();
    switch (intent) {
      case DEBUG -> composeDebug(answer, entry);
      case HOW_TO -> composeHowTo(answer, entry);
      case PICK -> composePick(answer, entry);
      default -> composeDefinition(answer, entry);
    }
    return new Reply(answer.toString().trim(), followupsFor(entry));
  }

  private static void composeDefinition(StringBuilder answer, KnowledgeEntry entry) {
    appendSection(answer, "Definition", firstNonBlank(entry.definition, entry.shortAnswer));
    appendBullets(answer, "Key points", entry.details, 6);
    appendSection(answer, "Why it matters", entry.why);
    appendSection(answer, "Example", entry.example);
    appendBullets(answer, "Common mistakes", entry.commonMistakes, 3);
    appendBullets(answer, "Use it when", entry.useWhen, 3);
  }

  private static void composeHowTo(StringBuilder answer, KnowledgeEntry entry) {
    appendSection(answer, "Goal", firstNonBlank(entry.shortAnswer, entry.definition));
    appendBullets(answer, "Steps", entry.steps, 6);
    appendBullets(answer, "Checks", entry.details, 5);
    appendSection(answer, "Example", entry.example);
    appendBullets(answer, "Common mistakes", entry.commonMistakes, 3);
  }

  private static void composeDebug(StringBuilder answer, KnowledgeEntry entry) {
    appendSection(answer, "Likely issue", firstNonBlank(entry.shortAnswer, entry.definition));
    appendBullets(answer, "What to check", firstNonEmpty(entry.steps, entry.details), 6);
    appendBullets(answer, "Common mistakes", entry.commonMistakes, 4);
    appendSection(answer, "Next move", entry.why);
  }

  private static void composePick(StringBuilder answer, KnowledgeEntry entry) {
    appendSection(answer, "Short answer", firstNonBlank(entry.shortAnswer, entry.definition));
    appendBullets(answer, "Choose it when", firstNonEmpty(entry.useWhen, entry.details), 6);
    appendBullets(answer, "Avoid it when", entry.avoidWhen, 4);
    appendSection(answer, "Example", entry.example);
  }

  private Reply composeComparison(List<Match> matches, String question) {
    List<KnowledgeEntry> selected = selectComparisonEntries(matches, question);
    if (selected.size() < 2) {
      return replyFor(matches.get(0).entry, Intent.DEFINITION);
    }

    StringBuilder answer = new StringBuilder();
    KnowledgeEntry first = selected.get(0);
    KnowledgeEntry second = selected.get(1);
    appendSection(answer, "Short answer",
        firstNonBlank(findComparisonSummary(matches), comparisonSummary(first, second)));
    appendSection(answer, first.title, first.definition);
    appendSection(answer, second.title, second.definition);
    appendBullets(answer, "How to choose", mergeLimited(first.useWhen, second.useWhen, 6), 6);

    LinkedHashSet<String> chips = new LinkedHashSet<>();
    chips.add("What is " + first.title + "?");
    chips.add("What is " + second.title + "?");
    chips.addAll(followupsFor(first));
    return new Reply(answer.toString().trim(), firstN(chips, 3));
  }

  private List<KnowledgeEntry> selectComparisonEntries(List<Match> matches, String question) {
    String normalizedQuestion = normalizeText(question);
    List<KnowledgeEntry> exact = new ArrayList<>();
    for (Match match : matches) {
      if (exact.size() >= 2) break;
      if (isComparisonSummary(match.entry)) continue;
      for (String term : match.entry.safeTerms()) {
        String normalizedTerm = normalizeText(term);
        if (normalizedTerm.length() > 2 && containsPhrase(normalizedQuestion, normalizedTerm)) {
          exact.add(match.entry);
          break;
        }
      }
    }
    if (exact.size() >= 2) return exact;
    List<KnowledgeEntry> top = new ArrayList<>();
    for (Match match : matches) {
      if (top.size() >= 2) break;
      if (isComparisonSummary(match.entry)) continue;
      if (!top.contains(match.entry)) top.add(match.entry);
    }
    return top;
  }

  private static boolean isComparisonSummary(KnowledgeEntry entry) {
    return entry != null && entry.id != null && entry.id.contains("-vs-");
  }

  private static String findComparisonSummary(List<Match> matches) {
    for (Match match : matches) {
      if (isComparisonSummary(match.entry)) {
        return firstNonBlank(match.entry.definition, match.entry.shortAnswer);
      }
    }
    return "";
  }

  private static String comparisonSummary(KnowledgeEntry first, KnowledgeEntry second) {
    return first.title + " is about " + lowerFirst(first.definition)
        + "\n\n" + second.title + " is about " + lowerFirst(second.definition);
  }

  private List<String> followupsFor(KnowledgeEntry entry) {
    LinkedHashSet<String> chips = new LinkedHashSet<>();
    if (entry.followups != null) chips.addAll(entry.followups);
    if (entry.related != null) {
      for (String relatedId : entry.related) {
        KnowledgeEntry related = entriesById.get(relatedId);
        if (related != null && related.title != null && !related.title.isBlank()) {
          chips.add("What is " + related.title + "?");
        }
      }
    }
    if (chips.isEmpty()) chips.addAll(fallbackFollowups());
    return firstN(chips, 3);
  }

  private static Intent detectIntent(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    if (q.matches(".*\\b(hi|hello|hey)\\b.*")) return Intent.GREETING;
    if (q.matches(".*\\b(thanks|thank you|cheers)\\b.*")) return Intent.THANKS;
    if (q.contains(" vs ") || q.contains(" versus ") || q.contains("difference")
        || q.contains("compare")) return Intent.COMPARE;
    if (q.contains("which") || q.contains("pick") || q.contains("choose")
        || q.contains("should i use") || q.contains("when should")) return Intent.PICK;
    if (q.contains("debug") || q.contains("why no") || q.contains("not working")
        || q.contains("error") || q.contains("slow") || q.contains("timeout")
        || q.contains("wrong") || q.contains("not generated") || q.contains("missing"))
      return Intent.DEBUG;
    if (q.contains("how should i understand")) return Intent.DEFINITION;
    if (q.contains("how do i") || q.contains("how to") || q.contains("how should i")
        || q.contains("steps")) return Intent.HOW_TO;
    return Intent.DEFINITION;
  }

  private static void appendSection(StringBuilder out, String title, String text) {
    if (text == null || text.isBlank()) return;
    if (out.length() > 0) out.append("\n\n");
    out.append("**").append(title).append("**\n");
    out.append(text.trim());
  }

  private static void appendBullets(StringBuilder out, String title, List<String> items, int limit) {
    if (items == null || items.isEmpty()) return;
    if (out.length() > 0) out.append("\n\n");
    out.append("**").append(title).append("**\n");
    int count = 0;
    for (String item : items) {
      if (item == null || item.isBlank()) continue;
      out.append("- ").append(item.trim()).append("\n");
      count++;
      if (count >= limit) break;
    }
    trimTrailingNewline(out);
  }

  private static void trimTrailingNewline(StringBuilder out) {
    while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
      out.deleteCharAt(out.length() - 1);
    }
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return "";
  }

  private static List<String> firstNonEmpty(List<String> first, List<String> second) {
    if (first != null && !first.isEmpty()) return first;
    return second == null ? List.of() : second;
  }

  private static List<String> mergeLimited(List<String> first, List<String> second, int limit) {
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (first != null) merged.addAll(first);
    if (second != null) merged.addAll(second);
    return firstN(merged, limit);
  }

  private static List<String> firstN(LinkedHashSet<String> values, int limit) {
    List<String> out = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      out.add(value);
      if (out.size() >= limit) break;
    }
    return out;
  }

  private static int overlap(Set<String> a, Set<String> b) {
    int count = 0;
    for (String token : a) if (b.contains(token)) count++;
    return count;
  }

  private static boolean containsPhrase(String text, String phrase) {
    return (" " + text + " ").contains(" " + phrase + " ");
  }

  private static Set<String> tokenize(String text) {
    if (text == null) return Set.of();
    Set<String> out = new HashSet<>();
    Matcher matcher = WORD_RE.matcher(canonicalInput(text));
    while (matcher.find()) {
      String token = normalizeToken(matcher.group());
      if ((token.length() < 2 && !token.chars().allMatch(Character::isDigit))
          || STOP_WORDS.contains(token)) continue;
      out.add(token);
      List<String> expansions = TOKEN_EXPANSIONS.get(token);
      if (expansions != null) out.addAll(expansions);
    }
    return out;
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    Matcher matcher = WORD_RE.matcher(canonicalInput(text));
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String token = normalizeToken(matcher.group());
      if (!token.isBlank()) {
        if (out.length() > 0) out.append(' ');
        out.append(token);
      }
    }
    return out.toString();
  }

  private static String normalizeToken(String token) {
    String t = token.toLowerCase(Locale.ROOT).replace("'", "");
    if (t.equals("dh")) return "diffie";
    if (t.equals("msr")) return "multiset";
    if (t.equals("snd")) return "send";
    if (t.equals("rcv") || t.equals("recv")) return "receive";
    if (t.length() > 5 && t.endsWith("ies")) t = t.substring(0, t.length() - 3) + "y";
    else if (t.length() > 5 && t.endsWith("ing")) t = t.substring(0, t.length() - 3);
    else if (t.length() > 4 && t.endsWith("ed")) t = t.substring(0, t.length() - 2);
    else if (t.length() > 4 && t.endsWith("es")) t = t.substring(0, t.length() - 2);
    else if (t.length() > 3 && t.endsWith("s")) t = t.substring(0, t.length() - 1);
    if (t.equals("receiv") || t.equals("reciev") || t.equals("recive")) return "receive";
    if (t.equals("receive") || t.equals("recieve")) return "receive";
    if (t.equals("sent")) return "send";
    return t;
  }

  private static String canonicalInput(String text) {
    return text.toLowerCase(Locale.ROOT)
        .replaceAll("[_\\-/]+", " ")
        .replace('&', ' ');
  }

  private static String lowerFirst(String text) {
    if (text == null || text.isBlank()) return "";
    return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
  }

  public static final class Reply {
    public final String text;
    public final List<String> followups;

    public Reply(String text, List<String> followups) {
      this.text = text == null ? "" : text;
      this.followups = followups == null ? List.of() : List.copyOf(followups);
    }
  }

  private enum Intent {
    DEFINITION,
    COMPARE,
    PICK,
    HOW_TO,
    DEBUG,
    GREETING,
    THANKS
  }

  private record Match(KnowledgeEntry entry, double score) {}

  public static final class KnowledgeBase {
    public String greeting;
    public String fallback;
    public List<String> initialFollowups = List.of();
    public List<String> fallbackFollowups = List.of();
    public List<KnowledgeEntry> entries = List.of();
  }

  public static final class KnowledgeEntry {
    public String id;
    public String title;
    public String category;
    public String kind;
    public List<String> terms = List.of();
    public String shortAnswer;
    public String definition;
    public String why;
    public List<String> details = List.of();
    public List<String> steps = List.of();
    public List<String> useWhen = List.of();
    public List<String> avoidWhen = List.of();
    public String example;
    public List<String> commonMistakes = List.of();
    public List<String> related = List.of();
    public List<String> followups = List.of();

    private Set<String> titleTokens = Set.of();
    private Set<String> contentTokens = Set.of();
    private Set<String> indexTokens = Set.of();
    private List<String> normalizedTerms = List.of();
    private List<Set<String>> termTokenSets = List.of();

    void prepare() {
      titleTokens = tokenize(title);
      List<String> normalized = new ArrayList<>();
      List<Set<String>> termTokens = new ArrayList<>();
      for (String term : safeTerms()) {
        normalized.add(normalizeText(term));
        termTokens.add(tokenize(term));
      }
      normalizedTerms = List.copyOf(normalized);
      termTokenSets = List.copyOf(termTokens);
      contentTokens = tokenize(String.join(" ",
          safe(title),
          safe(category),
          safe(kind),
          safe(shortAnswer),
          safe(definition),
          safe(why),
          String.join(" ", safeList(details)),
          String.join(" ", safeList(steps)),
          String.join(" ", safeList(useWhen)),
          String.join(" ", safeList(avoidWhen)),
          safe(example),
          String.join(" ", safeList(commonMistakes))));
      LinkedHashSet<String> index = new LinkedHashSet<>();
      index.addAll(titleTokens);
      index.addAll(contentTokens);
      for (Set<String> tokens : termTokenSets) index.addAll(tokens);
      indexTokens = index;
    }

    List<String> safeTerms() {
      return terms == null ? List.of() : terms;
    }

    private static String safe(String value) {
      return value == null ? "" : value;
    }

    private static List<String> safeList(List<String> values) {
      return values == null ? List.of() : values;
    }
  }
}
