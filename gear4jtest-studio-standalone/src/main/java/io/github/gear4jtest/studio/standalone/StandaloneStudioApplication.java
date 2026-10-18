package io.github.gear4jtest.studio.standalone;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runnable distribution; one immutable properties file, secrets only through
 * environment references.
 */
public final class StandaloneStudioApplication {
    private StandaloneStudioApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Expected a standalone properties file path");
        StandaloneConfiguration config;
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            config = StandaloneConfiguration.load(input, System::getenv);
        }
        var remote = new HttpRemoteManagement(config.directory(), config.credentials());
        StandaloneStudioServer server = null;
        boolean started = false;
        try {
            server = new StandaloneStudioServer(config.port(), config.directory(), remote, config::authenticate);
            var running = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    running.close();
                } finally {
                    remote.close();
                }
            }));
            server.start();
            started = true;
            System.getLogger(StandaloneStudioApplication.class.getName())
                    .log(System.Logger.Level.INFO, "Gear4J Studio: http://127.0.0.1:" + server.port());
        } finally {
            if (!started) {
                try {
                    if (server != null)
                        server.close();
                } finally {
                    remote.close();
                }
            }
        }
    }
}
