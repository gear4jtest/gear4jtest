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

    /**
     * Removes one station-boundary wrapper from a recoverable exception.
     *
     * @param exception recoverable exception caught by the station boundary
     * @return the original exception when wrapped, otherwise {@code exception}
     */
    public static Exception unwrap(Exception exception) {
        if (exception instanceof StationExecutionException stationExecutionException) {
            return stationExecutionException.getOriginalException();
        }
        return exception;
    }

    public Exception getOriginalException() {
        Throwable cause = getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(cause);
    }
}
