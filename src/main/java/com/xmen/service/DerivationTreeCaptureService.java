package com.xmen.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Service to capture derivation tree output that is normally printed to System.out.
 * Uses a ThreadLocal to store captured output per request thread.
 */
@Service
@Slf4j
public class DerivationTreeCaptureService {

  private static final ThreadLocal<ByteArrayOutputStream> captureStream = new ThreadLocal<>();
  private static final ThreadLocal<PrintStream> originalOut = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> isCapturing = ThreadLocal.withInitial(() -> false);

  /**
   * Starts capturing System.out for the current thread.
   */
  public void startCapture() {
    if (Boolean.TRUE.equals(isCapturing.get())) {
      log.warn("Already capturing output for this thread");
      return;
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    captureStream.set(baos);
    originalOut.set(System.out);

    // Create a PrintStream that writes to both the original System.out and our capture buffer
    PrintStream teeStream = new PrintStream(baos) {
      @Override
      public void write(byte[] buf, int off, int len) {
        super.write(buf, off, len);
        originalOut.get().write(buf, off, len);
      }

      @Override
      public void write(int b) {
        super.write(b);
        originalOut.get().write(b);
      }
    };

    System.setOut(teeStream);
    isCapturing.set(true);
    log.debug("Started capturing derivation tree output");
  }

  /**
   * Stops capturing and returns the captured output.
   *
   * @return The captured output as a String, or null if nothing was captured
   */
  public String stopCaptureAndGet() {
    if (!Boolean.TRUE.equals(isCapturing.get())) {
      log.debug("No active capture for this thread");
      return null;
    }

    try {
      // Restore original System.out
      System.setOut(originalOut.get());

      // Get captured content
      ByteArrayOutputStream baos = captureStream.get();
      String captured = baos.toString();

      log.debug("Stopped capturing, captured {} characters", captured.length());
      return captured.isEmpty() ? null : captured;

    } finally {
      // Clean up thread-local resources
      captureStream.remove();
      originalOut.remove();
      isCapturing.set(false);
    }
  }

  /**
   * Clears any existing capture state without returning the content.
   * Useful for cleanup in case of exceptions.
   */
  public void clearCapture() {
    if (Boolean.TRUE.equals(isCapturing.get())) {
      System.setOut(originalOut.get());
      captureStream.remove();
      originalOut.remove();
      isCapturing.set(false);
      log.debug("Cleared capture state");
    }
  }

  /**
   * Checks if capture is currently active.
   *
   * @return true if capturing is active, false otherwise
   */
  public boolean isCapturing() {
    return Boolean.TRUE.equals(isCapturing.get());
  }
}

