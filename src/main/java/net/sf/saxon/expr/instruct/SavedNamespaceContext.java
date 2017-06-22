////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceResolver;

import java.io.Serializable;
import java.util.*;

/**
  * An object representing a list of Namespaces. Used when the namespace
  * controller in the stylesheet needs to be kept for use at run-time. The list of namespaces
  * is maintained in the form of numeric prefix/uri codes, which are only meaningful
  * in the context of a name pool
  */

public final class SavedNamespaceContext implements Serializable, NamespaceResolver {

    // TODO: static context can't vary within an XPath expression. Therefore, save the
    // NamespaceContext at the outermost expression level if any subexpression needs it.
    // (Or put a namespace context expression on the expression tree, which has no effect
    // at run-time, but is available to descendant expressions).

    /*@NotNull*/ private Map<String,String> bindings = new HashMap<String, String>();

    /**
     * Create a NamespaceContext object
     * @param nsBindings an array of namespace bindings. Each namespace code is an integer
     * in which the first 16 bits represent the prefix (zero if it's the default namespace)
     * and the next 16 bits represent the uri. These are codes held in the NamePool. The
     * list will be searched from the "high" end.
    */

    public SavedNamespaceContext(/*@NotNull*/ Iterable<NamespaceBinding> nsBindings) {
        this(nsBindings.iterator());
    }

    /**
     * Create a NamespaceContext object
     * @param nsBindings an array of namespace bindings. Each namespace code is an integer
     * in which the first 16 bits represent the prefix (zero if it's the default namespace)
     * and the next 16 bits represent the uri. These are codes held in the NamePool. The
     * list will be searched from the "high" end.
    */

    public SavedNamespaceContext(/*@NotNull*/ Iterator<NamespaceBinding> nsBindings) {
        while (nsBindings.hasNext()) {
            NamespaceBinding next = nsBindings.next();
            bindings.put(next.getPrefix(), next.getURI());
        }
    }

    /**
     * Create a SavedNamespaceContext that captures all the information in a given NamespaceResolver
     * @param resolver the NamespaceResolver
     */

    public SavedNamespaceContext(/*@NotNull*/ NamespaceResolver resolver) {
        Iterator iter = resolver.iteratePrefixes();
        while (iter.hasNext()) {
            String prefix = (String)iter.next();
            String uri = resolver.getURIForPrefix(prefix, true);
            bindings.put(prefix, uri);
        }
    }

    /**
    * Get the namespace URI corresponding to a given prefix. Return null
    * if the prefix is not in scope.
    * @param prefix the namespace prefix
    * @param useDefault true if the default namespace is to be used when the
    * prefix is ""
    * @return the uri for the namespace, or null if the prefix is not in scope
    */

    /*@Nullable*/ public String getURIForPrefix(/*@NotNull*/ String prefix, boolean useDefault) {

        if (prefix.length()==0 && !useDefault) {
            return NamespaceConstant.NULL;
        }

        if (prefix.equals("xml")) {
            return NamespaceConstant.XML;
        }

        String uri = bindings.get(prefix);
        if (uri == null) {
            return (prefix.length()==0 ? NamespaceConstant.NULL : null);
        } else {
            return uri;
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    public Iterator<String> iteratePrefixes() {
        List<String> prefixes = new ArrayList<String>(bindings.size() + 1);
        for (String s : bindings.keySet()) {
            prefixes.add(s);
        }
        prefixes.add("xml");
        return prefixes.iterator();
    }


}

