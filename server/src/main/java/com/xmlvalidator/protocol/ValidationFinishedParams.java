package com.xmlvalidator.protocol;

/**
 * Parameters sent by the server via the custom/validationFinished notification.
 * Contains final metrics about the completed validation run.
 */
public class ValidationFinishedParams {

    private long elapsedTimeMs;
    private int totalErrors;
    private String xmlUri;

    public ValidationFinishedParams() {
    }

    public ValidationFinishedParams(long elapsedTimeMs, int totalErrors, String xmlUri) {
        this.elapsedTimeMs = elapsedTimeMs;
        this.totalErrors = totalErrors;
        this.xmlUri = xmlUri;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public void setElapsedTimeMs(long elapsedTimeMs) {
        this.elapsedTimeMs = elapsedTimeMs;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(int totalErrors) {
        this.totalErrors = totalErrors;
    }

    public String getXmlUri() {
        return xmlUri;
    }

    public void setXmlUri(String xmlUri) {
        this.xmlUri = xmlUri;
    }
}
