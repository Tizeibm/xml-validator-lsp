package com.xmlvalidator.protocol;

/**
 * Parameters sent by the client via the custom/startValidation notification.
 * Contains the file URIs for the XML document and the XSD schema.
 */
public class StartValidationParams {

    private String xmlUri;
    private String xsdUri;

    public StartValidationParams() {
    }

    public StartValidationParams(String xmlUri, String xsdUri) {
        this.xmlUri = xmlUri;
        this.xsdUri = xsdUri;
    }

    public String getXmlUri() {
        return xmlUri;
    }

    public void setXmlUri(String xmlUri) {
        this.xmlUri = xmlUri;
    }

    public String getXsdUri() {
        return xsdUri;
    }

    public void setXsdUri(String xsdUri) {
        this.xsdUri = xsdUri;
    }

    @Override
    public String toString() {
        return "StartValidationParams{xmlUri='" + xmlUri + "', xsdUri='" + xsdUri + "'}";
    }
}
