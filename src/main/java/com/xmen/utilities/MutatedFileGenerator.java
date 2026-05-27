package com.xmen.utilities;

import com.xmen.model.Builtins;
import com.xmen.model.Function;
import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * MutatedFileGenerator class. This class is responsible for generating mutated files based on the
 * provided parameters bundle. It saves the mutated rules into files with a specific naming
 * convention, ensuring that existing mutated files are deleted before saving new ones.
 */
@Component
@Slf4j
public class MutatedFileGenerator {

  @Autowired private FileHandler fileHandler;

  /**
   * Saves the mutated files based on the provided parameters bundle.
   *
   * @param parametersBundle ParametersBundle containing the necessary data to generate the files.
   */
  public void saveFiles(ParametersBundle parametersBundle) {
    deleteExistingMutatedFiles();

    String filename = parametersBundle.getFileName();
    ArrayList<ArrayList<Rule>> collections = parametersBundle.getCollections();
    ArrayList<Function> functions = parametersBundle.getFunctions();
    ArrayList<Builtins> builtins = parametersBundle.getBuiltins();

    // Get stored sections
    String preamble = parametersBundle.getExtraContent("preamble");
    String postamble = parametersBundle.getExtraContent("postamble");

    String baseName = filename.split("\\.(?=[^\\.]+$)")[0];
    int fileCounter = 0;

    for (ArrayList<Rule> model : collections) {
      model = fileHandler.demergeTagsValues(model, parametersBundle);
      String newFileName = baseName + "_M" + fileCounter + ".m";

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(newFileName))) {
        // Write preamble
        writer.write(preamble);
        writer.newLine();
        writer.newLine();

        // Write functions
        if (!functions.isEmpty()) {
          writer.write("functions: ");
          Iterator<Function> iter = functions.iterator();
          while (iter.hasNext()) {
            writer.write(iter.next().toString());
            if (iter.hasNext()) {
              writer.write(",");
            }
          }
          writer.newLine();
          writer.newLine();
        }

        // Write builtins
        if (!builtins.isEmpty()) {
          writer.write(builtins.get(0).toString());
          writer.newLine();
          writer.newLine();
        }

        // Write rules
        for (Rule rule : model) {
          writer.write(rule.toString());
        }

        // Write postamble
        writer.newLine();
        writer.write(postamble);

        fileCounter++;
      } catch (IOException e) {
        log.error("Error saving file {}: {}", newFileName, e.getMessage());
      }
    }
  }

  /** Deletes existing mutated files in the current directory. */
  public void deleteExistingMutatedFiles() {
    File directory = new File(Paths.get("").toAbsolutePath().toString());
    File[] files = directory.listFiles((dir, name) -> name.contains("_M") && name.endsWith(".m"));

    if (files != null) {
      for (File file : files) {
        if (!file.delete()) {
          log.error("Failed to delete file: {}", file.getName());
        }
      }
    }
  }
}
