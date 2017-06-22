////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.tree.linked.NodeFactory;
import net.sf.saxon.tree.linked.TextImpl;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.DecimalValue;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.util.List;

/**
  * Class StyleNodeFactory. <br>
  * A Factory for nodes in the stylesheet tree. <br>
  * Currently only allows Element nodes to be user-constructed.
  * @author Michael H. Kay
  */

public class StyleNodeFactory implements NodeFactory {


    protected Configuration config;
    protected NamePool namePool;
    protected DecimalValue processorVersion = DecimalValue.TWO;

    /**
     * Create the node factory for representing an XSLT stylesheet as a tree structure
     * @param config the Saxon configuration
     */

    public StyleNodeFactory(Configuration config) {
		this.config = config;
        namePool = config.getNamePool();
    }

    /**
     * Set the XSLT processor version to be used by this node factory. This must
     * be 0.0, 2.0 or 3.0. The value 3.0 is ignored in Saxon-HE. The value 0.0 indicates that the
     * processor version is to be taken from the version attribute of the xsl:stylesheet element.
     * @param version the XSLT version number, as a decimal
     */

    public void setXsltProcessorVersion(DecimalValue version) {
        processorVersion = version;
    }

    /**
     * Get the XSLT processor version to be used by this node factory.
     * @return the processor version (always 2.0 or 3.0 once the stylesheet version attribute
     * has been read)
     */

    public DecimalValue getXsltProcessorVersion() {
        return processorVersion;
    }

    /**
    * Create an Element node. Note, if there is an error detected while constructing
    * the Element, we add the element anyway, and return success, but flag the element
    * with a validation error. This allows us to report more than
    * one error from a single compilation.
     */

