    package com.sermas.x.men.utilities;

    import com.sermas.x.men.model.*;
    import lombok.extern.slf4j.Slf4j;
    import org.antlr.v4.runtime.ANTLRInputStream;
    import org.antlr.v4.runtime.CommonTokenStream;
    import org.antlr.v4.runtime.tree.ParseTree;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.web.multipart.MultipartFile;

    import java.io.IOException;
    import java.io.InputStream;
    import java.util.ArrayList;
    import java.util.List;

    @Slf4j
    @org.springframework.stereotype.Component
    public class ModelLoader {

        ArrayList<Rule> theory = new ArrayList<>();


        @Autowired
        private FileHandler fileHandler;


        /**
         * Opens the file and loads the model.
         *
         * @param file The file to open.
         * @throws IOException                            If an I/O error occurs.
         * @throws org.antlr.runtime.RecognitionException If an error occurs during recognition.
         */
        public ParametersBundle openFile(MultipartFile file, ParametersBundle parametersBundle) throws IOException, org.antlr.runtime.RecognitionException {
            // File selection and loading logic
            // List of components in the model
            ArrayList<Function> functions = new ArrayList<>();
            ArrayList<Builtins> builtins = new ArrayList<>();
            ArrayList<Component> full_model = new ArrayList<>();

            // Validate the file extension
            log.debug("-----------------------\nStarting File Validation\n-----------------------");

            if (!isValidExtension(file)) {
                log.error("Invalid file extension");
                throw new IllegalArgumentException("Invalid file extension");
            }

            log.debug("-----------------------\nFile Validation Ended\n-----------------------");

            // Load the file
            log.debug("-----------------------\nLoading the model started\n-----------------------");

            ArrayList<Component> loadedModel = loadSPTHY(file, full_model);
            loadedModel.forEach(element -> log.debug("Component: {}", element));

            theory = new ArrayList<>();

            // Adding components to their respective lists
            loadedModel.forEach((element) -> {
                if (element instanceof Builtins) {
                    builtins.add((Builtins) element);
                    log.debug("Added Builtins component: {}", element);
                } else if (element instanceof Rule) {
                    theory.add((Rule) element);
                    log.debug("Added Rule component: {}", element);
                } else if (element instanceof Function) {
                    functions.add((Function) element);
                    log.debug("Added Function component: {}", element);
                } else {
                    log.warn("Unknown component type: {}", element);
                }
            });

            parametersBundle.setTheory(theory);
            // Additional logic to arrange the theory in a specific order
            parametersBundle = fileHandler.arrangeTheory(parametersBundle);
            parametersBundle = fileHandler.arrangeLets(parametersBundle);
            parametersBundle = fileHandler.mergeTagsValues(parametersBundle);
            parametersBundle = fileHandler.spreadTagsie(parametersBundle);
            parametersBundle = fileHandler.letArrangement(parametersBundle);
            parametersBundle = fileHandler.arrangeValues(parametersBundle);
            parametersBundle = fileHandler.identifyRoles(parametersBundle);

            log.debug("-----------------------\nLoading the model ended\n-----------------------");

            parametersBundle.setCollections(new ArrayList<>(List.of(parametersBundle.getTheory())));
            parametersBundle.setFunctions(functions);
            parametersBundle.setBuiltins(builtins);

            return parametersBundle;
        }


        /**
         * Loads the Tamarin-Prover specific SPTHY file.
         *
         * @param spthyFile The file to load.
         * @param componentList The list to save the components to.
         * @return The list of components.
         * @throws IOException If an I/O error occurs.
         */
        public ArrayList<Component> loadSPTHY(MultipartFile spthyFile, ArrayList<Component> componentList) throws IOException {
            log.debug("Starting loadSPTHY with file: {}", spthyFile.getOriginalFilename());

            try (InputStream inputStream = spthyFile.getInputStream()) {
                // Create a CharStream that reads from the input stream
                ANTLRInputStream antlrInputStream = new ANTLRInputStream(inputStream);
                log.debug("Created ANTLRInputStream");

                // Create a lexer that feeds off of input CharStream
                TamarinLexer tamarinLexer = new TamarinLexer(antlrInputStream);
                log.debug("Created TamarinLexer");

                // Create a buffer of tokens pulled from the lexer
                CommonTokenStream tokenStream = new CommonTokenStream(tamarinLexer);
                log.debug("Created CommonTokenStream");

                // Create a parser that feeds off the tokens buffer
                TamarinParser tamarinParser = new TamarinParser(tokenStream);
                log.debug("Created TamarinParser");

                // Begin parsing at theory rule
                ParseTree parseTree = tamarinParser.theory();
                log.debug("Parsed theory rule");

                TamVisitor tamVisitor = new TamVisitor();
                ArrayList<Rule> ruleList = (ArrayList<Rule>) tamVisitor.visit(parseTree);
                componentList.addAll(ruleList);
                log.debug("Visited parse tree and added rules to componentList");

            } catch (IOException e) {
                log.error("IOException occurred while loading SPTHY file: {}", e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                log.error("Unexpected exception occurred while loading SPTHY file: {}", e.getMessage(), e);
                throw new RuntimeException("Error loading SPTHY file", e);
            }

            log.debug("loadSPTHY completed");
            return componentList;
        }


        /**
         * Validates the file extension.
         *
         * @param file The file to validate.
         * @return True if the file extension is valid, false otherwise.
         */
        public boolean isValidExtension(MultipartFile file) {
            String fileName = file.getOriginalFilename();
            return fileName.endsWith(".spthy");
        }
    }