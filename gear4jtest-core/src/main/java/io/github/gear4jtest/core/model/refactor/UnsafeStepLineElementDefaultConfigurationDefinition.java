package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.UnsafeOnError;
import io.github.gear4jtest.core.processor.GenericTransformer;

public class UnsafeStepLineElementDefaultConfigurationDefinition {

	private OperationConfigurationDefinition.Builder configurationBuilder;

	public static class Builder {

		private UnsafeStepLineElementDefaultConfigurationDefinition managedInstance;

		public Builder(OperationConfigurationDefinition.Builder configuration) {
			this.managedInstance = new UnsafeStepLineElementDefaultConfigurationDefinition();
			this.managedInstance.configurationBuilder = configuration;
		}

		public Builder onError(BaseOnError onError) {
			this.managedInstance.configurationBuilder.onError(onError);
			return this;
		}

		public UnsafeStepLineElementDefaultConfigurationDefinition.Builder onError(UnsafeOnError<?> onError) {
			this.managedInstance.configurationBuilder.onError(onError.getOnError());
			return this;
		}

		public OperationConfigurationDefinition.Builder transformer(GenericTransformer transformer) {
			this.managedInstance.configurationBuilder.transformer(transformer);
			return this.managedInstance.configurationBuilder;
		}

	}

}