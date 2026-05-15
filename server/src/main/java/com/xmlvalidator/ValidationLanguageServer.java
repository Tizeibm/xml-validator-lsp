package com.xmlvalidator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xmlvalidator.protocol.StartValidationParams;
import com.xmlvalidator.protocol.ValidationFinishedParams;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.File;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main Language Server implementation for the Mass XML Validator.
 *
 * <p>This server handles the custom LSP notification {@code custom/startValidation}
 * to trigger streaming XML validation against an XSD schema. It delegates the
 * actual validation work to a {@link ValidationWorker} running on a dedicated
 * worker thread, ensuring the LSP message loop remains responsive.
 *
 * <p>Standard LSP document services (didOpen, didChange, etc.) are intentionally
 * no-ops, as per the architecture blueprint's strict prohibition against opening
 * massive files in the editor.
 */
public class ValidationLanguageServer implements LanguageServer {

    private static final Logger LOG = Logger.getLogger(ValidationLanguageServer.class.getName());
    private static final Gson GSON = new Gson();

    /** The custom LSP client that can receive our custom notifications. */
    private ValidationLanguageClient client;

    /** Single-thread executor for running validation workers sequentially. */
    private final ExecutorService validationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xml-validation-worker");
        t.setDaemon(true);
        return t;
    });

    private final TextDocumentService textDocumentService = new ValidationTextDocumentService();
    private final WorkspaceService workspaceService = new ValidationWorkspaceService();

    /**
     * Called by the LSP4J launcher to inject the client proxy.
     *
     * @param client the LSP client proxy
     */
    public void connect(ValidationLanguageClient client) {
        this.client = client;
        LOG.info("Client connected to the validation language server.");
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        LOG.info("Initializing XML Validator LSP Server...");

        ServerCapabilities capabilities = new ServerCapabilities();

        // We intentionally set minimal capabilities since we don't use standard LSP features.
        // Text document sync is set to None — we never want VS Code to send us file contents.
        capabilities.setTextDocumentSync(TextDocumentSyncKind.None);

        InitializeResult result = new InitializeResult(capabilities);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        LOG.info("XML Validator LSP Server initialized and ready.");
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        LOG.info("Shutting down XML Validator LSP Server...");
        validationExecutor.shutdownNow();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        LOG.info("Exiting XML Validator LSP Server.");
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    // ──────────────────────────────────────────────────────────────────
    // Custom LSP Notification Handler
    // ──────────────────────────────────────────────────────────────────

    /**
     * Handles the {@code custom/startValidation} notification from the VS Code client.
     * Converts URIs to filesystem paths and launches the validation worker thread.
     *
     * @param params raw JSON parameters containing xmlUri and xsdUri
     */
    @JsonNotification("custom/startValidation")
    public void startValidation(Object params) {
        LOG.info("Received custom/startValidation notification.");

        try {
            // Parse the incoming params (LSP4J delivers them as a JsonObject)
            StartValidationParams validationParams;
            if (params instanceof JsonObject jsonObj) {
                validationParams = GSON.fromJson(jsonObj, StartValidationParams.class);
            } else {
                validationParams = GSON.fromJson(GSON.toJson(params), StartValidationParams.class);
            }

            LOG.info("Validation request: " + validationParams);

            // Convert file:// URIs to absolute filesystem paths
            String xmlPath = uriToPath(validationParams.getXmlUri());
            String xsdPath = uriToPath(validationParams.getXsdUri());

            LOG.info("XML path: " + xmlPath);
            LOG.info("XSD path: " + xsdPath);

            // Validate that files exist
            if (!new File(xmlPath).exists()) {
                LOG.severe("XML file does not exist: " + xmlPath);
                sendFinished(validationParams.getXmlUri(), 0, 0);
                return;
            }
            if (!new File(xsdPath).exists()) {
                LOG.severe("XSD file does not exist: " + xsdPath);
                sendFinished(validationParams.getXmlUri(), 0, 0);
                return;
            }

            // Create the batching error handler wired to the client proxy
            BatchingErrorHandler errorHandler = new BatchingErrorHandler(
                    batchParams -> {
                        if (client != null) {
                            client.validationBatch(batchParams);
                        }
                    }
            );

            // Record start time for metrics
            final long startTime = System.currentTimeMillis();
            final String xmlUri = validationParams.getXmlUri();

            // Create and submit the validation worker to the executor
            ValidationWorker worker = new ValidationWorker(xmlPath, xsdPath, errorHandler, () -> {
                long elapsed = System.currentTimeMillis() - startTime;
                int totalErrors = errorHandler.getTotalErrorCount();
                LOG.info("Validation finished. Errors: " + totalErrors + ", Time: " + elapsed + "ms");
                sendFinished(xmlUri, elapsed, totalErrors);
            });

            validationExecutor.submit(worker);
            LOG.info("Validation worker submitted to executor.");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start validation", e);
        }
    }

    /**
     * Sends the custom/validationFinished notification to the client.
     */
    private void sendFinished(String xmlUri, long elapsedMs, int totalErrors) {
        if (client != null) {
            client.validationFinished(new ValidationFinishedParams(elapsedMs, totalErrors, xmlUri));
        }
    }

    /**
     * Converts a file:// URI string to an absolute filesystem path.
     * Handles both URI-encoded paths and plain filesystem paths.
     *
     * @param uriString the URI or path string
     * @return the absolute filesystem path
     */
    private String uriToPath(String uriString) {
        if (uriString == null) {
            return "";
        }
        try {
            if (uriString.startsWith("file://")) {
                return new File(new URI(uriString)).getAbsolutePath();
            }
            // If it's already a plain path, return as-is
            return uriString;
        } catch (Exception e) {
            LOG.warning("Could not parse URI '" + uriString + "', using as raw path. Error: " + e.getMessage());
            return uriString;
        }
    }
}
