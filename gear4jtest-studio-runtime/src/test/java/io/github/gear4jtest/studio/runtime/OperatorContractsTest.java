package io.github.gear4jtest.studio.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.studio.model.DataContract;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.studio.model.DataContract.Compatibility.*;
import static org.assertj.core.api.Assertions.*;

class OperatorContractsTest {
    abstract static class Parent<T> implements Operator<T, List<T>> {
    }

    abstract static class Concrete extends Parent<String> {
    }

    abstract static class Unresolved<T> extends Parent<T> {
    }

    abstract static class Numbers implements Operator<Integer, String> {
    }

    abstract static class Arrays implements Operator<String[], Object[]> {
    }

    abstract static class Nested implements Operator<List<String>, Map<String, List<Integer>>> {
    }

    abstract static class GenericNumbers implements Operator<List<Integer>, String> {
    }

    @Test
    void resolvesGenericInheritanceAndNestedTypesWithoutInstantiation() {
        var signature = OperatorContracts.resolve(Concrete.class);
        assertThat(signature.input().rawType()).isEqualTo(String.class);
        assertThat(signature.input().contract().origin()).isEqualTo(DataContract.Origin.INFERRED);
        assertThat(signature.output().contract().javaType()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(signature.output().contract().complete()).isTrue();
        assertThat(OperatorContracts.resolve(Nested.class).output().contract().javaType())
                .isEqualTo("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
    }

    @Test
    void distinguishesAssignableIncompatibleAndUnknownContracts() {
        var text = OperatorContracts.resolve(Concrete.class).input().contract();
        var number = OperatorContracts.resolve(Numbers.class).input().contract();
        assertThat(text.compatibilityWith(new DataContract("java.lang.CharSequence", null))).isEqualTo(COMPATIBLE);
        assertThat(text.compatibilityWith(number)).isEqualTo(INCOMPATIBLE);
        assertThat(OperatorContracts.resolve(Unresolved.class).input().contract().compatibilityWith(text))
                .isEqualTo(UNKNOWN);
        var arrays = OperatorContracts.resolve(Arrays.class);
        assertThat(arrays.input().contract().compatibilityWith(arrays.output().contract())).isEqualTo(COMPATIBLE);
        assertThat(OperatorContracts.resolve(Concrete.class).output().contract()
                .compatibilityWith(OperatorContracts.resolve(GenericNumbers.class).input().contract()))
                .isEqualTo(INCOMPATIBLE);
        assertThat(text.compatibilityWith(new DataContract("java.lang.String", "unverified-schema")))
                .isEqualTo(UNKNOWN);
    }

    private DataContract shape(Map<String, DataContract.Field> fields, boolean closed) {
        return new DataContract("java.util.Map", null, DataContract.Origin.DECLARED, true, Set.of(),
                new DataContract.Shape(fields, closed));
    }

    @Test
    void structuralRequiredOptionalAndUnknownFieldsAreConservative() {
        var text = OperatorContracts.resolve(Concrete.class).input().contract();
        var integer = OperatorContracts.resolve(Numbers.class).input().contract();
        assertThat(shape(Map.of("extra", new DataContract.Field(text, true)), true)
                .compatibilityWith(shape(Map.of(), true))).isEqualTo(INCOMPATIBLE);
        assertThat(shape(Map.of("extra", new DataContract.Field(text, false)), true)
                .compatibilityWith(shape(Map.of(), true))).isEqualTo(UNKNOWN);
        var expected = shape(Map.of("vin", new DataContract.Field(text, true)), false);
        assertThat(shape(Map.of("vin", new DataContract.Field(text, true)), true).compatibilityWith(expected))
                .isEqualTo(COMPATIBLE);
        assertThat(shape(Map.of(), true).compatibilityWith(expected)).isEqualTo(INCOMPATIBLE);
        assertThat(shape(Map.of(), false).compatibilityWith(expected)).isEqualTo(UNKNOWN);
        assertThat(shape(Map.of("vin", new DataContract.Field(text, false)), true).compatibilityWith(expected))
                .isEqualTo(UNKNOWN);
        assertThat(shape(Map.of("vin", new DataContract.Field(integer, true)), true).compatibilityWith(expected))
                .isEqualTo(INCOMPATIBLE);
    }
}
