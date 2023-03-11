package io.github.gear4jtest.core.service;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.internal.ChainExecutorService;
import io.github.gear4jtest.core.internal.Gear4jContext;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.ElementOnError.RuleOverridingPolicy;
import io.github.gear4jtest.core.model.OnError;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.Transformer;
import io.github.gear4jtest.core.processor.operation.ChainContextInjector;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector;
import io.github.gear4jtest.core.processor.operation.OperationProcessor;
import io.github.gear4jtest.core.service.steps.Step1;
import io.github.gear4jtest.core.service.steps.Step2;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step4;
import io.github.gear4jtest.core.service.steps.Step5;
import io.github.gear4jtest.core.service.steps.Step7;
import io.github.gear4jtest.core.service.steps.Step8;

// handle factory for step / processor... configuration
public class SimpleChainBuilderTest {

	@Test
	public void simple_test() {
		// Given
		ChainModel<String, Integer> pipe = 
				chain(String.class)
					.assemble(
					    branches(String.class).withBranch(
								branch(String.class).withStep(
										operation(Step7::new)
											.parameter(newParameter(Step7::getValue).value(14538))
											.context(Step7::getChainContext)
											.build()
									)
								.build())
								.returns("", Integer.class)
							.build())
					.defaultConfiguration(chainDefaultConfiguration()
							.stepDefaultConfiguration(stepLineElementDefaultConfiguration()
									.preProcessors(Arrays.asList(
											OperationParamsInjector::new,
											ChainContextInjector::new))
									.onError(onError().rules(Arrays.asList(rule(IOException.class).ignore().build())).build())
									.build())
							.build())
					.build();

		Map<String, Object> context = new HashMap<String, Object>() {{ put("a", 45612); }};
		
		// When
		Object result = new ChainExecutorService().execute(pipe, "", context);

		// Then
		assertThat(result).isNotNull().isEqualTo("14538_45612");
		
		// Given
		ChainModel<String, Integer> newPipe = 
				chain(String.class)
					.assemble(
					    branches(String.class).withBranch(
								branch(String.class)
									.withStep(operation(Step3::new)
											.onError(preOnError(OperationParamsInjector.class)
													.rule(chainBreakRule(Exception.class).build())
													.rule(ignoreRule(RuntimeException.class).build())
													.build())
											.onError(onProcessingError(OperationProcessor.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(Exception.class).build())
													.build())
											.onError(postOnError(TestPostProcessor.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(RuntimeException.class).build())
													.build())
											.transformer(a -> new HashMap<>())
											.build()
											)
									.withStep(operation(Step8::new).build())
								.build())
								.returns("", Integer.class)
							.build())
					.defaultConfiguration(chainDefaultConfiguration()
							.stepDefaultConfiguration(stepLineElementDefaultConfiguration()
									.preProcessors(Arrays.asList(OperationParamsInjector::new))
									.build())
							.build())
					.build();

		// When
		result = new ChainExecutorService().execute(newPipe, "a", context);

		// Then
		assertThat(result).isNotNull().isEqualTo(5);
	}
	
	public static class TestPostProcessor implements PostProcessor {

		@Override
		public void process(Object input, StepLineElement currentElement, Gear4jContext context,
				ProcessorDrivingElement<StepLineElement> chainDriver) {
			
		}
		
	}
	
