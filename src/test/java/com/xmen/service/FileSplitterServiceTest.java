package com.xmen.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileSplitterService Tests")
class FileSplitterServiceTest {

  private final FileSplitterService service = new FileSplitterService();

  @Test
  @DisplayName("File without markers: whole content treated as rules section")
  void testFileWithoutMarkers() {
    String content = "theory Foo\nbegin\nrule R: [] --[]-> []\nend";

    FileSplitterService.FileSections sections = service.splitFile(content);

    assertThat(sections.preamble()).isEmpty();
    assertThat(sections.rules().trim()).startsWith("theory Foo");
    assertThat(sections.rules().trim()).endsWith("end");
    assertThat(sections.postamble()).isEmpty();
  }

  @Test
  @DisplayName("File with all markers: preamble/rules/postamble split unchanged")
  void testFileWithMarkersUnchanged() {
    String content =
        "/****MODEL****/\n"
            + "theory Sample\n"
            + "begin\n"
            + "/****RULES****/\n"
            + "rule R1: [] --[]-> []\n"
            + "/****ENDOFRULES****/\n"
            + "lemma L1: \"all-traces\"\n"
            + "end\n"
            + "/****ENDOFMODEL****/\n";

    FileSplitterService.FileSections sections = service.splitFile(content);

    assertThat(sections.preamble()).contains("theory Sample");
    assertThat(sections.preamble()).contains("/****RULES****/");
    assertThat(sections.rules()).contains("rule R1:");
    assertThat(sections.rules().trim()).endsWith("end");
    assertThat(sections.postamble()).contains("/****ENDOFRULES****/");
    assertThat(sections.postamble()).contains("lemma L1:");
    assertThat(sections.postamble()).contains("/****ENDOFMODEL****/");
  }
}
