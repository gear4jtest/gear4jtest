package io.github.gear4jtest.core.internal;

import java.util.List;

public class AssemblyLineException extends Exception {

	private static final long serialVersionUID = 1855070000094708059L;

	private final List<Throwable> throwables;

	public AssemblyLineException(List<Throwable> throwables) {
		this.throwables = throwables;
	}

	public List<Throwable> getThrowables() {
		return throwables;
	}

}
