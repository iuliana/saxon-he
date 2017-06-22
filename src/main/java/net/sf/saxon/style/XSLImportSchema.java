////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.TransformerConfigurationException;


/**
* Compile-time representation of an xsl:import-schema declaration
 * in a stylesheet
*/

public class XSLImportSchema extends StyleElement {

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();
        String namespace = null;

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
            if (f.equals(StandardNames.SCHEMA_LOCATION)) {
        		//
            } else if (f.equals(StandardNames.NAMESPACE)) {
                namespace = Whitespace.trim(atts.getValue(a));
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if ("".equals(namespace)) {
            compileError("The zero-length string is not a valid namespace URI. "+
                    "For a schema with no namespace, omit the namespace attribute");
        }
    }

    public void validate(Declaration decl) throws XPathException {
        checkTopLevel("XTSE0010");
    }

    protected void index(Declaration decl, PrincipalStylesheetModule top) throws XPathException {
        try {
            readSchema();
        } catch (SchemaException e) {
            throw XPathException.makeXPathException(e);
        }
    }

    private void readSchema() throws SchemaException, XPathException {
        try {
            String schemaLoc = Whitespace.trim(getAttributeValue("", StandardNames.SCHEMA_LOCATION));
            String namespace = Whitespace.trim(getAttributeValue("", StandardNames.NAMESPACE));
            if (namespace==null) {
                namespace = "";
            } else {
                namespace = namespace.trim();
            }
            PreparedStylesheet preparedStylesheet = getPreparedStylesheet();
            Configuration config = getConfiguration();
            try {
                config.checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT, "xsl:import-schema");
            } catch (LicenseException err) {
                XPathException xe = new XPathException(err);
                xe.setErrorCode("XTSE1650");
                xe.setLocator(this);
                throw xe;
            }
            synchronized (config) {   // bug 2210
                AxisIterator kids = iterateAxis(AxisInfo.CHILD);
                NodeInfo inlineSchema = null;
                while (true) {
                    Item child = kids.next();
                    if (child==null) {
                        break;
                    }
                    if (inlineSchema != null) {
                        compileError(getDisplayName() + " must not have more than one child element");
                    }
                    inlineSchema = (NodeInfo)child;
                    if (inlineSchema.getFingerprint() != StandardNames.XS_SCHEMA) {
                        compileError("The only child element permitted for " + getDisplayName() + " is xs:schema");
                    }
                    if (schemaLoc != null) {
                        compileError("The schema-location attribute must be absent if an inline schema is present");
                    }

                    namespace = config.readInlineSchema(inlineSchema, namespace,
                            preparedStylesheet.getCompilerInfo().getErrorListener());
                    getPrincipalStylesheetModule().addImportedSchema(namespace);
                }
                if (inlineSchema != null) {
                    return;
                }
                if (!config.isSchemaAvailable(namespace)) {
                    if (schemaLoc == null) {
                        compileWarning("No schema for this namespace is known, " +
                                "and no schema-location was supplied, so no schema has been imported",
                                SaxonErrorCode.SXWN9006);
                        return;
                    }
                    PipelineConfiguration pipe = config.makePipelineConfiguration();
                    namespace = config.readSchema(pipe, getBaseURI(), schemaLoc, namespace);
                }
                getPrincipalStylesheetModule().addImportedSchema(namespace);
            }
        } catch (SchemaException err) {
            compileError(err.getMessage(), "XTSE0220");
        } catch (TransformerConfigurationException err) {
            compileError(err.getMessage(), "XTSE0220");
        }

    }


    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {
        // No action. The effect of import-schema is compile-time only
    }
}

