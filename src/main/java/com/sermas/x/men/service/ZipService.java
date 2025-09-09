package com.sermas.x.men.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Service for creating ZIP files containing generated mutation files.
 */
@Service
@Slf4j
public class ZipService {

  /**
   * Creates a ZIP file containing all files matching the pattern and returns it as a ResponseEntity.
   *
   * @param baseFileName The base name of the original file (without extension)
   * @return ResponseEntity containing the ZIP file as a ByteArrayResource
   */
  public ResponseEntity<ByteArrayResource> createZipResponse(String baseFileName) {
    try {
      // Find all generated mutation files
      File directory = new File(Paths.get("").toAbsolutePath().toString());
      File[] mutationFiles = directory.listFiles((dir, name) ->
          name.startsWith(baseFileName + "_M") && name.endsWith(".m"));

      if (mutationFiles == null || mutationFiles.length == 0) {
        log.warn("No mutation files found for base name: {}", baseFileName);
        return ResponseEntity.noContent().build();
      }

      // Create ZIP in memory
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ZipOutputStream zos = new ZipOutputStream(baos)) {

        for (File file : mutationFiles) {
          try {
            // Add file to ZIP
            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);

            byte[] fileContent = Files.readAllBytes(file.toPath());
            zos.write(fileContent);
            zos.closeEntry();

            log.debug("Added file to ZIP: {}", file.getName());
          } catch (IOException e) {
            log.error("Error adding file {} to ZIP: {}", file.getName(), e.getMessage());
          }
        }
      }

      // Create response
      byte[] zipData = baos.toByteArray();
      ByteArrayResource resource = new ByteArrayResource(zipData);

      String zipFileName = baseFileName + "_mutations.zip";

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFileName + "\"")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .contentLength(zipData.length)
          .body(resource);

    } catch (Exception e) {
      log.error("Error creating ZIP file: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
