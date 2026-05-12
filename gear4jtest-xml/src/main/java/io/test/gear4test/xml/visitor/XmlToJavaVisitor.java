// package io.test.gear4test.xml.visitor;
//
// import com.palantir.javapoet.TypeSpec;
// import io.test.gear4jtest.xml.generated.*;
//
/// **
// * Visiteur pour la génération de code Java à partir d'éléments XML.
// * Compatible Java 17.
// */
// public interface XmlToJavaVisitor {
// TypeSpec visit(AssemblyLine assemblyLine, VisitorContext visitorContext)
// throws ClassNotFoundException;
// void visit(ProcessingOperationType operation, TypeSpec.Builder classBuilder,
// VisitorContext visitorContext) throws
// ClassNotFoundException;
// void visit(SignalType signal, TypeSpec.Builder classBuilder, VisitorContext
// visitorContext);
// void visit(IteratorType iterator, TypeSpec.Builder classBuilder,
// VisitorContext visitorContext);
// void visit(ContainerType container, TypeSpec.Builder classBuilder,
// VisitorContext visitorContext) throws
// ClassNotFoundException;
// void visit(IfElseContainerType ifElse, TypeSpec.Builder classBuilder,
// VisitorContext visitorContext) throws
// ClassNotFoundException;
// void visit(ConfigurationType configuration, TypeSpec.Builder classBuilder,
// VisitorContext visitorContext);
// }