    public ElementImpl makeElementNode(
            NodeInfo parent,
            NodeName elemName,
            SchemaType elemType,
            boolean isNilled,
            AttributeCollectionImpl attlist,
            NamespaceBinding[] namespaces,
            int namespacesUsed,
            PipelineConfiguration pipe,
            int locationId,
            int sequence)
    {
        int nameCode = elemName.allocateNameCode(pipe.getConfiguration().getNamePool());
        boolean toplevel = (parent instanceof XSLStylesheet);
        String baseURI = null;
        int lineNumber = -1;
        int columnNumber = -1;
        LocationProvider locator = pipe.getLocationProvider();
        if (locator!=null) {
            baseURI = locator.getSystemId(locationId);
            lineNumber = locator.getLineNumber(locationId);
            columnNumber = locator.getColumnNumber(locationId);
        }

        if (parent instanceof DataElement) {
            DataElement d = new DataElement();
            d.setNamespaceDeclarations(namespaces, namespacesUsed);
            d.initialise(elemName, elemType, attlist, parent, sequence);
            d.setLocation(baseURI, lineNumber, columnNumber);
            return d;
        }

    	int f = nameCode&0xfffff;

    	// Try first to make an XSLT element

    	StyleElement e = makeXSLElement(f, parent);
        if (e instanceof XSLStylesheet && parent.getNodeKind() != Type.DOCUMENT) {
            e = new AbsentExtensionElement();
            e.setValidationError(new XPathException(elemName.getDisplayName() + " can only appear at the outermost level"), StyleElement.REPORT_ALWAYS);
        }

		if (e != null) {  // recognized as an XSLT element

            e.setNamespaceDeclarations(namespaces, namespacesUsed);
            e.initialise(elemName, elemType, attlist, parent, sequence);
            e.setLocation(baseURI, lineNumber, columnNumber);
            // We're not catching multiple errors in the following attributes, but catching each of the
            // exceptions helps to ensure we don't report spurious errors through not processing some
            // of the attributes when others are faulty.
            try {
	            e.processExtensionElementAttribute("");
            } catch (TransformerException err) {
	            e.setValidationError(err, StyleElement.REPORT_ALWAYS);
	        }
            try {
	            e.processExcludedNamespaces("");
            } catch (TransformerException err) {
	            e.setValidationError(err, StyleElement.REPORT_ALWAYS);
	        }
            try {
	            e.processVersionAttribute("");
            } catch (TransformerException err) {
	            e.setValidationError(err, StyleElement.REPORT_ALWAYS);
	        }
	        e.processDefaultXPathNamespaceAttribute("");
            try {
                e.processExpandTextAttribute("");
            } catch (XPathException err) {
	            e.setValidationError(err, StyleElement.REPORT_ALWAYS);
	        }

            if (e instanceof XSLStylesheet && processorVersion.compareTo(DecimalValue.ZERO) == 0) {
                DecimalValue effectiveVersion = e.getEffectiveVersion();
                processorVersion = (effectiveVersion.equals(DecimalValue.THREE) ?
                        DecimalValue.THREE : DecimalValue.TWO);
            }
            return e;

        }

        short uriCode = namePool.getURICode(nameCode);
        String uri = namePool.getURI(nameCode);

        if (parent instanceof XSLStylesheet && uriCode != 0 && uriCode != NamespaceConstant.XSLT_CODE) {
            DataElement d = new DataElement();
            d.setNamespaceDeclarations(namespaces, namespacesUsed);
            d.initialise(elemName, elemType, attlist, parent, sequence);
            d.setLocation(baseURI, lineNumber, columnNumber);
            return d;

        } else {   // not recognized as an XSLT element, not top-level

            String localname = namePool.getLocalName(nameCode);
            StyleElement temp = null;

            // Detect a misspelt XSLT element, or a 3.0 element used in a 2.0 stylesheet

            if (uri.equals(NamespaceConstant.XSLT)) {
                if (parent instanceof XSLStylesheet) {
                    if (((XSLStylesheet)parent).getEffectiveVersion().compareTo(DecimalValue.TWO) <= 0 ) {
                        temp = new AbsentExtensionElement();
                        temp.setValidationError(new XPathException("Unknown top-level XSLT declaration"),
                               StyleElement.REPORT_UNLESS_FORWARDS_COMPATIBLE );
                    }
                } else {
                    temp = new AbsentExtensionElement();
                    temp.initialise(elemName, elemType, attlist, parent, sequence);
                    temp.setLocation(baseURI, lineNumber, columnNumber);
                    try {
                        temp.processStandardAttributes("");
                        if (temp.getEffectiveVersion().compareTo(DecimalValue.TWO) > 0) {
                            temp.setValidationError(new XPathException("Unknown XSLT instruction"),
                                    StyleElement.REPORT_UNLESS_FALLBACK_AVAILABLE);
                        } else {
                            temp.setValidationError(new XPathException("Unknown XSLT instruction"),
                                    StyleElement.REPORT_IF_INSTANTIATED);
                        }
                    } catch (XPathException err) {
                        temp.setValidationError(err, StyleElement.REPORT_ALWAYS);
                    }
                }
            }

            // Detect an unrecognized element in the Saxon namespace

            if (uri.equals(NamespaceConstant.SAXON)) {
                try {
                    XPathException te = new XPathException(namePool.getDisplayName(nameCode) +
                                    " is not recognized as a Saxon instruction");
                    te.setLocator(pipe.getSourceLocation(locationId));
                    te.setErrorCode(SaxonErrorCode.SXWN9008);
                    pipe.getErrorListener().warning(te);
                } catch (TransformerException e1) {
                    // no action
                }
            }

            Class assumedClass = LiteralResultElement.class;

            // We can't work out the final class of the node until we've examined its attributes
            // such as version and extension-element-prefixes; but we can have a good guess, and
            // change it later if need be.

            if (temp==null) {
                temp = new LiteralResultElement();
            }

            temp.setNamespaceDeclarations(namespaces, namespacesUsed);

            try {
                temp.initialise(elemName, elemType, attlist, parent, sequence);
                temp.setLocation(baseURI, lineNumber, columnNumber);
                temp.processStandardAttributes(NamespaceConstant.XSLT);
            } catch (XPathException err) {
                temp.setValidationError(err, StyleElement.REPORT_ALWAYS);
            }

            // Now we work out what class of element we really wanted, and change it if necessary

            TransformerException reason;
            Class actualClass;

            if (uri.equals(NamespaceConstant.XSLT)) {
                reason = new XPathException("Unknown XSLT element: " + localname);
                ((XPathException)reason).setErrorCode("XTSE0010");
                ((XPathException)reason).setIsStaticError(true);
                actualClass = AbsentExtensionElement.class;
                temp.setValidationError(reason, StyleElement.REPORT_UNLESS_FALLBACK_AVAILABLE);

            } else if (!toplevel && temp.isExtensionNamespace(uri)) {

                // if we can't instantiate an extension element, we don't give up
                // immediately, because there might be an xsl:fallback defined. We
                // create a surrogate element called AbsentExtensionElement, and
                // save the reason for failure just in case there is no xsl:fallback

                actualClass = AbsentExtensionElement.class;
                XPathException se = new XPathException("Unknown extension instruction", temp);
                se.setErrorCode("XTDE1450");
                reason = se;
                temp.setValidationError(reason, StyleElement.REPORT_IF_INSTANTIATED);

            } else {
                actualClass = LiteralResultElement.class;
            }

            StyleElement node;
            if (actualClass.equals(assumedClass)) {
                node = temp;    // the original element will do the job
            } else {
                try {
                    node = (StyleElement)actualClass.newInstance();
                } catch (InstantiationException err1) {
                    throw new TransformerFactoryConfigurationError(err1, "Failed to create instance of " + actualClass.getName());
                } catch (IllegalAccessException err2) {
                    throw new TransformerFactoryConfigurationError(err2, "Failed to access class " + actualClass.getName());
                }
                node.substituteFor(temp);   // replace temporary node with the new one
            }
            return node;
        }
    }

