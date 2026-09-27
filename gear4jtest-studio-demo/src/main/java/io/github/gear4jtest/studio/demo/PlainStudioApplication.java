package io.github.gear4jtest.studio.demo;

import org.slf4j.LoggerFactory;

public final class PlainStudioApplication {
    private PlainStudioApplication() {
    }

    public static void main(String[] args) throws Exception {
        var resources = DemoWiring.plainResources();
        var runtime = DemoWiring.runtime("java", DemoWiring.plainExecutor(resources), resources,
                                         DemoWiring.capturePolicy(System.getenv("GEAR4J_STUDIO_CAPTURE")));
        var server = new StudioHttpServer(DemoWiring.service(runtime),
                args.length == 0 ? 8787 : Integer.parseInt(args[0]), System.getenv("GEAR4J_STUDIO_TOKEN"),
                System.getenv("GEAR4J_STUDIO_VIEWER_TOKEN"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            runtime.close();
        }));
        server.start();
        LoggerFactory.getLogger(PlainStudioApplication.class).info("Gear4J Studio P0: http://127.0.0.1:{}",
                                                                   server.port());
    }
}
