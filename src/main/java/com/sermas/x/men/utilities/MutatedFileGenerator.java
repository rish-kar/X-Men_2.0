package com.sermas.x.men.utilities;

import com.sermas.x.men.model.Builtins;
import com.sermas.x.men.model.Function;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
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

@Component
@Slf4j
public class MutatedFileGenerator {

    @Autowired
    private FileHandler fileHandler;

    /**
     * Saves the mutated models to files.
     *
     * @param parametersBundle The final parameters bundle containing the models to be saved.
     */
    public void saveFiles(ParametersBundle parametersBundle) {
        deleteExistingMutatedFiles();

        // Get parameters from the ParametersBundle
        String filename = parametersBundle.getFileName();
        ArrayList<ArrayList> collections = parametersBundle.getCollections();
        ArrayList<Function> functions = parametersBundle.getFunctions();
        ArrayList<Builtins> builtins = parametersBundle.getBuiltins();

        // Get the directory path of the file
        String directoryPath = Paths.get("").toAbsolutePath().normalize().toString();
        directoryPath = directoryPath.substring(0, directoryPath.lastIndexOf("/") + 1);
        int fileCounter = 0;

        // Iterate through each model in the collections
        for (ArrayList<Rule> model : collections) {
            // De-merge tags and values in the model
            model = fileHandler.demergeTagsValues(model, parametersBundle);

            // Split the file name to create new file names
            String[] fileNameTokens = filename.split("\\.(?=[^\\.]+$)");
            String newFileName = directoryPath + fileNameTokens[0] + "_M" + fileCounter + ".m";

            BufferedWriter writer = null;
            try {
                // Initialize the BufferedWriter
                writer = new BufferedWriter(new FileWriter(newFileName, false));

                // Write functions if they exist
                if (!functions.isEmpty()) {
                    writer.append("functions: ");
                    Iterator<Function> iter = functions.iterator();
                    while (iter.hasNext()) {
                        writer.append(iter.next().toString());
                        if (iter.hasNext()) {
                            writer.append(",");
                        }
                    }
                    writer.append("\n\n");
                }

                // Write builtins if they exist
                if (!builtins.isEmpty()) {
                    writer.append(builtins.get(0).toString());
                    writer.append("\n\n");
                }

                // Write each rule in the model
                for (Rule rule : model) {
                    writer.append(rule.toString());
                }

                // Increment the file counter
                fileCounter++;
            } catch (IOException e) {
                // Log the error if an exception occurs
                log.error("An error occurred while saving the file: {}", e.getMessage());
            } finally {
                // Close the writer in the finally block to ensure it is closed even if an exception occurs
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        log.error("An error occurred while closing the writer: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Deletes all mutated files in the current directory.
     * This method is used to clean up the directory with existing files before new mutations are written into files.
     */
    public void deleteExistingMutatedFiles() {

        // Get the directory path of the file
        String directoryPath = Paths.get("").toAbsolutePath().normalize().toString();
        File directory = new File(directoryPath);

        // Get all files in the directory that contain "_M" in their name and end with ".m"
        File[] files = directory.listFiles((dir, name) -> name.contains("_M") && name.endsWith(".m"));

        // Delete each file in the directory
        if (files != null) {
            for (File file : files) {
                if (file.delete()) {
                    log.info("Deleted file: " + file.getName());
                } else {
                    log.error("Failed to delete file: " + file.getName());
                }
            }
        }
    }
}
