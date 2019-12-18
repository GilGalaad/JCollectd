package common.exception;

public class ExecutionException extends Exception {

    private final boolean wrapperOnly;

    public ExecutionException(String message) {
        super(message);
        this.wrapperOnly = false;
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.wrapperOnly = false;
    }

    public ExecutionException(Throwable cause) {
        super("wrapped " + ExceptionUtils.getCanonicalForm(cause), cause);
        this.wrapperOnly = true;
    }

    public boolean isWrapperOnly() {
        return wrapperOnly;
    }

    @Override
    public String toString() {
        if (wrapperOnly) {
            Throwable wrappedCause = this.getCause();
            Throwable rootCause = ExceptionUtils.getRootCause(wrappedCause);
            if (wrappedCause == rootCause) {
                return ExceptionUtils.getCanonicalForm(wrappedCause);
            } else {
                return ExceptionUtils.getCanonicalFormWithRootCause(wrappedCause, rootCause);
            }
        }
        return ExceptionUtils.getCanonicalFormWithRootCause(this);
    }

}
