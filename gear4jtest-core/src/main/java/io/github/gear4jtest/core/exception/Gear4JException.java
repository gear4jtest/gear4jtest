package io.github.gear4jtest.core.exception;

public class Gear4JException extends RuntimeException {

	private static final long serialVersionUID = -4881192097085952188L;

	public Gear4JException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public Gear4JException(String message, Throwable cause) {
		super(message, cause);
	}

	public Gear4JException(String message) {
		super(message);
	}

	public Gear4JException(Throwable cause) {
		super(cause);
	}
	
}
