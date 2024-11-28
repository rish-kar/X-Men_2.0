    package com.sermas.x.men.utilities;

    import com.sermas.x.men.model.*;
    import lombok.extern.slf4j.Slf4j;
    import org.antlr.v4.runtime.ANTLRInputStream;
    import org.antlr.v4.runtime.CommonTokenStream;
    import org.antlr.v4.runtime.tree.ParseTree;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.web.multipart.MultipartFile;

    import java.io.File;
    import java.io.FileInputStream;
    import java.io.IOException;
    import java.io.InputStream;
    import java.util.ArrayList;

    @Slf4j
    @org.springframework.stereotype.Component
    public class ModelLoader {

        public ArrayList<Component> full_model = new ArrayList<>();
        public ArrayList<Function> functions = new ArrayList<>();
        public ArrayList<Builtins> builtins = new ArrayList<>();
        public ArrayList<Rule> theory = new ArrayList<>();
        public boolean modelWithTags;
        public File file2;

        @Autowired
        private FileHandler fileHandler;

        public void openFile(MultipartFile file) throws IOException, org.antlr.runtime.RecognitionException {
            // File selection and loading logic

            // Validate the file extension
            System.out.println("-----------------------\nStarting File Validation\n-----------------------");

            try {
                if (!isValidExtension(file)) {
                    throw new IllegalArgumentException("Invalid file extension");
                }
            } catch (IllegalArgumentException e) {
                log.error("Exception occurred: {}", e.getMessage(), e);
            }

            System.out.println("-----------------------\nFile Validation Ended\n-----------------------");

            // Load the file
            System.out.println("-----------------------\nLoading the model started\n-----------------------");

            full_model = loadSPTHY(file, full_model);
            for (Component element : full_model) {
                System.out.println("Component: " + element);
                System.out.println();
            }

            // Adding components to their respective lists
            full_model.forEach((element) -> {
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

            theory = fileHandler.arrangeTheory(theory);
            theory = fileHandler.arrangeLets(theory);
            theory = fileHandler.mergeTagsValues(theory);
            theory = fileHandler.spreadTagsie(theory);
            theory = fileHandler.letArrangement(theory);
            theory = fileHandler.arrangeValues(theory);
            theory = fileHandler.identifyRoles(theory);

            System.out.println("-----------------------\nLoading the model ended\n-----------------------");
            // Additional logic
        }

        /**
         * Loads the Tamarin-Prover specific SPTHY file.
         *
         * @param file The file to load.
         * @param save The list to save the components to.
         * @return The list of components.
         * @throws IOException If an I/O error occurs.
         */
        public ArrayList<Component> loadSPTHY(MultipartFile file, ArrayList<Component> save) throws IOException {
            log.debug("Starting loadSPTHY with file: {}", file.getOriginalFilename());

            try (InputStream inputStream = file.getInputStream()) {
                // Create a CharStream that reads from the input stream
                ANTLRInputStream input = new ANTLRInputStream(inputStream);
                log.debug("Created ANTLRInputStream");

                // Create a lexer that feeds off of input CharStream
                TamarinLexer lexer = new TamarinLexer(input);
                log.debug("Created TamarinLexer");

                // Create a buffer of tokens pulled from the lexer
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                log.debug("Created CommonTokenStream");

                // Create a parser that feeds off the tokens buffer
                TamarinParser parser = new TamarinParser(tokens);
                log.debug("Created TamarinParser");

                // Begin parsing at theory rule
                ParseTree tree = parser.theory();
                log.debug("Parsed theory rule");

                TamVisitor v = new TamVisitor();
                ArrayList<Rule> tmp = (ArrayList<Rule>) v.visit(tree);
                save.addAll(tmp);
                log.debug("Visited parse tree and added rules to save");

            } catch (IOException e) {
                log.error("IOException occurred while loading SPTHY file: {}", e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                log.error("Unexpected exception occurred while loading SPTHY file: {}", e.getMessage(), e);
                throw new RuntimeException("Error loading SPTHY file", e);
            }

            log.debug("loadSPTHY completed");
            return save;
        }

        /**
         * Validates the file extension.
         *
         * @param file The file to validate.
         * @return True if the file extension is valid, false otherwise.
         */
        public boolean isValidExtension(MultipartFile file) {
            String fileName = file.getName();
            return fileName.endsWith(".spthy");
        }
    }