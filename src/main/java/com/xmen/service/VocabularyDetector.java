package com.xmen.service;

import com.xmen.config.CeremonyVocabulary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort vocabulary extraction from a raw {@code .spthy} file.
 *
 * <p>The detector scans action labels inside {@code --[ ... ]->} blocks, fact families used
 * in rule pre/postconditions, and the conventional Tamarin sigils ({@code $}, {@code ~},
 * {@code !}). It builds a fresh {@link CeremonyVocabulary} populated with whatever it can
 * recognise, falling back to the project defaults for anything it cannot find.
 *
 * <p>This is intentionally conservative: it never replaces a default it isn't confident
 * about. The result still goes through {@link TamarinValidationService} on the controller
 * side before it touches the live config.
 */
@Slf4j
@Service
public class VocabularyDetector {

  // Action labels e.g.  Send($Human,'m1', ...)
  private static final Pattern ACTION_PAT =
      Pattern.compile("(?<![A-Za-z0-9_])([A-Z][A-Za-z0-9_]*)\\s*\\(");

  // Fact names in [ ... ]  e.g.  State($U,...)  or  !Pk($A,...)  or  Out(x)
  private static final Pattern FACT_PAT =
      Pattern.compile("(?<![A-Za-z0-9_])(!?[A-Z][A-Za-z0-9_]*)\\s*\\(");

  // Common send / receive synonyms — first match wins.
  private static final List<String> SEND_CANDIDATES =
      List.of("Send", "Snd", "Sends", "Out", "SndS", "ChanSndS");
  private static final List<String> RECEIVE_CANDIDATES =
      List.of("Receive", "Rcv", "Recv", "In", "RcvS", "ChanRcvS");
  private static final List<String> FORGET_CANDIDATES =
      List.of("Forget", "Forgets", "Lose", "Loses");
  private static final List<String> HUMAN_CANDIDATES = List.of("H", "Human", "Person");

  public CeremonyVocabulary detectFrom(String spthyContent) {
    CeremonyVocabulary v = new CeremonyVocabulary();
    if (spthyContent == null || spthyContent.isBlank()) {
      log.debug("VocabularyDetector: empty input, returning defaults.");
      return v;
    }

    // Scan the whole file — earlier versions only swept action labels inside
    // --[ ... ]-> blocks, which missed files (e.g. coach-service style) that
    // declare their action names elsewhere in the theory body. The regexes
    // are anchored on the upper-case identifier shape so noise is rare.
    Set<String> actions = collect(ACTION_PAT, spthyContent);
    Set<String> facts = collect(FACT_PAT, spthyContent);

    // ---- Action mappings ----
    firstHit(actions, SEND_CANDIDATES).ifPresent(s -> v.getActions().setSend(s));
    firstHit(actions, RECEIVE_CANDIDATES).ifPresent(s -> v.getActions().setReceive(s));
    firstHit(actions, FORGET_CANDIDATES).ifPresent(s -> v.getActions().setForget(s));
    firstHit(actions, HUMAN_CANDIDATES).ifPresent(s -> v.getActions().setHumanMarker(s));

    // Core-actions set: the upper-case action labels seen near rule arrows.
    List<String> core = new ArrayList<>(actions);
    core.removeIf(a -> a.length() > 24);            // drop oddly long matches
    if (!core.isEmpty()) v.getActions().setCoreActions(core);

    // ---- Fact families ----
    List<String> stateFacts = new ArrayList<>();
    List<String> outboundFacts = new ArrayList<>();
    List<String> inboundFacts = new ArrayList<>();
    List<String> freshFacts = new ArrayList<>();

    for (String f : facts) {
      if (f.equalsIgnoreCase("State")) stateFacts.add(f);
      else if (f.equalsIgnoreCase("Out") || f.equalsIgnoreCase("SndS") || f.equalsIgnoreCase("ChanSndS")) outboundFacts.add(f);
      else if (f.equalsIgnoreCase("In")  || f.equalsIgnoreCase("RcvS") || f.equalsIgnoreCase("ChanRcvS")) inboundFacts.add(f);
      else if (f.equalsIgnoreCase("Fr")) freshFacts.add(f);
    }
    if (!stateFacts.isEmpty())     v.getFacts().setState(stateFacts);
    if (!outboundFacts.isEmpty())  v.getFacts().setOutboundChannels(outboundFacts);
    if (!inboundFacts.isEmpty())   v.getFacts().setInboundChannels(inboundFacts);
    if (!freshFacts.isEmpty())     v.getFacts().setFresh(freshFacts);

    // ---- Adornments (presence-based) ----
    v.getAdornments().setFreshPrefix(spthyContent.contains("~") ? "~" : v.getAdornments().getFreshPrefix());
    v.getAdornments().setPublicPrefix(spthyContent.contains("$") ? "$" : v.getAdornments().getPublicPrefix());
    v.getAdornments().setPersistentPrefix(spthyContent.contains("!") ? "!" : v.getAdornments().getPersistentPrefix());

    return v;
  }

  /* ------------------------------------------------------------------ */
  /*  Helpers                                                           */
  /* ------------------------------------------------------------------ */

  private Set<String> collect(Pattern p, String text) {
    Set<String> out = new LinkedHashSet<>();
    Matcher m = p.matcher(text);
    while (m.find()) out.add(m.group(1));
    return out;
  }

  private java.util.Optional<String> firstHit(Set<String> haystack, List<String> needles) {
    for (String n : needles) {
      if (haystack.contains(n)) return java.util.Optional.of(n);
    }
    return java.util.Optional.empty();
  }
}
