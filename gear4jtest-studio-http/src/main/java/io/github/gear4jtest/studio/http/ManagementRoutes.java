package io.github.gear4jtest.studio.http;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gear4jtest.studio.json.DefinitionJson;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioExpectation;
import io.github.gear4jtest.studio.model.ScenarioInput;
import io.github.gear4jtest.studio.model.ScenarioTarget;
import io.github.gear4jtest.studio.service.StudioProblem;

import static io.github.gear4jtest.studio.json.DefinitionJson.*;

/** HTTP mapping shared by embedded hosts and remotely connected hosts. */
final class ManagementRoutes {
    private final StudioManagementService service;
    private final io.github.gear4jtest.studio.model.ChainDefinition sample;

    ManagementRoutes(StudioManagementService service, io.github.gear4jtest.studio.model.ChainDefinition sample) {
        this.service = service;
        this.sample = sample;
    }

    record Response(int status, Object value) {}

    Response handle(String method, String path, Map<String, String> query, JsonNode body) {
        var endpoint = ManagementEndpoint.match(method, path)
                .orElseThrow(() -> new StudioProblem(StudioProblem.Code.NOT_FOUND));
        String[] parts = path.substring(8).split("/", -1);
        UUID id = parts.length >= 2 ? UUID.fromString(parts[1]) : null;
        int size = query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 25;
        String after = query.get("after");
        boolean runQuery = endpoint == ManagementEndpoint.RESOURCES_RUNS_GET
                || endpoint == ManagementEndpoint.RESOURCES_METRICS_GET;
        var queryFields = runQuery
                ? Set.of("after", "limit", "from", "until", "status", "mode", "versionId", "runId",
                         "minimumDurationMillis")
                : Set.of("parent", "after", "limit");
        if (query.keySet().stream().anyMatch(k -> !queryFields.contains(k)))
            throw new IllegalArgumentException("Unknown query field");
        return switch (endpoint) {
            case CONTEXT -> ok(Map.of("context", service.context(), "identity", service.identity(),
                                      "canCreateDomain", service.canCreateDomain(), "permissions", Permission.values(),
                                      "sample", DefinitionJson.write(sample)));
            case CATALOG -> ok(service.catalog());
            case DOMAINS_LIST, CHAINS_LIST, DRAFTS_LIST -> ok(service.list(switch (endpoint) {
                case DOMAINS_LIST -> ResourceKind.DOMAIN;
                case CHAINS_LIST -> ResourceKind.CHAIN;
                default -> ResourceKind.DRAFT;
            }, query.containsKey("parent") ? UUID.fromString(query.get("parent")) : null, after, size));
            case DOMAINS_CREATE -> {
                fields(body, Set.of("key", "name", "description"));
                yield created(view(service.createDomain(text(body, "key"), text(body, "name"),
                                                        text(body, "description"))));
            }
            case CHAINS_CREATE -> {
                fields(body, Set.of("domainId", "key", "name", "description", "runtimeAssemblyLineId"));
                yield created(view(service.createChain(id(body, "domainId"), text(body, "key"), text(body, "name"),
                                                       text(body, "description"),
                                                       text(body, "runtimeAssemblyLineId"))));
            }
            case DRAFTS_CREATE -> {
                fields(body, Set.of("chainId", "name", "definition"));
                yield created(draft(service.createDraft(id(body, "chainId"), text(body, "name"),
                                                        DefinitionJson.read(body.path("definition")))));
            }
            case IMPORT -> {
                fields(body, Set.of("chainId", "name", "source"));
                yield created(draft(service.importDraft(id(body, "chainId"), text(body, "name"),
                                                        text(body, "source"))));
            }
            case RESOURCES_GET -> ok(view(service.resource(id)));
            case RESOURCES_PUT -> {
                fields(body, Set.of("expectedVersion", "name", "description"));
                yield ok(view(service.updateMetadata(id, number(body, "expectedVersion"), text(body, "name"),
                                                     text(body, "description"))));
            }
            case RESOURCES_ACCESS_GET -> ok(service.access(id));
            case RESOURCES_ACCESS_PUT -> {
                fields(body, Set.of("expectedVersion", "policy"));
                yield ok(view(service.setAccess(id, number(body, "expectedVersion"), policy(body.path("policy")))));
            }
            case RESOURCES_AUDIT_GET -> ok(service.audit(id, after, size));
            case RESOURCES_ARCHIVE_POST -> {
                fields(body, Set.of("expectedVersion"));
                yield ok(view(service.archive(id, number(body, "expectedVersion"))));
            }
            case RESOURCES_XML_PREVIEW_POST -> {
                fields(body, Set.of("source"));
                var definition = service.previewXml(id, text(body, "source"));
                yield ok(Map.of("definition", DefinitionJson.write(definition), "source",
                                service.export(id, definition)));
            }
            case RESOURCES_VALIDATE_POST, RESOURCES_EXPORT_POST, RESOURCES_DIAGNOSTICS_POST -> {
                fields(body, Set.of("definition"));
                var definition = DefinitionJson.read(body.path("definition"));
                if (endpoint == ManagementEndpoint.RESOURCES_EXPORT_POST)
                    yield ok(Map.of("source", service.export(id, definition), "mediaType", "application/xml"));
                var report = endpoint == ManagementEndpoint.RESOURCES_DIAGNOSTICS_POST ? service.analyze(id, definition)
                        : service.validate(id, definition);
                yield ok(Map.of("valid", report.valid(), "diagnostics", report.diagnostics(), "catalogFingerprint",
                                report.catalogFingerprint()));
            }
            case RESOURCES_RUNS_GET, RESOURCES_METRICS_GET -> {
                var filter = new io.github.gear4jtest.studio.model.RunFilter(instant(query, "from"),
                        instant(query, "until"),
                        query.get("status"), query.get("mode"),
                        query.containsKey("versionId") ? UUID.fromString(query.get("versionId")) : null,
                        query.containsKey("runId") ? UUID.fromString(query.get("runId")) : null,
                        query.containsKey("minimumDurationMillis") ? Long.valueOf(query.get("minimumDurationMillis"))
                                : null);
                yield ok(endpoint == ManagementEndpoint.RESOURCES_RUNS_GET ? service.runs(id, filter, after, size)
                        : service.metrics(id, filter));
            }
            case DRAFTS_GET -> ok(draft(service.draft(id)));
            case DRAFTS_PUT -> {
                fields(body, Set.of("expectedVersion", "definition"));
                yield ok(draft(service.saveDraft(id, number(body, "expectedVersion"),
                                                 DefinitionJson.read(body.path("definition")))));
            }
            case DRAFTS_SOURCE_PUT -> {
                fields(body, Set.of("expectedVersion", "mediaType", "source"));
                yield ok(draft(service
                        .saveSource(id, number(body, "expectedVersion"),
                                    new DraftRevision.Source(text(body, "mediaType"), text(body, "source")))));
            }
            case DRAFTS_VERSIONS_POST -> {
                fields(body, Set.of("revision", "label"));
                yield created(service.createVersion(id, number(body, "revision"), text(body, "label")));
            }
            case DRAFTS_DIFF_POST -> {
                fields(body, Set.of("revision", "definition"));
                yield ok(service.compare(id, number(body, "revision"), DefinitionJson.read(body.path("definition"))));
            }
            case DRAFTS_COMPARE_POST -> {
                fields(body, Set.of("fromRevision", "toRevision"));
                yield ok(service.compareRevisions(id, number(body, "fromRevision"), number(body, "toRevision")));
            }
            case DRAFTS_REVISIONS_GET -> {
                var page = service.revisions(id, after == null ? 0 : Long.parseLong(after), size);
                yield ok(new StudioPage<>(page.items().stream().map(ManagementRoutes::revision).toList(),
                        page.nextCursor()));
            }
            case DRAFTS_FORK_POST -> {
                fields(body, Set.of("revision", "name"));
                yield created(draft(service.forkDraft(id, number(body, "revision"), text(body, "name"))));
            }
            case DRAFTS_TESTS_POST -> {
                fields(body, Set.of("revision", "requestId", "input"));
                yield ok(service.test(id, number(body, "revision"), id(body, "requestId"), text(body, "input")));
            }
            case CHAINS_VERSIONS_GET -> ok(service.versions(id, after, size));
            case VERSIONS_GET -> ok(service.version(id));
            case VERSIONS_COMPARE_POST -> {
                fields(body, Set.of("otherVersionId"));
                yield ok(service.compareVersions(id, id(body, "otherVersionId")));
            }
            case VERSIONS_TESTS_POST -> {
                fields(body, Set.of("requestId", "input"));
                yield ok(service.testVersion(id, id(body, "requestId"), text(body, "input")));
            }
            case VERSIONS_PUBLISH_POST, VERSIONS_PROMOTE_POST, VERSIONS_REFRESH_POST -> {
                fields(body, Set.of());
                yield ok(switch (endpoint) {
                    case VERSIONS_PUBLISH_POST -> service.publishTest(id);
                    case VERSIONS_PROMOTE_POST -> service.promoteRun(id);
                    default -> service.refreshVersion(id);
                });
            }
            case RUNS_GET -> ok(service.run(id));
            case TESTS_GET -> ok(service.testRequest(id));
            case RESOURCES_SCENARIOS_GET -> ok(service.scenarios(id, after, size));
            case RESOURCES_SCENARIOS_POST -> {
                fields(body, Set.of("name", "data"));
                yield created(service.createScenario(id, text(body, "name"), scenarioData(body.path("data"))));
            }
            case SCENARIOS_GET -> ok(service.scenario(id));
            case SCENARIOS_PUT -> {
                fields(body, Set.of("expectedVersion", "name", "data"));
                yield ok(service.saveScenario(id, number(body, "expectedVersion"), text(body, "name"),
                                              scenarioData(body.path("data"))));
            }
            case SCENARIOS_REVISIONS_GET -> ok(service.scenarioRevisions(id, after, size));
            case SCENARIOS_ARCHIVE_POST -> {
                fields(body, Set.of("expectedVersion"));
                yield ok(service.archiveScenario(id, number(body, "expectedVersion")));
            }
            case SCENARIOS_TESTS_POST -> {
                fields(body, Set.of("revision", "target", "requestId"));
                yield ok(service.testScenario(id, number(body, "revision"), target(body.path("target")),
                                              id(body, "requestId")));
            }
            case SCENARIOS_SIMULATION_PLAN_POST -> {
                fields(body, Set.of("revision", "target"));
                var plan = service.planSimulation(id, number(body, "revision"), target(body.path("target")));
                yield ok(Map.of("valid", plan.valid(), "engineVersion", plan.engineVersion(), "catalogFingerprint",
                                plan.catalogFingerprint(),
                                "diagnostics", plan.diagnostics(), "substitutions", plan.substitutions()));
            }
            case SCENARIOS_SIMULATIONS_POST -> {
                fields(body, Set.of("revision", "target", "requestId"));
                yield ok(service.simulate(id, number(body, "revision"), target(body.path("target")),
                                          id(body, "requestId")));
            }
            case SCENARIOS_SIMULATIONS_GET -> ok(service.simulations(id, after, size));
            case SIMULATIONS_GET -> ok(service.simulation(id));
            case RUN_EVENTS -> throw new IllegalStateException("Local SSE is handled before JSON dispatch");
        };
    }

