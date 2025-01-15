package com.sermas.x.men.utilities;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

@Slf4j
public class TamarinValidator {
    public static boolean validateTamarinFile(MultipartFile file) {
        File tempFile = null;
        try {
            // Create a temporary file
            tempFile = File.createTempFile("tamarin-", ".spthy");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            // Build the Tamarin Prover command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "tamarin-prover", "validate", tempFile.getAbsolutePath()
            );
            processBuilder.redirectErrorStream(true); // Combine stdout and stderr

            // Start the process
            Process process = processBuilder.start();

            // Capture the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();

            // Check the output and exit code
            if (exitCode == 0) {
                System.out.println("File validation successful.");
                return true;
            } else {
                System.err.println("Validation failed with output:");
                System.err.println(output);
                return false;
            }
        } catch (IOException e) {
            log.error("Error validating Tamarin file: Tamarin Prover executable not found", e);
            return false;
        } catch (Exception e) {
            log.error("Error validating Tamarin file", e);
            return false;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
