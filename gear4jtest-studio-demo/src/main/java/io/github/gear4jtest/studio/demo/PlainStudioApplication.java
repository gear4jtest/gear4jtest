package io.github.gear4jtest.studio.demo;

import io.github.gear4jtest.studio.http.RequestActorContext;
import org.slf4j.LoggerFactory;

public final class PlainStudioApplication {
    private PlainStudioApplication() {
    }

    public static void main(String[] args) throws Exception {
        var resources = DemoWiring.plainResources();
        var runtime = DemoWiring.runtime("java", DemoWiring.plainExecutor(resources), resources,
                                         DemoWiring.capturePolicy(System.getenv("GEAR4J_STUDIO_CAPTURE")));
        StudioHttpServer server = null;
        DemoPublicationHost publications = null;
        boolean started = false;
        try {
            var actors = new RequestActorContext();
            var dataSource = DemoDatabase.fromEnvironment("java");
            publications = new DemoPublicationHost(dataSource, DemoWiring.catalog());
            var service = DemoDatabase.service(dataSource, runtime, actors, publications.publisher());
            server = new StudioHttpServer(service, actors, args.length == 0 ? 8787 : Integer.parseInt(args[0]),
                    System.getenv("GEAR4J_STUDIO_TOKEN"), System.getenv("GEAR4J_STUDIO_VIEWER_TOKEN"));
            var running = server;
            var publicationHost = publications;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    running.close();
                } finally {
                    try {
                        publicationHost.close();
                    } finally {
                        runtime.close();
                    }
                }
            }));
            server.start();
            started = true;
            LoggerFactory.getLogger(PlainStudioApplication.class).info("Gear4J Studio: http://127.0.0.1:{}",
                                                                       server.port());
        } finally {
            if (!started) {
                try {
                    if (server != null)
                        server.close();
                } finally {
                    try {
                        if (publications != null)
                            publications.close();
                    } finally {
                        runtime.close();
                    }
                }
            }
        }
    }
}
