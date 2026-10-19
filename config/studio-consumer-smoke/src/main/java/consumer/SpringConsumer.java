package consumer;

import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class SpringConsumer {
    private SpringConsumer() { }
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(LocalGear4jRuntime.class, PlainConsumer::runtime,
                    bean -> bean.setDestroyMethodName("close"));
            context.refresh();
            PlainConsumer.verify(context.getBean(LocalGear4jRuntime.class));
        }
    }
}
