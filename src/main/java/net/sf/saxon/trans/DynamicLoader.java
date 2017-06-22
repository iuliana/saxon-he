////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.serialize.MessageEmitter;

import java.io.PrintStream;
import java.util.HashMap;

/**
 * Utility class used to perform dynamic loading of user-hook implementations
 */
public class DynamicLoader {

    private ClassLoader classLoader;

    protected HashMap<String, Class> knownClasses = new HashMap<String, Class>(20);

    public DynamicLoader() {
        registerKnownClasses();
    }

    /**
     * Register classes that might be dynamically loaded even though they are contained
     * within Saxon itself. This typically occurs for default implementations of classes that
     * can be substituted or subclassed by the user.
     */

    protected void registerKnownClasses() {
        knownClasses.put("net.sf.saxon.serialize.MessageEmitter", MessageEmitter.class);
        //knownClasses.put("net.sf.saxon.java.JavaPlatform", JavaPlatform.class);  // not available on .NET
        knownClasses.put("net.sf.saxon.Configuration", Configuration.class);
    }

    /**
     * Set a ClassLoader to be used when loading external classes. Examples of classes that are
     * loaded include SAX parsers, localization modules for formatting numbers and dates,
     * extension functions, external object models. In an environment such as Eclipse that uses
     * its own ClassLoader, this ClassLoader should be nominated to ensure that any class loaded
     * by Saxon is identical to a class of the same name loaded by the external environment.
     * <p>
     * @param loader the ClassLoader to be used in this configuration
     */

    public void setClassLoader(ClassLoader loader) {
        classLoader = loader;
    }

    /**
     * Get the ClassLoader supplied using the method {@link #setClassLoader}.
     * If none has been supplied, return null.
     * <p>
     * @return the ClassLoader used in this configuration
     */

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Load a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p>
     * This method is intended for internal use only.
     *
     * @param className A string containing the name of the
     *   class, for example "com.microstar.sax.LarkDriver"
     * @param traceOut if diagnostic tracing is required, the destination for the output; otherwise null
     * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     * the classLoader used will be the first one available of: the classLoader registered
     * with the Configuration using {@link #setClassLoader}; the context class loader for
     * the current thread; or failing that, the class loader invoked implicitly by a call
     * of Class.forName() (which is the ClassLoader that was used to load the Configuration
     * object itself).
     * @return an instance of the class named, or null if it is not
     * loadable.
     * @throws XPathException if the class cannot be loaded.
     *
    */

    public Class getClass(String className, /*@Nullable*/ PrintStream traceOut, /*@Nullable*/ ClassLoader classLoader) throws XPathException {
        Class known = knownClasses.get(className);
        if (known != null) {
            return known;
        }

        boolean tracing = traceOut != null;
        if (tracing) {
            traceOut.println("Loading " + className);
        }

        try {
            ClassLoader loader = classLoader;
            if (loader == null) {
                loader = this.classLoader;
            }
            if (loader == null) {
                loader = Thread.currentThread().getContextClassLoader();
            }
            if (loader != null) {
                try {
                    return loader.loadClass(className);
                } catch (Throwable ex) {
                    // Catching Exception is not enough; Java sometimes throws a NoClassDefFoundError
                    return Class.forName(className);
                }
            } else {
                return Class.forName(className);
            }
        }
        catch (Throwable e) {
            if (tracing) {
                // The exception is often masked, especially when calling extension
                // functions
                traceOut.println("The class " + className + " could not be loaded: " + e.getMessage());
            }
            throw new XPathException("Failed to load " + className, e );
        }

    }

  /**
    * Instantiate a class using the class name provided.
    * Note that the method does not check that the object is of the right class.
   * <p>
   * This method is intended for internal use only.
   *
    * @param className A string containing the name of the
    *   class, for example "com.microstar.sax.LarkDriver"
    * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     * the classLoader used will be the first one available of: the classLoader registered
     * with the Configuration using {@link #setClassLoader}; the context class loader for
     * the current thread; or failing that, the class loader invoked implicitly by a call
     * of Class.forName() (which is the ClassLoader that was used to load the Configuration
     * object itself).
    * @return an instance of the class named, or null if it is not
    * loadable.
    * @throws XPathException if the class cannot be loaded.
    *
    */

    public Object getInstance(String className, /*@Nullable*/ ClassLoader classLoader) throws XPathException {
        Class theclass = getClass(className, null, classLoader);
        try {
            return theclass.newInstance();
        } catch (Exception err) {
            throw new XPathException("Failed to instantiate class " + className +
                    " (does it have a public zero-argument constructor?)", err);
        }
    }

    /**
      * Instantiate a class using the class name provided, with the option of tracing
      * Note that the method does not check that the object is of the right class.
      * <p>
      * This method is intended for internal use only.
      *
      * @param className A string containing the name of the
      *   class, for example "com.microstar.sax.LarkDriver"
      * @param traceOut if attempts to load classes are to be traced, then the destination
      * for the trace output; otherwise null
      * @param classLoader The ClassLoader to be used to load the class. If this is null, then
      * the classLoader used will be the first one available of: the classLoader registered
      * with the Configuration using {@link #setClassLoader}; the context class loader for
      * the current thread; or failing that, the class loader invoked implicitly by a call
      * of Class.forName() (which is the ClassLoader that was used to load the Configuration
      * object itself).
      * @return an instance of the class named, or null if it is not
      * loadable.
      * @throws XPathException if the class cannot be loaded.
      *
      */

      public Object getInstance(String className, PrintStream traceOut, /*@Nullable*/ ClassLoader classLoader) throws XPathException {
          Class theclass = getClass(className, traceOut, classLoader);
          try {
              return theclass.newInstance();
          } catch (NoClassDefFoundError err) {
              throw new XPathException("Failed to load instance of class " + className, err);
          } catch (Exception err) {
              throw new XPathException("Failed to instantiate class " + className, err);
          }
      }




}

