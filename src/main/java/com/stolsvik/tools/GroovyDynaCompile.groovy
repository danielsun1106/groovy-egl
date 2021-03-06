package com.stolsvik.tools

import groovy.transform.CompileStatic

/**
 * Simple tool that utilizes the {@link GroovyClassLoader} to dynamically compile a class from source each time the
 * underlying resource is changed. Put the ".groovy" file in some resource-folder (not in the compile path), and point
 * to it using {@link GroovyDynaCompile#fromClassLoader(String)}. Each time you invoke
 * {@link GroovyDynaCompile#getInstance()}, the resource is checked whether is has changed, and if so, a new Class is
 * compiled, and a new instance is constructed (and potentially the
 * {@link GroovyDynaCompile#setPostInstantiationClosure(groovy.lang.Closure) post instantiation closure} is invoked),
 * before it is returned.
 *
 * @author Endre Stølsvik, 2016-10-18 23:35 - http://endre.stolsvik.com/
 */
@CompileStatic
class GroovyDynaCompile {

    private String _fileContent
    private Class _class
    private Object _instance

    private String _classLoaderResourceName

    private Closure _postInstantiationClosure

    /**
     * Creates a {@link GroovyDynaCompile} using a ClassLoader resource as the source file.
     * @param classLoaderResourceName the name of the file, e.g. "dynacompile/GdcExample.groovy".
     * @return the {@link GroovyDynaCompile} instance, which will compile, load and instantiate the Groovy resource
     * when you invoke {@link #getInstance()} or {@link #getClazz()}.
     */
    static GroovyDynaCompile fromClassLoader(String classLoaderResourceName) {
        getClassLoaderResourceAsInputStream(classLoaderResourceName) // Fail fast if it isn't there.
        return new GroovyDynaCompile(_classLoaderResourceName: classLoaderResourceName)
    }

    /**
     * Each time a new instance is created, this closure will be invoked with the new instance as argument.
     * Great if you want e.g. Spring to inject members into the instance. For Spring, check out
     * <code>AutowiredAnnotationBeanPostProcessor .processInjection(instance)</code>.
     *
     * @param postInstantiationClosure the closure to run, which will be provided with the new instance.
     * @return this
     */
    GroovyDynaCompile setPostInstantiationClosure(Closure postInstantiationClosure) {
        _postInstantiationClosure = postInstantiationClosure
        return this
    }

    /**
     * @return the compiled Class instance. Do remember that this can change for each invocation - whenever the
     * underlying resource has changed.
     */
    Class getClazz() {
        loadIfResourceChanged()
        return _class
    }

    /**
     * @return an instance of the compiled Class. Do remember that this can change for each invocation - whenever the
     * underlying resource has changed.
     */
    Object getInstance() {
        loadIfResourceChanged()
        return _instance
    }

    private void loadIfResourceChanged() {
        InputStream stream = getClassLoaderResourceAsInputStream(_classLoaderResourceName)
        String fileContent = stream.getText("UTF-8")
        if (fileContent.equals(_fileContent)) {
            // -> File is identical, do nothing.
            return
        }
        _fileContent = fileContent
        GroovyClassLoader loader = new GroovyClassLoader()
        _class = loader.parseClass(fileContent, _classLoaderResourceName)
        _instance = _class.newInstance()
        if (_postInstantiationClosure) {
            _postInstantiationClosure(_instance)
        }
    }

    private static InputStream getClassLoaderResourceAsInputStream(String classLoaderResourceName) {
        InputStream stream = GroovyDynaCompile.class.getClassLoader().getResourceAsStream(classLoaderResourceName)
        if (stream == null) {
            throw new IllegalArgumentException("No ClassLoader Resource of name '$classLoaderResourceName' found.")
        }
        return stream
    }
}
