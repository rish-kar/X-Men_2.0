# X-Men

## X-Men: A Mutation-Based Approach for the Formal Analysis of Security Ceremonies

---

## Table of Contents

- [About The Project](#about-the-project)
- [Built With](#built-with)
- [Getting Started](#getting-started)
    - [Installing Java and Maven](#installing-java-and-maven)
    - [Setting Up Path Variables](#setting-up-path-variables)
    - [Installing Git](#installing-git)
    - [Cloning the Project](#cloning-the-project)
    - [Installing IntelliJ IDEA](#installing-intellij-idea)
    - [Building and Running the Project](#building-and-running-the-project)
    - [Setting Up Postman](#setting-up-postman)

---

## About The Project

Security ceremonies often expand protocols to include user actions and behaviors, encompassing all human-induced vulnerabilities in systems (e.g., payment, voting, or critical infrastructure systems). X-Men automates analyzing and mutating security ceremonies by focusing on:

- Modeling human behaviors and errors during ceremonies.
- Mutating the ceremony to test vulnerabilities and implementation flaws.

X-Men builds upon formal modeling techniques to uncover vulnerabilities in real-world scenarios. Three case studies have been analyzed using this tool, revealing critical issues.

---

## Built With

This project uses the following technologies and frameworks:

- [Java 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- [Maven](https://maven.apache.org/)
- [Spring Boot](https://spring.io/projects/spring-boot)

---

## Getting Started

Follow these steps to set up the project locally and start working with X-Men.

### Installing Java and Maven

1. **Java 21**:
    - Download Java 21 from [here](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html).
    - Follow the installation instructions for your operating system.

2. **Maven**:
    - Download Maven from [here](https://maven.apache.org/download.cgi).
    - Follow the installation instructions.

---

### Setting Up Path Variables

1. **Windows**:
    - Right-click on `This PC` or `My Computer`, select `Properties` -> `Advanced system settings` -> `Environment Variables`.
    - Under `System Variables`, click `New` and add:
        - `JAVA_HOME`: Path to your Java installation (e.g., `C:\Program Files\Java\jdk-21`).
        - `MAVEN_HOME`: Path to your Maven installation (e.g., `C:\Program Files\Maven`).
    - Add `%JAVA_HOME%\bin` and `%MAVEN_HOME%\bin` to the `Path` variable.

2. **macOS**:
    - Open the terminal and edit your shell configuration file (`~/.zshrc` or `~/.bash_profile`):
      ```bash
      export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
      export MAVEN_HOME=/opt/apache-maven
      export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH
      ```
    - Save and reload the file:
      ```bash
      source ~/.zshrc
      ```

#### Verify Installation

- **Java**:
  ```bash
  java -version
  ```
- **Maven**:
  ```bash
  mvn -version
  ```

---

### Installing Git

#### Windows

1. Download Git from [git-scm.com](https://git-scm.com/).
2. Run the installer and follow the setup wizard.
3. During installation, choose `Git Bash` as the default terminal emulator.
4. Verify the installation:
   ```bash
   git --version
   ```

#### macOS

1. Open the terminal and install Git using Homebrew:
   ```bash
   brew install git
   ```
2. Verify the installation:
   ```bash
   git --version
   ```

---

### Cloning the Project

1. Open a terminal.
2. Clone the repository:

   ```bash
   git clone https://github.kcl.ac.uk/SERMAS/X-Men_3.0.git
   cd x-men
   ```

---

### Installing IntelliJ IDEA

1. **Download IntelliJ IDEA Community Edition**
    - Download from [IntelliJ IDEA](https://www.jetbrains.com/idea/download/).
    - Install it by following the instructions for your operating system.

2. **Open the Project**
    - Launch IntelliJ IDEA and select `Open`.
    - Navigate to the cloned `x-men` repository folder and open it.

3. **Set Up Maven**
    - IntelliJ will automatically detect the `pom.xml` file and import the Maven project.
    - If not, right-click on the `pom.xml` file and select `Add as Maven Project`.

---

### Building and Running the Project

1. **Build the Project**
    - Open the internal terminal in IntelliJ IDEA (`View -> Tool Windows -> Terminal`).
    - Run the build command:
      ```bash
      mvn clean install
      ```

2. **Run the Application**
    - In IntelliJ, go to `Run -> Edit Configurations`.
    - Click `+` and select `Spring Boot` -> `Application`.
    - Choose the main class of the application and save the configuration.
    - Click `Run` to start the application.

3. **Access the Application**
    - Open your browser and navigate to `http://localhost:8081`.

---

### Setting Up Postman

1. **Download Postman**
    - Download Postman from [here](https://www.postman.com/downloads/).

2. **Import the Collection**
    - In Postman, click `Import`.
    - Navigate to the `src/main/resources` folder of the project.
    - Select the collection file named `X-Men.postman_collection.json.`.
    - Postman will load the requests into a collection for testing.
    - Click on the folder named 'Mutations (Generate Mutations)'

3. **Make a Request**
    - Ensure the application is running in IntelliJ IDEA.
    - Use Postman to send a request from the imported collection to test the system functionality.

---

