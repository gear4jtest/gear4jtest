/**
 * Experimental transport envelope SPI for forwarding Gear4J events outside the
 * in-process runtime.
 *
 * <p>
 * Using these contracts through the current in-memory event runtime remains
 * best-effort. Durable delivery requires a dedicated durable store and
 * dispatcher configuration.
 * </p>
 */
package io.github.gear4jtest.core.event.transport;