    private static ScenarioTarget target(JsonNode target) {
        fields(target, Set.of("draftId", "revision", "versionId"));
        return new ScenarioTarget(optional(target, "draftId") == null ? null : id(target, "draftId"),
                target.hasNonNull("revision") ? number(target, "revision") : null,
                optional(target, "versionId") == null ? null : id(target, "versionId"));
    }

    private static String optional(JsonNode value, String field) {
        return value.hasNonNull(field) ? text(value, field) : null;
    }

    private static ScenarioData scenarioData(JsonNode value) {
        fields(value, Set.of("input", "expected", "simulation"));
        var input = value.path("input");
        fields(input, Set.of("kind", "value", "fixtureVersion"));
        var fixture = new ScenarioInput(ScenarioInput.Kind.valueOf(text(input, "kind")), text(input, "value"),
                optional(input, "fixtureVersion"));
        ScenarioExpectation expected = null;
        if (value.hasNonNull("expected")) {
            var e = value.path("expected");
            fields(e, Set.of("outcome", "output"));
            expected = new ScenarioExpectation(text(e, "outcome"), optional(e, "output"));
        }
        return new ScenarioData(fixture, expected, simulationProfile(value.path("simulation")));
    }

    private static io.github.gear4jtest.studio.model.SimulationProfile simulationProfile(JsonNode value) {
        if (value.isMissingNode() || value.isNull())
            return null;
        fields(value, Set.of("schemaVersion", "mocks"));
        var nodes = value.path("mocks");
        if (!nodes.isObject() || nodes.size() > 32)
            throw new IllegalArgumentException("Invalid mock profile");
        var mocks = new java.util.TreeMap<String, io.github.gear4jtest.studio.model.SimulationProfile.Mock>();
        nodes.fields().forEachRemaining(entry -> {
            var mock = entry.getValue();
            fields(mock, Set.of("capability", "behavior", "type", "value"));
            mocks.put(entry.getKey(), new io.github.gear4jtest.studio.model.SimulationProfile.Mock(
                    text(mock, "capability"),
                    io.github.gear4jtest.studio.model.SimulationProfile.Behavior.valueOf(text(mock, "behavior")),
                    optional(mock, "type") == null ? null
                            : io.github.gear4jtest.studio.model.SimulationProfile.Scalar.valueOf(text(mock, "type")),
                    optional(mock, "value")));
        });
        return new io.github.gear4jtest.studio.model.SimulationProfile(Math.toIntExact(number(value, "schemaVersion")),
                mocks);
    }

