////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;

import java.util.*;

/**
* An xsl:output element in the stylesheet.
*/
public class XSLOutput extends StyleElement {

	private static final Logger LOGGER = Logger.getLogger(XSLOutput.class);
	
    private StructuredQName outputFormatName;
    private String method = null;
    private String version = null;
    private String htmlVersion = null;
    private String indent = null;
    private String encoding = null;
    private String mediaType = null;
    private String doctypeSystem = null;
    private String doctypePublic = null;
    private String omitDeclaration = null;
    private String standalone = null;
    private String cdataElements = null;
    private String includeContentType = null;
    private String nextInChain = null;
    private String suppressIndentation = null;
    private String doubleSpace = null;
    private String representation = null;
    private String indentSpaces = null;
    private String lineLength = null;
    private String byteOrderMark = null;
    private String escapeURIAttributes = null;
    private String normalizationForm = null;
    private String recognizeBinary = null;
    private String requireWellFormed = null;
    private String undeclareNamespaces = null;
    private String useCharacterMaps = null;
    private String attributeOrder = null;
    private HashMap<String, String> userAttributes = null;

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
		String nameAtt = null;

        for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getNodeName(a).getStructuredQName().getClarkName();
			if (f.equals(StandardNames.NAME)) {
        		nameAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals(StandardNames.METHOD)) {
        		method = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.VERSION)) {
        		version = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.HTML_VERSION)) {
        		htmlVersion = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.BYTE_ORDER_MARK)) {
                byteOrderMark = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.ENCODING)) {
        		encoding = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.OMIT_XML_DECLARATION)) {
        		omitDeclaration = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.STANDALONE)) {
        		standalone = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.DOCTYPE_PUBLIC)) {
        		doctypePublic = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.DOCTYPE_SYSTEM)) {
        		doctypeSystem = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.CDATA_SECTION_ELEMENTS)) {
        		cdataElements = atts.getValue(a);
        	} else if (f.equals(StandardNames.INDENT)) {
        		indent = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.MEDIA_TYPE)) {
        		mediaType = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.INCLUDE_CONTENT_TYPE)) {
        		includeContentType = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.NORMALIZATION_FORM)) {
        		normalizationForm = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.ESCAPE_URI_ATTRIBUTES)) {
        		escapeURIAttributes = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.USE_CHARACTER_MAPS)) {
        		useCharacterMaps = atts.getValue(a);
            } else if (f.equals(StandardNames.UNDECLARE_PREFIXES)) {
        		undeclareNamespaces = atts.getValue(a);
            } else if (f.equals(StandardNames.SAXON_CHARACTER_REPRESENTATION)) {
        		representation = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.SAXON_INDENT_SPACES)) {
        		indentSpaces = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_LINE_LENGTH)) {
        		lineLength = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_SUPPRESS_INDENTATION)) {
        		suppressIndentation = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_DOUBLE_SPACE)) {
        		doubleSpace = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_NEXT_IN_CHAIN)) {
        		nextInChain = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_RECOGNIZE_BINARY)) {
                recognizeBinary = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_REQUIRE_WELL_FORMED)) {
                requireWellFormed = Whitespace.trim(atts.getValue(a));
            } else if (f.equals(StandardNames.SAXON_ATTRIBUTE_ORDER)) {
                attributeOrder = Whitespace.trim(atts.getValue(a));
        	} else {
        	    String attributeURI = atts.getURI(a);
				if ("".equals(attributeURI) || NamespaceConstant.XSLT.equals(attributeURI) ||
					NamespaceConstant.SAXON.equals(attributeURI)) {
					checkUnknownAttribute(atts.getNodeName(a));
        		} else {
					String name = '{' + attributeURI + '}' + atts.getLocalName(a);
					if (userAttributes == null) {
						userAttributes = new HashMap<>(5);
					}
					userAttributes.put(name, atts.getValue(a));
				}
        	}
        }
		if (nameAtt != null) {
			try {
				outputFormatName = makeQName(nameAtt);
			} catch (NamespaceException | XPathException err) {
				LOGGER.error("Could not create a structured QName", err);
				compileError(err.getMessage(), "XTSE1570");
			}
		}
    }

    /**
     * Get the name of the xsl:output declaration
     * @return the name, as a structured QName; or null for an unnamed output declaration
     */
    public StructuredQName getFormatQName() {
        return outputFormatName;
    }

    @Override
    public void validate(Declaration decl) throws XPathException {
        checkTopLevel("XTSE0010");
        checkEmpty();
    }
    
    @Override
	public void compileDeclaration(Executable exec, Declaration decl) {}
    /**
     * Validate the properties,
     * and return the values as additions to a supplied Properties object.
     * @param details the Properties object to be populated with property values
     * @param precedences a HashMap to be populated with information about the precedence
     * of the property values: the key is the property name as a Clark name, the value
     * is a boxed integer giving the property's import precedence
     * @param thisPrecedence the precedence of thi instruction
     * @throws net.sf.saxon.trans.XPathException if an error is found
    */
	protected void gatherOutputProperties(Properties details, HashMap<String, Integer> precedences, int thisPrecedence)
		throws XPathException {
		if (method != null) {
			if ("xml".equals(method) || "html".equals(method) || "text".equals(method) || "xhtml".equals(method)) {
				checkAndPut(OutputKeys.METHOD, method, details, precedences, thisPrecedence);
				// details.put(OutputKeys.METHOD, method);
			} else {
				String[] parts;
				try {
					parts = getConfiguration().getNameChecker().getQNameParts(method);
					String prefix = parts[0];
					if (prefix.length() == 0) {
						compileError("method must be xml, html, xhtml, or text, or a prefixed name", "XTSE1570");
					} else {
						String uri = getURIForPrefix(prefix, false);
						if (uri == null) {
							undeclaredNamespaceError(prefix, "XTSE0280");
						}
						checkAndPut(OutputKeys.METHOD, '{' + uri + '}' + parts[1], details, precedences,
							thisPrecedence);
						// details.put(OutputKeys.METHOD, '{' + uri + '}' + parts[1] );
					}
				} catch (QNameException e) {
					LOGGER.error("Could not obtain the QNameParts for method: " + method, e);
					compileError("Invalid method name. " + e.getMessage(), "XTSE1570");
				}
			}
		}

        if (byteOrderMark != null) {
            checkAndPut(SaxonOutputKeys.BYTE_ORDER_MARK, byteOrderMark, details, precedences, thisPrecedence);
        }

        if (version != null) {
            checkAndPut(OutputKeys.VERSION, version, details, precedences, thisPrecedence);
        }

        if (htmlVersion != null) {
            checkAndPut(SaxonOutputKeys.HTML_VERSION, htmlVersion, details, precedences, thisPrecedence);
        }

        if (indent != null) {
            checkAndPut(OutputKeys.INDENT, indent, details, precedences, thisPrecedence);
        }

        if (indentSpaces != null) {
            checkAndPut(SaxonOutputKeys.INDENT_SPACES, indentSpaces, details, precedences, thisPrecedence);
        }

        if (lineLength != null) {
            checkAndPut(SaxonOutputKeys.LINE_LENGTH, lineLength, details, precedences, thisPrecedence);
        }

		if (suppressIndentation != null) {
			String existing = details.getProperty(SaxonOutputKeys.SUPPRESS_INDENTATION);
			if (existing == null) {
				// support retained for backwards compatibility
				existing = details.getProperty("{http://saxon.sf.net/}suppress-indentation");
			}
			if (existing == null) {
				existing = "";
			}
			String s = SaxonOutputKeys.parseListOfNodeNames(suppressIndentation, this, true, false,
				getConfiguration().getNameChecker(), "XTSE0280");
			details.setProperty(SaxonOutputKeys.SUPPRESS_INDENTATION, existing + s);
		}

        if (doubleSpace != null) {
            String existing = details.getProperty(SaxonOutputKeys.DOUBLE_SPACE);
            if (existing==null) {
                existing = "";
            }
            String s = SaxonOutputKeys.parseListOfNodeNames(
                    doubleSpace, this, true, false, getConfiguration().getNameChecker(), "XTSE0280");
			details.setProperty(SaxonOutputKeys.DOUBLE_SPACE, existing + s);
        }

        if (encoding != null) {
            checkAndPut(OutputKeys.ENCODING, encoding, details, precedences, thisPrecedence);
        }

        if (mediaType != null) {
            checkAndPut(OutputKeys.MEDIA_TYPE, mediaType, details, precedences, thisPrecedence);
        }

        if (doctypeSystem != null) {
            checkAndPut(OutputKeys.DOCTYPE_SYSTEM, doctypeSystem, details, precedences, thisPrecedence);
        }

        if (doctypePublic != null) {
            checkAndPut(OutputKeys.DOCTYPE_PUBLIC, doctypePublic, details, precedences, thisPrecedence);
        }

        if (omitDeclaration != null) {
            checkAndPut(OutputKeys.OMIT_XML_DECLARATION, omitDeclaration, details, precedences, thisPrecedence);
        }

        if (standalone != null) {
            checkAndPut(OutputKeys.STANDALONE, standalone, details, precedences, thisPrecedence);
        }

        if (cdataElements != null) {
            String existing = details.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
			if (existing == null) {
				existing = "";
			}
			String s = SaxonOutputKeys.parseListOfNodeNames(cdataElements, this, true, false,
				getConfiguration().getNameChecker(), "XTSE0280");
			details.setProperty(OutputKeys.CDATA_SECTION_ELEMENTS, existing + s);
        }

        if (normalizationForm != null && !normalizationForm.equals("none")) {
            checkAndPut(SaxonOutputKeys.NORMALIZATION_FORM, normalizationForm, details, precedences, thisPrecedence);
        }

        if (undeclareNamespaces != null) {
            checkAndPut(SaxonOutputKeys.UNDECLARE_PREFIXES, undeclareNamespaces, details, precedences, thisPrecedence);
        }

        if (useCharacterMaps != null) {
            String s = prepareCharacterMaps(this, useCharacterMaps, details);
            details.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, s);
        }

        if (representation != null) {
            checkAndPut(SaxonOutputKeys.CHARACTER_REPRESENTATION, representation, details, precedences, thisPrecedence);
        }

        if (includeContentType != null) {
            checkAndPut(SaxonOutputKeys.INCLUDE_CONTENT_TYPE, includeContentType, details, precedences, thisPrecedence);
        }

		if (escapeURIAttributes != null) {
			checkAndPut(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES, escapeURIAttributes, details, precedences,
				thisPrecedence);
        }

        if (nextInChain != null) {
            checkAndPut(SaxonOutputKeys.NEXT_IN_CHAIN, nextInChain, details, precedences, thisPrecedence);
            checkAndPut(SaxonOutputKeys.NEXT_IN_CHAIN_BASE_URI, getSystemId(), details, precedences, thisPrecedence);
        }

        if (recognizeBinary != null) {
            checkAndPut(SaxonOutputKeys.RECOGNIZE_BINARY, recognizeBinary, details, precedences, thisPrecedence);
        }

        if (requireWellFormed != null) {
            checkAndPut(SaxonOutputKeys.REQUIRE_WELL_FORMED, requireWellFormed, details, precedences, thisPrecedence);
        }

        if (attributeOrder != null) {
            String existing = details.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
			if (existing == null) {
				existing = "";
			}
			String s = SaxonOutputKeys.parseListOfNodeNames(attributeOrder, this, false, false,
				getConfiguration().getNameChecker(), "XTSE0280");
            details.setProperty(SaxonOutputKeys.ATTRIBUTE_ORDER, existing + ' ' + s);
        }

        // deal with user-defined attributes

		if (userAttributes != null) {
			for (Map.Entry<String, String> e : userAttributes.entrySet()) {
				details.setProperty(e.getKey(), e.getValue());
			}
		}
	}

    /**
     * Add an output property to the list of properties after checking that it is consistent
     * with other properties
     * @param property the name of the property
     * @param value the value of the ptoperty
     * @param props the list of properties to be updated
     * @param precedences the import precedence of each property
     * @param thisPrecedence the import precedence of the declaration containing this value
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */
	private void checkAndPut(String property, String value, Properties props, HashMap<String, Integer> precedences,
		int thisPrecedence) throws XPathException {
        try {
            SaxonOutputKeys.checkOutputProperty(property, value, getConfiguration());
        } catch (XPathException err) {
        	LOGGER.error("Could not check output property", err);
            compileError(err.getMessage(), "XTSE0020");
            return;
        }
        String old = props.getProperty(property);
        if (old == null) {
            props.setProperty(property, value);
            precedences.put(property, thisPrecedence);
        } else if (old.equals(value)) {

        } else {
            Integer oldPrec = precedences.get(property);
			if (oldPrec == null) {
				return;
			}
            if (oldPrec > thisPrecedence) {
                // ignore this value, the other has higher precedence
            } else if (oldPrec == thisPrecedence) {
                compileError("Conflicting values for output property " + property, "XTSE1560");
            } else {
                // this has higher precedence: can't happen
                throw new IllegalStateException("Output properties must be processed in decreasing precedence order");
            }
        }
    }

    /**
     * Process the use-character-maps attribute
     * @param element the stylesheet element on which the use-character-maps attribute appears
     * @param useCharacterMaps the value of the use-character-maps attribute
     * @param details The existing output properties
     * @return the augmented value of the use-character-maps attribute in Clark notation
     * @throws XPathException if the value is invalid
     */
	public static String prepareCharacterMaps(StyleElement element, String useCharacterMaps, Properties details)
		throws XPathException {
		PrincipalStylesheetModule psm = element.getPrincipalStylesheetModule();
		String existing = details.getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
		if (existing == null) {
			existing = "";
		}
		StringBuilder s = new StringBuilder();
		StringTokenizer st = new StringTokenizer(useCharacterMaps, " \t\n\r", false);
		while (st.hasMoreTokens()) {
			String displayname = st.nextToken();
			try {
				StructuredQName qName = element.makeQName(displayname);
				Declaration decl = psm.getCharacterMap(qName);
				if (decl == null) {
					element.compileError("No character-map named '" + displayname + "' has been defined", "XTSE1590");
				}
				s.append(' ').append(qName.getClarkName());
			} catch (NamespaceException err) {
				LOGGER.error("Could not compile the time error", err);
				element.undeclaredNamespaceError(err.getPrefix(), "XTSE0280");
			}
		}
		existing = s + existing;
		return existing;
	}
}
