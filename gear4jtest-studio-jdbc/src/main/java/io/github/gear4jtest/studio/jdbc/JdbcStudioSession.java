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
        String source = r.getString("definition_json");
        if (source == null || source.length() > 262144 || !hash(source).equals(r.getString("definition_hash")))
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        try {
            return new DraftRevision(uuid(r, "draft_id"), uuid(r, "id"), r.getLong("revision_number"),
                    r.getString("author"), instant(r, "saved_at"),
                    DraftRevision.Origin.valueOf(r.getString("origin")), DefinitionJson.read(JSON.readTree(source)));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new StudioProblem(STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void appendRevision(DraftRevision revision) {
        String json = json(DefinitionJson.write(revision.definition()));
        update("INSERT INTO g4js_revision(id,draft_id,revision_number,author,saved_at,origin,definition_json,definition_hash) VALUES(?,?,?,?,?,?,?,?)",
               revision.revisionId(), revision.draftId(), revision.number(), revision.author(), revision.savedAt(),
               revision.origin(), json, hash(json));
    }

    @Override
    public void appendAudit(AuditEvent event) {
        update("INSERT INTO g4js_audit(event_id,domain_id,chain_id,draft_id,resource_kind,resource_id,resource_version,actor,action,occurred_at,revision_id,request_id,access_before,access_after) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               event.eventId(), event.domainId(), event.chainId(), event.draftId(), event.resourceKind(),
               event.resourceId(), event.resourceVersion(), event.actor(), event.action(),
               event.occurredAt(), event.revisionId(), event.requestId(),
               event.accessBefore() == null ? null : json(event.accessBefore()),
               event.accessAfter() == null ? null : json(event.accessAfter()));
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
                policy(r.getString("access_before")), policy(r.getString("access_after")));
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
