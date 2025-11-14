package io.test.gear4jtest.external.api.loader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassLoader personnalisé (identique à l'original)
 */
public class InMemoryClassLoader extends ClassLoader {
    private final Map<String, byte[]> classes = new ConcurrentHashMap<>();

    public InMemoryClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    public void addCompiledClasses(Map<String, byte[]> compiledClasses) {
        classes.putAll(compiledClasses);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException("Classe non trouvée: " + name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (classes.containsKey(name)) {
            Class<?> clazz = findLoadedClass(name);
            if (clazz == null) {
                clazz = findClass(name);
            }
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        }
        return super.loadClass(name, resolve);
    }

    public Object createInstance(String className) throws Exception {
        Class<?> clazz = loadClass(className);
        return clazz.getDeclaredConstructor().newInstance();
    }
}