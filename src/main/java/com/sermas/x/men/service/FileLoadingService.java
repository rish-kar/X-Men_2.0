package com.sermas.x.men.service;

import com.sermas.x.men.model.ParametersBundle;
import com.sermas.x.men.model.Rule;
import com.sermas.x.men.utilities.ModelLoader;
import java.io.IOException;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Service class for skipping send mutation. */
@Service
@Slf4j
@Component
public class FileLoadingService {

  @Autowired private ModelLoader modelLoader;

  @Autowired private MutationGeneratorService mutationGeneratorService;

  private final ArrayList<Rule> theory = new ArrayList<>();

  /**
   * Trigger skipping of send mutation.
   *
   * @param file The file to process.
   * @return A message indicating the result of the file processing.
   */
  public ParametersBundle fileLoader(MultipartFile file, ParametersBundle parametersBundle)
      throws Exception {

    // Basic Level File Validation: Check if the file is empty or null
    {
      if (file == null) {
        log.error("File is null: ", file.getOriginalFilename());
        throw new Exception("File is null");
      }
      if (file.isEmpty()) {
        log.error("File is empty: ", file.getOriginalFilename());
        throw new Exception("File is empty");
      }
    }

    log.debug("Starting Tamarin validation for file: {}", file.getOriginalFilename());

    parametersBundle = loadFile(file, parametersBundle);

    return parametersBundle;
  }

  /**
   * Load the file.
   *
   * @param file The file to load.
   */
  public ParametersBundle loadFile(MultipartFile file, ParametersBundle parametersBundle) {
    try {
      return modelLoader.openFile(file, parametersBundle);
    } catch (IOException | org.antlr.runtime.RecognitionException e) {
      log.error("Error occurred while loading file: {}", e.getMessage(), e);
    }
    return null;
  }
}
