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
 * Controller for add mutation.
 */
@RestController
@RequestMapping("/api")
public class AddMutationController {

    @Autowired
    private FileLoadingService fileLoadingService;

    @Autowired
    @Qualifier("mutationGeneratorServiceImpl")
    MutationGeneratorService mutationGeneratorService;


    /**
     * Trigger of add mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/addMutations")
    public ResponseEntity<Object> addMutations(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        parametersBundle = fileLoadingService.fileLoader(file, parametersBundle);
        ArrayList<Rule> rules = parametersBundle.getCollections().get(0);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.ADD), parametersBundle);

        return ResponseEntity.ok(null);
    }
}