	/**
	 * Make an XSL element node
     *
     * @param f the fingerprint of the node name
     * @param parent the parent node
     * @return the constructed element node
	*/

	/*@Nullable*/ protected StyleElement makeXSLElement(int f, NodeInfo parent) {
        switch (f) {
		case StandardNames.XSL_ANALYZE_STRING:
            return new XSLAnalyzeString();
		case StandardNames.XSL_APPLY_IMPORTS:
            return new XSLApplyImports();
		case StandardNames.XSL_APPLY_TEMPLATES:
            return new XSLApplyTemplates();
		case StandardNames.XSL_ATTRIBUTE:
            return new XSLAttribute();
		case StandardNames.XSL_ATTRIBUTE_SET:
            return new XSLAttributeSet();
		case StandardNames.XSL_CALL_TEMPLATE:
            return new XSLCallTemplate();
		case StandardNames.XSL_CHARACTER_MAP:
            return new XSLCharacterMap();
		case StandardNames.XSL_CHOOSE:
            return new XSLChoose();
		case StandardNames.XSL_COMMENT:
            return new XSLComment();
		case StandardNames.XSL_COPY:
            return new XSLCopy();
		case StandardNames.XSL_COPY_OF:
            return new XSLCopyOf();
		case StandardNames.XSL_DECIMAL_FORMAT:
            return new XSLDecimalFormat();
		case StandardNames.XSL_DOCUMENT:
            return new XSLDocument();
		case StandardNames.XSL_ELEMENT:
            return new XSLElement();
		case StandardNames.XSL_FALLBACK:
            return new XSLFallback();
		case StandardNames.XSL_FOR_EACH:
            return new XSLForEach();
		case StandardNames.XSL_FOR_EACH_GROUP:
            return new XSLForEachGroup();
		case StandardNames.XSL_FUNCTION:
            return new XSLFunction();
		case StandardNames.XSL_IF:
            return new XSLIf();
		case StandardNames.XSL_IMPORT:
            return new XSLImport();
		case StandardNames.XSL_IMPORT_SCHEMA:
            return new XSLImportSchema();
		case StandardNames.XSL_INCLUDE:
            return new XSLInclude();
		case StandardNames.XSL_KEY:
            return new XSLKey();
		case StandardNames.XSL_MATCHING_SUBSTRING:
            return new XSLMatchingSubstring();
		case StandardNames.XSL_MESSAGE:
            return new XSLMessage();
		case StandardNames.XSL_NEXT_MATCH:
            return new XSLNextMatch();
		case StandardNames.XSL_NON_MATCHING_SUBSTRING:
            return new XSLMatchingSubstring();	//sic
		case StandardNames.XSL_NUMBER:
            return new XSLNumber();
		case StandardNames.XSL_NAMESPACE:
            return new XSLNamespace();
		case StandardNames.XSL_NAMESPACE_ALIAS:
            return new XSLNamespaceAlias();
		case StandardNames.XSL_OTHERWISE:
            return new XSLOtherwise();
		case StandardNames.XSL_OUTPUT:
            return new XSLOutput();
		case StandardNames.XSL_OUTPUT_CHARACTER:
            return new XSLOutputCharacter();
		case StandardNames.XSL_PARAM:
            return (parent instanceof XSLStylesheet) ? new XSLGlobalParam() : new XSLLocalParam();
		case StandardNames.XSL_PERFORM_SORT:
            return new XSLPerformSort();
		case StandardNames.XSL_PRESERVE_SPACE:
            return new XSLPreserveSpace();
		case StandardNames.XSL_PROCESSING_INSTRUCTION:
            return new XSLProcessingInstruction();
		case StandardNames.XSL_RESULT_DOCUMENT:
            return new XSLResultDocument();
		case StandardNames.XSL_SEQUENCE:
            return new XSLSequence();
		case StandardNames.XSL_SORT:
            return new XSLSort();
		case StandardNames.XSL_STRIP_SPACE:
            return new XSLPreserveSpace();
		case StandardNames.XSL_STYLESHEET:
            return new XSLStylesheet();
		case StandardNames.XSL_TEMPLATE:
            return new XSLTemplate();
		case StandardNames.XSL_TEXT:
            return new XSLText();
		case StandardNames.XSL_TRANSFORM:
            return new XSLStylesheet();
		case StandardNames.XSL_VALUE_OF:
            return new XSLValueOf();
		case StandardNames.XSL_VARIABLE:
            return (parent instanceof XSLStylesheet) ? new XSLGlobalVariable() : new XSLLocalVariable();
		case StandardNames.XSL_WITH_PARAM:
            return new XSLWithParam();
		case StandardNames.XSL_WHEN:
            return new XSLWhen();
        default:
            return null;
        }
	}

