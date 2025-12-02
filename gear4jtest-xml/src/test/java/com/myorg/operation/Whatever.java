// Généré automatiquement par XmlToJavaGeneratorV4
package com.myorg.operation;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;

import com.myorg.operation.Step10;
import com.myorg.operation.Step3;
import com.myorg.operation.Step8;
import com.myorg.operation.Step9;
import com.myorg.services.ModelsService;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.test.gear4test.xml.generator.GeneratedAssemblyLine;
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Générée automatiquement à partir de: assembly-line-iterator.xml
 */
public final class Whatever implements GeneratedAssemblyLine {
    private final ModelsService modelsService;

    /**
     * Constructeur avec injection de dépendances
     */
    public Whatever(ModelsService modelsService) {
        this.modelsService = modelsService;
    }

    /**
     * Crée l'opération de traitement 'step3'
     * @return l'opération configurée
     */
    private ProcessingOperationDefinition<String, Map<String, String>> createProcessingStep3() {
        return processingOperation("step3", Step3.class)
            .parameter(Step3::getParam, "a")
            .onError(ElementModelBuilders.<String>ignore(Exception.class)
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
    private ProcessingOperationDefinition<Map<String, String>, Integer> createProcessingStep8() {
        return processingOperation("step8", Step8.class)
            .build();
    }

    /**
     * Crée l'opération de traitement 'step9'
     * @return l'opération configurée
     */
    private ProcessingOperationDefinition<Integer, List<Integer>> createProcessingStep9() {
        return processingOperation("step9", Step9.class)
            .build();
    }

    /**
     * Crée l'itérateur 'iterator'
     * @return l'itérateur configuré
     */
    private IteratorDefinition<List<Integer>, List<List<String>>> createIteratorIterator() {
        return new IteratorIteratorOperations().create();
    }

    /**
     * Crée la configuration de l'AssemblyLine
     * @return la configuration
     */
    private AssemblyLineDefinition.Configuration createConfiguration() {
        return configuration()
            .eventHandling(eventHandling().build())
            .build();
    }

    /**
     * Crée l'AssemblyLine 'test'
     * @return l'AssemblyLine configurée
     */
    @Override
    public AssemblyLineDefinition<String, List<List<String>>> getAssemblyLineDefinition() {
        return ElementModelBuilders.<String>createAssemblyLine("test")
            .then(createProcessingStep3())
            .then(createProcessingStep8())
            .then(createProcessingStep9())
            .then(createIteratorIterator())
            .configuration(createConfiguration())
            .build();
    }

    /**
     * Opérations pour l'itérateur 'iterator'
     */
    private final class IteratorIteratorOperations {
        /**
         * Crée l'opération de traitement 'step10'
         * @return l'opération configurée
         */
        private ProcessingOperationDefinition<Integer, List<String>> createProcessingStep10() {
            return processingOperation("step10", Step10.class)
                .build();
        }

        IteratorDefinition<List<Integer>, List<List<String>>> create() {
            return ElementModelBuilders.<List<Integer>>iterate("iterator")
                .iterableFunction(java.util.function.Function.identity())
                .pipeline(chain(createProcessingStep10()).build())
                .collector(Collectors.toList())
                .build();
        }
    }
}
