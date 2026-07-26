package io.github.gear4jtest.xml.capability;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.external.api.ExecutionMode;

/**
 * Resolves stable XML operator capability identifiers to application operator
 * classes.
 *
 * <p>
 * Restricted policies are deny-by-default and keep separate allowlists for
 * {@link ExecutionMode#TEST TEST} and {@link ExecutionMode#RUN RUN}. The XML
 * definition supplies the capability identifier in the
 * {@code processingOperation type} attribute; only trusted application
 * configuration chooses the corresponding Java class.
 * </p>
 *
 * <p>
 * {@link #trustedClassNames()} preserves the legacy trusted-source behavior in
 * which the XML attribute is itself a fully-qualified Java class name. It must
 * only be used for reviewed definitions because it is not a sandbox.
 * </p>
 */
public final class XmlOperatorCapabilityPolicy {
    private static final Pattern CAPABILITY_ID = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}");
    private static final Pattern JAVA_CLASS_NAME = Pattern
            .compile("[a-zA-Z_$][a-zA-Z\\d_$]*+(?:\\.[a-zA-Z_$][a-zA-Z\\d_$]*+)*+");
    private static final XmlOperatorCapabilityPolicy DENY_ALL = new XmlOperatorCapabilityPolicy(false, Map.of());
    private static final XmlOperatorCapabilityPolicy TRUSTED_CLASS_NAMES = new XmlOperatorCapabilityPolicy(true,
            Map.of());

    private final boolean classNamesTrusted;
    private final Map<ExecutionMode, Map<String, String>> allowedCapabilities;

    private XmlOperatorCapabilityPolicy(boolean classNamesTrusted,
                                        Map<ExecutionMode, Map<String, String>> allowedCapabilities) {
        this.classNamesTrusted = classNamesTrusted;
        this.allowedCapabilities = allowedCapabilities;
    }

    /**
     * Returns a restricted policy with no allowed capabilities.
     */
    public static XmlOperatorCapabilityPolicy denyAll() {
        return DENY_ALL;
    }

    /**
     * Returns an explicit trusted-source policy accepting Java class names from
     * XML.
     */
    public static XmlOperatorCapabilityPolicy trustedClassNames() {
        return TRUSTED_CLASS_NAMES;
    }

    /**
     * Starts a deny-by-default capability allowlist.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves one XML operator reference for the requested execution mode.
     *
     * @throws SecurityException when a restricted policy does not allow the
     *                           capability
     */
    public String resolve(String operatorReference, ExecutionMode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        if (classNamesTrusted) {
            return requireJavaClassName(operatorReference);
        }

        String capabilityId = requireRequestedCapabilityId(operatorReference, mode);
        String operatorClassName = allowedCapabilities.getOrDefault(mode, Map.of()).get(capabilityId);
        if (operatorClassName == null) {
            throw new SecurityException("XML operator capability '" + capabilityId
                    + "' is not allowed for execution mode " + mode);
        }
        return operatorClassName;
    }

    /**
     * Builds a mode-aware immutable capability allowlist.
     */
    public static final class Builder {
        private final EnumMap<ExecutionMode, Map<String, String>> allowedCapabilities = new EnumMap<>(
                ExecutionMode.class);

        private Builder() {
        }

        /**
         * Allows a capability in one or more explicitly selected modes.
         */
        public Builder allow(String capabilityId,
                             Class<? extends Operator<?, ?>> operatorType,
                             ExecutionMode firstMode,
                             ExecutionMode... additionalModes) {
            Objects.requireNonNull(operatorType, "operatorType must not be null");
            return allowClassName(capabilityId, operatorType.getName(), firstMode, additionalModes);
        }

        /**
         * Allows a capability class name in one or more explicitly selected modes.
         *
         * <p>
         * This variant is intended for trusted configuration layers, such as build
         * plugins, which cannot retain a consumer class literal. The generator still
         * verifies that the resolved class implements {@link Operator}.
         * </p>
         */
        public Builder allowClassName(String capabilityId,
                                      String operatorClassName,
                                      ExecutionMode firstMode,
                                      ExecutionMode... additionalModes) {
            String validCapabilityId = requireCapabilityId(capabilityId);
            String validClassName = requireJavaClassName(operatorClassName);
            register(validCapabilityId, validClassName, Objects.requireNonNull(firstMode,
                                                                               "firstMode must not be null"));
            if (additionalModes != null) {
                for (ExecutionMode mode : additionalModes) {
                    register(validCapabilityId, validClassName,
                             Objects.requireNonNull(mode, "additional mode must not be null"));
                }
            }
            return this;
        }

        /**
         * Allows the same operator class in TEST and RUN.
         */
        public Builder allowInAllModes(String capabilityId, Class<? extends Operator<?, ?>> operatorType) {
            return allow(capabilityId, operatorType, ExecutionMode.TEST, ExecutionMode.RUN);
        }

        public XmlOperatorCapabilityPolicy build() {
            EnumMap<ExecutionMode, Map<String, String>> snapshot = new EnumMap<>(ExecutionMode.class);
            allowedCapabilities
                    .forEach((mode, capabilities) -> snapshot.put(mode, Map.copyOf(capabilities)));
            return new XmlOperatorCapabilityPolicy(false, Map.copyOf(snapshot));
        }

        private void register(String capabilityId, String operatorClassName, ExecutionMode mode) {
            Map<String, String> capabilities = allowedCapabilities.computeIfAbsent(mode,
                                                                                   ignored -> new LinkedHashMap<>());
            String previous = capabilities.putIfAbsent(capabilityId, operatorClassName);
            if (previous != null && !previous.equals(operatorClassName)) {
                throw new IllegalArgumentException("XML operator capability '" + capabilityId
                        + "' is already mapped to " + previous + " for execution mode " + mode);
            }
        }
    }

    private static String requireCapabilityId(String capabilityId) {
        if (capabilityId == null || !CAPABILITY_ID.matcher(capabilityId).matches()) {
            throw new IllegalArgumentException("Invalid XML operator capability identifier: " + capabilityId);
        }
        return capabilityId;
    }

    private static String requireRequestedCapabilityId(String capabilityId, ExecutionMode mode) {
        if (capabilityId == null || !CAPABILITY_ID.matcher(capabilityId).matches()) {
            throw new SecurityException("XML operator reference '" + capabilityId
                    + "' is not a valid allowed capability identifier for execution mode " + mode);
        }
        return capabilityId;
    }

    private static String requireJavaClassName(String className) {
        if (className == null || !JAVA_CLASS_NAME.matcher(className).matches()) {
            throw new IllegalArgumentException("Invalid XML operator Java class name: " + className);
        }
        return className;
    }
}
