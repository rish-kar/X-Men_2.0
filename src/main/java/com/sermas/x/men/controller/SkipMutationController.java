package com.sermas.x.men.controller;

import com.sermas.x.men.service.SkipMutationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for skipping mutation.
 */
@RestController
@RequestMapping("/api")
public class SkipMutationController {

    @Autowired
    private SkipMutationService skipMutationService;

    /**
     * Trigger skipping of send mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    @PostMapping("/skipSendMutation")
    public String skipSendMutation(@RequestParam("file") MultipartFile file) {
        return skipMutationService.skipS_Mutation(file);
    }
}