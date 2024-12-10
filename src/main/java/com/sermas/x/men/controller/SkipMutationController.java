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
 * Controller for skipping mutation.
 */
@RestController
@RequestMapping("/api/skip")
public class SkipMutationController {

    @Autowired
    private FileLoadingService fileLoadingService;

    @Autowired
    @Qualifier("mutationGeneratorServiceImpl")
    MutationGeneratorService mutationGeneratorService;


    /**
     * Trigger skipping of send mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/sendMutations")
    public ResponseEntity<Object> skipSendMutation(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        ArrayList<Rule> rules = fileLoadingService.fileLoader(file);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.SKIP_SEND), parametersBundle);

        return ResponseEntity.ok(null);
    }


    /**
     * Trigger skipping of receive mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/receiveMutations")
    public ResponseEntity<Object> skipReceiveMutation(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        ArrayList<Rule> rules = fileLoadingService.fileLoader(file);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.SKIP_RECEIVE), parametersBundle);

        return ResponseEntity.ok(null);
    }


    /**
     * Trigger skipping of send receive mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/sendReceiveMutations")
    public ResponseEntity<Object> skipSendReceiveMutation(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        ArrayList<Rule> rules = fileLoadingService.fileLoader(file);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.SKIP_SEND_RECEIVE), parametersBundle);

        return ResponseEntity.ok(null);
    }


    /**
     * Trigger skipping of receive send mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/receiveSendMutations")
    public ResponseEntity<Object> skipReceiveSendMutation(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        ArrayList<Rule> rules = fileLoadingService.fileLoader(file);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.SKIP_RECEIVE_SEND), parametersBundle);

        return ResponseEntity.ok(null);
    }


    /**
     * Trigger skipping of receive send receive mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/receiveSendReceiveMutations")
    public ResponseEntity<Object> skipReceiveSendReceiveMutation(@RequestParam("file") MultipartFile file) throws Exception {

        ParametersBundle parametersBundle = new ParametersBundle();

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        ArrayList<Rule> rules = fileLoadingService.fileLoader(file);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, Collections.singleton(Mutations.SKIP_RECEIVE_SEND_RECEIVE), parametersBundle);

        return ResponseEntity.ok(null);
    }
}