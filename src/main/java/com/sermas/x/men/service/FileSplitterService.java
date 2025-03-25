package com.sermas.x.men.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FileSplitterService {

    private static final String[] MARKERS = {
            "/****RULES****/",
            "/****ENDOFRULES****/"
    };

    public FileSections splitFile(String content) {
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

        return new FileSections(
                String.join("\n", preamble),
                String.join("\n", rules),
                String.join("\n", postamble)
        );
    }

    public record FileSections(String preamble, String rules, String postamble) {}
}