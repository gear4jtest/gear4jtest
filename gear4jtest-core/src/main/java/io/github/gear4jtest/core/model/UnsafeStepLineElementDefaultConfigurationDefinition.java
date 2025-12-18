package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.processor.GenericOperator;

public class UnsafeStepLineElementDefaultConfigurationDefinition {

	private StationConfigurationDefinition.Builder configurationBuilder;

	public static class Builder {

		private UnsafeStepLineElementDefaultConfigurationDefinition managedInstance;

		public Builder(StationConfigurationDefinition.Builder configuration) {
			this.managedInstance = new UnsafeStepLineElementDefaultConfigurationDefinition();
			this.managedInstance.configurationBuilder = configuration;
		}

//		public Builder onError(BaseOnError onError) {
//			this.managedInstance.configurationBuilder.onError(onError);
//			return this;
//		}
//
//		public UnsafeStepLineElementDefaultConfigurationDefinition.Builder onError(UnsafeOnError<?> onError) {
//			this.managedInstance.configurationBuilder.onError(onError.getOnError());
//			return this;
//		}

		public StationConfigurationDefinition.Builder transformer(GenericOperator transformer) {
			this.managedInstance.configurationBuilder.transformer(transformer);
			return this.managedInstance.configurationBuilder;
		}

	}

}