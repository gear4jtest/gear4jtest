package io.github.gear4jtest.jdbc.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Loads, parses and fingerprints the versioned migration resources. */
final class MigrationResourceLoader {
    private final String migrationListResource;
    private final ClassLoader classLoader;

    MigrationResourceLoader(String migrationListResource, ClassLoader classLoader) {
        this.migrationListResource = normalizeResource(migrationListResource);
        this.classLoader = classLoader;
    }

    List<SchemaMigration> loadMigrations() throws IOException {
        String listContent = readResource(migrationListResource);
        List<SchemaMigration> migrations = new ArrayList<>();
        String basePath = migrationListResource.substring(0, migrationListResource.lastIndexOf('/') + 1);
        for (String raw : listContent.split("\\R")) {
            String entry = raw.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String fileName = entry.substring(entry.lastIndexOf('/') + 1);
            int separator = fileName.indexOf("__");
            int extension = fileName.lastIndexOf('.');
            if (!fileName.startsWith("V") || separator < 0 || extension <= separator) {
                throw new IOException("Invalid Gear4J migration file name: " + entry);
            }
            String version = fileName.substring(1, separator);
            String description = fileName.substring(separator + 2, extension).replace('_', ' ');
            migrations.add(new SchemaMigration(version, description, basePath + entry));
        }
        return migrations;
    }

    String readResource(String resourcePath) throws IOException {
        String normalized = normalizeResource(resourcePath);
        InputStream stream = classLoader.getResourceAsStream(normalized);
        if (stream == null) {
            stream = MigrationResourceLoader.class.getClassLoader().getResourceAsStream(normalized);
        }
        if (stream == null) {
            throw new IOException("Migration resource not found: " + normalized);
        }
        try (InputStream input = stream;
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalizeResource(String resource) {
        return resource.startsWith("/") ? resource.substring(1) : resource;
    }
}
