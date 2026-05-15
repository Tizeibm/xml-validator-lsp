package com.xmlvalidator;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.concurrent.CompletableFuture;

/**
 * No-op TextDocumentService implementation.
 *
 * <p>Per the architecture blueprint, the standard LSP document lifecycle
 * (didOpen, didChange, publishDiagnostics) is completely banned for this
 * extension. A 100 GB file must NEVER be opened in the editor.
 * This service exists only to satisfy the LSP4J contract.
 */
public class ValidationTextDocumentService implements TextDocumentService {

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        // Intentionally empty — files are never opened in the editor
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // Intentionally empty
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // Intentionally empty
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Intentionally empty
    }
}
