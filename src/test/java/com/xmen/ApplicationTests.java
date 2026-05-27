package com.xmen;

import com.xmen.user_interface.XMenInterface;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers every branch of com.xmen.Application without ever opening a JavaFX window.
 *
 * <p>Needs test dependency: org.mockito:mockito-inline
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
class ApplicationTests {

  /* Make JVM headless by default so no real UI can pop up accidentally */
  @BeforeAll
  static void forceHeadless() {
    System.setProperty("java.awt.headless", "true");
  }

  @AfterEach
  void clearUiFlag() {
    System.clearProperty("xmen.ui.enabled");
  }

  /* 1 ─ main(..) prints flag and delegates to SpringApplication.run */
  @Test
  @DisplayName("01 main() delegates to SpringApplication.run")
  void main_delegatesToSpring() {
    try (MockedStatic<SpringApplication> spring = Mockito.mockStatic(SpringApplication.class)) {

      var fakeCtx = Mockito.mock(org.springframework.context.ConfigurableApplicationContext.class);

      spring
          .when(
              () ->
                  SpringApplication.run(
                      Mockito.eq(com.xmen.Application.class), Mockito.any()))
          .thenReturn(fakeCtx);

      var buf = new java.io.ByteArrayOutputStream();
      var old = System.out;
      System.setOut(new java.io.PrintStream(buf));

      com.xmen.Application.main(new String[] {"arg"});

      spring.verify(
          () ->
              SpringApplication.run(
                  Mockito.eq(com.xmen.Application.class), Mockito.any()));

      assertTrue(buf.toString().contains("java.awt.headless="));
      System.setOut(old);
    }
  }

  /* 2 ─ run(..) when headless */
  @Test
  @DisplayName("02 run() headless prints warning, no UI launch")
  void run_headlessBranch() {
    try (MockedStatic<GraphicsEnvironment> ge = Mockito.mockStatic(GraphicsEnvironment.class);
        MockedStatic<javafx.application.Application> fx =
            Mockito.mockStatic(javafx.application.Application.class)) {

      ge.when(GraphicsEnvironment::isHeadless).thenReturn(true);

      ByteArrayOutputStream err = new ByteArrayOutputStream();
      PrintStream oldErr = System.err;
      System.setErr(new PrintStream(err));

      new com.xmen.Application().run(); // invoke CommandLineRunner

      fx.verifyNoInteractions(); // launch never called
      assertTrue(err.toString().contains("Cannot run GUI"));

      System.setErr(oldErr);
    }
  }

  /* 3 ─ run(..) when not headless */
  @Test
  @DisplayName("03 run() non-headless calls Application.launch")
  void run_guiBranch() {
    try (MockedStatic<GraphicsEnvironment> ge = Mockito.mockStatic(GraphicsEnvironment.class);
        MockedStatic<javafx.application.Application> fx =
            Mockito.mockStatic(javafx.application.Application.class)) {

      ge.when(GraphicsEnvironment::isHeadless).thenReturn(false);

      fx.when(() -> javafx.application.Application.launch(XMenInterface.class))
          .thenAnswer(inv -> null); // stub out real launch

      new com.xmen.Application().run();

      fx.verify(() -> javafx.application.Application.launch(XMenInterface.class));
    }
  }

  @Test
  @DisplayName("04 run() can disable JavaFX launch for service-only runtimes")
  void run_uiDisabledBranch() {
    System.setProperty("xmen.ui.enabled", "false");
    try (MockedStatic<GraphicsEnvironment> ge = Mockito.mockStatic(GraphicsEnvironment.class);
        MockedStatic<javafx.application.Application> fx =
            Mockito.mockStatic(javafx.application.Application.class)) {

      ge.when(GraphicsEnvironment::isHeadless).thenReturn(false);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintStream oldOut = System.out;
      System.setOut(new PrintStream(out));

      try {
        new com.xmen.Application().run();
      } finally {
        System.setOut(oldOut);
      }

      fx.verifyNoInteractions();
      assertTrue(out.toString().contains("JavaFX UI disabled"));
    }
  }
}
