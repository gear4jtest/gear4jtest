// Généré automatiquement par XmlToJavaGeneratorV4
package com.myorg.assemblylines.generated;

import static io.github.gear4jtest.core.api.util.ElementModelBuilders.*;

import com.myorg.operation.Step10;
import com.myorg.operation.Step3;
import com.myorg.operation.Step8;
import com.myorg.operation.Step9;
import com.myorg.services.ModelsService;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.station.IteratorStation;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.test.gear4test.xml.generator.GeneratedAssemblyLine;
import java.util.List;
import java.util.Map;

/**
 * Classe générée pour l'assembly line {@code test}.
 */
public final class TestLine implements GeneratedAssemblyLine {
    private final ModelsService modelsService;

    /**
     * Constructeur avec injection de dépendances
     */
    public TestLine(ModelsService modelsService) {
        this.modelsService = modelsService;
    }

    /**
     * Crée l'opération de traitement 'step3'
     * @return l'opération configurée
     */
    private WorkStation<String, Map<String, String>> processStep3() {
        return processingOperation("step3", Step3.class)
                .parameter(Step3::getParam, "a")
                .onError(ElementModelBuilders.<String>ignore(java.lang.Exception.class)
                        .condition((input, ctx) -> ctx.getContext().containsKey("a"))
                        .action(() -> System.out.println("Error occurred!"))
                        .build())
                .conditional((input, ctx) -> input.equals(modelsService.getModel("fjeifj")))
                .transformer((input, ctx, exec) -> new HashMap<>())
                .build();
    }

    /**
     * Crée l'opération de traitement 'step8'
     * @return l'opération configurée
     */
    private WorkStation<Map<String, String>, Integer> processStep8() {
        return processingOperation("step8", Step8.class)
                .build();
    }

    /**
     * Crée l'opération de traitement 'step9'
     * @return l'opération configurée
     */
    private WorkStation<Integer, List<Integer>> processStep9() {
        return processingOperation("step9", Step9.class)
                .build();
    }

    /**
     * Crée l'opération de traitement 'step10'
     * @return l'opération configurée
     */
    private WorkStation<Integer, List<String>> processStep10() {
        return processingOperation("step10", Step10.class)
                .build();
    }

    private IteratorStation<List<Integer>, List<List<String>>> iterateIterator() {
        return ElementModelBuilders.<List<Integer>>iterate("iterator")
                .iterableFunction(java.util.function.Function.identity())
                .operation(processStep10())
                .collector(Collectors.toList())
                .build();
    }

    /**
     * Crée la configuration de l'AssemblyLine
     * @return la configuration
     */
    private AssemblyLine.Configuration createConfiguration() {
        return configuration()
                .stepDefaultConfiguration(operationConfiguration().build())
                .eventHandlingDefinition(eventHandling().build())
                .build();
    }

    /**
     * Crée l'AssemblyLine 'test'
     * @return l'AssemblyLine configurée
     */
    @Override
    public AssemblyLine<String, List<List<String>>> getAssemblyLineDefinition() {
        return ElementModelBuilders.<String>createAssemblyLine("test")
                .then(processStep3())
                .then(processStep8())
                .then(processStep9())
                .then(iterateIterator())
                .configuration(createConfiguration())
                .build();
    }
}
