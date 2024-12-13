package com.sermas.x.men.controller;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.FileLoadingService;
import com.sermas.x.men.service.MutationGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Controller for replace mutations.
 */
@RestController
@RequestMapping("/api/replace")
public class ReplaceMutationController {

    @Autowired
    private FileLoadingService fileLoadingService;

    @Autowired
    @Qualifier("mutationGeneratorServiceImpl")
    MutationGeneratorService mutationGeneratorService;


    /**
     * Trigger replacing of sub messages mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/subMessagesMutations")
    public ResponseEntity<Object> replaceSubMessagesMutations(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        parametersBundle = fileLoadingService.fileLoader(file, parametersBundle);
        ArrayList<Rule> rules = parametersBundle.getCollections().get(0);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.REPLACE_SUB_MESSAGES), parametersBundle);

        return ResponseEntity.ok(null);
    }


    /**
     * Trigger replacing of type mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/typeMutations")
    public ResponseEntity<Object> replaceTypeMutations(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        parametersBundle = fileLoadingService.fileLoader(file, parametersBundle);
        ArrayList<Rule> rules = parametersBundle.getCollections().get(0);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.REPLACE_TYPE), parametersBundle);

        return ResponseEntity.ok(null);
    }
}