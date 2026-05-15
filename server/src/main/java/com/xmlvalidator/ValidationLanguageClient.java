package com.xmlvalidator;

import com.xmlvalidator.protocol.ValidationBatchParams;
import com.xmlvalidator.protocol.ValidationFinishedParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

/**
 * Interface representing the custom LSP client capabilities.
 * Extends the standard LanguageClient with custom notification methods
 * that the server can call to send validation results back to VS Code.
 *
 * <p>These methods correspond to the custom LSP notifications defined
 * in the protocol:
 * <ul>
 *   <li>{@code custom/validationBatch} — sends a batch of validation errors</li>
 *   <li>{@code custom/validationFinished} — signals that validation is complete</li>
 * </ul>
 */
public interface ValidationLanguageClient extends org.eclipse.lsp4j.services.LanguageClient {

    /**
     * Sends a batch of validation errors to the VS Code client.
     * Called whenever the error buffer reaches the batch size threshold (1000 errors)
     * or when the validation finishes and remaining errors need to be flushed.
     *
     * @param params the batch containing a list of validation errors
     */
    @JsonNotification("custom/validationBatch")
    void validationBatch(ValidationBatchParams params);

    /**
     * Signals to the VS Code client that the validation process has completed.
     * Includes final metrics such as elapsed time and total error count.
     *
     * @param params the finished notification containing summary metrics
     */
    @JsonNotification("custom/validationFinished")
    void validationFinished(ValidationFinishedParams params);
}
