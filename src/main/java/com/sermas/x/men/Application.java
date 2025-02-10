package com.sermas.x.men;

import com.sermas.x.men.user_interface.XMenInterface;
import com.sermas.x.men.utilities.UtilityFunctions;
import javafx.application.Platform;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import javax.swing.*;

@SpringBootApplication(scanBasePackages = "com.sermas.x.men")
public class Application implements CommandLineRunner {

	/**
	 * Main method.
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		System.out.println("java.awt.headless=" + System.getProperty("java.awt.headless"));
		ApplicationContext context = SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(String... args) {
		System.out.println("Is Headless: " + java.awt.GraphicsEnvironment.isHeadless());

		if (!java.awt.GraphicsEnvironment.isHeadless()) {
			// Just launch JavaFX directly.
			// NOTE: This call is blocking until the JavaFX app is closed.
			XMenInterface.launch(XMenInterface.class);
		} else {
			System.err.println("Cannot run GUI in a headless environment");
		}
	}

}
