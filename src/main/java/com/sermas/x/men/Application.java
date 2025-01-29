package com.sermas.x.men;

import com.sermas.x.men.utilities.UtilityFunctions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Main class for the application.
 */
@SpringBootApplication(scanBasePackages = "com.sermas.x.men")
public class Application {

	/**
	 * Main method.
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		ApplicationContext context = SpringApplication.run(Application.class, args);

		// Check if UtilityFunctions bean exists
		if (context.getBean(UtilityFunctions.class) == null) {
			System.err.println("UtilityFunctions bean is not created!");
		} else {
			System.out.println("UtilityFunctions bean created successfully!");
		}
	}

}
