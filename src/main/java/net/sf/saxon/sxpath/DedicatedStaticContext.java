////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.om.StructuredQName;

import java.util.HashMap;

/**
 * This implementation of the XPath static context is mainly used when XPath expressions are hosted
 * in other Saxon processing contexts, for example in xsl:evaluate, or in XSD assertions
 */
public class DedicatedStaticContext extends IndependentContext implements Container {

    private Executable executable;

    public DedicatedStaticContext(Configuration config) {
        super(config);
    }

    /**
     * Create a DedicatedStaticContext as a copy of an IndependentContext
     * @param ic the IndependentContext to be copied
     */

    public DedicatedStaticContext(IndependentContext ic) {
        super(ic.getConfiguration());
        setBaseURI(ic.getBaseURI());
        setLocationMap(ic.getLocationMap());
        setCollationMap(ic.getCollationMap());
        setDefaultElementNamespace(ic.getDefaultElementNamespace());
        setDefaultFunctionNamespace(ic.getDefaultFunctionNamespace());
        setBackwardsCompatibilityMode(ic.isInBackwardsCompatibleMode());
        setSchemaAware(ic.isSchemaAware());
        namespaces = new HashMap<String, String>(ic.namespaces);
        variables = new HashMap<StructuredQName, XPathVariable>(10);
        FunctionLibraryList libList = (FunctionLibraryList)ic.getFunctionLibrary();
        if (libList != null) {
            setFunctionLibrary((FunctionLibraryList)libList.copy());
        }
        setImportedSchemaNamespaces(ic.importedSchemaNamespaces);
        externalResolver = ic.externalResolver;
        autoDeclare = ic.autoDeclare;
        setXPathLanguageLevel(ic.getXPathLanguageLevel());
        requiredContextItemType = ic.requiredContextItemType;
        if (ic instanceof DedicatedStaticContext) {
            setExecutable(((DedicatedStaticContext)ic).getExecutable());
        }
    }

    public void setExecutable(Executable exec) {
        executable = exec;
    }

    /*@Nullable*/ public Executable getExecutable() {
        return executable;
    }
}

