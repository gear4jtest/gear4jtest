package io.github.gear4jtest.studio.springdemo;

import java.io.IOException;

import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.demo.StudioHttpServer;
import io.github.gear4jtest.studio.service.InMemoryStudioService;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Local Spring host that owns its HTTP server and application context. */
public final class SpringStudioApplication implements AutoCloseable {
    private final AnnotationConfigApplicationContext context;
    private final StudioHttpServer server;

    private SpringStudioApplication(AnnotationConfigApplicationContext context, StudioHttpServer server) {
        this.context = context;
        this.server = server;
    }

    /** Starts a local host; port zero selects an available ephemeral port. */
    public static SpringStudioApplication start(int port,
                                                String editorToken,
                                                String viewerToken,
                                                DataCapturePolicy capturePolicy)
            throws IOException {
        var context = new AnnotationConfigApplicationContext();
        StudioHttpServer server = null;
        boolean started = false;
        try {
            context.registerBean(DataCapturePolicy.class, () -> capturePolicy);
            context.register(StudioSpringConfiguration.class);
            context.refresh();
            server = new StudioHttpServer(context.getBean(InMemoryStudioService.class), port, editorToken,
                    viewerToken);
            var application = new SpringStudioApplication(context, server);
            server.start();
            started = true;
            return application;
        } finally {
            if (!started) {
                try {
                    if (server != null)
                        server.close();
                } finally {
                    context.close();
                }
            }
        }
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        try {
            server.close();
        } finally {
            context.close();
        }
    }

    public static void main(String[] args) throws IOException {
        var application = start(args.length == 0 ? 8788 : Integer.parseInt(args[0]),
                                System.getenv("GEAR4J_STUDIO_TOKEN"), System.getenv("GEAR4J_STUDIO_VIEWER_TOKEN"),
                                DemoWiring.capturePolicy(System.getenv("GEAR4J_STUDIO_CAPTURE")));
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
        LoggerFactory.getLogger(SpringStudioApplication.class).info("Gear4J Studio P0 (Spring): http://127.0.0.1:{}",
                                                                    application.port());
    }
}
