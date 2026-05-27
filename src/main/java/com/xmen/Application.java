package com.xmen;

import com.xmen.user_interface.XMenInterface;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/** Main Application. */
@SpringBootApplication(scanBasePackages = "com.xmen")
public class Application implements CommandLineRunner {

  /** Spring context handle — kept so the shutdown hook can close it. */
  private static volatile ConfigurableApplicationContext SPRING_CONTEXT;

  /**
   * Main method.
   *
   * @param args Command line arguments
   */
  public static void main(String[] args) {
    System.out.println("java.awt.headless=" + System.getProperty("java.awt.headless"));
    SPRING_CONTEXT = SpringApplication.run(Application.class, args);

    if (!isTestRuntime()) {
      // JVM-wide safety net: if anything (an OS signal, a sibling exit() call,
      // an Actuator /shutdown, etc.) starts tearing the JVM down, make sure the
      // JavaFX runtime also exits so we don't leak windows.
      Runtime.getRuntime()
              .addShutdownHook(
                      new Thread(
                              () -> {
                                try {
                                  XMenInterface.shutdownUi();
                                } catch (Throwable ignored) {
                                }

                                try {
                                  if (SPRING_CONTEXT != null && SPRING_CONTEXT.isActive()) {
                                    SPRING_CONTEXT.close();
                                  }
                                } catch (Throwable ignored) {
                                }
                              },
                              "xmen-force-shutdown"));
    }
  }

  /**
   * Run method that is executed after the application context is loaded.
   *
   * @param args Command line arguments
   */
  @Override
  public void run(String... args) {
    System.out.println("Is Headless: " + java.awt.GraphicsEnvironment.isHeadless());

    if (!isUiEnabled()) {
      System.out.println("JavaFX UI disabled by xmen.ui.enabled/XMEN_UI_ENABLED.");
      return;
    }

    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      // Blocks until the JavaFX app exits (last stage closed or Platform.exit()).
      XMenInterface.launch(XMenInterface.class);

      // Belt-and-braces: when launch() returns, force a JVM exit so the
      // embedded web server doesn't keep the process alive forever.
      if (!isTestRuntime()) System.exit(0);
    } else {
      System.err.println("Cannot run GUI in a headless environment");
    }
  }

  /**
   * Spring context is closing for some reason other than the user clicking
   * the X (e.g. SIGTERM, /actuator/shutdown, devtools reload). Pull the
   * JavaFX runtime down with it so no window is left orphaned.
   */
  @EventListener
  public void onContextClosed(ContextClosedEvent event) {
    try {
      XMenInterface.shutdownUi();
    } catch (Throwable ignored) {
      // already gone
    }
  }

  private static boolean isTestRuntime() {
    return System.getProperty("surefire.test.class.path") != null
        || System.getProperty("org.gradle.test.worker") != null
        || System.getProperty("java.class.path", "").contains("surefire");
  }

  private static boolean isUiEnabled() {
    String value =
        System.getProperty(
            "xmen.ui.enabled", System.getenv().getOrDefault("XMEN_UI_ENABLED", "true"));
    return Boolean.parseBoolean(value);
  }
}
