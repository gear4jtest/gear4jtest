package io.github.gear4jtest.external.api.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;

/**
 * Thread-safe in-memory repository for object metadata, tags and atomic
 * publication.
 *
 * <p>
 * This implementation is intended for tests, samples and small single-process
 * deployments. State is not durable and is not shared across JVMs.
 * </p>
 */
public final class InMemoryOperationChainRepository
        implements OperationChainObjectRepository, OperationChainTagRepository, OperationChainPublicationRepository {
    private static final Comparator<OperationChainObject> PUBLICATION_ORDER = Comparator
            .comparing(OperationChainObject::publishedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(OperationChainObject::id, Comparator.nullsFirst(Comparator.naturalOrder()));

    private long nextIdentifier;
    private final Map<PublicationKey, OperationChainObject> objects = new HashMap<>();
    private final Map<String, Set<String>> tagsByAssemblyLine = new HashMap<>();

    @Override
    public synchronized long insert(OperationChainObject object) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        PublicationKey key = PublicationKey.from(requiredObject);
        if (objects.containsKey(key)) {
            throw new OperationChainPublicationConflictException(
                    "Publication " + key + " already exists");
        }
        OperationChainObject stored = withIdentifier(requiredObject);
        objects.put(key, stored);
        return stored.id();
    }

    @Override
    public synchronized void publish(OperationChainObject object, List<String> tags) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        List<String> requiredTags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
        PublicationKey key = PublicationKey.from(requiredObject);
        OperationChainObject existing = objects.get(key);
        if (existing != null && !samePublishedContent(existing, requiredObject)) {
            throw new OperationChainPublicationConflictException(
                    "Publication " + key + " already exists with different content or metadata");
        }

        if (existing == null) {
            objects.put(key, withIdentifier(requiredObject));
        }
        if (!requiredTags.isEmpty()) {
            tagsByAssemblyLine.computeIfAbsent(requiredObject.alId(), ignored -> new TreeSet<>())
                    .addAll(requiredTags);
        }
    }

    @Override
    public synchronized Optional<OperationChainObject> find(String assemblyLineId,
                                                            String version,
                                                            ExecutionMode mode) {
        return Optional.ofNullable(objects.get(new PublicationKey(assemblyLineId, version, mode)));
    }

    @Override
    public synchronized Optional<OperationChainObject> findLatestRun(String assemblyLineId) {
        return objects.values().stream()
                .filter(object -> Objects.equals(assemblyLineId, object.alId()))
                .filter(object -> object.mode() == ExecutionMode.RUN)
                .max(PUBLICATION_ORDER);
    }

    @Override
    public synchronized boolean exists(String assemblyLineId, String version, ExecutionMode mode) {
        return objects.containsKey(new PublicationKey(assemblyLineId, version, mode));
    }

    @Override
    public synchronized List<OperationChainObject> findAll(String assemblyLineId) {
        return objects.values().stream()
                .filter(object -> Objects.equals(assemblyLineId, object.alId()))
                .sorted(PUBLICATION_ORDER.reversed())
                .toList();
    }

    @Override
    public synchronized void addTag(String assemblyLineId, String tag) {
        tagsByAssemblyLine.computeIfAbsent(Objects.requireNonNull(assemblyLineId, "assemblyLineId must not be null"),
                                           ignored -> new TreeSet<>())
                .add(Objects.requireNonNull(tag, "tag must not be null"));
    }

    @Override
    public synchronized void removeTag(String assemblyLineId, String tag) {
        Set<String> tags = tagsByAssemblyLine.get(assemblyLineId);
        if (tags == null) {
            return;
        }
        tags.remove(tag);
        if (tags.isEmpty()) {
            tagsByAssemblyLine.remove(assemblyLineId);
        }
    }

    @Override
    public synchronized Set<String> listTags(String assemblyLineId) {
        Set<String> tags = tagsByAssemblyLine.get(assemblyLineId);
        return tags == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    @Override
    public synchronized List<String> findAssemblyLineIdsByTag(String tag) {
        Objects.requireNonNull(tag, "tag must not be null");
        List<String> assemblyLineIds = new ArrayList<>();
        tagsByAssemblyLine.forEach((assemblyLineId, tags) -> {
            if (tags.contains(tag)) {
                assemblyLineIds.add(assemblyLineId);
            }
        });
        assemblyLineIds.sort(String::compareTo);
        return List.copyOf(assemblyLineIds);
    }

    private OperationChainObject withIdentifier(OperationChainObject object) {
        return new OperationChainObject(++nextIdentifier, object.alId(), object.version(), object.mode(),
                object.contentHash(), object.sizeBytes(), object.mimeType(), object.createdAt(), object.createdBy(),
                object.publishedAt());
    }

    private static boolean samePublishedContent(OperationChainObject existing, OperationChainObject candidate) {
        return Objects.equals(existing.contentHash(), candidate.contentHash())
                && existing.sizeBytes() == candidate.sizeBytes()
                && Objects.equals(existing.mimeType(), candidate.mimeType());
    }

    private record PublicationKey(String assemblyLineId, String version, ExecutionMode mode) {
        private PublicationKey {
            Objects.requireNonNull(assemblyLineId, "assemblyLineId must not be null");
            Objects.requireNonNull(version, "version must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
        }

        private static PublicationKey from(OperationChainObject object) {
            return new PublicationKey(object.alId(), object.version(), object.mode());
        }

        @Override
        public String toString() {
            return assemblyLineId + ":" + version + ":" + mode;
        }
    }
}
