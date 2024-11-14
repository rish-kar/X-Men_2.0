package com.sermas.x.men.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service class for skipping send mutation.
 */
@Service
public class SkipMutationService {

    /**
     * Trigger skipping of send mutation.
     *
     * @param file The file to process.
     * @return A message indicating the result of the file processing.
     */
    public String skipS_Mutation(MultipartFile file) {
        // Implement your file processing logic here
        return "File processed successfully";
    }
}