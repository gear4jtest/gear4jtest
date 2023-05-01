package io.github.gear4jtest.core.service;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.context.Contexts;
import io.github.gear4jtest.core.context.StepProcessingContext;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventListener;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.ChainExecutorService;
import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDrivingElement;
import io.github.gear4jtest.core.processor.operation.ChainContextInjector;
import io.github.gear4jtest.core.processor.operation.OperationInvoker;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector;
import io.github.gear4jtest.core.service.steps.Step1;
import io.github.gear4jtest.core.service.steps.Step2;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step4.Step4Map;
import io.github.gear4jtest.core.service.steps.Step5;
import io.github.gear4jtest.core.service.steps.Step7;
import io.github.gear4jtest.core.service.steps.Step8;

// handle factory for step / processor... configuration
public class SimpleChainBuilderTest {

	@Test
	public void simple_test() {
		// Given
		ChainModel<String, Integer> pipe = chain(String.class)
				.resourceFactory(new TestResourceFactory())
				.assemble(branches(String.class)
						.withBranch(branch(String.class)
								.withStep(operation(Step7.class)
										.parameter(newParameter(Step7::getValue).name("whatever").value(14538))
										.context(Step7::getChainContext)
										.build())
								.build())
						.returns("", Integer.class).build())
				.queue(queue().eventListener(new TestEventListener()).build())
				.defaultConfiguration(
						chainDefaultConfiguration()
							.stepDefaultConfiguration(
									stepLineElementDefaultConfiguration()
										.preProcessors(Arrays.asList(OperationParamsInjector.class, ChainContextInjector.class))
										.onError(onError().rules(Arrays.asList(rule(IOException.class).ignore().build()))
										.build())
								.build())
							.build())
				.build();

		Map<String, Object> context = new HashMap<String, Object>() {
			{
				put("a", 45612);
			}
		};

		// When
		Object result = new ChainExecutorService().execute(pipe, "", context);

		// Then
		assertThat(result).isNotNull().isEqualTo("14538_45612");

		// Given
		ChainModel<String, Integer> newPipe = chain(String.class)
				.resourceFactory(new TestResourceFactory())
				.assemble(// definition as method name ?
						branches(String.class)
							.withBranch(branch(String.class)
									.withStep(operation(Step3.class)
											.onError(
													preOnError(OperationParamsInjector.class)
													.rule(chainBreakRule(Exception.class).build())
													.rule(ignoreRule(RuntimeException.class).build())
											.build())
						.onError(onProcessingError(OperationInvoker.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(Exception.class).build())
								.build())
						.onError(postOnError(TestPostProcessor.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(RuntimeException.class).build())
								.build())
						.transformer(a -> new HashMap<>()).build()).withStep(operation(Step8.class).build()).build())
						.returns("", Integer.class).build())
				.queue(queue().eventListener(new TestEventListener()).build())
				.defaultConfiguration(
						chainDefaultConfiguration().stepDefaultConfiguration(stepLineElementDefaultConfiguration()
								.preProcessors(Arrays.asList(OperationParamsInjector.class)).build()).build())
				.build();

		// When
		result = new ChainExecutorService().execute(newPipe, "a", context);

		// Then
		assertThat(result).isNotNull().isEqualTo(5);
	}

	public static class TestResourceFactory implements ResourceFactory {

		final static Map<Class<?>, Object> BEANS;
		static {
			BEANS = new HashMap<>();
			BEANS.put(OperationParamsInjector.class, new OperationParamsInjector());
			BEANS.put(ChainContextInjector.class, new ChainContextInjector());
//			BEANS.put(OperationRetriever.class, new OperationRetriever());
			BEANS.put(OperationInvoker.class, new OperationInvoker());
			BEANS.put(Step7.class, new Step7());
			BEANS.put(Step3.class, new Step3());
			BEANS.put(Step8.class, new Step8());
		}

		@Override
		public <T> T getResource(Class<T> clazz) {
			return (T) BEANS.get(clazz);
		}

	}

	public static class TestEventListener implements EventListener {

		@Override
		public void handleEvent(Event e) {
			System.out.println();
		}

	}	
	
	public static class TestPostProcessor implements PostProcessor {

		@Override
		public void process(Object input, StepLineElement currentElement,
				ProcessorDrivingElement<StepLineElement> chainDriver, Contexts<StepProcessingContext> context) {

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
		ChainModel<String, Integer> pipe = ElementModelBuilders.<String>chain()
				.assemble(ElementModelBuilders.<String>branches().withBranch(ElementModelBuilders.<String>branch()
						.withStep(ElementModelBuilders.<String>operation().type(Step1.class).build())
						.withStep(ElementModelBuilders.<Integer>operation().type(Step2.class).build())
						.withStep(ElementModelBuilders.<String>operation().type(Step3.class).build())
						.withStep(ElementModelBuilders.<Map<String, String>>operation().type(Step4Map.class).build())
						.withBranches(ElementModelBuilders.<Void>branches()
								.withBranch(ElementModelBuilders.<Void>branch()
										.withStep(ElementModelBuilders.<Void>operation().type(Step5.class).build())
										.build())
								.withBranch(ElementModelBuilders.<Void>branch()
										.withStep(ElementModelBuilders.<Void>operation().type(Step5.class).build())
										.build())
								.build())
						.build()).returns("", Integer.class).build())
				.build();

		// When
//		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
//		assertThat(result).isNotNull().isEqualTo("");
	}

	@Test
	public void testa() {
		// Given
		ChainModel<String, Integer> pipe = ElementModelBuilders.<String>chain()
				.assemble(ElementModelBuilders.<String>branches().withBranch(ElementModelBuilders.<String>branch()
						.withStep(step1()).withStep(step2()).withStep(step3()).withStep(step4())
						.withBranches(ElementModelBuilders.<Void>branches()
								.withBranch(ElementModelBuilders.<Void>branch().withStep(step5()).build())
								.withBranch(ElementModelBuilders.<Void>branch().withStep(step5()).build()).build())
						.build()).returns("", Integer.class).build())
				.build();

		// When
//		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
//		assertThat(result).isNotNull().isEqualTo("");
	}

	private OperationModel<Void, String> step5() {
		return ElementModelBuilders.<Void>operation().type(Step5.class).build();
	}

	private OperationModel<Map<String, String>, Void> step4() {
		return ElementModelBuilders.<Map<String, String>>operation().type(Step4Map.class).build();
	}

	private OperationModel<String, Map<String, String>> step3() {
		return ElementModelBuilders.<String>operation().type(Step3.class).build();
	}

	private OperationModel<Integer, String> step2() {
		return ElementModelBuilders.<Integer>operation().type(Step2.class).build();
	}

	private OperationModel<String, Integer> step1() {
		return ElementModelBuilders.<String>operation().type(Step1.class).build();
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
