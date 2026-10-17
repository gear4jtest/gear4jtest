package io.github.gear4jtest.studio.demo;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gear4jtest.studio.json.DefinitionJson;
import io.github.gear4jtest.studio.management.*;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.service.StudioProblem;

import static io.github.gear4jtest.studio.json.DefinitionJson.*;

/** Demo HTTP mapping: identity is absent from all request DTOs. */
final class ManagementRoutes {
    private final StudioManagementService service;

    ManagementRoutes(StudioManagementService service) {
        this.service = service;
    }

    record Response(int status, Object value) {}

    Response handle(String method, String path, Map<String, String> query, JsonNode body) {
        if (!path.startsWith("/api/v1/"))
            throw new StudioProblem(StudioProblem.Code.NOT_FOUND);
        String[] parts = path.substring(8).split("/", -1);
        int size = query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 25;
        String after = query.get("after");
        if (query.keySet().stream().anyMatch(k -> !Set.of("parent", "after", "limit").contains(k)))
            throw new IllegalArgumentException("Unknown query field");
        if (parts.length == 1) {
            String kind = parts[0];
            if (method.equals("GET") && kind.equals("context"))
                return ok(Map.of("context", service.context(), "identity", service.identity(),
                                 "canCreateDomain", service.canCreateDomain(), "permissions", Permission.values(),
                                 "sample", DefinitionJson.write(DemoWiring.sample())));
            if (method.equals("GET") && kind.equals("catalog"))
                return ok(service.catalog());
            if (method.equals("GET") && Set.of("domains", "chains", "drafts").contains(kind)) {
                ResourceKind resourceKind = switch (kind) {
                    case "domains" -> ResourceKind.DOMAIN;
                    case "chains" -> ResourceKind.CHAIN;
                    default -> ResourceKind.DRAFT;
                };
                return ok(service.list(resourceKind,
                                       query.containsKey("parent") ? UUID.fromString(query.get("parent")) : null, after,
                                       size));
            }
            if (method.equals("POST") && kind.equals("domains")) {
                fields(body, Set.of("key", "name", "description"));
                return created(view(service.createDomain(text(body, "key"), text(body, "name"),
                                                         text(body, "description"))));
            }
            if (method.equals("POST") && kind.equals("chains")) {
                fields(body, Set.of("domainId", "key", "name", "description", "runtimeAssemblyLineId"));
                return created(view(service.createChain(id(body, "domainId"), text(body, "key"), text(body, "name"),
                                                        text(body, "description"),
                                                        text(body, "runtimeAssemblyLineId"))));
            }
            if (method.equals("POST") && kind.equals("drafts")) {
                fields(body, Set.of("chainId", "name", "definition"));
                return created(draft(service.createDraft(id(body, "chainId"), text(body, "name"),
                                                         DefinitionJson.read(body.path("definition")))));
            }
            if (method.equals("POST") && kind.equals("import")) {
                fields(body, Set.of("chainId", "name", "source"));
                return created(draft(service.importDraft(id(body, "chainId"), text(body, "name"),
                                                         text(body, "source"))));
            }
        }
        if (parts.length >= 2) {
            UUID id = UUID.fromString(parts[1]);
            if (parts[0].equals("chains") && parts.length == 3 && parts[2].equals("versions") && method.equals("GET"))
                return ok(service.versions(id, after, size));
            if (parts[0].equals("versions")) {
                if (parts.length == 2 && method.equals("GET"))
                    return ok(service.version(id));
                if (parts.length == 3 && method.equals("POST")) {
                    if (parts[2].equals("compare")) {
                        fields(body, Set.of("otherVersionId"));
                        return ok(service.compareVersions(id, id(body, "otherVersionId")));
                    }
                    if (parts[2].equals("tests")) {
                        fields(body, Set.of("requestId", "input"));
                        return ok(service.testVersion(id, id(body, "requestId"), text(body, "input")));
                    }
                    fields(body, Set.of());
                    return switch (parts[2]) {
                        case "publish" -> ok(service.publishTest(id));
                        case "promote" -> ok(service.promoteRun(id));
                        case "refresh" -> ok(service.refreshVersion(id));
                        default -> throw new StudioProblem(StudioProblem.Code.NOT_FOUND);
                    };
                }
            }
            if (parts[0].equals("resources")) {
                if (parts.length == 2 && method.equals("GET"))
                    return ok(view(service.resource(id)));
                if (parts.length == 2 && method.equals("PUT")) {
                    fields(body, Set.of("expectedVersion", "name", "description"));
                    return ok(view(service.updateMetadata(id, number(body, "expectedVersion"), text(body, "name"),
                                                          text(body, "description"))));
                }
                if (parts.length == 3) {
                    String action = parts[2];
                    if (method.equals("POST") && action.equals("xml-preview")) {
                        fields(body, Set.of("source"));
                        var definition = service.previewXml(id, text(body, "source"));
                        return ok(Map.of("definition", DefinitionJson.write(definition),
                                         "source", service.export(id, definition)));
                    }
                    if (method.equals("GET") && action.equals("access"))
                        return ok(service.access(id));
                    if (method.equals("PUT") && action.equals("access")) {
                        fields(body, Set.of("expectedVersion", "policy"));
                        return ok(view(service.setAccess(id, number(body, "expectedVersion"),
                                                         policy(body.path("policy")))));
                    }
                    if (method.equals("GET") && action.equals("audit"))
                        return ok(service.audit(id, after, size));
                    if (method.equals("POST") && action.equals("archive")) {
                        fields(body, Set.of("expectedVersion"));
                        return ok(view(service.archive(id, number(body, "expectedVersion"))));
                    }
                    if (method.equals("POST") && Set.of("validate", "export", "diagnostics").contains(action)) {
                        fields(body, Set.of("definition"));
                        var definition = DefinitionJson.read(body.path("definition"));
                        if (action.equals("export"))
                            return ok(Map.of("source", service.export(id, definition), "mediaType", "application/xml"));
                        var report = action.equals("diagnostics") ? service.analyze(id, definition)
                                : service.validate(id, definition);
                        return ok(Map.of("valid", report.valid(), "diagnostics", report.diagnostics(),
                                         "catalogFingerprint", report.catalogFingerprint()));
                    }
                }
            }
            if (parts[0].equals("drafts")) {
                if (parts.length == 2 && method.equals("GET"))
                    return ok(draft(service.draft(id)));
                if (parts.length == 2 && method.equals("PUT")) {
                    fields(body, Set.of("expectedVersion", "definition"));
                    return ok(draft(service.saveDraft(id, number(body, "expectedVersion"),
                                                      DefinitionJson.read(body.path("definition")))));
                }
                if (parts.length == 3) {
                    if (method.equals("POST") && parts[2].equals("versions")) {
                        fields(body, Set.of("revision", "label"));
                        return created(service.createVersion(id, number(body, "revision"), text(body, "label")));
                    }
                    if (method.equals("PUT") && parts[2].equals("source")) {
                        fields(body, Set.of("expectedVersion", "mediaType", "source"));
                        return ok(draft(service.saveSource(id, number(body, "expectedVersion"),
                                                           new DraftRevision.Source(text(body, "mediaType"),
                                                                   text(body, "source")))));
                    }
                    if (method.equals("POST") && parts[2].equals("diff")) {
                        fields(body, Set.of("revision", "definition"));
                        return ok(service.compare(id, number(body, "revision"),
                                                  DefinitionJson.read(body.path("definition"))));
                    }
                    if (method.equals("POST") && parts[2].equals("compare")) {
                        fields(body, Set.of("fromRevision", "toRevision"));
                        return ok(service.compareRevisions(id, number(body, "fromRevision"),
                                                           number(body, "toRevision")));
                    }
                    if (method.equals("GET") && parts[2].equals("revisions")) {
                        var page = service.revisions(id, after == null ? 0 : Long.parseLong(after), size);
                        return ok(new StudioPage<>(page.items().stream().map(ManagementRoutes::revision).toList(),
                                page.nextCursor()));
                    }
                    if (method.equals("POST") && parts[2].equals("fork")) {
                        fields(body, Set.of("revision", "name"));
                        return created(draft(service.forkDraft(id, number(body, "revision"), text(body, "name"))));
                    }
                    if (method.equals("POST") && parts[2].equals("tests")) {
                        fields(body, Set.of("revision", "requestId", "input"));
                        return ok(service.test(id, number(body, "revision"), id(body, "requestId"),
                                               text(body, "input")));
                    }
                }
            }
            if (parts.length == 2 && parts[0].equals("tests") && method.equals("GET"))
                return ok(service.testRequest(id));
        }
        throw new StudioProblem(StudioProblem.Code.NOT_FOUND);
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
