package io.github.gear4jtest.core.service;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventListener;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.AssemblyLineException;
import io.github.gear4jtest.core.internal.ChainExecutorService;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.Container1Definition.Container1DFunction;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDataModel;
import io.github.gear4jtest.core.processor.CustomProcessingOperationProcessor;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector;
import io.github.gear4jtest.core.service.steps.Step10;
import io.github.gear4jtest.core.service.steps.Step11;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step7;
import io.github.gear4jtest.core.service.steps.Step8;
import io.github.gear4jtest.core.service.steps.Step9;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

// handle factory for step / processor... configuration
public class SimpleChainBuilderTest {

	@Test
	public void test_refactor_chain_building() throws AssemblyLineException {
		// Given
		LineDefinition<Integer, List<String>> subLine = line(startingPointt(Integer.class))
				.operator(processingOperation(Step10.class).build())
				.build();
		
		LineDefinition<String, List<Integer>> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step3.class)
							.parameter(Step3::getParam, "a")
							.onError(
									preOnError(OperationParamsInjector.class)
										.rule(chainBreakRule(Exception.class).build())
										.rule(ignoreRule(RuntimeException.class).build())
									.build())
							.onError(
									onProcessingError()
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(Exception.class).build())
									.build())
							.onError(
									postOnError(TestPostProcessor.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(RuntimeException.class).build())
									.build())
							.transformer(a -> new HashMap<>())
							.build())
				.operator(fatalSignal(typeMap(String.class, String.class))
						.condition(ctx -> ctx.getItem().containsKey("a")).build())
				.operator(processingOperation(Step8.class).build())
				.operator(processingOperation(Step9.class).build())
//				.iterate(Function.identity(), containerr().withSubLineAndReturns(subLine, Function.identity()).build())
				.build();

		AssemblyLineDefinition<String, List<Integer>> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.configuration(configuration()
						.stepDefaultConfiguration(operationConfiguration().build())
						.eventHandlingDefinition(eventHandling()
								.queue(queue().eventListener(new TestEventListener()).build())
								.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
								.build())
						.build())
				.build();

		Map<String, Object> context = new HashMap<String, Object>() {
			{
				put("a", 45612);
			}
		};

		// When
		List<Integer> result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", context, new TestResourceFactory());

		// Then
		assertThat(result).isNotNull()
			.hasSize(1)
			.contains(5);
	}

	@Test
	public void test_refactor_chain_building_stop_signal() {
		// Given
		LineDefinition<Integer, List<String>> subLine = line(startingPointt(Integer.class))
				.operator(processingOperation(Step10.class).build())
				.build();
		
		LineDefinition<String, Object> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step3.class)
							.parameter(Step3::getParam, "a")
							.onError(
									preOnError(OperationParamsInjector.class)
										.rule(chainBreakRule(Exception.class).build())
										.rule(ignoreRule(RuntimeException.class).build())
									.build())
							.onError(
									onProcessingError()
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(Exception.class).build())
									.build())
							.onError(
									postOnError(TestPostProcessor.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(RuntimeException.class).build())
									.build())
							.transformer(a -> new HashMap<>())
							.build())
				// possible hacking by adding cast (SignalDefiinition<Map<String, String>>)...
				.operator(stopSignal(typeMap(String.class, String.class))
						.condition(ctx -> ctx.getItem().containsKey("a")).build())
				.operator(processingOperation(Step8.class).build())
				.operator(processingOperation(Step9.class).build())
//				.iterate(Function.identity(), containerr().withSubLineAndReturns(subLine, Function.identity()).build())
				.build();

		AssemblyLineDefinition<String, Object> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.configuration(configuration()
						.stepDefaultConfiguration(operationConfiguration()
								.preProcessors(Arrays.asList(OperationParamsInjector.class))
								.build())
						.eventHandlingDefinition(eventHandling()
								.queue(queue().eventListener(new TestEventListener()).build())
								.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
								.build())
						.build())
				.build();

		Map<String, Object> context = new HashMap<String, Object>() {
			{
				put("a", 45612);
			}
		};

		// When
		Object result = new ChainExecutorService().execute(assemblyLine, "b", context, new TestResourceFactory());

		// Then
