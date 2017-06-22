////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.QNameValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
* An IndependentContext provides a context for parsing an XPath expression appearing
* in a context other than a stylesheet.
 *
 * <p>This class is used in a number of places where freestanding XPath expressions occur.
 * These include the native Saxon XPath API, the .NET XPath API, XPath expressions used
 * in XML Schema identity constraints, and XPath expressions supplied to saxon:evaluate().
 * It is not used by the JAXP XPath API (though it shares code with that API through
 * the common superclass AbstractStaticContext).</p>
 *
 * <p>This class currently provides no mechanism for binding user-defined functions.</p>
*/

public class IndependentContext extends AbstractStaticContext
                                implements XPathStaticContext, NamespaceResolver, Serializable {

	protected HashMap<String, String> namespaces = new HashMap<String, String>(10);
	protected HashMap<StructuredQName, XPathVariable> variables = new HashMap<StructuredQName, XPathVariable>(20);
    protected NamespaceResolver externalResolver = null;
    protected ItemType requiredContextItemType = AnyItemType.getInstance();
    protected Set importedSchemaNamespaces = new HashSet();
    protected boolean autoDeclare = false;


    /**
     * Create an IndependentContext along with a new (non-schema-aware) Saxon Configuration
     */

    public IndependentContext() {
        this(new Configuration());
    }

    /**
	 * Create an IndependentContext using a specific Configuration
     * @param config the Saxon configuration to be used
	*/

	public IndependentContext(Configuration config) {
        setConfiguration(config);
		clearNamespaces();
        setDefaultFunctionLibrary();
        usingDefaultFunctionLibrary = true;
    }

    /**
     * Get the granularity of the container.
     * @return 0 for a temporary container created during parsing; 1 for a container
     *         that operates at the level of an XPath expression; 2 for a container at the level
     *         of a global function or template
     */

    public int getContainerGranularity() {
        return 1;
    }

	/**
	* Declare a namespace whose prefix can be used in expressions
	* @param prefix The namespace prefix. Must not be null. Supplying "" sets the
     * default element namespace.
	* @param uri The namespace URI. Must not be null.
	*/

	public void declareNamespace(String prefix, String uri) {
	    if (prefix==null) {
	        throw new NullPointerException("Null prefix supplied to declareNamespace()");
	    }
	    if (uri==null) {
	        throw new NullPointerException("Null namespace URI supplied to declareNamespace()");
	    }
        if ("".equals(prefix)) {
            setDefaultElementNamespace(uri);
        } else {
		    namespaces.put(prefix, uri);
        }
	}

    /**
     * Set the default namespace for elements and types
     * @param uri the namespace to be used for unprefixed element and type names.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    @Override
    public void setDefaultElementNamespace(String uri) {
        super.setDefaultElementNamespace(uri);
        namespaces.put("", uri);
    }

	/**
	 * Clear all the declared namespaces, except for the standard ones (xml, xslt, saxon, xdt).
     * This also resets the default element namespace to the "null" namespace
	*/

	public void clearNamespaces() {
	    namespaces.clear();
		declareNamespace("xml", NamespaceConstant.XML);
		declareNamespace("xsl", NamespaceConstant.XSLT);
		declareNamespace("saxon", NamespaceConstant.SAXON);
		declareNamespace("xs", NamespaceConstant.SCHEMA);
        declareNamespace("", "");
	}

	/**
	* Clear all the declared namespaces, including the standard ones (xml, xslt, saxon).
     * Leave only the XML namespace and the default namespace (xmlns="").
     * This also resets the default element namespace to the "null" namespace.
	*/

	public void clearAllNamespaces() {
	    namespaces.clear();
		declareNamespace("xml", NamespaceConstant.XML);
		declareNamespace("", "");
	}

	/**
	* Declares all the namespaces that are in-scope for a given node, removing all previous
    * namespace declarations.
	* In addition, the standard namespaces (xml, xslt, saxon) are declared. This method also
    * sets the default element namespace to be the same as the default namespace for this node.
	* @param node The node whose in-scope namespaces are to be used as the context namespaces.
	* If the node is an attribute, text node, etc, then the namespaces of its parent element are used.
	*/

	public void setNamespaces(NodeInfo node) {
	    namespaces.clear();
        int kind = node.getNodeKind();
        if (kind == Type.ATTRIBUTE || kind == Type.TEXT ||
                kind == Type.COMMENT || kind == Type.PROCESSING_INSTRUCTION ||
                kind == Type.NAMESPACE) {
            node = node.getParent();
        }
        if (node == null) {
            return;
        }

	    AxisIterator iter = node.iterateAxis(AxisInfo.NAMESPACE);
	    while (true) {
	        NodeInfo ns = (NodeInfo)iter.next();
            if (ns == null) {
                return;
            }
            String prefix = ns.getLocalPart();
            if ("".equals(prefix)) {
                setDefaultElementNamespace(ns.getStringValue());
            } else {
	            declareNamespace(ns.getLocalPart(), ns.getStringValue());
            }
	    }
	}

    /**
     * Set an external namespace resolver. If this is set, then all resolution of namespace
     * prefixes is delegated to the external namespace resolver, and namespaces declared
     * individually on this IndependentContext object are ignored.
     * @param resolver the external NamespaceResolver
     */

    public void setNamespaceResolver(NamespaceResolver resolver) {
        externalResolver = resolver;
    }

    /**
     * Say whether undeclared variables are allowed. By default, they are not allowed. When
     * undeclared variables are allowed, it is not necessary to predeclare the variables that
     * may be used in the XPath expression; instead, a variable is automatically declared when a reference
     * to the variable is encountered within the expression.
     * @param allow true if undeclared variables are allowed, false if they are not allowed.
     * @since 9.2
     */

    public void setAllowUndeclaredVariables(boolean allow) {
        autoDeclare = allow;
    }

    /**
     * Ask whether undeclared variables are allowed. By default, they are not allowed. When
     * undeclared variables are allowed, it is not necessary to predeclare the variables that
     * may be used in the XPath expression; instead, a variable is automatically declared when a reference
     * to the variable is encountered within the expression.
     * @return true if undeclared variables are allowed, false if they are not allowed.
     * @since 9.2
     */

    public boolean isAllowUndeclaredVariables() {
        return autoDeclare;
    }
    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence
     * @param qname The name of the variable
     * @return an XPathVariable object representing information about the variable that has been
     * declared.
    */

    public XPathVariable declareVariable(QNameValue qname) {
        return declareVariable(qname.toStructuredQName());
    }

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence
     * @param namespaceURI The namespace URI of the name of the variable. Supply "" to represent
     * names in no namespace (null is also accepted)
     * @param localName The local part of the name of the variable (an NCName)
     * @return an XPathVariable object representing information about the variable that has been
     * declared.
    */

    public XPathVariable declareVariable(String namespaceURI, String localName) {
        StructuredQName qName = new StructuredQName("", namespaceURI, localName);
        return declareVariable(qName);
    }

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence
     * @param qName the name of the variable.
     * @return an XPathVariable object representing information about the variable that has been
     * declared.
     * @since 9.2
    */


    public XPathVariable declareVariable(StructuredQName qName) {
        XPathVariable var = variables.get(qName);
        if (var != null) {
            return var;
        } else {
            var = XPathVariable.make(qName);
            int slot = variables.size();
            var.setSlotNumber(slot);
            variables.put(qName, var);
            return var;
        }
    }

    /**
     * Get an iterator over all the variables that have been declared, either explicitly by an
     * application call on declareVariable(), or implicitly if the option <code>allowUndeclaredVariables</code>
     * is set.
     * @return an iterator; the objects returned by this iterator will be instances of XPathVariable
     * @since 9.2
     */

    public Iterator<XPathVariable> iterateExternalVariables() {
        return variables.values().iterator();
    }

    /**
     * Get the declared variable with a given name, if there is one
     * @param qName the name of the required variable
     * @return the explicitly or implicitly declared variable with this name if it exists,
     * or null otherwise
     * @since 9.2
     */

    public XPathVariable getExternalVariable(StructuredQName qName) {
        return variables.get(qName);
    }

    /**
     * Get the slot number allocated to a particular variable
     * @param qname the name of the variable
     * @return the slot number, or -1 if the variable has not been declared
     */

    public int getSlotNumber(QNameValue qname) {
        StructuredQName sq = qname.toStructuredQName();
        XPathVariable var = variables.get(sq);
        if (var == null) {
            return -1;
        }
        return var.getLocalSlotNumber();
    }

    /**
     * Get the URI for a prefix, using the declared namespaces as
     * the context for namespace resolution. The default namespace is NOT used
     * when the prefix is empty.
     * This method is provided for use by the XPath parser.
     * @param prefix The prefix
     * @throws net.sf.saxon.trans.XPathException if the prefix is not declared; the
     * associated error code should be XPST0081
    */

    /*@Nullable*/ public String getURIForPrefix(String prefix) throws XPathException {
        String uri = getURIForPrefix(prefix, false);
    	if (uri==null) {
    		throw new XPathException("Prefix " + prefix + " has not been declared", "XPST0081");
    	}
    	return uri;
    }

    public NamespaceResolver getNamespaceResolver() {
        if (externalResolver != null) {
            return externalResolver;
        } else {
            return this;
        }
    }


    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     * @param prefix the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     * prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * Return "" if the prefix maps to the null namespace.
     */

    public String getURIForPrefix(String prefix, boolean useDefault) {
        if (externalResolver != null) {
            return externalResolver.getURIForPrefix(prefix, useDefault);
        }
        if (prefix.length() == 0) {
            return useDefault ? getDefaultElementNamespace() : "";
        } else {
    	    return namespaces.get(prefix);
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    public Iterator<String> iteratePrefixes() {
        if (externalResolver != null) {
            return externalResolver.iteratePrefixes();
        } else {
            return namespaces.keySet().iterator();
        }
    }

    /**
     * Bind a variable used in an XPath Expression to the XSLVariable element in which it is declared.
     * This method is provided for use by the XPath parser, and it should not be called by the user of
     * the API, or overridden, unless variables are to be declared using a mechanism other than the
     * declareVariable method of this class.
     * @param qName the name of the variable
     * @return the resulting variable reference
     */

    public Expression bindVariable(StructuredQName qName) throws XPathException {
        XPathVariable var = variables.get(qName);
        if (var==null) {
            if (autoDeclare) {
                return new VariableReference(declareVariable(qName));
            } else {
                throw new XPathException("Undeclared variable in XPath expression: $" + qName.getClarkName(), "XPST0008");
            }
        } else {
            return new VariableReference(var);
        }
    }

    /**
     * Get a Stack Frame Map containing definitions of all the declared variables. This will return a newly
     * created object that the caller is free to modify by adding additional variables, without affecting
     * the static context itself.
     */

    public SlotManager getStackFrameMap() {
        SlotManager map = getConfiguration().makeSlotManager();
        XPathVariable[] va = new XPathVariable[variables.size()];

        for (Iterator<XPathVariable> v = variables.values().iterator(); v.hasNext();) {
            XPathVariable var = v.next();
            va[var.getLocalSlotNumber()] = var;
        }
        for (int i=0; i<va.length; i++) {
            map.allocateSlotNumber(va[i].getVariableQName());
        }
        return map;
    }



    public boolean isImportedSchema(String namespace) {
        return importedSchemaNamespaces.contains(namespace);
    }

    /**
     * Get the set of imported schemas
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    public Set<String> getImportedSchemaNamespaces() {
        return importedSchemaNamespaces;
    }

    /**
     * Register the set of imported schema namespaces
     * @param namespaces the set of namespaces for which schema components are available in the
     * static context
     */

    public void setImportedSchemaNamespaces(Set namespaces) {
        importedSchemaNamespaces = namespaces;
        if (!namespaces.isEmpty()) {
            setSchemaAware(true);
        }
    }

    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     * @param type the required type of the context item
     * @since 9.3
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextItemType = type;
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     * @return the required type of the context item
     * @since 9.3
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }


}

