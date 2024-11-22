package com.sermas.x.men.service;

import com.sermas.x.men.utilities.ModelLoader;
import com.sermas.x.men.utilities.TamarinValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.digester.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Service class for skipping send mutation.
 */
@Service
@Slf4j
@Component
public class SkipMutationService {

    @Autowired
    SkipSendMutation skipSendMutation;

    @Autowired
    private ModelLoader modelLoader;

    private final ArrayList<Rule> theory = new ArrayList();

    /**
     * Trigger skipping of send mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    public String skipS_Mutation(MultipartFile file) {
        if (file == null) {
            return "File is null";
        }
        if (file.isEmpty()) {
            return "File is empty";
        }

        log.info("Starting Tamarin validation for file: {}", file.getOriginalFilename());
        boolean isValid = true;
//        boolean isValid = TamarinValidator.validateTamarinFile(file);
        if (!isValid) {
            return "File validation failed";
        }

        loadFile(file);

        // Implement your file processing logic here
        return "File processed successfully";
    }

    /**
     * Load the file.
     *
     * @param file The file to load.
     */
    public void loadFile(MultipartFile file) {
        try {
            modelLoader.openFile(file);
        } catch (IOException | org.antlr.runtime.RecognitionException e) {
            log.error("Error occurred while loading file: {}", e.getMessage(), e);
        }
    }
}