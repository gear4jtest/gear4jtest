package io.github.gear4jtest.external.api.loader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClassLoader used to load generated pipeline classes compiled in memory.
 */
public class InMemoryClassLoader extends ClassLoader {
    private final Map<String, byte[]> classes = new ConcurrentHashMap<>();
    private final AtomicLong definedBytecodeBytes = new AtomicLong();
    private final AtomicLong retainedBytecodeBytes = new AtomicLong();

    public InMemoryClassLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public InMemoryClassLoader(ClassLoader parent) {
        super(parent != null ? parent : ClassLoader.getSystemClassLoader());
    }

    public void addCompiledClasses(Map<String, byte[]> compiledClasses) {
        Map<String, byte[]> defensiveCopy = new LinkedHashMap<>();
        Objects.requireNonNull(compiledClasses, "compiledClasses must not be null").forEach((name, bytes) -> {
            if (name == null) {
                throw new IllegalArgumentException("compiled class name must not be null");
            }
            if (bytes == null) {
                throw new IllegalArgumentException("compiled class bytes must not be null");
            }
            defensiveCopy.put(name, bytes.clone());
        });
        defensiveCopy.forEach((name, bytes) -> {
            byte[] previous = classes.put(name, bytes);
            retainedBytecodeBytes.addAndGet(bytes.length - (previous == null ? 0L : previous.length));
        });
    }

    /**
     * Returns the conservative bytecode weight owned by this loader, including
     * classes already defined into metaspace.
     */
    public long bytecodeWeightBytes() {
        return definedBytecodeBytes.get() + retainedBytecodeBytes.get();
    }

    /**
     * Returns bytecode still retained on heap for classes not yet defined.
     */
    public long retainedBytecodeBytes() {
        return retainedBytecodeBytes.get();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException("Generated class not found: " + name);
        }
        Class<?> defined = defineClass(name, bytes, 0, bytes.length);
        if (classes.remove(name, bytes)) {
            retainedBytecodeBytes.addAndGet(-bytes.length);
            definedBytecodeBytes.addAndGet(bytes.length);
        }
        return defined;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
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
    }

    public Object createInstance(String className) throws Exception {
        Class<?> clazz = loadClass(className);
        return clazz.getDeclaredConstructor().newInstance();
    }
}
