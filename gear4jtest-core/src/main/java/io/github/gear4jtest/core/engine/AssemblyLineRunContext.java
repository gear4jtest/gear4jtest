package io.github.gear4jtest.core.engine;

import java.util.Map;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.factory.IdGenerator;

record AssemblyLineRunContext(ExecutionContext context,
                              AssemblyRunTrace execution,
                              Map<String, Object> effectiveContext,
                              IdGenerator effectiveGenerator) {}
