package io.github.gear4jtest.xml.generator;

import java.util.Map;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;

record XmlGenerationContext(JavaImportManager imports,
                            XmlExpressionRenderer expressions,
                            Map<Operation, OperationSignature> signatures,
                            Map<ContainerOperation, String> parallelExecutorFields,
                            Map<String, Operation> emittedMethods) {}