	private static <T> T getT(Class<T> clazz) {
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Test
	public void test() {
		// Given
		ChainModel<String, Integer> pipe = 
				ElementModelBuilders.<String>chain()
					.assemble(
							ElementModelBuilders.<String>branches()
								.withBranch(
										ElementModelBuilders.<String>branch()
									.withStep(
											ElementModelBuilders.<String>operation()
											.handler(() -> new Step1())
											.build()
											)
									.withStep(
											ElementModelBuilders.<Integer>operation()
											.handler(() -> new Step2())
											.build()
											)
									.withStep(
											ElementModelBuilders.<String>operation()
											.handler(() -> new Step3())
											.build()
											)
									.withStep(
											ElementModelBuilders.<Map<String, String>>operation()
											.handler(() -> new Step4("a").new Step4Map())
											.build()
											)
									.withBranches(ElementModelBuilders.<Void>branches()
											.withBranch(ElementModelBuilders.<Void>branch()
													.withStep(
															ElementModelBuilders.<Void>operation()
														.handler(() -> new Step5())
														.build()
														)
													.build())
											.withBranch(ElementModelBuilders.<Void>branch()
													.withStep(
															ElementModelBuilders.<Void>operation()
														.handler(() -> new Step5())
														.build()
														)
													.build())
											.build())
								.build())
								.returns("", Integer.class)
							.build())
					.build();

		// When
//		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
//		assertThat(result).isNotNull().isEqualTo("");
	}
	
	@Test
	public void testa() {
		// Given
		ChainModel<String, Integer> pipe = 
				ElementModelBuilders.<String>chain()
					.assemble(
							ElementModelBuilders.<String>branches()
								.withBranch(
										ElementModelBuilders.<String>branch()
									.withStep(step1())
									.withStep(step2())
									.withStep(step3())
									.withStep(step4())
									.withBranches(ElementModelBuilders.<Void>branches()
											.withBranch(ElementModelBuilders.<Void>branch()
													.withStep(step5())
													.build())
											.withBranch(ElementModelBuilders.<Void>branch()
													.withStep(step5())
													.build())
											.build())
								.build())
								.returns("", Integer.class)
							.build())
					.build();

		// When
//		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
//		assertThat(result).isNotNull().isEqualTo("");
	}

	private OperationModel<Void, String> step5() {
		return ElementModelBuilders.<Void>operation()
		.handler(() -> new Step5())
		.build();
	}

	private OperationModel<Map<String, String>, Void> step4() {
		return ElementModelBuilders.<Map<String, String>>operation()
				.handler(() -> new Step4("a").new Step4Map())
				.build();
	}

	private OperationModel<String, Map<String, String>> step3() {
		return ElementModelBuilders.<String>operation()
				.handler(() -> new Step3())
				.build();
	}

	private OperationModel<Integer, String> step2() {
		return ElementModelBuilders.<Integer>operation()
				.handler(() -> new Step2())
				.build();
	}

	private OperationModel<String, Integer> step1() {
		return ElementModelBuilders.<String>operation()
				.handler(() -> new Step1())
				.build();
	}

//	@Test
//	public void testa() {
//		// Given
//		Chain.Builder<String, ?> pipe = Chain.<String>newChain();
//		
//		Branches.Builder<String, String> branchesBuilder = Branches.newBranches(pipe);
//		
//		Branch.Builder<String, String> branchBuilder = Branch.newBranch(branchesBuilder);
//		
//		Stepa.Builder<String, Integer> stepBuilder = Stepa.newStep(branchBuilder)
//				.operation(() -> new Step1());
//		
//		Branch.Builder<String, Integer> branchBuilder2 = branchBuilder.withStep(stepBuilder.build());
//		
//		Stepa.Builder<Integer, String> stepBuilder2 = Stepa.newStep(branchBuilder2)
//				.operation(() -> new Step2());
//		
//		Stepa.Builder<String, Integer> stepBuilder3 = Stepa.newStep(branchBuilder)
//				.operation(() -> new Step3());
//		
//		Stepa.Builder<String, Integer> stepBuilder4 = Stepa.newStep(branchBuilder)
//				.operation(() -> new Step4());
//		
//		pipe.assemble(Branches.newBranches(pipe).build());
//		
//		
////					.assemble(
////							Branches.<String>newBranches()
////								.withBranch(
////									Branch.<String>newBranch()
////									.withStep(
////											Stepa.<String>newStep()
////											.operation(() -> new Step1())
////											.build()
////											)
////									.withStep(
////											Stepa.<Integer>newStep()
////											.operation(() -> new Step2())
////											.build()
////											)
////									.withStep(
////											Stepa.<String>newStep()
////											.operation(() -> new Step3())
////											.build()
////											)
////									.withStep(
////											Stepa.<Map<String, String>>newStep()
////											.operation(() -> new Step4("a").new Step4Map())
////											.build()
////											)
////									.withBranches(Branches.<Void>newBranches()
////											.withBranch(Branch.<Void>newBranch()
////													.withStep(
////														Stepa.<Void>newStep()
////														.operation(() -> new Step5())
////														.build()
////														)
////													.build())
////											.withBranch(Branch.<Void>newBranch()
////													.withStep(
////														Stepa.<Void>newStep()
////														.operation(() -> new Step5())
////														.build()
////														)
////													.build())
////											.build())
////								.build())
////								.returns("", Integer.class)
////							.build())
////					.build();
//
//		// When
////		Object result = new ChainExecutorService().execute(pipe, "");
//
//		// Then
////		assertThat(result).isNotNull().isEqualTo("");
//	}

}