//		assertThat(result).isNotNull()
//			.hasSize(1)
//			.contains(5);
	}

	@Test
	public void testIteration() throws AssemblyLineException {
		// Given
		LineDefinition<Integer, List<String>> subLine = line(startingPointt(Integer.class))
				.operator(processingOperation(Step10.class).build())
				.build();
		
		LineDefinition<String, List<List<String>>> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step3.class).build())
				.operator(processingOperation(Step8.class).build())
				.operator(processingOperation(Step9.class).build())
				.iterate(Function.identity(), containerr().withSubLineAndReturns(subLine, Function.identity()).build(), Collectors.toList())
				.build();

		AssemblyLineDefinition<String, List<List<String>>> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.configuration(configuration()
						.stepDefaultConfiguration(operationConfiguration().build())
						.eventHandlingDefinition(eventHandling()
								.queue(queue().eventListener(new TestEventListener()).build())
								.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
								.build())
						.build())
				.build();

		// When
		List<List<String>> result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", new TestResourceFactory());

		// Then
		assertThat(result).isNotNull()
			.hasSize(1)
			.contains(Arrays.asList(""));
	}

	@Test
	public void shouldThrowExceptionWhenFatalSignal() {
		// Given
		LineDefinition<String, Map<String, String>> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step3.class).build())
				.operator(fatalSignal(typeMap(String.class, String.class))
						.condition(ctx -> ctx.getItem().containsKey("b")).build())
				.build();

		AssemblyLineDefinition<String, Map<String, String>> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.build();

		// When - Then
		ChainExecutorService service = new ChainExecutorService();
		assertThatExceptionOfType(AssemblyLineException.class)
				.isThrownBy(() -> service.executeAndUnwrap(assemblyLine, "b", new TestResourceFactory()));
	}

	@Test
	public void shouldReturnObjectWhenUsingStopSignalNotActivated() throws AssemblyLineException {
		// Given
		LineDefinition<String, Object> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step3.class).build())
				// possible hacking by adding cast (SignalDefiinition<Map<String, String>>)...
				.operator(stopSignal(typeMap(String.class, String.class))
						.condition(ctx -> ctx.getItem().containsKey("a")).build())
				.operator(processingOperation(Step8.class).build())
				.build();

		AssemblyLineDefinition<String, Object> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.build();

		// When
		Object result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", new TestResourceFactory());

		// Then
		assertThat(result).isNotNull()
			.isExactlyInstanceOf(Integer.class)
			.isEqualTo(5);
	}

	@Test
	public void shouldReturnObjectWhenUsingStopSignalActivated() throws AssemblyLineException {
		// Given
		LineDefinition<String, Object> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step3.class)
						.additionalModel(null, null)
						.build())
				.operator(stopSignal(typeMap(String.class, String.class))
						.condition(ctx -> ctx.getItem().containsKey("b")).build())
				.operator(processingOperation(Step8.class).build())
				.build();

		AssemblyLineDefinition<String, Object> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.build();

		// When
		Object result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", new TestResourceFactory());

		// Then
		assertThat(result).isNotNull()
			.isExactlyInstanceOf(HashMap.class)
			.asInstanceOf(InstanceOfAssertFactories.MAP)
			.containsEntry("b", "b");
	}

	@Test
	public void test_processor_chain() throws AssemblyLineException {
		// Given
		LineDefinition<String, String> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step11.class)
							.parameter(Step11::getParam, "a")
							.build())
				.build();

		AssemblyLineDefinition<String, String> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.configuration(configuration()
						.stepDefaultConfiguration(operationConfiguration()
								.preProcessors(Arrays.asList(OperationParamsInjector.class))
								.build())
						.eventHandlingDefinition(eventHandling()
								.queue(queue().eventListener(new TestEventListener()).build())
								.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
								.build())
						.build())
				.build();

		// When
		String result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", new TestResourceFactory());

		// Then
		assertThat(result).isNotNull()
			.isEqualTo("a");
	}

	@Test
	public void test_simple_container() throws AssemblyLineException {
		// Given
		LineDefinition<String, String> subLine = line(startingPointt(String.class))
				.operator(processingOperation(Step11.class)
							.parameter(Step11::getParam, "b")
							.build())
				.build();

		ContainerDefinition<String, String> container = container(String.class).withSubLine(subLine).returns(Container1DFunction.identity());
		
		LineDefinition<String, String> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step11.class)
							.parameter(Step11::getParam, "a")
							.build())
				.operator(container)
				.build();

		AssemblyLineDefinition<String, String> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.configuration(configuration()
						.stepDefaultConfiguration(operationConfiguration()
								.preProcessors(Arrays.asList(OperationParamsInjector.class))
								.build())
						.eventHandlingDefinition(eventHandling()
								.queue(queue().eventListener(new TestEventListener()).build())
								.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
								.build())
						.build())
				.build();

		// When
		String result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", new TestResourceFactory());

		// Then
		assertThat(result).isNotNull()
			.isEqualTo("b");
	}

	@Test
	public void test_container_two_sublines() throws AssemblyLineException {
		// Given
		LineDefinition<String, String> subLine = line(startingPointt(String.class))
				.operator(processingOperation(Step11.class)
							.parameter(Step11::getParam, "b")
							.build())
				.build();

		LineDefinition<String, String> subLine2 = line(startingPointt(String.class))
				.operator(processingOperation(Step11.class)
							.parameter(Step11::getParam, "c")
							.build())
				.build();

		ContainerDefinition<String, List<String>> container = container(String.class)
				.withSubLine(subLine).withSubLine(subLine2).returns((a, b) -> Arrays.asList(a, b));
		
		LineDefinition<String, List<String>> mainLine = line(startingPointt(String.class))
				.operator(processingOperation(Step11.class)
							.parameter(Step11::getParam, "a")
							.build())
				.operator(container)
				.build();

		AssemblyLineDefinition<String, List<String>> assemblyLine = asssemblyLineDefinition("my-basic-assembly-line")
				.definition(mainLine)
				.configuration(configuration()
						.stepDefaultConfiguration(operationConfiguration()
								.preProcessors(Arrays.asList(OperationParamsInjector.class))
								.build())
						.eventHandlingDefinition(eventHandling()
								.queue(queue().eventListener(new TestEventListener()).build())
								.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
								.build())
						.build())
				.build();

		// When
		List<String> result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", new TestResourceFactory());

		// Then
		assertThat(result)
			.isNotNull()
			.hasSize(2)
			.containsExactly("b", "c");
	}
	
	private class ProcessingOperationProxy implements MethodInterceptor {
	    private Operation originalOperation;
	    public ProcessingOperationProxy(Operation operation) {
	        this.originalOperation = operation;
	    }
	 
	    public Object intercept(Object object, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
	        if ("execute".equals(method.getName())) {
	        	throw new IllegalAccessException("Operation method execution is not allowed");
	        }
	        return method.invoke(originalOperation, args);
	    }
	}

	@Test
	public void testcglib() throws AssemblyLineException {
		// Given
//		LineDefinition<String, Object> mainLine = line(startingPointt(String.class))
//				.operator(processingOperation(Step3.class).build())
//				.operator(stopSignal(typeMap(String.class, String.class))
//						.condition(ctx -> ctx.getItem().containsKey("b")).build())
//				.operator(processingOperation(Step8.class).build())
//				.build();

		ResourceFactory resourceFactory = new TestResourceFactory();
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(Step3.class);
		enhancer.setCallback(new ProcessingOperationProxy(resourceFactory.getResource(Step3.class)));
		Step3 proxy = (Step3) enhancer.create();
		proxy.getParam();
		proxy.execute("", null);
		
		AssemblyLineDefinition<String, Object> assemblyLine = (AssemblyLineDefinition<String, Object>) asssemblyLineDefinition("my-basic-assembly-line")
//				.definition(mainLine)
				.build();

		// When
		Object result = new ChainExecutorService().executeAndUnwrap(assemblyLine, "b", resourceFactory);

		// Then
		assertThat(result).isNotNull()
			.isExactlyInstanceOf(HashMap.class)
			.asInstanceOf(InstanceOfAssertFactories.MAP)
			.containsEntry("b", "b");
	}

