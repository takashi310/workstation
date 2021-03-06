package org.janelia.workstation.integration.util;

public class UnprovidedServiceException extends RuntimeException {

    public UnprovidedServiceException() {
        super();
    }

    public UnprovidedServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnprovidedServiceException(String message) {
        super(message);
    }

    public UnprovidedServiceException(Throwable cause) {
        super(cause);
    }
}
