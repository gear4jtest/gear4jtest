/**
 * Experimental durable event/outbox building blocks.
 *
 * <p>
 * This package intentionally does not turn the in-memory {@code EventManager}
 * into a broker. It provides contracts and a dispatcher scaffold for future or
 * application-provided durable outbox integrations. APIs in this package should
 * be treated as experimental until a production store, retry policy and
 * dead-letter story are finalized.
 * </p>
 */
package io.github.gear4jtest.core.event.durable;
