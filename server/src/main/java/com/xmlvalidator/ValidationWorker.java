package com.xmlvalidator;

import com.ctc.wstx.stax.WstxInputFactory;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The core validation engine. Runs in a dedicated worker thread to avoid
 * blocking the LSP message loop. Implements the streaming single-pass
 * validation strategy outlined in the architecture blueprint.
 *
 * <p>Key optimizations:
 * <ul>
 *   <li>8 MB buffered input stream to minimize disk I/O syscalls</li>
 *   <li>Woodstox StAX parser with DTD/external entity resolution disabled (XXE protection)</li>
 *   <li>Coalescing disabled for maximum throughput</li>
 *   <li>Cursor API (XMLStreamReader) — zero-allocation event traversal</li>
 *   <li>StAXSource bridge to javax.xml.validation.Validator — no DOM tree</li>
 * </ul>
 */
public class ValidationWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger(ValidationWorker.class.getName());

    /** 8 MB buffer size for the BufferedInputStream wrapping the XML file. */
    private static final int BUFFER_SIZE = 8 * 1024 * 1024;

    private final String xmlPath;
    private final String xsdPath;
    private final BatchingErrorHandler errorHandler;
    private final Runnable onFinished;
    private final long startTimeMs;

    /**
     * Creates a new ValidationWorker.
     *
     * @param xmlPath      absolute filesystem path to the XML file
     * @param xsdPath      absolute filesystem path to the XSD schema file
     * @param errorHandler the batching error handler for capturing validation errors
     * @param onFinished   callback invoked when validation completes (success or failure)
     */
    public ValidationWorker(String xmlPath, String xsdPath,
                            BatchingErrorHandler errorHandler, Runnable onFinished) {
        this.xmlPath = xmlPath;
        this.xsdPath = xsdPath;
        this.errorHandler = errorHandler;
        this.onFinished = onFinished;
        this.startTimeMs = System.currentTimeMillis();
    }

    @Override
    public void run() {
        XMLStreamReader streamReader = null;
        InputStream xmlInputStream = null;

        try {
            // ──────────────────────────────────────────────────
            // 1. Compile the XSD schema (done once, kept in memory)
            // ──────────────────────────────────────────────────
            LOG.info("Compiling XSD schema: " + xsdPath);
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // Security: prevent the schema factory from loading external resources
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

            Schema schema = schemaFactory.newSchema(new File(xsdPath));

            // ──────────────────────────────────────────────────
            // 2. Configure the Woodstox StAX parser for maximum
            //    throughput and minimum memory usage
            // ──────────────────────────────────────────────────
            LOG.info("Initializing Woodstox parser with optimized configuration...");
            WstxInputFactory inputFactory = new WstxInputFactory();

            // Security: Absolute DTD and external entity resolution disabled (XXE protection)
            inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);

            // Performance: Disable coalescing (don't merge adjacent text nodes)
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, false);

            // Performance: Disable namespace interning to reduce memory pressure
            inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);

            // Woodstox-specific: increase internal input buffer size (default is ~4KB -> 512KB)
            inputFactory.setProperty("com.ctc.wstx.inputBufferLength", 512 * 1024);

            // ──────────────────────────────────────────────────
            // 3. Open the XML file with a massive buffered stream
            // ──────────────────────────────────────────────────
            LOG.info("Opening XML file with " + (BUFFER_SIZE / 1024 / 1024) + " MB buffer: " + xmlPath);
            xmlInputStream = new BufferedInputStream(new FileInputStream(xmlPath), BUFFER_SIZE);

            // Create the StAX cursor reader (zero-allocation pointer)
            streamReader = inputFactory.createXMLStreamReader(xmlInputStream, "UTF-8");

            // ──────────────────────────────────────────────────
            // 4. Create the Validator and attach our batching error handler
            // ──────────────────────────────────────────────────
            Validator validator = schema.newValidator();

            // Security: prevent the validator from loading external resources
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

            // Attach our custom error handler that batches errors
            validator.setErrorHandler(errorHandler);

            // ──────────────────────────────────────────────────
            // 5. Validate! The StAXSource bridges the cursor to the validator.
            //    The validator reads the XML stream event-by-event and checks
            //    each event against the compiled XSD schema in real-time.
            //    No DOM tree is ever built.
            // ──────────────────────────────────────────────────
            LOG.info("Starting streaming validation...");
            StAXSource staxSource = new StAXSource(streamReader);
            validator.validate(staxSource);

            LOG.info("Streaming validation completed successfully.");

        } catch (Exception e) {
            // Catch any unexpected exceptions (parser errors, I/O errors, etc.)
            // These are NOT validation errors — they are infrastructure failures.
            LOG.log(Level.SEVERE, "Validation process encountered an unexpected error", e);
        } finally {
            // ──────────────────────────────────────────────────
            // 6. Cleanup: close the StAX reader and input stream
            // ──────────────────────────────────────────────────
            if (streamReader != null) {
                try {
                    streamReader.close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error closing XMLStreamReader", e);
                }
            }
            if (xmlInputStream != null) {
                try {
                    xmlInputStream.close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error closing XML input stream", e);
                }
            }

            // Flush any remaining errors in the buffer
            errorHandler.flush();

            // Signal completion
            onFinished.run();
        }
    }

    /**
     * Returns the elapsed time in milliseconds since this worker was created.
     *
     * @return elapsed time in ms
     */
    public long getElapsedTimeMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
}
