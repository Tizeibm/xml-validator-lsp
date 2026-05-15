package com.xmlvalidator;

import com.xmlvalidator.protocol.ValidationBatchParams;
import com.xmlvalidator.protocol.ValidationError;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Custom SAX ErrorHandler that accumulates validation errors and sends them
 * in batches to the LSP client via a callback. This prevents flooding the
 * LSP channel with individual error notifications.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Errors and fatal errors are captured; the XML stream is NOT stopped.</li>
 *   <li>Warnings are also captured.</li>
 *   <li>Line numbers are converted from XML 1-based to LSP 0-based.</li>
 *   <li>When the batch reaches BATCH_SIZE, the callback is invoked and the buffer is cleared.</li>
 * </ul>
 */
public class BatchingErrorHandler implements ErrorHandler {

    /** Number of errors to accumulate before sending a batch to the client. */
    private static final int BATCH_SIZE = 1000;

    /** Internal buffer of accumulated errors. */
    private final List<ValidationError> errorBuffer = new ArrayList<>();

    /** Running total of all errors encountered during the validation session. */
    private int totalErrorCount = 0;

    /** Callback to invoke when a batch is ready to be sent. */
    private final Consumer<ValidationBatchParams> batchSender;

    /**
     * Creates a new BatchingErrorHandler.
     *
     * @param batchSender callback that sends a ValidationBatchParams notification
     *                    to the LSP client whenever a batch is full.
     */
    public BatchingErrorHandler(Consumer<ValidationBatchParams> batchSender) {
        this.batchSender = batchSender;
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        addError(exception, "WARNING");
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        addError(exception, "ERROR");
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        // Even on fatal errors, we capture and continue if possible.
        // The parser may stop regardless, but we don't throw.
        addError(exception, "FATAL");
    }

    /**
     * Captures a validation error, converts coordinates, and flushes
     * the batch if the buffer has reached its capacity.
     *
     * @param ex       the SAX parse exception containing error details
     * @param severity the severity level string (WARNING, ERROR, FATAL)
     */
    private void addError(SAXParseException ex, String severity) {
        // Convert XML 1-based line to LSP 0-based line.
        // Column remains as-is (both are typically 1-based in display,
        // but we store the raw value for the client to format).
        int lspLine = Math.max(0, ex.getLineNumber() - 1);
        int column = Math.max(0, ex.getColumnNumber());

        ValidationError error = new ValidationError(
                lspLine,
                column,
                severity,
                ex.getMessage()
        );

        errorBuffer.add(error);
        totalErrorCount++;

        // Flush when batch is full
        if (errorBuffer.size() >= BATCH_SIZE) {
            flush();
        }
    }

    /**
     * Sends any remaining errors in the buffer to the client.
     * Must be called after the validation loop completes to ensure
     * the last partial batch is not lost.
     */
    public void flush() {
        if (!errorBuffer.isEmpty()) {
            // Create a copy for the notification, then clear the internal buffer
            List<ValidationError> batch = new ArrayList<>(errorBuffer);
            errorBuffer.clear();
            batchSender.accept(new ValidationBatchParams(batch));
        }
    }

    /**
     * Returns the total number of errors encountered across all batches.
     *
     * @return total error count
     */
    public int getTotalErrorCount() {
        return totalErrorCount;
    }
}
