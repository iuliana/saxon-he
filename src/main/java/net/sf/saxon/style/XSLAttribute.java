////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import org.apache.log4j.Logger;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.ComputedAttribute;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.FixedAttribute;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
* xsl:attribute element in stylesheet. <br>
*/
public final class XSLAttribute extends XSLLeafNodeConstructor {

	private static final Logger LOGGER = Logger.getLogger(XSLAttribute.class);
	
    private Expression attributeName;
    private Expression separator;
    private Expression namespace = null;
    private Expression onEmpty = null;
    private int validationAction = Validation.PRESERVE;
    private SimpleType schemaType;

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();

		String nameAtt = null;
		String namespaceAtt = null;
        String selectAtt = null;
        String separatorAtt = null;
        String onEmptyAtt = null;
		String validationAtt = null;
		String typeAtt = null;

		for (int a = 0; a < atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals("name")) {
				nameAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals("namespace")) {
				namespaceAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals("select")) {
				selectAtt = atts.getValue(a);
			} else if (f.equals("separator")) {
				separatorAtt = atts.getValue(a);
			} else if (f.equals("on-empty")) {
				onEmptyAtt = atts.getValue(a);
			} else if (f.equals("validation")) {
				validationAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals("type")) {
				typeAtt = Whitespace.trim(atts.getValue(a));
			} else {
				checkUnknownAttribute(atts.getNodeName(a));
			}
		}

		if (nameAtt == null) {
			reportAbsence("name");
			return;
		}
        attributeName = makeAttributeValueTemplate(nameAtt);
		if (attributeName instanceof StringLiteral) {
			if (!getConfiguration().getNameChecker().isQName(((StringLiteral) attributeName).getStringValue())) {
				invalidAttributeName("Attribute name " + Err.wrap(nameAtt) + " is not a valid QName");
			}
			if (namespace == null && nameAtt.equals("xmlns")) {
				invalidAttributeName("Invalid attribute name: xmlns");
			}
            if (nameAtt.startsWith("xmlns:")) {
                if (namespaceAtt == null) {
                    invalidAttributeName("Invalid attribute name: " + Err.wrap(nameAtt));
                } else {
                    // ignore the prefix "xmlns"
                    nameAtt = nameAtt.substring(6);
                    attributeName = new StringLiteral(nameAtt);
                }
            }
        }

		if (namespaceAtt != null) {
			namespace = makeAttributeValueTemplate(namespaceAtt);
			if (namespace instanceof StringLiteral &&
				!StandardURIChecker.getInstance().isValidURI(((StringLiteral) namespace).getStringValue())) {
				compileError("The value of the namespace attribute must be a valid URI", "XTDE0865");
			}
		}
        if (selectAtt != null) {
            select = makeExpression(selectAtt);
        }

        if (separatorAtt == null) {
            if (selectAtt == null) {
                separator = new StringLiteral(StringValue.EMPTY_STRING);
            } else {
                separator = new StringLiteral(StringValue.SINGLE_SPACE);
            }
        } else {
            separator = makeAttributeValueTemplate(separatorAtt);
        }

        if (onEmptyAtt != null) {
            if (isXslt30Processor()) {
                onEmpty = makeExpression(onEmptyAtt);
            } else {
                compileError("The on-empty attribute requires XSLT 3.0 to be enabled", "XTSE0090");
            }
        }

		if (validationAtt != null) {
            validationAction = Validation.getCode(validationAtt);
            if (validationAction != Validation.STRIP && !getPreparedStylesheet().isSchemaAware()) {
                validationAction = Validation.STRIP;
                compileError("To perform validation, a schema-aware XSLT processor is needed", "XTSE1660");
            }
            if (validationAction == Validation.INVALID) {
                compileError("Invalid value of validation attribute", "XTSE0020");
                validationAction = getContainingStylesheet().getDefaultValidation();
            }
        } else {
            validationAction = getContainingStylesheet().getDefaultValidation();
        }

		if (typeAtt != null) {
            if (!getPreparedStylesheet().isSchemaAware()) {
                compileError(
                        "The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            } else {
                SchemaType type = getSchemaType(typeAtt);
                if (type == null) {
                    compileError("Unknown attribute type " + typeAtt, "XTSE1520");
                } else {
                    if (type.isSimpleType()) {
                        schemaType = (SimpleType)type;
                    } else {
                        compileError("Type annotation for attributes must be a simple type", "XTSE1530");
                    }
                }
                validationAction = Validation.BY_TYPE;
            }
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The validation and type attributes are mutually exclusive", "XTSE1505");
            validationAction = getContainingStylesheet().getDefaultValidation();
            schemaType = null;
        }
    }

	private void invalidAttributeName(String message) throws XPathException {
		compileError(message, "XTDE0850");
		attributeName = new StringLiteral("saxon-error-attribute");
	}

    @Override
    public void validate(Declaration decl) throws XPathException {
		if (schemaType != null && schemaType.isNamespaceSensitive()) {
			compileError("Validation at attribute level must not specify a namespace-sensitive type (xs:QName or " +
				"xs:NOTATION)", "XTTE1545");
		}
		attributeName = typeCheck("name", attributeName);
        namespace = typeCheck("namespace", namespace);
        select = typeCheck("select", select);
        separator = typeCheck("separator", separator);
        onEmpty = typeCheck("on-empty", onEmpty);
        super.validate(decl);
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */
    protected String getErrorCodeForSelectPlusContent() {
        return "XTSE0840";
    }

    @Override
	public Expression compile(Executable exec, Declaration decl) throws XPathException {
		NamespaceResolver nsContext = null;
		// deal specially with the case where the attribute name is known statically
		if (attributeName instanceof StringLiteral && onEmpty == null) {
			String qName = Whitespace.trim(((StringLiteral) attributeName).getStringValue());
			String[] parts;
			try {
				parts = getConfiguration().getNameChecker().getQNameParts(qName);
			} catch (QNameException e) {
				LOGGER.error("Could not validate the qName: " + qName, e);
				return null;
			}
			if (namespace == null) {
				String nsuri = "";
				if (!parts[0].equals("")) {
					nsuri = getURIForPrefix(parts[0], false);
					if (nsuri == null) {
						undeclaredNamespaceError(parts[0], "XTDE0860");
						return null;
					}
				}
				NodeName attributeName = new FingerprintedQName(parts[0], nsuri, parts[1]);
				attributeName.allocateNameCode(getNamePool());
				FixedAttribute inst = new FixedAttribute(attributeName,	validationAction, schemaType);
				inst.setContainer(this); // temporarily
				compileContent(exec, decl, inst, separator);
				return inst;
			} else if (namespace instanceof StringLiteral) {
				String nsuri = ((StringLiteral) namespace).getStringValue();
				if (nsuri.equals("")) {
					parts[0] = "";
				} else if (parts[0].equals("")) {
					// Need to choose an arbitrary prefix
					// First see if the requested namespace is declared in the stylesheet
					AxisIterator iter = iterateAxis(AxisInfo.NAMESPACE);
					while (true) {
						NodeInfo ns = iter.next();
						if (ns == null) {
							break;
						}
						if (ns.getStringValue().equals(nsuri)) {
							parts[0] = ns.getLocalPart();
							break;
						}
					}
					// Otherwise see the URI is known to the namepool
					if (parts[0].equals("")) {
						String p = getNamePool().suggestPrefixForURI(((StringLiteral) namespace).getStringValue());
						if (p != null) {
							parts[0] = p;
						}
					}
					// Otherwise choose something arbitrary. This will get changed
					// if it clashes with another attribute
					if (parts[0].equals("")) {
						parts[0] = "ns0";
					}
				}
				NodeName nameCode = new FingerprintedQName(parts[0], nsuri, parts[1]);
				nameCode.allocateNameCode(getNamePool());
				FixedAttribute inst = new FixedAttribute(nameCode, validationAction, schemaType);
				compileContent(exec, decl, inst, separator);
				return inst;
			}
		} else {
			// if the namespace URI must be deduced at run-time from the attribute name
			// prefix, we need to save the namespace context of the instruction
			if (namespace == null) {
				nsContext = makeNamespaceContext();
			}
		}
		ComputedAttribute inst = new ComputedAttribute(attributeName, namespace, nsContext, validationAction,
			schemaType, false);
		inst.setOnEmpty(onEmpty);
		compileContent(exec, decl, inst, separator);
		return inst;
	}
}
