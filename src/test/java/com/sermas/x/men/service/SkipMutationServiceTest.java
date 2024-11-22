package com.sermas.x.men.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkipMutationServiceTest {

    private SkipMutationService skipMutationService;
    private MultipartFile file;

    @BeforeEach
    void setUp() {
        skipMutationService = new SkipMutationService();
        file = mock(MultipartFile.class);
    }

//    @Test
//    void skipS_Mutation_shouldReturnSuccessMessage_whenFileIsProcessed() {
//        when(file.isEmpty()).thenReturn(false);
//        String result = skipMutationService.skipS_Mutation(file);
//        assertEquals("File processed successfully", result);
//    }
//
//    @Test
//    void skipS_Mutation_shouldReturnErrorMessage_whenFileIsEmpty() {
//        when(file.isEmpty()).thenReturn(true);
//        String result = skipMutationService.skipS_Mutation(file);
//        assertEquals("File is empty", result);
//    }
//
//    @Test
//    void skipS_Mutation_shouldReturnErrorMessage_whenFileIsNull() {
//        String result = skipMutationService.skipS_Mutation(null);
//        assertEquals("File is null", result);
//    }
}