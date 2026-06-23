package io.github.gear4jtest.core.api.station;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IteratorStationAccumulatorTest {
    @Test
    void listAccumulator_shouldProvideListCollection() {
        IteratorStation.ListAccumulator acc = new IteratorStation.ListAccumulator();

        Collection<Object> collection = acc.getCollectionSupplier().getSupplier().get();

        assertThat(collection).isInstanceOf(List.class).isEmpty();
    }

    @Test
    void setAccumulator_shouldProvideSetCollection() {
        IteratorStation.SetAccumulator acc = new IteratorStation.SetAccumulator();

        Collection<Object> collection = acc.getCollectionSupplier().getSupplier().get();

        assertThat(collection).isInstanceOf(Set.class).isEmpty();
    }
}
