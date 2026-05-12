// package io.test.gear4jtest.xml.compiler;
//
// import io.test.gear4test.xml.classloader.InMemoryClassLoader;
// import io.test.gear4test.xml.compiler.FinalRealJDTInMemoryCompiler;
// import org.junit.jupiter.api.Test;
//
// public class InMemoryCompilerTest {
//
// @Test
// void test() {
// FinalRealJDTInMemoryCompiler compiler = new FinalRealJDTInMemoryCompiler();
//
// String sourceCode = """
// public class JDTTestClass {
// private String message = "Compilé avec JDT en mémoire !";
//
// public void showInfo() {
// System.out.println("Message: " + message);
// System.out.println("Compilé avec: Eclipse JDT");
// }
// }""";
//
// try {
// System.out.println("=== Compilation avec JDT ===");
// var result = compiler.compileFromString("JDTTestClass", sourceCode);
//
// if (!result.isEmpty()) {
// System.out.println("✓ Compilation JDT réussie");
//
// // Création classloader
// InMemoryClassLoader classLoader = new InMemoryClassLoader();
// classLoader.addCompiledClasses(result);
//
// // Récupération des bytecodes
// byte[] bytecode = result.get("JDTTestClass");
// System.out.println("Taille bytecode: " + bytecode.length + " bytes");
//
// // Test de la classe
// classLoader.loadClass("JDTTestClass");
// Object instance = classLoader.createInstance("JDTTestClass");
// instance.getClass().getMethod("showInfo").invoke(instance);
//
// } else {
// System.err.println("✗ Échec compilation JDT");
// }
//
// } catch (Exception e) {
// e.printStackTrace();
// }
// }
// }
