package com.xmen.service.chatbot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the YAML-backed chatbot. These tests assert the important
 * contract: answers are concept-driven, sectioned, and do not depend on legacy XML training.
 */
class ChatBotServiceSmokeTest {

  @Test
  void yaml_knowledge_base_loads() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.isReady())
        .as("chatbot status: %s", bot.status())
        .isTrue();
    assertThat(bot.status()).contains("YAML knowledge base loaded");
  }

  @Test
  void greeting_and_core_entry_points_work() {
    ChatBotService bot = ChatBotService.getInstance();

    for (String input : new String[] {
        "hello",
        "who are you",
        "what is tamarin",
        "what mutations are available",
        "how does X men work"
    }) {
      ChatBotService.Reply reply = bot.respondReply(input);
      assertThat(reply.text)
          .as("reply for \"%s\"", input)
          .isNotBlank()
          .doesNotContain("not have enough signal");
      assertThat(reply.followups).as("followups for \"%s\"", input).isNotEmpty();
    }
  }

  @Test
  void unknown_answers_keep_suggestion_chips_available() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("zqxw impossible unknown topic");

    assertThat(reply.text).contains("not have enough signal");
    assertThat(reply.followups)
        .contains("What is Tamarin?", "Which mutation should I pick?");
  }

  @Test
  void tamarin_definitions_are_sectioned_and_understandable() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("what is a tamarin lemma");

    assertThat(reply.text)
        .contains("**Definition**")
        .contains("**Key points**")
        .containsIgnoringCase("property")
        .containsIgnoringCase("trace")
        .doesNotContain("Manual source")
        .doesNotContain("<category");
    assertThat(reply.followups)
        .contains("What is all-traces?", "What is exists-trace?");
  }

  @Test
  void related_tamarin_questions_retrieve_the_right_concept() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply =
        bot.respondReply("how should I understand attacker knowledge K in tamarin");

    assertThat(reply.text)
        .contains("**Definition**")
        .containsIgnoringCase("adversary")
        .containsIgnoringCase("derive")
        .doesNotContain("Manual sections")
        .doesNotContain("```");
    assertThat(reply.followups).contains("What is secrecy in Tamarin?");
  }

  @Test
  void comparison_questions_are_answered_as_comparisons() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply =
        bot.respondReply("what is the difference between forget and neglect");

    assertThat(reply.text)
        .contains("**Short answer**")
        .contains("**Forget**")
        .contains("**Neglect**")
        .containsIgnoringCase("gone")
        .containsIgnoringCase("available");
  }

  @Test
  void selection_questions_use_choice_template() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("which mutation should I pick");

    assertThat(reply.text)
        .contains("**Short answer**")
        .contains("**Choose it when**")
        .contains("Use Skip")
        .contains("Use Forget")
        .contains("Use Neglect");
  }

  @Test
  void paper_mutation_catalog_is_precise() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("what mutations are in the paper");

    assertThat(reply.text)
        .contains("**Definition**")
        .contains("Skip")
        .contains("Add")
        .contains("Replace")
        .contains("Neglect")
        .contains("four core human mutation families")
        .doesNotContain("<aiml")
        .doesNotContain("random");
  }

  @Test
  void skip_variants_are_answered_from_the_paper() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("what are the five skip variants");

    assertThat(reply.text)
        .contains("Skip Send")
        .contains("Skip Receive")
        .contains("Skip Send Receive")
        .contains("Skip Receive Send")
        .contains("Skip Receive Send Receive");
  }

  @Test
  void specific_skip_variant_does_not_fall_back_to_generic_skip() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("skip send receive skip");

    assertThat(reply.text)
        .contains("**Definition**")
        .contains("Skip-Send-Receive")
        .contains("skips a human send action and the later receive action")
        .contains("**Why it matters**")
        .contains("does not consume the response")
        .doesNotContain("Skip removes one or more actions from the human subtrace");
  }

  @Test
  void spaced_hyphenated_and_misspelled_skip_variants_match_the_specific_variant() {
    ChatBotService bot = ChatBotService.getInstance();

    String[] questions = {
        "define Skip-Receive-Send",
        "define Skip Receive Send",
        "define Skip Recieve Send",
        "what is skip rcv send"
    };

    for (String question : questions) {
      ChatBotService.Reply reply = bot.respondReply(question);
      assertThat(reply.text)
          .as("reply for %s", question)
          .contains("**Definition**")
          .contains("Skip-Receive-Send")
          .contains("skips a human receive action and the send action that would normally follow it")
          .doesNotContain("Skip removes one or more actions from the human subtrace");
    }
  }

  @Test
  void send_receive_order_is_preserved_for_specific_skip_variants() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("define Skip Send Receive"))
        .contains("Skip-Send-Receive")
        .contains("starts with a send");

    assertThat(bot.respond("define Skip Receive Send"))
        .contains("Skip-Receive-Send")
        .contains("starts with a receive");
  }

  @Test
  void all_named_mutation_variants_have_clear_answers() {
    ChatBotService bot = ChatBotService.getInstance();

    String[] questions = {
        "define Skip Send",
        "define Skip Receive",
        "define Skip Receive Send",
        "define Skip Receive Send Receive",
        "define Add new send",
        "define Add duplicate send",
        "define Replace Sub-Messages",
        "define Replace Type",
        "define Add plus Replace",
        "define Neglect mutation",
        "define Forget mutation"
    };

    for (String question : questions) {
      ChatBotService.Reply reply = bot.respondReply(question);
      assertThat(reply.text)
          .as("reply for %s", question)
          .contains("**Definition**")
          .contains("**Why it matters**")
          .doesNotContain("not have enough signal")
          .doesNotContain("<aiml");
    }
  }

  @Test
  void matching_mutation_answer_explains_propagation() {
    ChatBotService bot = ChatBotService.getInstance();

    ChatBotService.Reply reply = bot.respondReply("why does X-Men need matching mutations");

    assertThat(reply.text)
        .contains("**Definition**")
        .containsIgnoringCase("other agents")
        .containsIgnoringCase("executable")
        .containsIgnoringCase("propagation");
  }

  @Test
  void forget_paper_answers_are_specific_and_sectioned() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("define the Forget mutation from the new paper"))
        .contains("**Definition**")
        .contains("knowledge-level mutation")
        .contains("does not directly remove a send or receive action");

    assertThat(bot.respond("what is the forget set"))
        .contains("**Definition**")
        .containsIgnoringCase("monotonic")
        .contains("hypotheses");

    assertThat(bot.respond("what are blocked derivations under Forget"))
        .contains("Case 1")
        .contains("Case 2")
        .contains("Case 3")
        .contains("Dolev-Yao");

    assertThat(bot.respond("what is the Bank Login forget attack"))
        .contains("Bank2")
        .contains("did not intend")
        .contains("authentication goal");
  }

  @Test
  void expanded_forget_paper_entries_answer_narrow_questions() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("why does Forget not just delete knowledge"))
        .contains("**Definition**")
        .containsIgnoringCase("composed")
        .containsIgnoringCase("forget set")
        .containsIgnoringCase("derivations");

    assertThat(bot.respond("explain derivability example 3 forgotten pair"))
        .contains("**Definition**")
        .contains("Case 1")
        .contains("Case 2")
        .containsIgnoringCase("pair");

    assertThat(bot.respond("what is the replacement set in Forget"))
        .contains("**Definition**")
        .containsIgnoringCase("format")
        .containsIgnoringCase("label")
        .containsIgnoringCase("PW2");

    assertThat(bot.respond("explain the User_2 rule in the Bank Login appendix"))
        .contains("**Definition**")
        .contains("PasswordAttempt")
        .contains("pw2")
        .contains("SndS");

    assertThat(bot.respond("what is SG1 Human_intends_Bank2_if_Bank2_OK"))
        .contains("**Definition**")
        .contains("LoginOK")
        .contains("U_LoginRequest")
        .containsIgnoringCase("falsifies");
  }

  @Test
  void paper_case_study_attacks_are_retrievable() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("what is the Oyster incomplete journey attack"))
        .contains("GO1")
        .containsIgnoringCase("touch in");
    assertThat(bot.respond("what is the SSO replace submessage attack"))
        .contains("injective")
        .containsIgnoringCase("service provider");
    assertThat(bot.respond("what is the Coach forged ticket attack"))
        .contains("Eq(date")
        .containsIgnoringCase("driver");
  }

  @Test
  void xmen_manual_entries_answer_developer_questions() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("what does FileSplitterService do in the X-Men manual"))
        .contains("**Definition**")
        .contains("preamble")
        .contains("/RULES/")
        .contains("/ENDOFRULES/");

    assertThat(bot.respond("explain ParametersBundle from the manual"))
        .contains("**Definition**")
        .contains("central container")
        .contains("forgetMutationSet")
        .contains("Flags");

    assertThat(bot.respond("what is PSpecial in X-Men 2.0"))
        .contains("**Definition**")
        .contains("groups multiple values")
        .containsIgnoringCase("angle brackets");
  }

  @Test
  void xmen_manual_entries_answer_operation_and_derivation_questions() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("what are the X-Men 2.0 manual case studies"))
        .contains("**Definition**")
        .contains("Oyster")
        .contains("SAML")
        .contains("Coach");

    assertThat(bot.respond("explain the Haskell derivation service endpoints"))
        .contains("**Definition**")
        .contains("DerivationService.hs")
        .contains("/derive")
        .contains("/health");

    assertThat(bot.respond("mutation files not generated what should I check"))
        .contains("**Likely issue**")
        .contains(".spthy")
        .containsIgnoringCase("permissions")
        .containsIgnoringCase("logs");
  }

  @Test
  void xmen_manual_replace_type_answer_is_specific() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("define Replace Type from the X-Men manual"))
        .contains("**Definition**")
        .contains("same type")
        .contains("different name")
        .contains("Mutants")
        .doesNotContain("partial-message variants");
  }

  @Test
  void generic_and_sentence_style_questions_match_atomic_entries() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.respond("explain X-Men like I am new and why we need it"))
        .contains("**Definition**")
        .contains("realistic mistake")
        .contains("Tamarin");

    assertThat(bot.respond("what can I type if I want better answers"))
        .contains("**Definition**")
        .contains("short terms")
        .contains("full sentences");

    assertThat(bot.respond("the user misses a message and does not reply, which mutation is that"))
        .contains("**Short answer**")
        .contains("Use Skip")
        .contains("Use Forget");

    assertThat(bot.respond("what file should I upload to X-Men"))
        .contains("**Definition**")
        .contains(".spthy")
        .contains("Tamarin");
  }
}
