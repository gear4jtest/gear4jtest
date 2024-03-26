package io.github.gear4jtest.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gear4jtest.core.internal.AssemblyLineOrchestrator;
import io.github.gear4jtest.core.internal.ChainExecutorService;

@ExtendWith(MockitoExtension.class)
class SimpleTest {

	@InjectMocks
	private ChainExecutorService service;
	
	@Mock
	private AssemblyLineOrchestrator commander;
	
	@Test
	void test() {
		System.out.println(service);
	}
	
}
