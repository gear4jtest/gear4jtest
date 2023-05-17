package io.github.gear4jtest.core.processor.operation;

import java.util.Map;

import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel.ChainContextRetriever;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class ChainContextInjector implements PreProcessor {

	@Override
	public ProcessorResult process(Item input, StepLineElement model, ProcessorDrivingElement<StepLineElement> chain, StepProcessingContext context) {
		ChainContextRetriever contextRetriever = model.getChainContextRetriever();
		if (contextRetriever == null) {
			return chain.proceed();
		}

		contextRetriever.getParameterValue(context.getOperation()).setValue(model.getLine().getContext());
		
		return chain.proceed();
	}


	public static class ChainContext {

		private Map<String, Object> context;

		private ChainContext() { }

		public static ChainContext of() {
			return new ChainContext();
		}

		public Map<String, Object> getValue() {
			return context;
		}
		
		void setValue(Map<String, Object> value) {
			this.context = value;
		}

	}
	
}
