package io.test.gear4test.xml.generator;

import com.squareup.javapoet.*;
import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.test.gear4jtest.xml.generated.AssemblyLine;
import io.test.gear4jtest.xml.generated.Line;
import io.test.gear4test.xml.validator.AssemblyLineValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.InputSource;

import javax.lang.model.element.Modifier;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AssemblyLineGenerator {
    private static final Logger LOGGER = LogManager.getLogger("AssemblyLineGenerator");
    private static final Pattern GENERIC_TYPE_PATTERN = Pattern.compile("(?<type>[\\w.]+)<(?<nestedClass>[\\w.<>]+)>");

    public static TypeSpec generateFlatAssemblyLines(InputStream is) {
        ByteArrayOutputStream baos =  getByteArrayOutputStream(is);

        AssemblyLineValidator.FileParserReport report = AssemblyLineValidator.validate(new ByteArrayInputStream(baos.toByteArray()));
        if (report.getStatus() == AssemblyLineValidator.FileParserReport.Status.FAILED) {
            throw new IllegalArgumentException(report.getThrowable());
        }

        try {
            AssemblyLine line = unmarshal(new InputSource(new ByteArrayInputStream(baos.toByteArray())), AssemblyLine.class);
            return buildAssemblyLine(line.getName(), line.getDefinition().getLine());
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private static ByteArrayOutputStream getByteArrayOutputStream(InputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos;
        } catch (IOException e) {
            throw new RuntimeException("");
        }
    }

    /**
     * Unmarshal given content file to given type class.
     *
     * @param source the {@link InputStream} representing the XML Market
     * @return the wanted jaxb object or null if an error was encountered while unmarshalling object.
     * @throws JAXBException
     */
    public static <T> T unmarshal(InputSource source, Class<T> type) throws JAXBException {
        final JAXBContext jaxbContext = JAXBContext.newInstance(type);
        final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (T) unmarshaller.unmarshal(source);
    }

    private static TypeSpec buildAssemblyLine(String name, Line line) {
        Specs mainLineSpecs = buildLine(line, getTypeName(line.getStartingPoint()));

        TypeSpec.Builder assemblyLineClass = TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC);

        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        codeBuilder.add("return asssemblyLineDefinition($S))\n", name);
        codeBuilder.add("        .definition(MainLine.mainLine())\n");
        codeBuilder.add("        .build();");

        MethodSpec methodSpec = MethodSpec.methodBuilder("assemblyLine")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mainLineSpecs.typeSpec.get(0).methodSpecs.stream()
                            .filter(methodSpec1 -> methodSpec1.name.equals("mainLine"))
                            .map(methodSpec1 -> methodSpec1.returnType)
                            .findFirst().get())
                .addCode(codeBuilder.build())
                .build();
        assemblyLineClass.addMethod(methodSpec);
        assemblyLineClass.addTypes(mainLineSpecs.getTypeSpec());
        return assemblyLineClass.build();
    }

    private static Specs buildLine(Line line, TypeName inputType) {
        TypeSpec.Builder lineClass = TypeSpec.classBuilder(line.getName())
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        StringBuilder stringOperators = new StringBuilder();

        inputType = Optional.ofNullable(getTypeName(line.getStartingPoint())).orElse(inputType);
        TypeName chainedInputType = inputType;
        for (Object element : line.getOperators().getOperationOrContainer()) {
            Specs specs = buildElement(element, chainedInputType);

            lineClass.addMethods(specs.methodSpec);
            lineClass.addTypes(specs.typeSpec);

            stringOperators.append("        .operator(");
            stringOperators.append(specs.methodSpec.get(0).name);
            stringOperators.append("())\n");

            chainedInputType = specs.returnType;
        }

        ClassName pod = ClassName.get(LineDefinition.class.getPackage().getName(), LineDefinition.class.getSimpleName());
        ParameterizedTypeName ptn = ParameterizedTypeName.get(pod, inputType, chainedInputType);
        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        codeBuilder.add("return line(startingPointt($T.class))\n", inputType);
        codeBuilder.add(stringOperators.toString());
        codeBuilder.add("        .build();");
        MethodSpec methodSpec = MethodSpec.methodBuilder("mainLine")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ptn)
                .addCode(codeBuilder.build())
                .build();
        lineClass.addMethod(methodSpec);
        return new Specs(Arrays.asList(lineClass.build()), Arrays.asList(), chainedInputType);
    }

    private static Specs buildElement(Object element, TypeName returnType) {
        try {
            if (element instanceof Line.Operators.Operation) {
                return buildElement((Line.Operators.Operation) element);
            } else if (element instanceof Line.Operators.Container) {
                return buildElement((Line.Operators.Container) element, returnType);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error", e);
        }
        return null;
    }

    private static Specs buildElement(Line.Operators.Operation operation) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(operation.getType());
        ParameterizedType operationType = Arrays.stream(clazz.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(type -> type.getRawType() == Operation.class)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Operation class does not implements Operation"));
        Type[] argumentsTypes = operationType.getActualTypeArguments();

        String stepType = operation.getType().substring(operation.getType().lastIndexOf(".") + 1);

        ParameterizedTypeName ptn = ParameterizedTypeName.get(ProcessingOperationDefinition.class, argumentsTypes[0], argumentsTypes[1]);
        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        codeBuilder.add("return processingOperation($T.class)\n", clazz);
        operation.getParameters().getParameter().stream()
                .forEach(param -> codeBuilder.add("        .parameter($L, $S)\n", stepType + "::" + param.getRetriever(), param.getValue()));
        codeBuilder.add("        .build();");
        MethodSpec methodSpec = MethodSpec.methodBuilder(stepType.toLowerCase())
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ptn)
                .addCode(codeBuilder.build())
                .build();
        return new Specs(Arrays.asList(), Arrays.asList(methodSpec), getTypeName(argumentsTypes[1].getTypeName()));
    }

    private static Specs buildElement(Line.Operators.Container container, TypeName returnType) throws ClassNotFoundException {
        List<Specs> list = container.getSubLine().stream()
                .map(Line.Operators.Container.SubLine::getLine)
                .map(line -> buildLine(line, returnType))
                .collect(Collectors.toList());

        String containerReturnType = Optional.ofNullable(container.getReturns()).map(Line.Operators.Container.Returns::getJavaType).orElse("java.lang.Void");
        ClassName pod = ClassName.get(ContainerDefinition.class.getPackage().getName(), ContainerDefinition.class.getSimpleName());
        TypeName containerReturnTypeName = getTypeName(containerReturnType);
        ParameterizedTypeName ptn = ParameterizedTypeName.get(pod, returnType, containerReturnTypeName);

        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        codeBuilder.add("return container($L.class)\n", returnType);
        list.stream().map(Specs::getTypeSpec).flatMap(List::stream).forEach(type -> codeBuilder.add("        .withSubLine($L.mainLine())\n", type.name));
        if (container.getReturns() != null) {
            codeBuilder.add("        .returns($L)\n", container.getReturns().getExpression());
        }
        codeBuilder.add("        .build();");

        MethodSpec containerMethod = MethodSpec.methodBuilder("container")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ptn)
                .addCode(codeBuilder.build())
                .build();

        return new Specs(list.stream().map(Specs::getTypeSpec).flatMap(List::stream).collect(Collectors.toList()), Arrays.asList(containerMethod), containerReturnTypeName);
    }

    private static TypeName getTypeName(String typeString) {
        TypeName resultType = null;
        try {
            Matcher m = GENERIC_TYPE_PATTERN.matcher(typeString);
            if (m.matches()) {
                String type = m.group("type");
                String[] nestedTypes = m.group("nestedClass").split(", ?");
                List<TypeName> nestedTypesName = Arrays.asList(nestedTypes).stream().map(AssemblyLineGenerator::getTypeName).collect(Collectors.toList());
                resultType = ParameterizedTypeName.get(ClassName.get(Class.forName(type)), nestedTypesName.stream().toArray(TypeName[]::new));
            } else {
                resultType = ClassName.get(Class.forName(typeString));
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error while retrieving class", e);
        }
        return resultType;
    }

    private static class Specs {
        private List<TypeSpec> typeSpec;
        private List<MethodSpec> methodSpec;
        private TypeName returnType;

        public Specs(List<TypeSpec> typeSpec, List<MethodSpec> methodSpec, TypeName returnType) {
            this.typeSpec = typeSpec;
            this.methodSpec = methodSpec;
            this.returnType = returnType;
        }

        public List<TypeSpec> getTypeSpec() {
            return typeSpec;
        }

        public List<MethodSpec> getMethodSpec() {
            return methodSpec;
        }

        public TypeName getReturnType() {
            return returnType;
        }
    }
}
