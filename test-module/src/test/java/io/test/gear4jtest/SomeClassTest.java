package io.test.gear4jtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class SomeClassTest {

	@Test
	public void test() {
		// Given
		SomeClass some = new SomeClass();
		
		// When
		int result = some.add(2, 3);
		
		// Then
		assertThat(result).isEqualTo(5);
	}
	
}