    /**
     * Make a text node
     *
     * @param content the content of the text node
     * @return the constructed text node
     */
    public TextImpl makeTextNode(NodeInfo parent, CharSequence content) {
        return new TextImpl(content.toString());
    }

    /**
     * Method to support the element-available() function
     * @param uri the namespace URI
     * @param localName the local Name
     * @return true if an extension element of this name is recognized
    */

    public boolean isElementAvailable(String uri, String localName) {
    	int fingerprint = namePool.getFingerprint(uri, localName);
    	if (uri.equals(NamespaceConstant.XSLT)) {
    		if (fingerprint == -1) {
                return false; 	// all names are pre-registered
            }
    		StyleElement e = makeXSLElement(fingerprint, null);
    		if (e != null) {
                return e.isInstruction();
            }
    	}
        return false;
    }

    public void makeAccumulatorManager(PreparedStylesheet pss) {
        // no action
    }

    /**
     * Validate a text node in the stylesheet
     * @param node the text node
     * @return true if the node is a text value template containing one or more expressions
     * @throws XPathException if the node is invalid
     */

    public boolean validateTextNode(NodeInfo node) throws XPathException {
        // no action (overridden in Saxon-PE subclass)
        return false;
    }

    /**
     * Compile a content value text node. Dummy implementation (these can exist only in Saxon-PE or higher)
     */

    public void compileContentValueTemplate(TextImpl node, List<Expression> contents) throws XPathException {
        // no action
    }


}

