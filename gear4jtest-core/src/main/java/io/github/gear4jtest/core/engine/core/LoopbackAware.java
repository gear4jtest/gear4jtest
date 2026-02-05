package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.StationRunner;

public interface LoopbackAware {
    void setRootRunner(StationRunner rootRunner);
}