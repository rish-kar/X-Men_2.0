package com.xmen.utilities;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Single source of truth for "where do per-run mutation files live on disk."
 *
 * <p>The mutation pipeline previously wrote {@code .m} files via a relative {@code FileWriter}
 * path, which resolves against the process's current working directory. That works in IDE/dev
 * runs (CWD = repo root), and on the per-user Windows installer (CWD = the user-writable
 * install folder), but breaks on the macOS .app bundle and the Linux .deb because jpackage
 * launches those with CWD set to {@code /} — read-only for the user — so every mutation file
 * silently failed with IOException and the ZIP came back empty.
 *
 * <p>This component centralises the choice of working directory on a writable, OS-agnostic
 * location ({@code ~/.xmen/runs}) and creates it on demand. {@link MutatedFileGenerator} and
 * {@link com.xmen.service.ZipService} read/write through it.
 */
@Component
@Slf4j
public class MutationWorkspace {

  /**
   * Override hook for tests or admins:
   *   -Dxmen.workspace.dir=/some/path
   * Falls back to {@code <user.home>/.xmen/runs} when unset.
   */
  private static final String SYSTEM_PROPERTY = "xmen.workspace.dir";

  private Path root;

  @PostConstruct
  void init() {
    String override = System.getProperty(SYSTEM_PROPERTY);
    Path candidate;
    if (override != null && !override.isBlank()) {
      candidate = Paths.get(override);
    } else if (isSurefireRuntime()) {
      // Integration tests (SkipSendTests, AddMutationTests, …) assert that
      // generated `.m` files live next to `src/test/resources/Oyster.spthy`,
      // i.e. the repo root, and clean them up via `Paths.get("Oyster_M0.m")`.
      // Honouring CWD under surefire keeps those assertions valid without
      // forcing every test to inject a custom workspace.
      candidate = Paths.get("").toAbsolutePath();
    } else {
      candidate = Paths.get(System.getProperty("user.home"), ".xmen", "runs");
    }
    try {
      Files.createDirectories(candidate);
      this.root = candidate;
      log.info("Mutation workspace: {}", candidate);
    } catch (IOException e) {
      // Fall back to the OS temp dir if user.home isn't usable (e.g. a sandboxed
      // environment). Better to produce files somewhere than to fail entirely.
      Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "xmen-runs");
      try {
        Files.createDirectories(tmp);
        this.root = tmp;
        log.warn(
            "Mutation workspace fell back to {} ({} was not writable: {})",
            tmp, candidate, e.getMessage());
      } catch (IOException fatal) {
        // Last-resort: CWD. Matches legacy behaviour so tests that run from the
        // repo root still pass.
        this.root = Paths.get("").toAbsolutePath();
        log.warn(
            "Mutation workspace fell back to CWD {} (no writable user.home or tmpdir found)",
            this.root);
      }
    }
  }

  /** Directory all generated {@code .m} files are written to and read from. */
  public Path root() {
    return root;
  }

  /** Convenience for tests: resolve a filename inside the workspace. */
  public Path resolve(String name) {
    return root.resolve(name);
  }

  /**
   * Mirror of {@code Application.isTestRuntime()} — detect that we're inside a
   * surefire fork so the workspace stays on the project root for assertions
   * that hard-code {@code Paths.get("Oyster_M0.m")}.
   */
  private static boolean isSurefireRuntime() {
    return System.getProperty("surefire.test.class.path") != null
        || System.getProperty("org.gradle.test.worker") != null
        || System.getProperty("java.class.path", "").contains("surefire");
  }
}
