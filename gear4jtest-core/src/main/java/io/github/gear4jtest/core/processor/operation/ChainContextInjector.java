package io.github.gear4jtest.core.processor.operation;

import java.util.Map;

import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.OperationModel.ChainContextRetriever;
import io.github.gear4jtest.core.processor.Processor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;

public class ChainContextInjector implements Processor<StepLineElement> {

	@Override
	public void process(Object input, StepLineElement model, Gear4jContext context, ProcessorDrivingElement<StepLineElement> chain) {
		ChainContextRetriever contextRetriever = model.getChainContextRetriever();
		if (contextRetriever == null) {
			chain.proceed();
			return;
		}

		contextRetriever.getParameterValue(model.getOperation().getOperation()).setValue(context.getChainContext());
		
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
