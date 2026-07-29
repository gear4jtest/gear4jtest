package io.github.gear4jtest.external.api.repository;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.persistence.PageRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationChainObjectRepositoryContractTest {
    @Test
    void versionListing_shouldExposeOneMandatoryBoundedOperation() {
        List<java.lang.reflect.Method> listingMethods = Arrays
                .stream(OperationChainObjectRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("findAll"))
                .toList();

        assertThat(listingMethods).hasSize(1);
        assertThat(Arrays.asList(listingMethods.get(0).getParameterTypes()))
                .isEqualTo(List.of(String.class, PageRequest.class));
        assertThat(Modifier.isAbstract(listingMethods.get(0).getModifiers())).isTrue();
    }
}
