package com.xmen.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * FileSplitterService class. This service is responsible for splitting a file content into three
 * sections: preamble, rules, and postamble based on specific markers.
 */
@Service
public class FileSplitterService {

  private static final String[] MARKERS = {"/****RULES****/", "/****ENDOFRULES****/"};

  /**
   * Splits the given file content into preamble, rules, and postamble sections.
   *
   * @param content The content of the file to be split
   * @return A FileSections object containing the preamble, rules, and postamble
   */
  public FileSections splitFile(String content) {
    if (!hasAnyMarker(content)) {
      String rulesStr = content;
      if (!rulesStr.trim().endsWith("end")) {
        rulesStr = rulesStr + "\nend\n";
      }
      return new FileSections("", rulesStr, "");
    }

    List<String> preamble = new ArrayList<>();
    List<String> rules = new ArrayList<>();
    List<String> postamble = new ArrayList<>();
    List<String> currentSection = preamble;

    for (String line : content.split("\n")) {
      String trimmed = line.trim();

      if (trimmed.equals(MARKERS[0])) {
        // Add RULES marker to preamble and switch to rules section
        currentSection.add(line);
        currentSection = rules;
      } else if (trimmed.equals(MARKERS[1])) {
        // Switch to postamble first, then add ENDOFRULES marker
        currentSection = postamble;
        currentSection.add(line);
      } else {
        currentSection.add(line);
      }
    }

    // Append 'end' to rules section to make it a valid Tamarin theory for parsing
    String rulesStr = String.join("\n", rules);
    if (!rulesStr.trim().endsWith("end")) {
      rulesStr = rulesStr + "\nend\n";
    }

    return new FileSections(
        String.join("\n", preamble), rulesStr, String.join("\n", postamble));
  }

  private static boolean hasAnyMarker(String content) {
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.equals(MARKERS[0]) || trimmed.equals(MARKERS[1])) {
        return true;
      }
    }
    return false;
  }

  /**
   * FileSections record to hold the sections of the file.
   *
   * @param preamble The preamble section of the file
   * @param rules The rules section of the file
   * @param postamble The postamble section of the file
   */
  public record FileSections(String preamble, String rules, String postamble) {}
}
