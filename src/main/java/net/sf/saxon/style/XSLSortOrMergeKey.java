////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;

public abstract class XSLSortOrMergeKey extends StyleElement {

	protected SortKeyDefinition sortKeyDefinition;
	protected Expression select;
	protected Expression order;
	protected Expression dataType = null;
	protected Expression caseOrder;
	protected Expression lang;
	protected Expression collationName;
	protected Expression stable;
	protected boolean useDefaultCollation = true;


	/**
	 * Determine whether this type of element is allowed to contain a sequence constructor
	 * @return true: yes, it may contain a sequence constructor
	 */

	public boolean mayContainSequenceConstructor() {
		return true;
	}

	protected String getErrorCode(){
		return "XTSE1015";
	}

    @Override
	public void validate(Declaration decl) throws XPathException {
		if (select != null && hasChildNodes()) {
			compileError("An "+getDisplayName()+" element with a select attribute must be empty", getErrorCode());
		}
		if (select == null && !hasChildNodes()) {
			select = new ContextItemExpression();
		}

		// Get the named or default collation

		if (useDefaultCollation) {
			collationName = new StringLiteral(getDefaultCollationName());
		}

		StringCollator stringCollator = null;
		if (collationName instanceof StringLiteral) {
			String collationString = ((StringLiteral)collationName).getStringValue();
			try {
				URI collationURI = new URI(collationString);
				if (!collationURI.isAbsolute()) {
					URI base = new URI(getBaseURI());
					collationURI = base.resolve(collationURI);
					collationString = collationURI.toString();
				}
			} catch (URISyntaxException err) {
				compileError("Collation name '" + collationString + "' is not a valid URI");
				collationString = NamespaceConstant.CODEPOINT_COLLATION_URI;
			}
			stringCollator = getPrincipalStylesheetModule().findCollation(collationString, getBaseURI());
			if (stringCollator==null) {
				compileError("Collation " + collationString + " has not been defined", "XTDE1035");
				stringCollator = CodepointCollator.getInstance();     // for recovery paths
			}
		}

		select      = typeCheck("select", select);
		order       = typeCheck("order", order);
		caseOrder   = typeCheck("case-order", caseOrder);
		lang        = typeCheck("lang", lang);
		dataType    = typeCheck("data-type", dataType);
		collationName = typeCheck("collation", collationName);


		if (select != null) {
			try {
				RoleLocator role =
					new RoleLocator(RoleLocator.INSTRUCTION, getDisplayName()+"//select", 0);
				//role.setSourceLocator(new ExpressionLocation(this));
				select = TypeChecker.staticTypeCheck(select,
                        SequenceType.ATOMIC_SEQUENCE,
                        false, role, makeExpressionVisitor());
			} catch (XPathException err) {
				compileError(err);
			}
		}

		sortKeyDefinition = new SortKeyDefinition();
		sortKeyDefinition.setOrder(order);
		sortKeyDefinition.setCaseOrder(caseOrder);
		sortKeyDefinition.setLanguage(lang);
		sortKeyDefinition.setSortKey(select, true);
		sortKeyDefinition.setDataTypeExpression(dataType);
		sortKeyDefinition.setCollationNameExpression(collationName);
		sortKeyDefinition.setCollation(stringCollator);
		sortKeyDefinition.setBaseURI(getBaseURI());
		sortKeyDefinition.setStable(stable);
		sortKeyDefinition.setBackwardsCompatible(xPath10ModeIsEnabled());
	}

	protected Expression getStable(){
		return stable;
	}

	@Override
	protected void prepareAttributes() throws XPathException {
		AttributeCollection atts = getAttributeList();

		String selectAtt = null;
		String orderAtt = null;
		String dataTypeAtt = null;
		String caseOrderAtt = null;
		String langAtt = null;
		String collationAtt = null;
		String stableAtt = null;

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.SELECT)) {
				selectAtt = atts.getValue(a);
			} else if (f.equals(StandardNames.ORDER)) {
				orderAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals(StandardNames.DATA_TYPE)) {
				dataTypeAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals(StandardNames.CASE_ORDER)) {
				caseOrderAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals(StandardNames.LANG)) {
				langAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals(StandardNames.COLLATION)) {
				collationAtt = Whitespace.trim(atts.getValue(a));
			} else if (f.equals(StandardNames.STABLE)) {
				stableAtt = Whitespace.trim(atts.getValue(a));
			} else {
				checkUnknownAttribute(atts.getNodeName(a));
			}
		}

		if (selectAtt==null) {
			//select = new ContextItemExpression();
		} else {
			select = makeExpression(selectAtt);
		}

		if (orderAtt == null) {
			order = new StringLiteral("ascending");
		} else {
			checkAttributeValue("order", orderAtt, true, new String[]{"ascending", "descending"});
			order = makeAttributeValueTemplate(orderAtt);
		}

		if (dataTypeAtt == null) {
			dataType = null;
		} else {
			dataType = makeAttributeValueTemplate(dataTypeAtt);
		}

		if (caseOrderAtt == null) {
			caseOrder = new StringLiteral("#default");
		} else {
			checkAttributeValue("case-order", caseOrderAtt, true, new String[]{"lower-first", "upper-first"});
			caseOrder = makeAttributeValueTemplate(caseOrderAtt);
			useDefaultCollation = false;
		}

		if (langAtt == null || langAtt.equals("")) {
			lang = new StringLiteral(StringValue.EMPTY_STRING);
		} else {
			lang = makeAttributeValueTemplate(langAtt);
			useDefaultCollation = false;
			if (lang instanceof StringLiteral) {
				String s = ((StringLiteral)lang).getStringValue();
				if (s.length() != 0) {
                    ValidationFailure vf = StringConverter.STRING_TO_LANGUAGE.validate(s);
                    if (vf != null) {
                        compileError("The lang attribute must be a valid language code", "XTDE0030");
                        lang = new StringLiteral(StringValue.EMPTY_STRING);
                    }
				}
			}
		}

		if (stableAtt == null) {
			stable = null;
		} else {
			checkAttributeValue("stable", stableAtt, true, StyleElement.YES_NO);
			stable = makeAttributeValueTemplate(stableAtt);
		}

		if (collationAtt != null) {
			collationName = makeAttributeValueTemplate(collationAtt);
			useDefaultCollation = false;
		}

	}


	/*@Nullable*/ public Expression compile(Executable exec, Declaration decl) throws XPathException {
		if (select == null) {
			Expression b = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), true);
			if (b == null) {
				b = Literal.makeEmptySequence();
			}
			b.setContainer(this);
			try {
				ExpressionVisitor visitor = makeExpressionVisitor();
				Expression atomizedSortKey = Atomizer.makeAtomizer(b);
				atomizedSortKey = visitor.simplify(atomizedSortKey);
				ExpressionTool.copyLocationInfo(b, atomizedSortKey);
				sortKeyDefinition.setSortKey(atomizedSortKey, true);
			} catch (XPathException e) {
				compileError(e);
			}
		}
		// Simplify the sort key definition - this is especially important in the case where
		// all aspects of the sort key are known statically.
		sortKeyDefinition = sortKeyDefinition.simplify(makeExpressionVisitor());
		// not an executable instruction
		return null;
	}

	public SortKeyDefinition getSortKeyDefinition() {
		return sortKeyDefinition;
	}

}

