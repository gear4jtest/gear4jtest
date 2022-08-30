package io.github.gear4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.junit.Test;

import io.github.gear4j.steps.Step1;
import io.github.gear4j.steps.Step2;
import io.github.gear4j.steps.Step3;
import io.github.gear4j.steps.Step4;
import io.github.gear4j.steps.Step5;
import io.github.gear4jtest.core.model.simple.Branch;
import io.github.gear4jtest.core.model.simple.Branches;
import io.github.gear4jtest.core.model.simple.Chain;
import io.github.gear4jtest.core.model.simple.Stepa;
import io.github.gear4jtest.core.service.ChainExecutorService;

public class SimpleChainBuilderTest {

	@Test
	public void simple_test() {
		// Given
		Chain<String, Integer> pipe = 
				Chain.<String>newChain()
					.assemble(
							Branches.<String>newBranches()
								.withBranch(
									Branch.<String>newBranch()
									.withStep(
											Stepa.<String>newStep()
											.operation(() -> new Step1())
											.build()
											)
								.build())
								.returns("", Integer.class)
							.build())
					.build();

		// When
		Object result = new ChainExecutorService().execute(pipe, "");

		// Then
		assertThat(result).isNotNull().isEqualTo(1);
	}
	
	@Test
	public void test() {
		// Given
		Chain<String, Integer> pipe = 
				Chain.<String>newChain()
					.assemble(
							Branches.<String>newBranches()
								.withBranch(
									Branch.<String>newBranch()
									.withStep(
											Stepa.<String>newStep()
											.operation(() -> new Step1())
											.build()
											)
									.withStep(
											Stepa.<Integer>newStep()
											.operation(() -> new Step2())
											.build()
											)
									.withStep(
											Stepa.<String>newStep()
											.operation(() -> new Step3())
											.build()
											)
									.withStep(
											Stepa.<Map<String, String>>newStep()
											.operation(() -> new Step4("a").new Step4Map())
											.build()
											)
									.withBranches(Branches.<Void>newBranches()
											.withBranch(Branch.<Void>newBranch()
													.withStep(
														Stepa.<Void>newStep()
														.operation(() -> new Step5())
														.build()
														)
													.build())
											.withBranch(Branch.<Void>newBranch()
													.withStep(
														Stepa.<Void>newStep()
														.operation(() -> new Step5())
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
		Chain<String, Integer> pipe = 
				Chain.<String>newChain()
					.assemble(
							Branches.<String>newBranches()
								.withBranch(
									Branch.<String>newBranch()
									.withStep(step1())
									.withStep(step2())
									.withStep(step3())
									.withStep(step4())
									.withBranches(Branches.<Void>newBranches()
											.withBranch(Branch.<Void>newBranch()
													.withStep(step5())
													.build())
											.withBranch(Branch.<Void>newBranch()
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

	private Stepa<Void, String> step5() {
		return Stepa.<Void>newStep()
		.operation(() -> new Step5())
		.build();
	}

	private Stepa<Map<String, String>, Void> step4() {
		return Stepa.<Map<String, String>>newStep()
				.operation(() -> new Step4("a").new Step4Map())
				.build();
	}

	private Stepa<String, Map<String, String>> step3() {
		return Stepa.<String>newStep()
				.operation(() -> new Step3())
				.build();
	}

	private Stepa<Integer, String> step2() {
		return Stepa.<Integer>newStep()
				.operation(() -> new Step2())
				.build();
	}

	private Stepa<String, Integer> step1() {
		return Stepa.<String>newStep()
				.operation(() -> new Step1())
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
