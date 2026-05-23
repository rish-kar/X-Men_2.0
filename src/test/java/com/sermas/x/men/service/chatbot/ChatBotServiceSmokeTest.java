package com.sermas.x.men.service.chatbot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that verifies the AIML brain loads and that trained patterns
 * route to non-fallback replies. Kept content-agnostic so it doesn't break
 * when the AIML files are re-trained — it only asserts the wiring works.
 */
class ChatBotServiceSmokeTest {

  private static final String FALLBACK_PREFIX = "I don't have a trained answer";

  @Test
  void brain_loads_and_responds_to_trained_patterns() {
    ChatBotService bot = ChatBotService.getInstance();

    assertThat(bot.isReady())
        .as("AIML brain status: %s", bot.status())
        .isTrue();

    // A few canonical entry-point patterns the bundled brain ships with.
    // We don't assert exact wording — only that the bot doesn't fall back.
    for (String input : new String[] {
        "hello",
        "who are you",
        "which mutation should I pick",
        "how does X men work"
    }) {
      String reply = bot.respond(input);
      assertThat(reply)
          .as("reply for \"%s\"", input)
          .isNotBlank()
          .doesNotStartWith(FALLBACK_PREFIX);
    }
  }

  @Test
  void cdata_templates_are_unwrapped_for_the_engine() {
    String input =
        "<aiml>\n  <template><![CDATA[Hi <b>there</b> & welcome]]></template>\n</aiml>";
    String out = ChatBotService.unwrapCdata(input);
    assertThat(out).contains("Hi &lt;b&gt;there&lt;/b&gt; &amp; welcome");
    assertThat(out).doesNotContain("CDATA");
  }
}
