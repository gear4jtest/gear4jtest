package io.github.gear4jtest.studio.management;

import java.util.List;

/** Opaque seek cursor; only authorized items contribute to a page. */
public record StudioPage<T>(List<T> items, String nextCursor) {
    public StudioPage {
        items = List.copyOf(items);
    }
}
