package io.github.gear4jtest.studio.jdbc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.studio.json.DefinitionJson;
import io.github.gear4jtest.studio.json.StudioJson;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioRef;
import io.github.gear4jtest.studio.model.ScenarioRevision;
import io.github.gear4jtest.studio.model.StudioPublication;
import io.github.gear4jtest.studio.model.StudioScenario;
import io.github.gear4jtest.studio.model.StudioVersion;
import io.github.gear4jtest.studio.service.StudioProblem;

import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

final class JdbcStudioSession implements StudioStore.Session {
    private static final ObjectMapper JSON = StudioJson.mapper();
    private final Connection connection;
    private final boolean readOnly;
    private boolean expired;

    JdbcStudioSession(Connection connection, boolean readOnly) {
        this.connection = connection;
        this.readOnly = readOnly;
    }

    void expire() {
        expired = true;
    }

    private void active() {
        if (expired)
            throw new IllegalStateException("Expired Studio transaction");
    }

    private void writable() {
        active();
        if (readOnly)
            throw new IllegalStateException("Read-only Studio transaction");
    }

    private PreparedStatement statement(String sql, Object... values) throws SQLException {
        active();
        var query = connection.prepareStatement(sql);
        try {
            query.setQueryTimeout(15);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof UUID id)
                    value = id.toString();
                if (value instanceof Enum<?> enumeration)
                    value = enumeration.name();
                if (value instanceof Instant instant)
                    value = instant.toEpochMilli();
                query.setObject(i + 1, value);
            }
            return query;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                query.close();
            } catch (SQLException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private int update(String sql, Object... values) {
        writable();
        try (var query = statement(sql, values)) {
            return query.executeUpdate();
        } catch (SQLException failure) {
            throw JdbcStudioStore.problem(failure);
        }
    }

    @Override
    public void lockDomain(UUID domainId) {
        writable();
        try (var query = statement("SELECT id FROM g4js_resource WHERE id=? AND kind='DOMAIN' FOR UPDATE", domainId);
                var rows = query.executeQuery()) {
            if (!rows.next())
                throw new StudioProblem(NOT_FOUND);
        } catch (SQLException failure) {
            throw JdbcStudioStore.problem(failure);
        }
    }

    void bindContext(String id) {
        var bound = one("SELECT context_id FROM g4js_context WHERE singleton=? FOR UPDATE", 1,
                        r -> java.util.Optional.ofNullable(r.getString("context_id")));
        if (bound == null)
            throw new IllegalStateException("Missing Studio context binding row");
        if (bound.isEmpty())
            update("UPDATE g4js_context SET context_id=? WHERE singleton=1", id);
        else if (!bound.get().equals(id))
            throw new IllegalStateException("Studio store belongs to a different runtime context");
    }

    @Override
    public ManagedResource resource(UUID id) {
        try (var query = statement("SELECT kind FROM g4js_resource WHERE id=?", id); var rows = query.executeQuery()) {
            if (!rows.next())
                return null;
            return switch (ResourceKind.valueOf(rows.getString(1))) {
                case DOMAIN ->
                    one("SELECT r.*,d.* FROM g4js_resource r JOIN g4js_domain d ON d.resource_id=r.id WHERE r.id=?", id,
                        this::domain);
                case CHAIN ->
                    one("SELECT r.*,c.* FROM g4js_resource r JOIN g4js_chain c ON c.resource_id=r.id WHERE r.id=?", id,
                        this::chain);
                case DRAFT ->
                    one("SELECT r.*,d.* FROM g4js_resource r JOIN g4js_draft d ON d.resource_id=r.id WHERE r.id=?", id,
                        this::draft);
            };
        } catch (SQLException failure) {
            throw JdbcStudioStore.problem(failure);
        }
    }

    @Override
    public List<StudioDomain> domains(String after, int limit) {
        return query("SELECT r.*,d.* FROM g4js_resource r JOIN g4js_domain d ON d.resource_id=r.id WHERE r.id>? ORDER BY r.id",
                     limit, this::domain, after == null ? "" : after);
    }

    @Override
    public List<StudioChain> chains(UUID domainId, String after, int limit) {
        String sql = "SELECT r.*,c.* FROM g4js_resource r JOIN g4js_chain c ON c.resource_id=r.id WHERE r.id>?";
        return domainId == null ? query(sql + " ORDER BY r.id", limit, this::chain, after == null ? "" : after)
                : query(sql + " AND c.domain_id=? ORDER BY r.id", limit, this::chain, after == null ? "" : after,
                        domainId);
    }

    @Override
    public List<StudioDraft> drafts(UUID chainId, String after, int limit) {
        String sql = "SELECT r.*,d.* FROM g4js_resource r JOIN g4js_draft d ON d.resource_id=r.id WHERE r.id>?";
        return chainId == null ? query(sql + " ORDER BY r.id", limit, this::draft, after == null ? "" : after)
                : query(sql + " AND d.chain_id=? ORDER BY r.id", limit, this::draft, after == null ? "" : after,
                        chainId);
    }

    private StudioDomain domain(ResultSet r) throws SQLException {
        return new StudioDomain(uuid(r, "id"), r.getString("resource_key"), r.getString("display_name"),
                r.getString("description"),
                r.getString("owner_subject"), r.getLong("entity_version"), r.getBoolean("archived"),
                instant(r, "created_at"), instant(r, "updated_at"));
    }

    private StudioChain chain(ResultSet r) throws SQLException {
        return new StudioChain(uuid(r, "id"), uuid(r, "domain_id"), r.getString("resource_key"),
                r.getString("runtime_assembly_line_id"),
                r.getString("display_name"), r.getString("description"),
                r.getString("owner_subject"), r.getLong("entity_version"), r.getBoolean("inherit_access"),
                r.getBoolean("archived"), instant(r, "created_at"), instant(r, "updated_at"));
    }

    private StudioDraft draft(ResultSet r) throws SQLException {
        return new StudioDraft(uuid(r, "id"), uuid(r, "chain_id"), r.getString("display_name"),
                r.getString("owner_subject"), r.getLong("entity_version"),
                r.getBoolean("inherit_access"), r.getBoolean("abandoned"), r.getLong("head_number"),
                DraftRevision.Origin.valueOf(r.getString("origin")),
                uuid(r, "base_revision_id"), instant(r, "created_at"), instant(r, "updated_at"));
    }

    @Override
    public void insert(ManagedResource value) {
        update("INSERT INTO g4js_resource(id,kind,owner_subject,inherit_access,entity_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
               value.id(), value.kind(), value.owner(), value.inheritAccess(), value.version(), value.createdAt(),
               value.updatedAt());
        if (value instanceof StudioDomain d)
            update("INSERT INTO g4js_domain(resource_id,resource_key,display_name,description,archived) VALUES(?,?,?,?,?)",
                   d.id(), d.key(), d.name(), d.description(), d.archived());
        else if (value instanceof StudioChain c)
            update("INSERT INTO g4js_chain(resource_id,domain_id,resource_key,runtime_assembly_line_id,display_name,description,archived) VALUES(?,?,?,?,?,?,?)",
                   c.id(), c.domainId(), c.key(), c.runtimeAssemblyLineId(), c.name(), c.description(), c.archived());
        else {
            var d = (StudioDraft) value;
            update("INSERT INTO g4js_draft(resource_id,chain_id,display_name,abandoned,head_number,origin,base_revision_id) VALUES(?,?,?,?,?,?,?)",
                   d.id(), d.chainId(), d.name(), d.abandoned(), d.head(), d.origin(), d.baseRevisionId());
        }
    }

    @Override
    public void update(ManagedResource value, long expectedVersion) {
        if (value.version() != expectedVersion + 1)
            throw new IllegalArgumentException("Version must advance once");
        int count = update("UPDATE g4js_resource SET inherit_access=?,entity_version=?,updated_at=? WHERE id=? AND entity_version=?",
                           value.inheritAccess(), value.version(), value.updatedAt(), value.id(), expectedVersion);
        if (count != 1)
            throw new StudioProblem(CONFLICT);
        if (value instanceof StudioDomain d)
            update("UPDATE g4js_domain SET display_name=?,description=?,archived=? WHERE resource_id=?", d.name(),
                   d.description(), d.archived(), d.id());
        else if (value instanceof StudioChain c)
            update("UPDATE g4js_chain SET display_name=?,description=?,archived=? WHERE resource_id=?", c.name(),
                   c.description(), c.archived(), c.id());
        else {
            var d = (StudioDraft) value;
            update("UPDATE g4js_draft SET display_name=?,abandoned=?,head_number=? WHERE resource_id=?", d.name(),
                   d.abandoned(), d.head(), d.id());
        }
    }

    @Override
    public List<AccessGrant> grants(UUID resourceId) {
        record Subject(AccessGrant.SubjectType type, String name) {}
        var result = new LinkedHashMap<Subject, EnumSet<Permission>>();
        try (var query = statement("SELECT subject_type,subject_id,permission FROM g4js_acl WHERE resource_id=? ORDER BY subject_type,subject_id,permission",
                                   resourceId);
                var rows = query.executeQuery()) {
            while (rows.next()) {
                var subject = new Subject(AccessGrant.SubjectType.valueOf(rows.getString(1)), rows.getString(2));
                result.computeIfAbsent(subject, k -> EnumSet.noneOf(Permission.class))
                        .add(Permission.valueOf(rows.getString(3)));
            }
            return result.entrySet().stream()
                    .map(e -> new AccessGrant(e.getKey().type(), e.getKey().name(), e.getValue())).toList();
        } catch (SQLException failure) {
            throw JdbcStudioStore.problem(failure);
        }
    }

    @Override
    public void replaceGrants(UUID resourceId, List<AccessGrant> grants) {
        update("DELETE FROM g4js_acl WHERE resource_id=?", resourceId);
        for (var grant : grants)
            for (var permission : grant.permissions())
                update("INSERT INTO g4js_acl(resource_id,subject_type,subject_id,permission) VALUES(?,?,?,?)",
                       resourceId, grant.subjectType(), grant.subject(), permission);
    }

    @Override
    public DraftRevision revision(UUID draftId, long number) {
        var result = query("SELECT * FROM g4js_revision WHERE draft_id=? AND revision_number=?", 1, this::revision,
                           draftId, number);
        return result.isEmpty() ? null : result.get(0);
    }

    @Override
    public List<DraftRevision> revisions(UUID draftId, long after, int limit) {
        return query("SELECT * FROM g4js_revision WHERE draft_id=? AND revision_number>? ORDER BY revision_number",
                     limit, this::revision, draftId, after);
    }

    private DraftRevision revision(ResultSet r) throws SQLException {
        try {
            var kind = DraftRevision.Kind.valueOf(r.getString("revision_kind"));
            String payload = r.getString(kind == DraftRevision.Kind.STRUCTURED ? "definition_json" : "source_text");
            String mediaType = r.getString("source_media_type");
            if (payload == null || payload.length() > 262144
                    || !revisionHash(kind, mediaType, payload).equals(r.getString("definition_hash")))
                throw new StudioProblem(STORAGE_UNAVAILABLE);
            return new DraftRevision(uuid(r, "draft_id"), uuid(r, "id"), r.getLong("revision_number"),
                    r.getString("author"), instant(r, "saved_at"), DraftRevision.Origin.valueOf(r.getString("origin")),
                    kind, kind == DraftRevision.Kind.STRUCTURED ? DefinitionJson.read(JSON.readTree(payload)) : null,
                    kind == DraftRevision.Kind.SOURCE ? new DraftRevision.Source(mediaType, payload) : null);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void appendRevision(DraftRevision revision) {
        String payload = revision.kind() == DraftRevision.Kind.STRUCTURED
                ? json(DefinitionJson.write(revision.definition())) : revision.source().text();
        String mediaType = revision.source() == null ? null : revision.source().mediaType();
        update("INSERT INTO g4js_revision(id,draft_id,revision_number,author,saved_at,origin,revision_kind,definition_json,source_text,source_media_type,definition_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
               revision.revisionId(), revision.draftId(), revision.number(), revision.author(), revision.savedAt(),
               revision.origin(), revision.kind(), revision.definition() == null ? null : payload,
               revision.source() == null ? null : payload, mediaType,
               revisionHash(revision.kind(), mediaType, payload));
    }

    private static String revisionHash(DraftRevision.Kind kind, String mediaType, String payload) {
        return hash(kind.name() + "\n" + (mediaType == null ? "" : mediaType) + "\n" + payload);
    }

    @Override
    public StudioScenario scenario(UUID id) {
        return one("SELECT * FROM g4js_scenario WHERE id=?", id, this::scenario);
    }

    private StudioScenario scenario(ResultSet r) throws SQLException {
        return new StudioScenario(uuid(r, "id"), uuid(r, "resource_id"), uuid(r, "chain_id"),
                r.getString("display_name"), r.getString("owner_subject"), r.getLong("entity_version"),
                r.getLong("head_number"), r.getBoolean("archived"), instant(r, "created_at"), instant(r, "updated_at"));
    }

    @Override
    public List<StudioScenario> scenarios(UUID scope, String after, int limit) {
        if (after == null)
            return query("SELECT * FROM g4js_scenario WHERE resource_id=? ORDER BY created_at DESC,id DESC",
                         limit, this::scenario, scope);
        var cursor = scenario(UUID.fromString(after));
        if (cursor == null || !cursor.resourceId().equals(scope))
            throw new StudioProblem(INVALID_REQUEST);
        return query("SELECT * FROM g4js_scenario WHERE resource_id=? AND (created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC",
                     limit, this::scenario, scope, cursor.createdAt(), cursor.createdAt(), cursor.id());
    }

    @Override
    public void insertScenario(StudioScenario s) {
        update("INSERT INTO g4js_scenario(id,resource_id,chain_id,display_name,owner_subject,entity_version,head_number,archived,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
               s.id(), s.resourceId(), s.chainId(), s.name(), s.owner(), s.version(), s.head(), s.archived(),
               s.createdAt(), s.updatedAt());
    }

    @Override
    public void updateScenario(StudioScenario s, long expectedVersion) {
        int changed = update("UPDATE g4js_scenario SET display_name=?,entity_version=?,head_number=?,archived=?,updated_at=? WHERE id=? AND entity_version=?",
                             s.name(), s.version(), s.head(), s.archived(), s.updatedAt(), s.id(), expectedVersion);
        if (changed != 1)
            throw new StudioProblem(CONFLICT);
    }

    @Override
    public void appendScenarioRevision(ScenarioRevision revision) {
        String data = json(revision.data());
        update("INSERT INTO g4js_scenario_revision(id,scenario_id,revision_number,author,saved_at,data_json,data_hash) VALUES(?,?,?,?,?,?,?)",
               revision.id(), revision.scenarioId(), revision.number(), revision.author(), revision.savedAt(), data,
               hash(data));
    }

    @Override
    public ScenarioRevision scenarioRevision(UUID id, long number) {
        var values = query("SELECT * FROM g4js_scenario_revision WHERE scenario_id=? AND revision_number=?", 1,
                           this::scenarioRevision, id, number);
        return values.isEmpty() ? null : values.get(0);
    }

    @Override
    public List<ScenarioRevision> scenarioRevisions(UUID id, long after, int limit) {
        return query("SELECT * FROM g4js_scenario_revision WHERE scenario_id=? AND revision_number>? ORDER BY revision_number",
                     limit, this::scenarioRevision, id, after);
    }

    private ScenarioRevision scenarioRevision(ResultSet r) throws SQLException {
        String data = r.getString("data_json");
        if (data == null || data.length() > 262144 || !hash(data).equals(r.getString("data_hash")))
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        try {
            return new ScenarioRevision(uuid(r, "scenario_id"), uuid(r, "id"), r.getLong("revision_number"),
                    r.getString("author"), instant(r, "saved_at"), JSON.readValue(data, ScenarioData.class));
        } catch (JsonProcessingException invalid) {
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void insertVersion(StudioVersion version) {
        update("INSERT INTO g4js_version(id,chain_id,draft_id,revision_id,revision_number,runtime_assembly_line_id,version_label,author,created_at,runtime_context_id,application_build,catalog_fingerprint,media_type,artifact_hash,source_text,definition_schema_version,format_adapter_id,format_adapter_version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               version.id(), version.chainId(), version.draftId(), version.revisionId(), version.revision(),
               version.runtimeAssemblyLineId(), version.label(), version.author(), version.createdAt(),
               version.runtimeContextId(), version.applicationBuild(), version.catalogFingerprint(),
               version.mediaType(), version.artifactHash(), version.source(), version.definitionSchemaVersion(),
               version.formatAdapterId(), version.formatAdapterVersion());
    }

    @Override
    public StudioVersion version(UUID id) {
        var values = query("SELECT * FROM g4js_version WHERE id=?", 1, this::version, id);
        return values.isEmpty() ? null : values.get(0);
    }

    @Override
    public List<StudioVersion> versions(UUID chainId, String after, int limit) {
        if (after == null)
            return query("SELECT * FROM g4js_version WHERE chain_id=? ORDER BY created_at DESC,id DESC", limit,
                         this::version, chainId);
        var cursor = version(UUID.fromString(after));
        if (cursor == null || !cursor.chainId().equals(chainId))
            throw new StudioProblem(INVALID_REQUEST);
        return query("SELECT * FROM g4js_version WHERE chain_id=? AND (created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC",
                     limit, this::version, chainId, cursor.createdAt(), cursor.createdAt(), cursor.id());
    }

    private StudioVersion version(ResultSet r) throws SQLException {
        String source = r.getString("source_text");
        if (source == null || source.length() > 262144 || !hash(source).equals(r.getString("artifact_hash")))
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        return new StudioVersion(uuid(r, "id"), uuid(r, "chain_id"), uuid(r, "draft_id"), uuid(r, "revision_id"),
                r.getLong("revision_number"), r.getString("runtime_assembly_line_id"), r.getString("version_label"),
                r.getString("author"), instant(r, "created_at"), r.getString("runtime_context_id"),
                r.getString("application_build"), r.getString("catalog_fingerprint"), r.getString("media_type"),
                r.getString("artifact_hash"), source, r.getInt("definition_schema_version"),
                r.getString("format_adapter_id"), r.getString("format_adapter_version"));
    }

    @Override
    public StudioPublication publication(UUID versionId, StudioPublication.Mode mode) {
        var values = query("SELECT * FROM g4js_publication WHERE version_id=? AND execution_mode=?", 1,
                           r -> new StudioPublication(uuid(r, "version_id"),
                                   StudioPublication.Mode.valueOf(r.getString("execution_mode")),
                                   StudioPublication.State.valueOf(r.getString("result_state")),
                                   r.getString("requested_by"),
                                   instant(r, "requested_at"), nullableInstant(r, "completed_at"),
                                   r.getString("published_by"),
                                   nullableInstant(r, "published_at"), r.getString("error_code"),
                                   uuid(r, "attempt_id")),
                           versionId, mode);
        return values.isEmpty() ? null : values.get(0);
    }

    private static Instant nullableInstant(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column);
        return r.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    @Override
    public void savePublication(StudioPublication value) {
        // Callers hold the domain lock, including across service instances.
        if (publication(value.versionId(), value.mode()) == null)
            update("INSERT INTO g4js_publication(version_id,execution_mode,result_state,requested_by,requested_at,completed_at,published_by,published_at,error_code,attempt_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                   value.versionId(), value.mode(), value.state(), value.requestedBy(), value.requestedAt(),
                   value.completedAt(), value.publishedBy(), value.publishedAt(), value.errorCode(), value.attemptId());
        else
            update("UPDATE g4js_publication SET result_state=?,completed_at=?,published_by=?,published_at=?,error_code=?,attempt_id=?,requested_by=?,requested_at=? WHERE version_id=? AND execution_mode=?",
                   value.state(), value.completedAt(), value.publishedBy(), value.publishedAt(), value.errorCode(),
                   value.attemptId(), value.requestedBy(), value.requestedAt(), value.versionId(), value.mode());
    }

    @Override
    public void appendAudit(AuditEvent event) {
        update("INSERT INTO g4js_audit(event_id,domain_id,chain_id,draft_id,resource_kind,resource_id,resource_version,actor,action,occurred_at,revision_id,request_id,access_before,access_after,version_id,attempt_id,scenario_id,scenario_revision_id,scenario_revision_number) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               event.eventId(), event.domainId(), event.chainId(), event.draftId(), event.resourceKind(),
               event.resourceId(), event.resourceVersion(), event.actor(), event.action(),
               event.occurredAt(), event.revisionId(), event.requestId(),
               event.accessBefore() == null ? null : json(event.accessBefore()),
               event.accessAfter() == null ? null : json(event.accessAfter()), event.versionId(), event.attemptId(),
               event.scenario() == null ? null : event.scenario().scenarioId(),
               event.scenario() == null ? null : event.scenario().revisionId(),
               event.scenario() == null ? null : event.scenario().revision());
    }

    @Override
    public io.github.gear4jtest.studio.model.StudioRun run(UUID id) {
        return one("SELECT snapshot_json FROM g4js_run WHERE id=?", id, this::run);
    }

    @Override
    public io.github.gear4jtest.studio.model.StudioRun lockRun(UUID id) {
        writable();
        return one("SELECT snapshot_json FROM g4js_run WHERE id=? FOR UPDATE", id, this::run);
    }

    @Override
    public List<UUID> staleCandidates(StudioStore.InstantCutoff cutoff) {
        return query("SELECT id FROM g4js_run WHERE stale_at IS NULL AND updated_at<? AND result_state IN ('PREPARING','RUNNING') ORDER BY updated_at,id",
                     cutoff.limit(), r -> uuid(r, "id"), cutoff.before());
    }

    @Override
    public int purgeStaleRuns(StudioStore.InstantCutoff cutoff) {
        var ids = query("SELECT id FROM g4js_run WHERE stale_at<? AND result_state IN ('PREPARING','RUNNING') ORDER BY stale_at,id",
                        cutoff.limit(), r -> uuid(r, "id"), cutoff.before());
        int removed = 0;
        for (var id : ids)
            removed += update("DELETE FROM g4js_run WHERE id=? AND stale_at<? AND result_state IN ('PREPARING','RUNNING')",
                              id, cutoff.before());
        return removed;
    }

    private io.github.gear4jtest.studio.model.StudioRun run(ResultSet row) throws SQLException {
        String source = row.getString("snapshot_json");
        if (source == null || source.length() > 262144)
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        try {
            return JSON.readValue(source, io.github.gear4jtest.studio.model.StudioRun.class);
        } catch (JsonProcessingException invalid) {
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public List<io.github.gear4jtest.studio.model.StudioRun> runs(UUID chainId,
                                                                  UUID draftId,
                                                                  io.github.gear4jtest.studio.model.RunFilter filter,
                                                                  String after,
                                                                  int limit) {
        var sql = new StringBuilder(
                "SELECT snapshot_json FROM g4js_run WHERE chain_id=? AND created_at>=? AND created_at<?");
        var values = new ArrayList<Object>(List.of(chainId, filter.from(), filter.until()));
        if (draftId != null) {
            sql.append(" AND resource_id=?");
            values.add(draftId);
        }
        if (filter.status() != null) {
            sql.append(" AND result_state=?");
            values.add(filter.status());
        }
        if (filter.mode() != null) {
            sql.append(" AND execution_mode=?");
            values.add(filter.mode());
        }
        if (filter.versionId() != null) {
            sql.append(" AND version_id=?");
            values.add(filter.versionId());
        }
        if (filter.engineRunId() != null) {
            sql.append(" AND engine_run_id=?");
            values.add(filter.engineRunId());
        }
        if (filter.minimumDurationMillis() != null) {
            sql.append(" AND ended_at-started_at>=?");
            values.add(filter.minimumDurationMillis());
        }
        if (after != null) {
            String[] cursor = after.split(":", -1);
            if (cursor.length != 2)
                throw new StudioProblem(INVALID_REQUEST);
            long created = Long.parseLong(cursor[0]);
            UUID id = UUID.fromString(cursor[1]);
            sql.append(" AND (created_at<? OR (created_at=? AND id<?))");
            values.add(created);
            values.add(created);
            values.add(id);
        }
        sql.append(" ORDER BY created_at DESC,id DESC");
        return query(sql.toString(), limit, this::run, values.toArray());
    }

    @Override
    public void saveRun(io.github.gear4jtest.studio.model.StudioRun value) {
        var observation = value.observation();
        UUID engineId = observation == null ? null : observation.runId();
        String snapshot = json(value);
        if (snapshot.length() > 262144)
            throw new StudioProblem(INVALID_REQUEST);
        if (run(value.id()) == null)
            update("INSERT INTO g4js_run(id,resource_id,chain_id,version_id,engine_run_id,execution_mode,result_state,created_at,started_at,ended_at,snapshot_json,updated_at,stale_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                   value.id(), value.resourceId(), value.chainId(), value.provenance().versionId(), engineId,
                   value.provenance().mode(), value.status(), value.createdAt(), value.startedAt(), value.endedAt(),
                   snapshot, value.updatedAt(), value.staleAt());
        else
            update("UPDATE g4js_run SET engine_run_id=?,result_state=?,started_at=?,ended_at=?,snapshot_json=?,updated_at=?,stale_at=? WHERE id=?",
                   engineId, value.status(), value.startedAt(), value.endedAt(), snapshot, value.updatedAt(),
                   value.staleAt(), value.id());
    }

    @Override
    public int purgeRuns(StudioStore.InstantCutoff cutoff) {
        var ids = query("SELECT id FROM g4js_run WHERE ended_at<? AND result_state NOT IN ('RUNNING','PREPARING') ORDER BY ended_at,id",
                        cutoff.limit(), row -> uuid(row, "id"), cutoff.before());
        int deleted = 0;
        for (var id : ids)
            deleted += update("DELETE FROM g4js_run WHERE id=? AND ended_at<? AND result_state NOT IN ('RUNNING','PREPARING')",
                              id, cutoff.before());
        return deleted;
    }

    @Override
    public List<AuditEvent> audit(UUID domainId, UUID chainId, UUID draftId, long after, int limit) {
        String sql = "SELECT * FROM g4js_audit WHERE domain_id=? AND sequence_id>?";
        if (draftId != null)
            return query(sql + " AND draft_id=? ORDER BY sequence_id", limit, this::audit, domainId, after, draftId);
        if (chainId != null)
            return query(sql + " AND chain_id=? ORDER BY sequence_id", limit, this::audit, domainId, after, chainId);
        return query(sql + " ORDER BY sequence_id", limit, this::audit, domainId, after);
    }

    private AuditEvent audit(ResultSet r) throws SQLException {
        return new AuditEvent(r.getLong("sequence_id"), uuid(r, "event_id"), uuid(r, "domain_id"), uuid(r, "chain_id"),
                uuid(r, "draft_id"),
                ResourceKind.valueOf(r.getString("resource_kind")), uuid(r, "resource_id"),
                r.getLong("resource_version"), r.getString("actor"),
                AuditEvent.Action.valueOf(r.getString("action")), instant(r, "occurred_at"), uuid(r, "revision_id"),
                uuid(r, "request_id"),
                policy(r.getString("access_before")), policy(r.getString("access_after")), uuid(r, "version_id"),
                uuid(r, "attempt_id"), uuid(r, "scenario_id") == null ? null
                        : new ScenarioRef(uuid(r, "scenario_id"), uuid(r, "scenario_revision_id"),
                                r.getLong("scenario_revision_number")));
    }

    private static String json(Object value) {
        try {
            String result = JSON.writeValueAsString(value);
            if (result.length() > 262144)
                throw new StudioProblem(INVALID_REQUEST);
            return result;
        } catch (JsonProcessingException failure) {
            throw new StudioProblem(INVALID_REQUEST);
        }
    }

    private static AccessPolicy policy(String source) {
        if (source == null)
            return null;
        if (source.length() > 262144)
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        try {
            return JSON.readValue(source, AccessPolicy.class);
        } catch (JsonProcessingException failure) {
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        }
    }

    private static String hash(String source) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static UUID uuid(ResultSet r, String name) throws SQLException {
        String value = r.getString(name);
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant instant(ResultSet r, String name) throws SQLException {
        return Instant.ofEpochMilli(r.getLong(name));
    }

    private <T> T one(String sql, Object id, RowMapper<T> mapper) {
        var values = query(sql, 1, mapper, id);
        return values.isEmpty() ? null : values.get(0);
    }

    private <T> List<T> query(String sql, int limit, RowMapper<T> mapper, Object... values) {
        if (limit < 1 || limit > 101)
            throw new IllegalArgumentException("Query limit outside bounds");
        try (var query = statement(sql, values)) {
            query.setMaxRows(limit);
            try (var rows = query.executeQuery()) {
                var result = new ArrayList<T>();
                while (rows.next())
                    result.add(mapper.map(rows));
                return List.copyOf(result);
            }
        } catch (SQLException failure) {
            throw JdbcStudioStore.problem(failure);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet result) throws SQLException;
    }
}
