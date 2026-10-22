package io.github.gear4jtest.studio.demo;

import java.util.UUID;

import io.github.gear4jtest.studio.model.CapturedValue;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DemoWiringTest {
    @Test
    void operatorFactoriesKeepParameterStateIsolatedAndRejectUnknownResources() {
        // Given: production operators are made afresh for each request.
        var resources = DemoWiring.plainResources();
        var first = resources.getResource(PrefixOperation.class);
        var second = resources.getResource(PrefixOperation.class);
        assertThat(first).isNotSameAs(second);
        assertThat(resources.getResource(String.class)).isNull();

        // When / Then: one injected parameter must not leak to a second operator.
        assertThat(first.transform("car", null)).isEqualTo("car");
        first.getPrefix().injectValue("demo:");
        assertThat(first.transform("car", null)).isEqualTo("demo:car");
        assertThat(second.transform("car", null)).isEqualTo("car");
        assertThat(resources.getResource(TrimOperation.class).transform("  value  ", null)).isEqualTo("value");
        assertThat(resources.getResource(UppercaseOperation.class).transform("i", null)).isEqualTo("I");
        assertThat(resources.getResource(LengthOperation.class).transform("value", null)).isEqualTo(5);
        assertThat(resources.getResource(FormatNumberOperation.class).transform(42, null)).isEqualTo("42");
    }

    @Test
    void resourceOperationUsesOnlyExplicitlyRegisteredSyntheticResources() {
        // Given
        var resources = DemoWiring.plainResources();
        var operator = resources.getResource(ResourceSuffixOperation.class);
        assertThatThrownBy(() -> operator.transform("text", null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Resource unavailable");

        // When / Then
        operator.getResource().injectValue("demo/suffix");
        assertThat(operator.transform("text", null)).isEqualTo("text [synthetic resource]");
        operator.getResource().injectValue("unknown/path");
        assertThatThrownBy(() -> operator.transform("text", null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Resource unavailable");
        assertThatThrownBy(() -> resources.getResource(ProductionOnlyOperation.class).transform("text", null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Must never execute from Studio");
    }

    @Test
    void captureModeMustBeExplicitAndNeverAcceptsUnknownSettings() {
        // Given
        assertThat(DemoWiring.capturePolicy(null).capture(DataCapturePolicy.Target.INPUT, "private"))
                .isEqualTo(CapturedValue.none());
        assertThat(DemoWiring.capturePolicy("none").capture(DataCapturePolicy.Target.OUTPUT, "private"))
                .isEqualTo(CapturedValue.none());

        // When / Then
        assertThat(DemoWiring.capturePolicy("masked").capture(DataCapturePolicy.Target.INPUT, "private"))
                .isEqualTo(new CapturedValue(CapturedValue.State.REDACTED, "[masqué]"));
        assertThat(DemoWiring.capturePolicy("synthetic").capture(DataCapturePolicy.Target.OUTPUT, "demo"))
                .isEqualTo(new CapturedValue(CapturedValue.State.CAPTURED, "demo"));
        assertThatThrownBy(() -> DemoWiring.capturePolicy("allowed"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown capture policy");
    }

    @Test
    void demoDatabaseCreatesAConstrainedLocalH2DataSourceWithoutOpeningIt() {
        // Given / When
        var source = (JdbcDataSource) DemoDatabase.fromEnvironment("coverage-" + UUID.randomUUID());

        // Then: constructing the data source must not open or migrate the database.
        assertThat(source.getURL()).startsWith("jdbc:h2:file:")
                .contains(";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000");
        assertThat(source.getUser()).isEqualTo("studio");
    }
}
