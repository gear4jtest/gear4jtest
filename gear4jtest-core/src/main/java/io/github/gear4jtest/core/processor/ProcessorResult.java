package io.github.gear4jtest.core.processor;

public class ProcessorResult {

	private Class<? extends BaseProcessor<?, ?, ?>> processor;

	private Result result;

	private Throwable throwable;

	private ProcessorResult(Class<? extends BaseProcessor<?, ?, ?>> clazz, Result result) {
		this(clazz, result, null);
	}
	
	private ProcessorResult(Class<? extends BaseProcessor<?, ?, ?>> clazz, Result result, Throwable throwable) {
		this.processor = clazz;
		this.result = result;
		this.throwable = throwable;
	}

	public static ProcessorResult succeeded(Class<? extends BaseProcessor<?, ?, ?>> clazz) {
		return new ProcessorResult(clazz, Result.SUCCESS);
	}

	public static ProcessorResult failed(Class<? extends BaseProcessor<?, ?, ?>> clazz, Throwable throwable) {
		return new ProcessorResult(clazz, Result.FAILED, throwable);
	}

	public static ProcessorResult passedWithWarnings(Class<? extends BaseProcessor<?, ?, ?>> clazz,
			Throwable throwable) {
		return new ProcessorResult(clazz, Result.PASSED_WITH_WARNINGS, throwable);
	}

	public static ProcessorResult passedAndBreak(Class<? extends BaseProcessor<?, ?, ?>> clazz, Throwable throwable) {
		return new ProcessorResult(clazz, Result.PASSED_AND_BREAK, throwable);
	}

	public Class<? extends BaseProcessor<?, ?, ?>> getProcessor() {
		return processor;
	}

	public Result getResult() {
		return result;
	}

	public Throwable getThrowable() {
		return throwable;
	}

	public static enum Result {
		SUCCESS(0), PASSED_WITH_WARNINGS(1), PASSED_AND_BREAK(2), FAILED(3);

		private final int priority;

		private Result(int priority) {
			this.priority = priority;
		}

		public int getPriority() {
			return this.priority;
		}

	}

}
