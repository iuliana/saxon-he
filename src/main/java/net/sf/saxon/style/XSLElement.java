////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:element element in the stylesheet. <br>
 */

public class XSLElement extends StyleElement {

    /*@Nullable*/ private Expression elementName;
    private Expression namespace = null;
    private String use;
    private AttributeSet[] attributeSets = null;
    private int validation;
    private SchemaType schemaType = null;
    private boolean inheritNamespaces = true;
    private Expression onEmpty;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();

        String nameAtt = null;
        String namespaceAtt = null;
        String validationAtt = null;
        String typeAtt = null;
        String inheritAtt = null;
        String onEmptyAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("name")) {
                nameAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("namespace")) {
                namespaceAtt = atts.getValue(a);
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("inherit-namespaces")) {
                inheritAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("use-attribute-sets")) {
                use = atts.getValue(a);
            } else if (f.equals("on-empty")) {
                onEmptyAtt = atts.getValue(a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
		} else {
			elementName = makeAttributeValueTemplate(nameAtt);
			if (elementName instanceof StringLiteral) {
				String eName = ((StringLiteral) elementName).getStringValue();
				if (!getConfiguration().getNameChecker().isQName(eName)) {
					compileError("Element name " + Err.wrap(eName, Err.ELEMENT) + " is not a valid QName", "XTDE0820");
					// to prevent duplicate error messages:
					elementName = new StringLiteral("saxon-error-element");
				}
			}
		}

        if (namespaceAtt != null) {
            namespace = makeAttributeValueTemplate(namespaceAtt);
            if (namespace instanceof StringLiteral) {
                if (!StandardURIChecker.getInstance().isValidURI(((StringLiteral)namespace).getStringValue())) {
                    compileError("The value of the namespace attribute must be a valid URI", "XTDE0835");
                }
            }
        }

        if (validationAtt != null) {
            validation = Validation.getCode(validationAtt);
            if (validation != Validation.STRIP && !getPreparedStylesheet().isSchemaAware()) {
                validation = Validation.STRIP;
                compileError("To perform validation, a schema-aware XSLT processor is needed", "XTSE1660");
            }
            if (validation == Validation.INVALID) {
                compileError("Invalid value for @validation attribute. " +
                        "Permitted values are (strict, lax, preserve, strip)", "XTSE0020");
            }
        } else {
            validation = getContainingStylesheet().getDefaultValidation();
        }

        if (typeAtt != null) {
            if (!getPreparedStylesheet().isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            schemaType = getSchemaType(typeAtt);
            validation = Validation.BY_TYPE;
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }

        if (inheritAtt != null) {
            checkAttributeValue("inherit-namespaces", inheritAtt, false, StyleElement.YES_NO);
            inheritNamespaces = (inheritAtt.equals("yes"));
        }

        if (onEmptyAtt != null) {
            if (!isXslt30Processor()) {
                compileError("The 'on-empty' attribute requires XSLT 3.0");
            }
            onEmpty = makeExpression(onEmptyAtt);
        }
    }

    public void validate(Declaration decl) throws XPathException {
        if (use != null) {
            // find any referenced attribute sets
            attributeSets = getAttributeSets(use, null);
        }
        elementName = typeCheck("name", elementName);
        namespace = typeCheck("namespace", namespace);
        onEmpty = typeCheck("on-empty", onEmpty);
    }

    public Expression compile(Executable exec, Declaration decl) throws XPathException {

        NamespaceResolver nsContext = null;

        // deal specially with the case where the element name is known statically

        if (elementName instanceof StringLiteral) {
            CharSequence qName = ((StringLiteral)elementName).getStringValue();

            String[] parts;
            try {
                parts = getConfiguration().getNameChecker().getQNameParts(qName);
            } catch (QNameException e) {
                compileError("Invalid element name: " + qName, "XTDE0820");
                return null;
            }

            String nsuri = null;
            if (namespace instanceof StringLiteral) {
                nsuri = ((StringLiteral)namespace).getStringValue();
                if (nsuri.length() == 0) {
                    parts[0] = "";
                }
            } else if (namespace == null) {
                nsuri = getURIForPrefix(parts[0], true);
                if (nsuri == null) {
                    undeclaredNamespaceError(parts[0], "XTDE0830");
                }
            }
            if (nsuri != null) {
                // Local name and namespace are both known statically: generate a FixedElement instruction
                FingerprintedQName qn = new FingerprintedQName(parts[0], nsuri, parts[1]);
                qn.allocateNameCode(getNamePool());
                FixedElement inst = new FixedElement(qn,
                        NamespaceBinding.EMPTY_ARRAY,
                        inheritNamespaces,
                        true,
                        schemaType,
                        validation);
                inst.setBaseURI(getBaseURI());
                return compileContentExpression(exec, decl, inst);
            }
        } else {
            // if the namespace URI must be deduced at run-time from the element name
            // prefix, we need to save the namespace context of the instruction

            if (namespace == null) {
                nsContext = makeNamespaceContext();
            }
        }

        ComputedElement inst = new ComputedElement(elementName,
                namespace,
                nsContext,
                //(nsContext==null ? null : nsContext.getURIForPrefix("", true)),
                schemaType,
                validation,
                inheritNamespaces,
                false);
        if (onEmpty != null) {
            inst.setOnEmpty(onEmpty);
        }
        return compileContentExpression(exec, decl, inst);
    }

    private Expression compileContentExpression(Executable exec, Declaration decl, ElementCreator inst) throws XPathException {
        Expression content = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), true);

        if (attributeSets != null) {
            UseAttributeSets use = new UseAttributeSets(attributeSets);
            if (content == null) {
                content = use;
            } else {
                content = Block.makeBlock(use, content);
                content.setLocationId(
                            allocateLocationId(getSystemId(), getLineNumber()));
            }
        }
        if (content == null) {
            content = Literal.makeEmptySequence();
        }
        inst.setContentExpression(content);
        return inst;
    }


}

