////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Container;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.Location;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A "trace" clause in a FLWOR expression, added by a TraceCodeInjector
 */
public class ClauseInfo implements InstructionInfo {

    private Clause clause;
    private Container container;
    private NamespaceResolver nsResolver;

    public ClauseInfo(Clause clause, Container container) {
        this.clause = clause;
        this.container = container;
    }

    /**
     * Get the clause being traced
     * @return the clause in the FLWOR expression to which this ClauseInfo relates
     */

    public Clause getClause() {
        return clause;
    }

    /**
     * Get the type of construct. This will either be the fingerprint of a standard XSLT instruction name
     * (values in {@link net.sf.saxon.om.StandardNames}: all less than 1024)
     * or it will be a constant in class {@link net.sf.saxon.trace.Location}.
     *
     * @return an integer identifying the kind of construct
     */
    public int getConstructType() {
        return Location.CLAUSE_BASE + clause.getClauseKey();
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */
    public StructuredQName getObjectName() {
        LocalVariableBinding[] vars = clause.getRangeVariables();
        if (vars != null && vars.length > 0) {
            return vars[0].getVariableQName();
        } else {
            return null;
        }
    }

    /**
     * Get the namespace bindings from the static context of the clause
     * @return a namespace resolver that reflects the in scope namespaces of the clause
     */

    public NamespaceResolver getNamespaceResolver() {
        return nsResolver;
    }

    /**
     * Set the namespace bindings from the static context of the clause
     * @param nsResolver a namespace resolver that reflects the in scope namespaces of the clause
     */

    public void setNamespaceResolver(NamespaceResolver nsResolver) {
        this.nsResolver = nsResolver;
    }

    /**
     * Get the system identifier (URI) of the source stylesheet or query module containing
     * the instruction. This will generally be an absolute URI. If the system
     * identifier is not known, the method may return null. In some cases, for example
     * where XML external entities are used, the correct system identifier is not
     * always retained.
     *
     * @return the URI of the containing module
     */
    public String getSystemId() {
        return container.getLocationProvider().getSystemId(clause.getLocationId());
    }

    /**
     * Get the line number of the instruction in the source stylesheet module.
     * If this is not known, or if the instruction is an artificial one that does
     * not relate to anything in the source code, the value returned may be -1.
     *
     * @return the line number of the expression within the containing module
     */
    public int getLineNumber() {
        return container.getLocationProvider().getLineNumber(clause.getLocationId());
    }

    /**
     * Get the value of a particular property of the instruction. Properties
     * of XSLT instructions are generally known by the name of the stylesheet attribute
     * that defines them.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */
    public Object getProperty(String name) {
        return null;
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property. The iterator may return properties whose
     * value is null.
     *
     * @return an iterator over the properties.
     */
    public Iterator<String> getProperties() {
        List<String> ls = Collections.emptyList();
        return ls.iterator();
    }

    /**
     * Get the URI of the document, entity, or module containing a particular location
     *
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the URI of the document, XML entity or module. For a SourceLocationProvider this will
     *         be the URI of the document or entity (the URI that would be the base URI if there were no
     *         xml:base attributes). In other cases it may identify the query or stylesheet module currently
     *         being executed.
     */
    public String getSystemId(long locationId) {
        return getSystemId();
    }

    /**
     * Get the line number within the document, entity or module containing a particular location
     *
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the line number within the document, entity or module, or -1 if no information is available.
     */
    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    /**
     * Get the column number within the document, entity, or module containing a particular location
     *
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the column number within the document, entity, or module, or -1 if this is not available
     */
    public int getColumnNumber(long locationId) {
        return -1;
    }

    /**
     * Return the public identifier for the current document event.
     * <p/>
     * <p>The return value is the public identifier of the document
     * entity or of the external parsed entity in which the markup
     * triggering the event appears.</p>
     *
     * @return A string containing the public identifier, or
     *         null if none is available.
     * @see #getSystemId
     */
    public String getPublicId() {
        return null;
    }

    /**
     * Return the column number where the current document event ends.
     * This is one-based number of Java <code>char</code> values since
     * the last line end.
     * <p/>
     * <p><strong>Warning:</strong> The return value from the method
     * is intended only as an approximation for the sake of diagnostics;
     * it is not intended to provide sufficient information
     * to edit the character content of the original XML document.
     * For example, when lines contain combining character sequences, wide
     * characters, surrogate pairs, or bi-directional text, the value may
     * not correspond to the column in a text editor's display. </p>
     * <p/>
     * <p>The return value is an approximation of the column number
     * in the document entity or external parsed entity where the
     * markup triggering the event appears.</p>
     * <p/>
     * <p>If possible, the SAX driver should provide the line position
     * of the first character after the text associated with the document
     * event.  The first column in each line is column 1.</p>
     *
     * @return The column number, or -1 if none is available.
     * @see #getLineNumber
     */
    public int getColumnNumber() {
        return -1;
    }
}

