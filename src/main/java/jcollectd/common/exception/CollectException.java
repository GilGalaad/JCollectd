package jcollectd.common.exception;

public class CollectException extends RuntimeException {

    public CollectException() {
    }

    public CollectException(String message) {
        super(message);
    }

    public CollectException(String message, Throwable cause) {
        super(message, cause);
    }

}
