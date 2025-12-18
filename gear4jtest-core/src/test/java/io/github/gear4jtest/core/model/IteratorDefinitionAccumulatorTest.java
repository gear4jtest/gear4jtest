package io.github.gear4jtest.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.core.model.IteratorDefinition;
import org.junit.jupiter.api.Test;

class IteratorDefinitionAccumulatorTest {

    @Test
    void listAccumulator_shouldProvideListCollection() {
        IteratorDefinition.ListAccumulator acc = new IteratorDefinition.ListAccumulator();

        Collection<Object> collection = acc.getCollectionSupplier().getSupplier().get();

        assertThat(collection)
                .isInstanceOf(List.class)
                .isEmpty();
    }

    @Test
    void setAccumulator_shouldProvideSetCollection() {
        IteratorDefinition.SetAccumulator acc = new IteratorDefinition.SetAccumulator();

        Collection<Object> collection = acc.getCollectionSupplier().getSupplier().get();

        assertThat(collection)
                .isInstanceOf(Set.class)
                .isEmpty();
    }
}
