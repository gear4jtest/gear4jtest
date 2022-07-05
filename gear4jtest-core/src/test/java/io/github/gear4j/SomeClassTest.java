package io.github.gear4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;

import io.github.gear4j.steps.Step1;
import io.github.gear4j.steps.Step2;
import io.github.gear4j.steps.Step3;
import io.github.gear4j.steps.Step4;
import io.github.gear4j.steps.Step4.Step4Map;
import io.github.gear4j.steps.Step5;
import io.github.gear4jtest.core.model.Chain;
import io.github.gear4jtest.core.model.test.BranchesChain;
import io.github.gear4jtest.core.service.ChainExecutorService;

public class SomeClassTest {

//	@Test
//	public void test() {
//		// Given
//		Chain<String, Void> pipe = ChainBuilder.<String>newChain()
//				.add(new Step1())
//				.add(new Step2())
//				.add(new Step3())
//				.add(new Step4("a").new Step4Map())
//				.build();
//		
//		// When
//		Object result = new ChainExecutorService().execute(pipe, "");
//		
//		// Then
//		assertThat(result).isNull();
//	}

//	@Test
//	public void testa() {
//		// Given
//		Chain<String, String> pipe = 
//				Chain.<String>newChain()
//					.branches()
//						.branch()
//							.step(StepBuilder.newStep(Step1.class).operation(() -> new Step1()))
//							.step(StepBuilder.newStep(Step2.class).operation(() -> new Step2()))
//							.step(StepBuilder.newStep(Step3.class).operation(() -> new Step3()))
//							.step(StepBuilder.newStep(Step4.Step4Map.class).operation(() -> new Step4("a").new Step4Map()).returns("", Void.class))
//							.branches()
//								.branch()
//									.step(StepBuilder.newStep(Step5.class).operation(() -> new Step5()))
//								.done()
//							.doneBranchBranches()
//						.done()
//					.doneChainBranches()
//				.build();
//		
//		// When
//		Object result = new ChainExecutorService().execute(pipe, "");
//		
//		// Then
//		assertThat(result).isNotNull().isEqualTo("");
//	}

	@Test
	public void test_builder() {
		// Given
		//@formatter:off
//		Chain<String, Integer> pipe = 
				Chain.<String>newChain()
					.branches()
						.branch()
						.done()
					.done()
				.build();
		//@formatter:on

		// When
		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
		assertThat(result).isNotNull().isEqualTo("");
	}

	@Test
	public void test_simple_builder() {
		// Given
		//@formatter:off
//		Chain<String, Integer> pipe = 
				Chain.<String>newChain()
					.branches()
						.branch()
						.returns("", Void.class)
						.done()
					.done()
				.build();
		//@formatter:on

		// When
		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
		assertThat(result).isNotNull().isEqualTo("");
	}

	@Test
	public void testb() {
		// Given
		//@formatter:off
		Chain<String, Integer> pipe = 
				Chain.<String>newChain()
					.branches()
						.branch()
							.step()
								.operation(() -> new Step1())
							.done()
							.step()
								.operation(() -> new Step2())
							.done()
							.step()
								.operation(() -> new Step3())
							.done()
							.step()
								.operation(() -> new Step4("a").new Step4Map())
							.done()
							.branches()
								.branch()
									.step()
										.operation(() -> new Step5())
										.returns("", String.class)
									.done()
								.done()
								.branch()
									.step()
										.operation(() -> new Step5())
										.returns("", String.class)
									.done()
								.done()
								.returns("", String.class)
							.done()
							.step()
								.operation(() -> new Step1())
							.done()
							.branches()
							.done()
						.done()
					.done()
				.build();
		//@formatter:on

		// When
		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
		assertThat(result).isNotNull().isEqualTo("");
	}
	
	@Test
	public void testc() {
		// Given
		//@formatter:off
		io.github.gear4jtest.core.model.test.Chain<String, Integer> pipe = 
				io.github.gear4jtest.core.model.test.Chain.<String>newChain()
					.assemble(
							new BranchesChain<String, String>()
								.branch()
									.step()
										.operation(() -> new Step1())
									.done()
									.returns("", Integer.class)
									.step()
										.operation(() -> new Step2())
									.done()
									.step()
										.operation(() -> new Step3())
									.done()
									.step()
										.operation(() -> new Step4("a").new Step4Map())
									.done()
									.branches()
										.branch()
											.step()
												.operation(() -> new Step5())
												.returns("", String.class)
											.done()
										.done()
										.branch()
											.step()
												.operation(() -> new Step5())
												.returns("", String.class)
											.done()
										.done()
									.done()
									.step()
										.operation(() -> new Step1())
									.done()
									.branches()
									.done()
								.done()
							.done())
				.build();
		//@formatter:on

		// When
		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
		assertThat(result).isNotNull().isEqualTo("");
	}

}
