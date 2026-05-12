package io.github.gear4jtest.core.exception;

public final class StationExecutionException extends Gear4JException {

    private static final long serialVersionUID = 1L;

    public StationExecutionException(Exception cause) {
        super(cause);
    }

    public static StationExecutionException wrap(Exception cause) {
        if (cause instanceof StationExecutionException alreadyWrapped) {
            return alreadyWrapped;
        }
        return new StationExecutionException(cause);
    }

    public static Exception unwrap(Throwable throwable) {
        if (throwable instanceof StationExecutionException stationExecutionException) {
            return stationExecutionException.getOriginalException();
        }
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

    public Exception getOriginalException() {
        Throwable cause = getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(cause);
    }
}