//	@Test
//	public void test_signal() {
//		// Given
//		ChainModel<String, Integer> newPipe = chain(String.class)
//				.resourceFactory(new TestResourceFactory())
//				.assemble(// definition as method name ?
//						branches(String.class)
//							.withBranch(branch(String.class)
//									.withStep(operation(Step3.class)
//											.onError(
//													preOnError(OperationParamsInjector.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(RuntimeException.class).build())
//											.build())
//										.onError(onProcessingError()
//				//													.rule(chainBreakRule(Exception.class).build())
//				//													.rule(ignoreRule(Exception.class).build())
//												.build())
//										.onError(postOnError(TestPostProcessor.class)
//				//													.rule(chainBreakRule(Exception.class).build())
//				//													.rule(ignoreRule(RuntimeException.class).build())
//												.build())
//										.transformer(a -> new HashMap<>()).build())
//									.withSignal(signal(typeMap(String.class, String.class))
//										.type(SignalType.STOP)
//										.condition(ctx -> ctx.getItem().containsKey("a")).build())
//									.withStep(operation(Step8.class).build())
//									.withIterableStep(operation(Step9.class).build())
//									.iterate()
//									.withStep(operation(Step9.class).build())
//									.build())
//						.returns("", Integer.class).build())
//				.eventHandling(eventHandling()
//						.queue(queue().eventListener(new TestEventListener()).build())
//						.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
//						.build())
//				.defaultConfiguration(
//						chainDefaultConfiguration().stepDefaultConfiguration(stepLineElementDefaultConfiguration().build()).build())
//				.build();
//		
//		Map<String, Object> context = new HashMap<String, Object>() {
//			{
//				put("a", 45612);
//			}
//		};
//
//		// When
//		Object result = new ChainExecutorService().execute(newPipe, "a", context);
//
//		// Then
//		assertThat(result).isNotNull().isEqualTo(5);
//	}
//
//	// create a class for assembly line definition with a first method waiting for startingPoint and then the real definition
//	@Test
//	public void test_simple_line() {
//		// Given
//		ChainModel<String, List<String>> newPipe = chain()
//				.assemble(
//						startingPoint(String.class).build()
//						.withStep(
//								operation(Step3.class)
//									.onError(preOnError(OperationParamsInjector.class)
//												.rule(chainBreakRule(Exception.class).build())
//												.rule(ignoreRule(RuntimeException.class).build())
//										.build())
//									.onError(onProcessingError()
//			//													.rule(chainBreakRule(Exception.class).build())
//			//													.rule(ignoreRule(Exception.class).build())
//											.build())
//									.onError(postOnError(TestPostProcessor.class)
//			//													.rule(chainBreakRule(Exception.class).build())
//			//													.rule(ignoreRule(RuntimeException.class).build())
//											.build())
//									.transformer(a -> new HashMap<>())
//								.build())
//						// store class in signal constructor to save it in DB
//						.withSignal(signal(typeMap(String.class, String.class))
//								.type(SignalType.STOP)
//								.condition(ctx -> ctx.getItem().containsKey("a")).build())
//						.withStep(operation(Step8.class).build())
//						.withStep(operation(Step9.class).build())
//						.iterate(Function.identity(), container().withSubLineAndReturns(
//										startingPoint(Integer.class).build()
//											.withStep(operation(Step10.class).build()), 
//										Function.identity()).build())
//				)
//				.eventHandling(eventHandling()
//						.queue(queue().eventListener(new TestEventListener()).build())
//						.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
//						.build())
//				.defaultConfiguration(
//						chainDefaultConfiguration().stepDefaultConfiguration(stepLineElementDefaultConfiguration().build()).build())
//				.build();
//		
//		Map<String, Object> context = new HashMap<String, Object>() {
//			{
//				put("a", 45612);
//			}
//		};
//
//		// When
//		Object result = new ChainExecutorService().execute(newPipe, "a", context);
//
//		// Then
//		assertThat(result).isNotNull().isEqualTo(5);
//	}
//
//	@Test
//	public void simple_test() {
//		// Given
//		ChainModel<String, Integer> pipe = chain(String.class)
//				.resourceFactory(new TestResourceFactory())
//				.assemble(branches(String.class)
//						.withBranch(branch(String.class)
//								.withStep(operation(Step7.class)
//										.processorModel(OperationParamsInjector.class, Parameters.newBuilder().withParameter(newParameter(Step6::getValue, 14538)).build())
//										.onError(
//												preOnError(OperationParamsInjector.class)
//													.rule(ignoreRule(RuntimeException.class).build())
//												.build())
//										.onError(globalOnError().rule(chainBreakRule(Exception.class).build()).build())
//										.transformer(a -> "")
//										.build())
//								.build())
//						.returns("", Integer.class).build())
//				.eventHandling(eventHandling()
//						.queue(queue().eventListener(new TestEventListener()).build())
//						.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
//						.build())
//				.defaultConfiguration(
//						chainDefaultConfiguration()
//							.stepDefaultConfiguration(
//									stepLineElementDefaultConfiguration()
//										// makes it optional to  define preInvokers / chain
//										.preProcessors(Arrays.asList(OperationParamsInjector.class))
//										.onError(globalOnError().rule(chainBreakRule(Exception.class).build()).build())
//										.onError(preOnError(OperationParamsInjector.class).rule(ignoreRule(RuntimeException.class).build()).build())
//										.transformer(a -> "")
//								.build())
//							.build())
//				.build();
//
//		Map<String, Object> context = new HashMap<String, Object>() {
//			{
//				put("a", 45612);
//			}
//		};
//
//		// When
//		Object result = new ChainExecutorService().execute(pipe, "", context);
//
//		// Then
//		assertThat(result).isNotNull().isEqualTo("14538_45612");
//
//		// Given
//		ChainModel<String, Integer> newPipe = chain(String.class)
//				.resourceFactory(new TestResourceFactory())
//				.assemble(// definition as method name ?
//						branches(String.class)
//							.withBranch(branch(String.class)
//									.withStep(operation(Step3.class)
//											.onError(
//													preOnError(OperationParamsInjector.class)
//													.rule(chainBreakRule(Exception.class).build())
//													.rule(ignoreRule(RuntimeException.class).build())
//											.build())
//										.onError(onProcessingError()
//				//													.rule(chainBreakRule(Exception.class).build())
//				//													.rule(ignoreRule(Exception.class).build())
//												.build())
//										.onError(postOnError(TestPostProcessor.class)
//				//													.rule(chainBreakRule(Exception.class).build())
//				//													.rule(ignoreRule(RuntimeException.class).build())
//												.build())
//										.transformer(a -> new HashMap<>()).build())
//									.withStep(operation(Step8.class).build()).build())
//						.returns("", Integer.class).build())
//				.eventHandling(eventHandling()
//						.queue(queue().eventListener(new TestEventListener()).build())
//						.globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
//						.build())
//				.defaultConfiguration(
//						chainDefaultConfiguration().stepDefaultConfiguration(stepLineElementDefaultConfiguration().build()).build())
//				.build();
//
//		// When
//		result = new ChainExecutorService().execute(newPipe, "a", context);
//
//		// Then
//		assertThat(result).isNotNull().isEqualTo(5);
//	}

	public static class TestResourceFactory implements ResourceFactory {

		final static Map<Class<?>, Object> BEANS;
		static {
			BEANS = new HashMap<>();
			// Gear4j itself should handle the initialization of its proper beans...
			BEANS.put(OperationParamsInjector.class, new OperationParamsInjector());
//			BEANS.put(OperationRetriever.class, new OperationRetriever());
			BEANS.put(Step3.class, new Step3());
			BEANS.put(Step7.class, new Step7());
			BEANS.put(Step8.class, new Step8());
			BEANS.put(Step9.class, new Step9());
			BEANS.put(Step10.class, new Step10());
			BEANS.put(Step11.class, new Step11());
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
	
	public static class TestPostProcessor implements ProcessingOperationProcessor {

		@Override
		public void process(Item input, ProcessingOperationDataModel model, Void customModel, StepExecution context) {
		}

	}	

	public static class ProcessorModel {
		
	}

	public static class CustomPreProcessor implements CustomProcessingOperationProcessor<ProcessorModel> {

		@Override
		public void process(Item input, ProcessingOperationDataModel model, ProcessorModel customModel, StepExecution context) {
			
		}

	}

	private static <T> T getT(Class<T> clazz) {
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

//	@Test
//	public void test() {
//		// Given
//		ChainModel<String, Integer> pipe = ElementModelBuilders.<String>chain()
//				.assemble(ElementModelBuilders.<String>branches().withBranch(ElementModelBuilders.<String>branch()
//						.withStep(ElementModelBuilders.<String>operation().type(Step1.class).build())
//						.withStep(ElementModelBuilders.<Integer>operation().type(Step2.class).build())
//						.withStep(ElementModelBuilders.<String>operation().type(Step3.class).build())
//						.withStep(ElementModelBuilders.<Map<String, String>>operation().type(Step4Map.class).build())
//						.withBranches(ElementModelBuilders.<Void>branches()
//								.withBranch(ElementModelBuilders.<Void>branch()
//										.withStep(ElementModelBuilders.<Void>operation().type(Step5.class).build())
//										.build())
//								.withBranch(ElementModelBuilders.<Void>branch()
//										.withStep(ElementModelBuilders.<Void>operation().type(Step5.class).build())
//										.build())
//								.build())
//						.build()).returns("", Integer.class).build())
//				.build();
//
//		// When
////		Object result = new ChainExecutorService().execute(pipe, "");
//
//		// Then
////		assertThat(result).isNotNull().isEqualTo("");
//	}

//	@Test
//	public void testa() {
//		// Given
//		ChainModel<String, Integer> pipe = ElementModelBuilders.<String>chain()
//				.assemble(ElementModelBuilders.<String>branches().withBranch(ElementModelBuilders.<String>branch()
//						.withStep(step1()).withStep(step2()).withStep(step3()).withStep(step4())
//						.withBranches(ElementModelBuilders.<Void>branches()
//								.withBranch(ElementModelBuilders.<Void>branch().withStep(step5()).build())
//								.withBranch(ElementModelBuilders.<Void>branch().withStep(step5()).build()).build())
//						.build()).returns("", Integer.class).build())
//				.build();
//
//		// When
////		Object result = new ChainExecutorService().execute(pipe, "");
//
//		// Then
////		assertThat(result).isNotNull().isEqualTo("");
//	}
//
//	private OperationModel<Void, String> step5() {
//		return ElementModelBuilders.<Void>operation().type(Step5.class).build();
//	}
//
//	private OperationModel<Map<String, String>, Void> step4() {
//		return ElementModelBuilders.<Map<String, String>>operation().type(Step4Map.class).build();
//	}
//
//	private OperationModel<String, Map<String, String>> step3() {
//		return ElementModelBuilders.<String>operation().type(Step3.class).build();
//	}
//
//	private OperationModel<Integer, String> step2() {
//		return ElementModelBuilders.<Integer>operation().type(Step2.class).build();
//	}
//
//	private OperationModel<String, Integer> step1() {
//		return ElementModelBuilders.<String>operation().type(Step1.class).build();
//	}
//
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

//	@Test
//	void test() {
////		Function<String, String> a = string -> "inon";
////		BiFunction<String, Integer, String> c = (x, y) -> x;
////		ContainerFunction b = string -> "inon";
//		Container1DFunction<String, String> a = string -> string;
//		apply(a);
//	}
//
//	private void apply(ContainerFunction func) {
//		System.out.print(func.apply("WHATEVER THE FUCK"));
//	}
//
//	@FunctionalInterface
//	interface ContainerFunction {
//		Object apply(Object... objects);
//	}
//
//	@FunctionalInterface
//	interface Container1DFunction<A, B> extends ContainerFunction {
//		B applya(A a);
//		
//		default Object apply(Object... objects) {
//			assert objects != null && objects.length == 1;
//			return applya((A) objects[0]);
//		}
//	}
}
