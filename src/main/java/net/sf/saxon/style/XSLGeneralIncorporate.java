////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.event.IDFilter;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.DocumentURI;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;


/**
* Abstract class to represent xsl:include or xsl:import element in the stylesheet. <br>
* The xsl:include and xsl:import elements have mandatory attribute href
*/

public abstract class XSLGeneralIncorporate extends StyleElement {

    private String href;

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }


    /**
    * isImport() returns true if this is an xsl:import declaration rather than an xsl:include
     * @return true if this is an xsl:import declaration, false if it is an xsl:include
    */

    public abstract boolean isImport();

    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.HREF)) {
        		href = Whitespace.trim(atts.getValue(a));
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (href==null) {
            reportAbsence("href");
        }
    }

    public void validate(Declaration decl) throws XPathException {
        validateInstruction();
    }

    public void validateInstruction() throws XPathException {
        checkEmpty();
        checkTopLevel(isImport() ? "XTSE0190" : "XTSE0170");
    }

    /**
     * Get the included or imported stylesheet module
     * @param importer the module that requested the include or import (used to check for cycles)
     * @param precedence the import precedence to be allocated to the included or imported module
     * @return the xsl:stylesheet element at the root of the included/imported module
     * @throws XPathException if any failure occurs
     */

    /*@Nullable*/ public StylesheetModule getIncludedStylesheet(StylesheetModule importer, int precedence)
                 throws XPathException {

        if (href==null) {
            // error already reported
            return null;
        }

        //checkEmpty();
        //checkTopLevel((this instanceof XSLInclude ? "XTSE0170" : "XTSE0190"));

        try {
            PrincipalStylesheetModule psm = importer.getPrincipalStylesheetModule();
            PreparedStylesheet pss = psm.getPreparedStylesheet();
            URIResolver resolver = pss.getCompilerInfo().getURIResolver();
            Configuration config = pss.getConfiguration();
            XSLStylesheet includedSheet;
            StylesheetModule incModule;

            DocumentURI key = DocumentFn.computeDocumentKey(href, getBaseURI(), resolver);
            includedSheet = psm.getStylesheetDocument(key);
            if (includedSheet != null) {
                // we already have the stylesheet document in cache; but we need to create a new module,
                // because the import precedence might be different. See test impincl30.
                incModule = new StylesheetModule(includedSheet, precedence);
                incModule.setImporter(importer);

                // check for recursion
                if (checkForRecursion(importer, incModule.getSourceElement())) {
                    return null;
                }

            } else {

                //System.err.println("GeneralIncorporate: href=" + href + " base=" + getBaseURI());
                String relative = href;
                String fragment = null;
                int hash = relative.indexOf('#');
                if (hash == 0 || relative.length() == 0) {
                    compileError("A stylesheet cannot " + getLocalPart() + " itself",
                                    (this instanceof XSLInclude ? "XTSE0180" : "XTSE0210"));
                    return null;
                } else if (hash == relative.length() - 1) {
                    relative = relative.substring(0, hash);
                } else if (hash > 0) {
                    if (hash+1 < relative.length()) {
                        fragment = relative.substring(hash+1);
                    }
                    relative = relative.substring(0, hash);
                }
                Source source;
                try {
                    source = resolver.resolve(relative, getBaseURI());
                } catch (TransformerException e) {
                    throw XPathException.makeXPathException(e);
                }

                // if a user URI resolver returns null, try the standard one
                // (Note, the standard URI resolver never returns null)
                if (source==null) {
                    source = config.getSystemURIResolver().resolve(relative, getBaseURI());
                }

                if (fragment != null) {
                    final String fragmentFinal = fragment;
                    FilterFactory factory = new FilterFactory() {
                        public ProxyReceiver makeFilter(Receiver next) {
                            return new IDFilter(next, fragmentFinal);
                        }
                    };
                    source = AugmentedSource.makeAugmentedSource(source);
                    ((AugmentedSource)source).addFilter(factory);
                }

                // check for recursion
                if (checkForRecursion(importer, source)) {
                    return null;
                }

                DocumentImpl includedDoc = pss.loadStylesheetModule(source);

                // allow the included document to use "Literal Result Element as Stylesheet" syntax

                ElementImpl outermost = includedDoc.getDocumentElement();

                if (outermost instanceof LiteralResultElement) {
                    includedDoc = ((LiteralResultElement)outermost)
                            .makeStylesheet(getPreparedStylesheet());
                    outermost = includedDoc.getDocumentElement();
                }

                if (!(outermost instanceof XSLStylesheet)) {
                    compileError("Included document " + href + " is not a stylesheet", "XTSE0165");
                    return null;
                }
                includedSheet = (XSLStylesheet)outermost;
                includedSheet.setPrincipalStylesheetModule(psm);
                psm.putStylesheetDocument(key, includedSheet);

                incModule = new StylesheetModule(includedSheet, precedence);
                incModule.setImporter(importer);
                Declaration decl = new Declaration(incModule, includedSheet);
                includedSheet.validate(decl);

                if (includedSheet.validationError!=null) {
                    if (reportingCircumstances == REPORT_ALWAYS) {
                        includedSheet.compileError(includedSheet.validationError);
                    } else if (includedSheet.reportingCircumstances == REPORT_UNLESS_FORWARDS_COMPATIBLE
                        // not sure if this can still happen
                                  /*&& !incSheet.forwardsCompatibleModeIsEnabled()*/) {
                        includedSheet.compileError(includedSheet.validationError);
                    }
                }
            }

            incModule.spliceIncludes();          // resolve any nested imports and includes;

            // Check the consistency of input-type-annotations
            //assert thisSheet != null;
            importer.setInputTypeAnnotations(includedSheet.getInputTypeAnnotationsAttribute() |
                    incModule.getInputTypeAnnotations());

            return incModule;

        } catch (XPathException err) {
            err.setErrorCode("XTSE0165");
            err.setIsStaticError(true);
            compileError(err);
            return null;
        }
    }

    private boolean checkForRecursion(StylesheetModule importer, Source source) throws XPathException {
        StylesheetModule anc = importer;

        if (source.getSystemId() != null) {
            while(anc!=null) {
                if (DocumentURI.normalizeURI(source.getSystemId())
                        .equals(DocumentURI.normalizeURI(anc.getSourceElement().getSystemId()))) {
                    compileError("A stylesheet cannot " + getLocalPart() + " itself",
                            (this instanceof XSLInclude ? "XTSE0180" : "XTSE0210"));
                    return true;
                }
                anc = anc.getImporter();
            }
        }
        return false;
    }

    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {
        // no action. The node will never be compiled, because it replaces itself
        // by the contents of the included file.
    }
}

