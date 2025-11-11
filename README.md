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
- [Haskell Derivation Service Integration](#haskell-derivation-service-integration)
    - [Overview](#haskell-overview)
    - [Setup and Configuration](#haskell-setup-and-configuration)
    - [Usage](#haskell-usage)
    - [How It Works](#haskell-how-it-works)
    - [API Endpoints](#haskell-api-endpoints)

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

## Haskell Derivation Service Integration

### <a name="haskell-overview"></a>Overview

X-Men integrates with an external **Haskell derivation-tree microservice** to provide advanced derivation analysis for Forget mutations. This integration allows you to:

- **Analyze protocol derivability** using Haskell-based logic
- **Visualize derivation trees** for protocol message flows
- **Determine if targets are derivable** from the intruder's knowledge
- **Make informed mutation decisions** based on Haskell derivation results

The integration is **optional** and **header-driven** — you can enable it per request without affecting standard behavior.

---

### <a name="haskell-setup-and-configuration"></a>Setup and Configuration

#### 1. **Haskell Service Requirements**

The Haskell derivation service should:
- Run on **port 9091** (configurable)
- Expose a `POST /derive` endpoint
- Accept plain text input (converted protocol format)
- Return derivation tree analysis

#### 2. **Configuration**

In `application.yaml`:
```yaml
derivation:
  service:
    url: ${DERIVATION_SERVICE_URL:http://localhost:9091}
```

**Environment Variable Override:**
```bash
export DERIVATION_SERVICE_URL=http://custom-host:9091
```

#### 3. **Start the Haskell Service**

Ensure the Haskell derivation service is running before using the integration:
```bash
# Start your Haskell service on port 9091
./derivation-service
```

---

### <a name="haskell-usage"></a>Usage

#### **Activating Haskell Derivation**

Add the `Haskell-Activate: true` header to your Forget mutation requests:

**Using cURL:**
```bash
curl -X POST http://localhost:8081/api/forget/mutations \
  -H "Haskell-Activate: true" \
  -F "file=@Oyster.spthy" \
  -o mutations.zip
```

**Using Multi-Endpoint:**
```bash
curl -X POST http://localhost:8081/api/generateMutations \
  -H "Forget-Mutation: true" \
  -H "Haskell-Activate: true" \
  -F "file=@CoachService.spthy" \
  -o mutations.zip
```

**Using Postman:**
1. Select a Forget mutation request
2. Go to the **Headers** tab
3. Add: `Haskell-Activate` with value `true`
4. Send the request

#### **Without Haskell (Standard Behavior)**

Simply omit the `Haskell-Activate` header:
```bash
curl -X POST http://localhost:8081/api/forget/mutations \
  -F "file=@Oyster.spthy" \
  -o mutations.zip
```

The system will use the standard Java-based derivation logic.

---

### <a name="haskell-how-it-works"></a>How It Works

#### **Architecture**

```
1. Parse SPTHY File
   ↓
2. Convert Rules → Haskell Format
   ↓
3. Call Haskell Service (POST /derive)
   ↓
4. Receive Derivation Tree
   ↓
5. Print Tree to Console
   ↓
6. Parse Result → Determine Derivability
   ↓
7. Use Result for Forget Mutation Decisions
```

#### **Conversion Process**

The `HaskellFormatConverter` transforms your SPTHY rules into a structured format the Haskell service understands:

**Input (SPTHY):**
```tamarin
rule setup:
  [Fr(~kS)]
  --[OnlyOnce(), Roles($Client,$S,$D)]->
  [ State($S,'1',<~kS,$D>)
  , State($Client,'1',<$S>)
  ]

rule H_1:
  [ State($Client,'1',<$S>) ]
  -->
  [ SndS($Client,$S,journey) ]
```

**Converted Output (Haskell Format):**
```
PROTOCOL: CoachService

INITIAL_KNOWLEDGE:
  Client
  S
  D
  pub(a)
  pub(b)

MESSAGES:
  M1: from Client to S: journey
  M2: from S to Client: solution

GOAL: derive(shared_key)
```

#### **Derivation Logic**

1. **Haskell Enabled**: `HybridDerivationService` routes derivation calls to Haskell
2. **Haskell Service**: Analyzes protocol and returns derivation tree
3. **Derivability Check**: Parses Haskell response to determine if target is derivable
4. **Mutation Decision**: Forget mutation proceeds or skips based on derivability

#### **Console Output Example**

```
================================================================================
HASKELL DERIVATION TREE FOR: CoachService
Target: (ticket, encTicket)
================================================================================

========================================
       DERIVATION TREE ANALYSIS        
========================================

Initial intruder knowledge 
l_0->Client
l_1->S
l_2->D
l_3->Pub(privk_a)

Receiving first message from Client and analyzing
l_4-><date, dtime, from, to>

Can the intruder derive session key?
[Aenc(l_3, l_4)]

========================================

================================================================================

Haskell derivation result: target is DERIVABLE
Target IS derivable from knowledge. Skipping mutation.
```

---

### <a name="haskell-api-endpoints"></a>API Endpoints

#### **1. Forget Mutations with Haskell**

**Endpoint:** `POST /api/forget/mutations`

**Headers:**
- `Haskell-Activate: true` (optional)

**Request:**
```bash
curl -X POST http://localhost:8081/api/forget/mutations \
  -H "Haskell-Activate: true" \
  -F "file=@protocol.spthy"
```

**Response:**
- `200 OK`: ZIP file with generated mutations
- `400 Bad Request`: Invalid file format
- `503 Service Unavailable`: Haskell service not reachable

#### **2. Multi-Endpoint with Haskell**

**Endpoint:** `POST /api/generateMutations`

**Headers:**
- `Forget-Mutation: true`
- `Haskell-Activate: true`

**Request:**
```bash
curl -X POST http://localhost:8081/api/generateMutations \
  -H "Forget-Mutation: true" \
  -H "Haskell-Activate: true" \
  -F "file=@protocol.spthy"
```

#### **3. Derivation Service Health Check**

**Endpoint:** `GET /api/derive/health`

**Request:**
```bash
curl http://localhost:8081/api/derive/health
```

**Response:**
- `200 OK`: "Derivation service is available"
- `503 Service Unavailable`: "Derivation service is unavailable"

---

### **Key Components**

#### **HaskellFormatConverter**
Converts parsed SPTHY rules into Haskell-compatible format by:
- Extracting initial knowledge from setup rules
- Mapping protocol messages from Snd/Rcv facts
- Identifying derivation goals

#### **DerivationTreeService**
- Calls external Haskell service via WebClient
- Converts rules using HaskellFormatConverter
- Formats and returns derivation tree analysis

#### **HybridDerivationService**
Smart router that chooses between:
- **Haskell derivation** (when `Haskell-Activate: true`)
- **Java derivation** (standard behavior)

Uses `ThreadLocal` for thread-safe per-request state management.

---

### **Graceful Fallback**

If the Haskell service is unavailable:
- ⚠️ Warning logged: "Haskell service unavailable"
- ✅ **Automatic fallback** to Java derivation
- ✅ Mutations still generated successfully
- ✅ No errors or failures

---

### **Logging**

**When Haskell is Active:**
```
INFO  Haskell derivation ENABLED for theory: CoachService
INFO  Forget mutation will use Haskell service for derivability checks
INFO  Using HASKELL derivation service for target: (p2, (~nh, ~nb))
INFO  Haskell derivation result: target is DERIVABLE
INFO  Haskell derivation DISABLED after mutation processing
```

**When Haskell is Unavailable:**
```
WARN  Haskell service requested but unavailable, using Java derivation
```

---

### **Troubleshooting**

#### **Issue: Service Unavailable (503)**
**Solution:**
- Ensure Haskell service is running: `curl http://localhost:9091/health`
- Check firewall settings
- Verify port 9091 is not in use

#### **Issue: Connection Timeout**
**Solution:**
- Increase timeout in `DerivationTreeService` configuration
- Check network connectivity
- Verify Haskell service performance

#### **Issue: Java Derivation Still Used**
**Solution:**
- Verify `Haskell-Activate: true` header is set
- Check logs for "Haskell derivation ENABLED"
- Ensure both `Forget-Mutation` and `Haskell-Activate` are true for multi-endpoint

---

### **Benefits**

✅ **Advanced Analysis**: Leverage Haskell's powerful derivation logic  
✅ **Visualization**: See detailed derivation trees in console  
✅ **Flexibility**: Enable/disable per request via header  
✅ **Backward Compatible**: Works without affecting standard behavior  
✅ **Safe Fallback**: Automatic Java derivation if Haskell unavailable  
✅ **Thread-Safe**: Concurrent requests handled independently

---

For more detailed technical documentation, see:
- `HASKELL_COMPLETE_SOLUTION.md`
- `HASKELL_ACTIVATION_GUIDE.md`
- `HASKELL_CONVERTER_IMPLEMENTATION.md`

---

