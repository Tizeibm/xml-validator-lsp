package com.xmlvalidator.protocol;

/**
 * Represents a single validation error with its location and message.
 * Line numbers are 0-based (LSP convention), converted from XML's 1-based.
 */
public class ValidationError {

    private int line;
    private int column;
    private String severity;
    private String message;

    public ValidationError() {
    }

    public ValidationError(int line, int column, String severity, String message) {
        this.line = line;
        this.column = column;
        this.severity = severity;
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "[Ligne " + (line + 1) + ", Col " + column + "] " + severity + ": " + message;
    }
}
