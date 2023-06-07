package io.github.gear4jtest.core.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.gear4jtest.core.processor.ProcessorResult.Result;

public class ProcessorChainResult {

	private Object output;
	private boolean processed;
	private List<ProcessorResult> processorResults;
	private Result result;

	public ProcessorChainResult() {
	}
	
	public ProcessorChainResult(Object output, boolean processed, Result result) {
		this.output = result;
		this.processed = processed;
		this.result = result;
	}

	public Object getOutput() {
		return output;
	}

	public boolean isProcessed() {
		return processed;
	}

	public Result getResult() {
		return result;
	}

	public static class Builder {

		private ProcessorChainResult managedInstance;
		
		public Builder() {
			this.managedInstance = new ProcessorChainResult();
		}
		
		public Builder output(Object output) {
			this.managedInstance.output = output;
			return this;
		}
		
		public Builder processed(boolean processed) {
			this.managedInstance.processed = processed;
			return this;
		}
		
		public Builder processorResult(ProcessorResult processorResult) {
			if (this.managedInstance.processorResults == null) {
				this.managedInstance.processorResults = new ArrayList<>();
			}
			this.managedInstance.processorResults.add(processorResult);
			computeResult();
			return this;
		}
		
		public ProcessorChainResult build() {
			return this.managedInstance;
		}
		
		private void computeResult() {
			this.managedInstance.processorResults.stream()
					.map(ProcessorResult::getResult)
					.sorted(Comparator.comparing(Result::getPriority))
					.findFirst()
					.ifPresent(result -> this.managedInstance.result = result);
		}

	}

}
