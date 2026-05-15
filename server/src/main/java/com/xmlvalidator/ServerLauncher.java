package com.xmlvalidator;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the XML Validator LSP Server.
 *
 * <p>This launcher sets up the LSP4J JSON-RPC communication channel over
 * stdin/stdout, which is the standard transport for VS Code language servers.
 * The server listens for custom notifications from the VS Code extension
 * and dispatches validation work accordingly.
 *
 * <p>Usage: {@code java -jar xml-validator-server.jar}
 */
public class ServerLauncher {

    private static final Logger LOG = Logger.getLogger(ServerLauncher.class.getName());

    public static void main(String[] args) {
        LOG.info("Starting XML Validator LSP Server...");

        try {
            // Use stdin/stdout for LSP JSON-RPC communication
            InputStream in = System.in;
            OutputStream out = System.out;

            // Redirect System.out to System.err to prevent log messages
            // from corrupting the JSON-RPC channel on stdout
            System.setOut(System.err);

            // Create the server instance
            ValidationLanguageServer server = new ValidationLanguageServer();

            // Build the LSP4J launcher with our custom client interface
            // This wires up JSON-RPC deserialization for incoming messages
            // and creates a proxy for sending notifications to the client
            Launcher<ValidationLanguageClient> launcher = new Launcher.Builder<ValidationLanguageClient>()
                    .setLocalService(server)
                    .setRemoteInterface(ValidationLanguageClient.class)
                    .setInput(in)
                    .setOutput(out)
                    .create();

            // Get the client proxy and connect it to the server
            ValidationLanguageClient client = launcher.getRemoteProxy();
            server.connect(client);

            // Start listening — this blocks until the connection is closed
            LOG.info("LSP Server listening on stdin/stdout...");
            launcher.startListening().get();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "LSP Server crashed", e);
            System.exit(1);
        }
    }
}
