package com.xmlvalidator.protocol;

import java.util.List;

/**
 * Parameters sent by the server via the custom/validationBatch notification.
 * Contains a batch of validation errors to be displayed in the client's OutputChannel.
 */
public class ValidationBatchParams {

    private List<ValidationError> errors;

    public ValidationBatchParams() {
    }

    public ValidationBatchParams(List<ValidationError> errors) {
        this.errors = errors;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }
}
