package io.github.gear4jtest.external.jdbc.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;

final class OperationChainObjectRowMapper {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private final Gear4jDatabaseDialect databaseDialect;

    OperationChainObjectRowMapper(Gear4jDatabaseDialect databaseDialect) {
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
    }

    OperationChainObject mapObject(ResultSet resultSet) throws SQLException {
        return new OperationChainObject(resultSet.getLong("id"), resultSet.getString("al_id"),
                resultSet.getString("version"), ExecutionMode.valueOf(resultSet.getString("publication_mode")),
                requireContentHash(resultSet.getString("content_hash")), resultSet.getLong("size_bytes"),
                resultSet.getString("mime_type"), databaseDialect.getInstant(resultSet, "created_at"),
                resultSet.getString("created_by"), databaseDialect.getInstant(resultSet, "published_at"));
    }

    OperationChainPublicationStage mapStage(ResultSet resultSet) throws SQLException {
        OperationChainObject object = new OperationChainObject(null, resultSet.getString("al_id"),
                resultSet.getString("version"), ExecutionMode.valueOf(resultSet.getString("publication_mode")),
                requireContentHash(resultSet.getString("content_hash")), resultSet.getLong("size_bytes"),
                resultSet.getString("mime_type"), databaseDialect.getInstant(resultSet, "created_at"),
                resultSet.getString("created_by"), databaseDialect.getInstant(resultSet, "published_at"));
        return new OperationChainPublicationStage(resultSet.getString("stage_id"), object, List.of(),
                resultSet.getString("store_fingerprint"), databaseDialect.getInstant(resultSet, "staged_at"),
                resultSet.getLong("stage_revision"));
    }

    static OperationChainPublicationStage copyStageWithTags(OperationChainPublicationStage stage, List<String> tags) {
        return new OperationChainPublicationStage(stage.stageId(), stage.object(), tags,
                stage.storeFingerprint(), stage.stagedAt(), stage.revision());
    }

    static String requireContentHash(String contentHash) {
        if (contentHash == null || !SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 content hash: " + contentHash);
        }
        return contentHash.toLowerCase(Locale.ROOT);
    }
}
