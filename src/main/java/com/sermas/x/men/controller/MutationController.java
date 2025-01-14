package com.sermas.x.men.controller;

import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.service.FileLoadingService;
import com.sermas.x.men.service.MutationGeneratorService;
import org.apache.tomcat.util.digester.Rules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/**
 * Controller for mutation generation.
 */
@RestController
@RequestMapping("/api")
public class MutationController {

    @Autowired
    public FileLoadingService fileLoadingService;

    @Autowired
    @Qualifier("mutationGeneratorServiceImpl")
    public MutationGeneratorService mutationGeneratorService;


    @PostMapping("/generateMutations")
    public ResponseEntity<ArrayList<Rules>> generateMutations(
            @RequestHeader(value = "Skip-Send", required = false) Boolean skipSend,
            @RequestHeader(value = "Skip-Receive", required = false) Boolean skipReceive,
            @RequestHeader(value = "Skip-Send-Receive", required = false) Boolean skipSendReceive,
            @RequestHeader(value = "Skip-Recieve-Send", required = false) Boolean skipReceiveSend,
            @RequestHeader(value = "Skip-Recieve-Send-Receive", required = false) Boolean skipReceiveSendReceive,
            @RequestHeader(value = "Add-Mutation", required = false) Boolean addMutation,
            @RequestHeader(value = "Replace-Sub-Messages", required = false) Boolean replaceSubMessages,
            @RequestHeader(value = "Replace-Type", required = false) Boolean replaceType,
            @RequestHeader(value = "Neglect-Mutation", required = false) Boolean neglectMutation,
            @RequestParam("file") MultipartFile file) throws Exception {


        // Create a set of mutations based on the request headers
        Set<Mutations> mutationSet = EnumSet.noneOf(Mutations.class);

        if (Boolean.TRUE.equals(skipSend)) mutationSet.add(Mutations.SKIP_SEND);
        if (Boolean.TRUE.equals(skipReceive)) mutationSet.add(Mutations.SKIP_RECEIVE);
        if (Boolean.TRUE.equals(skipSendReceive)) mutationSet.add(Mutations.SKIP_SEND_RECEIVE);
        if (Boolean.TRUE.equals(skipReceiveSend)) mutationSet.add(Mutations.SKIP_RECEIVE_SEND);
        if (Boolean.TRUE.equals(skipReceiveSendReceive)) mutationSet.add(Mutations.SKIP_RECEIVE_SEND_RECEIVE);
        if (Boolean.TRUE.equals(addMutation)) mutationSet.add(Mutations.ADD);
        if (Boolean.TRUE.equals(replaceSubMessages)) mutationSet.add(Mutations.REPLACE_SUB_MESSAGES);
        if (Boolean.TRUE.equals(replaceType)) mutationSet.add(Mutations.REPLACE_TYPE);
        if (Boolean.TRUE.equals(neglectMutation)) mutationSet.add(Mutations.NEGLECT);

        // Assuming you have a method to convert MultipartFile to ArrayList<Rules>
        ArrayList<Rule> rules = fileLoadingService.fileLoader(file);
        ArrayList<Rule> newSetofRules = mutationGeneratorService.generateMutation(rules, mutationSet);


        return ResponseEntity.ok(null);
    }

}