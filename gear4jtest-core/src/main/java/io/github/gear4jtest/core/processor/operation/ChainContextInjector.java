package io.github.gear4jtest.core.processor.operation;

import java.util.Map;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel.ChainContextRetriever;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

public class ChainContextInjector implements PreProcessor {

	@Override
	public void process(Object input, StepLineElement model, ProcessorDrivingElement<StepLineElement> chain, Contexts<StepProcessingContext> context) {
		ChainContextRetriever contextRetriever = model.getChainContextRetriever();
		if (contextRetriever == null) {
			chain.proceed();
			return;
		}

		contextRetriever.getParameterValue(context.getLineElementContext().getOperation()).setValue(context.getGlobalContext().getChainContext());
		
		chain.proceed();
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
