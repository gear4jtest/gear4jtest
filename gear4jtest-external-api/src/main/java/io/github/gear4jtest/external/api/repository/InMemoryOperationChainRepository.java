package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
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

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.identity.OperationChainIdentityCodec;
import io.github.gear4jtest.external.api.model.OperationChainObject;

/**
 * Thread-safe in-memory repository for object metadata, tags and staged atomic
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
    private static final Comparator<OperationChainPublicationStage> STAGE_ORDER = Comparator
            .comparing(OperationChainPublicationStage::stagedAt)
            .thenComparing(OperationChainPublicationStage::stageId);

    private long nextIdentifier;
    private final Map<PublicationKey, OperationChainObject> objects = new HashMap<>();
    private final Map<String, Set<String>> tagsByAssemblyLine = new HashMap<>();
    private final Map<String, OperationChainPublicationStage> stagesById = new HashMap<>();
    private final Map<PublicationKey, String> stageIdsByPublication = new HashMap<>();

    @Override
    public synchronized long insert(OperationChainObject object) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        PublicationKey key = PublicationKey.from(requiredObject);
        if (objects.containsKey(key)) {
            throw new OperationChainPublicationConflictException("Publication " + key + " already exists");
        }
        OperationChainObject stored = withIdentifier(requiredObject);
        objects.put(key, stored);
        return stored.id();
    }

    @Override
    public synchronized void publish(OperationChainObject object, List<String> tags) {
        OperationChainPublicationStage stage = stage(object, tags);
        commit(stage.stageId());
    }

    @Override
    public boolean supportsStaging() {
        return true;
    }

    @Override
    public synchronized OperationChainPublicationStage stage(OperationChainObject object,
                                                             List<String> tags,
                                                             String storeFingerprint) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        List<String> requiredTags = normalizedTags(tags);
        String requiredStoreFingerprint = requireStoreFingerprint(storeFingerprint);
        PublicationKey key = PublicationKey.from(requiredObject);

        OperationChainObject committed = objects.get(key);
        if (committed != null && !committed.contentIdentity().equals(requiredObject.contentIdentity())) {
            throw conflict(key);
        }

        String existingStageId = stageIdsByPublication.get(key);
        if (existingStageId != null) {
            OperationChainPublicationStage existing = stagesById.get(existingStageId);
            if (existing == null) {
                stageIdsByPublication.remove(key);
            } else {
                if (!existing.object().contentIdentity().equals(requiredObject.contentIdentity())
                        || !Objects.equals(existing.storeFingerprint(), requiredStoreFingerprint)) {
                    throw conflict(key);
                }
                List<String> mergedTags = mergeTags(existing.tags(), requiredTags);
                OperationChainPublicationStage renewed = new OperationChainPublicationStage(existing.stageId(),
                        existing.object(), mergedTags, requiredStoreFingerprint, Instant.now(),
                        existing.revision() + 1L);
                stagesById.put(existingStageId, renewed);
                return renewed;
            }
        }

        String stageId = OperationChainIdentityCodec.publicationStageId(requiredObject);
        OperationChainPublicationStage created = new OperationChainPublicationStage(stageId, requiredObject,
                requiredTags, requiredStoreFingerprint, Instant.now());
        stagesById.put(stageId, created);
        stageIdsByPublication.put(key, stageId);
        return created;
    }

    @Override
    public synchronized void commit(String stageId) {
        OperationChainPublicationStage stage = stagesById.get(requireStageId(stageId));
        if (stage == null) {
            return;
        }
        PublicationKey key = PublicationKey.from(stage.object());
        OperationChainObject existing = objects.get(key);
        if (existing != null && !existing.contentIdentity().equals(stage.object().contentIdentity())) {
            throw conflict(key);
        }
        if (existing == null) {
            objects.put(key, withIdentifier(stage.object()));
        }
        if (!stage.tags().isEmpty()) {
            tagsByAssemblyLine.computeIfAbsent(stage.object().alId(), ignored -> new TreeSet<>())
                    .addAll(stage.tags());
        }
        removeStage(stage);
    }

    @Override
    public synchronized void abort(String stageId) {
        OperationChainPublicationStage stage = stagesById.get(requireStageId(stageId));
        if (stage != null) {
            removeStage(stage);
        }
    }

    @Override
    public synchronized boolean abortIfUnchanged(OperationChainPublicationStage expectedStage) {
        OperationChainPublicationStage requiredStage = Objects.requireNonNull(expectedStage,
                                                                              "expectedStage must not be null");
        OperationChainPublicationStage current = stagesById.get(requiredStage.stageId());
        if (!requiredStage.equals(current)) {
            return false;
        }
        removeStage(current);
        return true;
    }

    @Override
    public synchronized List<OperationChainPublicationStage> findStagedBefore(Instant cutoff, PageRequest pageRequest) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return stagesById.values().stream()
                .filter(stage -> !stage.stagedAt().isAfter(cutoff))
                .sorted(STAGE_ORDER)
                .skip(pageRequest.offset())
                .limit(pageRequest.limit())
                .toList();
    }

    @Override
    public synchronized List<OperationChainPublicationStage> findStagedAfter(Instant cutoff,
                                                                             OperationChainPublicationStageCursor after,
                                                                             int limit) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        int requiredLimit = PageRequest.first(limit).limit();
        return stagesById.values().stream()
                .filter(stage -> !stage.stagedAt().isAfter(cutoff))
                .filter(stage -> after == null || isAfter(stage, after))
                .sorted(STAGE_ORDER)
                .limit(requiredLimit)
                .toList();
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
    public synchronized List<OperationChainObject> findAll(String assemblyLineId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return objects.values().stream()
                .filter(object -> Objects.equals(assemblyLineId, object.alId()))
                .sorted(PUBLICATION_ORDER.reversed())
                .skip(pageRequest.offset())
                .limit(pageRequest.limit())
                .toList();
    }

    @Override
    public synchronized List<OperationChainObject> findAllAfter(String assemblyLineId,
                                                                OperationChainObjectCursor after,
                                                                int limit) {
        int requiredLimit = PageRequest.first(limit).limit();
        return objects.values().stream()
                .filter(object -> Objects.equals(assemblyLineId, object.alId()))
                .filter(object -> after == null || isAfter(object, after))
                .sorted(PUBLICATION_ORDER.reversed())
                .limit(requiredLimit)
                .toList();
    }

    @Override
    public synchronized void addTag(String assemblyLineId, String tag) {
        tagsByAssemblyLine.computeIfAbsent(Objects.requireNonNull(assemblyLineId, "assemblyLineId must not be null"),
                                           ignored -> new TreeSet<>())
                .add(OperationChainPublicationTags.requireValidTag(tag));
    }

    @Override
    public synchronized void removeTag(String assemblyLineId, String tag) {
        String requiredTag = OperationChainPublicationTags.requireValidTag(tag);
        Set<String> tags = tagsByAssemblyLine.get(assemblyLineId);
        if (tags == null) {
            return;
        }
        tags.remove(requiredTag);
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
        String requiredTag = OperationChainPublicationTags.requireValidTag(tag);
        List<String> assemblyLineIds = new ArrayList<>();
        tagsByAssemblyLine.forEach((assemblyLineId, tags) -> {
            if (tags.contains(requiredTag)) {
                assemblyLineIds.add(assemblyLineId);
            }
        });
        assemblyLineIds.sort(String::compareTo);
        return List.copyOf(assemblyLineIds);
    }

    private void removeStage(OperationChainPublicationStage stage) {
        stagesById.remove(stage.stageId());
        stageIdsByPublication.remove(PublicationKey.from(stage.object()), stage.stageId());
    }

    private static boolean isAfter(OperationChainPublicationStage stage,
                                   OperationChainPublicationStageCursor cursor) {
        int timeComparison = stage.stagedAt().compareTo(cursor.stagedAt());
        return timeComparison > 0 || timeComparison == 0 && stage.stageId().compareTo(cursor.stageId()) > 0;
    }

    private static boolean isAfter(OperationChainObject object, OperationChainObjectCursor cursor) {
        int timeComparison = object.publishedAt().compareTo(cursor.publishedAt());
        return timeComparison < 0 || timeComparison == 0 && object.id() < cursor.id();
    }

    private OperationChainObject withIdentifier(OperationChainObject object) {
        return new OperationChainObject(++nextIdentifier, object.alId(), object.version(), object.mode(),
                object.contentHash(), object.sizeBytes(), object.mimeType(), object.createdAt(), object.createdBy(),
                object.publishedAt());
    }

    private static List<String> normalizedTags(List<String> tags) {
        return OperationChainPublicationTags.normalize(tags);
    }

    private static List<String> mergeTags(List<String> existing, List<String> additional) {
        return OperationChainPublicationTags.merge(existing, additional);
    }

    private static OperationChainPublicationConflictException conflict(PublicationKey key) {
        return new OperationChainPublicationConflictException(
                "Publication " + key.description() + " already exists with different content or metadata");
    }

    private static String requireStoreFingerprint(String storeFingerprint) {
        if (storeFingerprint == null || storeFingerprint.length() != 64) {
            throw new IllegalArgumentException("storeFingerprint must be a lowercase SHA-256 value");
        }
        return storeFingerprint;
    }

    private static String requireStageId(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId must not be blank");
        }
        return stageId;
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

        private String description() {
            return assemblyLineId + ":" + version + ":" + mode;
        }
    }
}