    private static AccessPolicy policy(JsonNode value) {
        fields(value, Set.of("inheritAccess", "grants"));
        if (!value.path("inheritAccess").isBoolean() || !value.path("grants").isArray()
                || value.path("grants").size() > 64)
            throw new IllegalArgumentException("Invalid policy");
        var grants = new java.util.ArrayList<AccessGrant>();
        for (var grant : value.path("grants")) {
            fields(grant, Set.of("subjectType", "subject", "permissions"));
            if (!grant.path("permissions").isArray() || grant.path("permissions").size() > Permission.values().length)
                throw new IllegalArgumentException("Invalid permissions");
            var permissions = java.util.EnumSet.noneOf(Permission.class);
            for (var permission : grant.path("permissions")) {
                if (!permission.isTextual())
                    throw new IllegalArgumentException("Invalid permission");
                permissions.add(Permission.valueOf(permission.textValue()));
            }
            grants.add(new AccessGrant(AccessGrant.SubjectType.valueOf(text(grant, "subjectType")),
                    text(grant, "subject"), permissions));
        }
        return new AccessPolicy(value.path("inheritAccess").booleanValue(), grants);
    }

    private static UUID id(JsonNode value, String key) {
        return UUID.fromString(text(value, key));
    }

    private static java.time.Instant instant(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null)
            throw new IllegalArgumentException("Time window required");
        try {
            return java.time.Instant.parse(value);
        } catch (java.time.DateTimeException invalid) {
            throw new IllegalArgumentException("Invalid time window", invalid);
        }
    }

    private static Map<String, Object> view(ResourceView<? extends ManagedResource> view) {
        return Map.of("resource", view.resource(), "kind", view.resource().kind(), "permissions", view.permissions());
    }

    private static Map<String, Object> draft(DraftState state) {
        return Map.of("draft", state.draft(), "revision", revision(state.revision()), "permissions",
                      state.permissions());
    }

    private static Map<String, Object> revision(DraftRevision revision) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("draftId", revision.draftId());
        result.put("revisionId", revision.revisionId());
        result.put("number", revision.number());
        result.put("author", revision.author());
        result.put("savedAt", revision.savedAt());
        result.put("origin", revision.origin());
        result.put("kind", revision.kind());
        result.put("definition", revision.definition() == null ? null : DefinitionJson.write(revision.definition()));
        result.put("source", revision.source());
        return result;
    }

    private static Response ok(Object value) {
        return new Response(200, value);
    }

    private static Response created(Object value) {
        return new Response(201, value);
    }
}
