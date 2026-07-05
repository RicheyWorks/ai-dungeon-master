package com.xai.dungeonmaster.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A {@link URLClassLoader} that runs every class it defines from the plugin JAR
 * through {@link SandboxVerifier} first, throwing {@link SecurityException} if
 * the class references a {@link SandboxPolicy}-denied API.
 *
 * Only classes actually defined from the JAR are scanned: host and JDK classes
 * resolve through the parent (standard parent-first delegation) and are trusted.
 * {@link #findResource} searches only this loader's own URLs, so we read the
 * plugin's bytes rather than something on the parent classpath.
 */
final class SandboxedClassLoader extends URLClassLoader {

    private final SandboxPolicy policy;

    SandboxedClassLoader(URL[] urls, ClassLoader parent, SandboxPolicy policy) {
        super(urls, parent);
        this.policy = (policy != null) ? policy : SandboxPolicy.disabled();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL url = findResource(path); // this loader's URLs only, not the parent
        if (url == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try (InputStream is = url.openStream()) {
            bytes = is.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        String violation = SandboxVerifier.firstViolation(bytes, policy);
        if (violation != null) {
            throw new SecurityException("plugin class " + name + " references blocked API: " + violation);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
