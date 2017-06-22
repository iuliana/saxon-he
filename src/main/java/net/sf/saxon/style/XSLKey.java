////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.ItemTypePattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.KeyDefinition;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;

/**
* Handler for xsl:key elements in stylesheet. <br>
*/

public class XSLKey extends StyleElement implements StylesheetProcedure {

    private Pattern match;
    private Expression use;
    private String collationName;
    private StructuredQName keyName;
    SlotManager stackFrameMap;
    private boolean rangeKey;
                // needed if variables are used

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
      * Determine whether this type of element is allowed to contain a sequence constructor
      * @return true: yes, it may contain a sequence constructor
      */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Get the Procedure object that looks after any local variables declared in the content constructor
     */

    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    public void prepareAttributes() throws XPathException {

        String nameAtt = null;
        String matchAtt = null;
        String useAtt = null;

		AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
			String uri = atts.getURI(a);
			String local = atts.getLocalName(a);
            if ("".equals(uri)) {
                if (local.equals(StandardNames.NAME)) {
                    nameAtt = Whitespace.trim(atts.getValue(a)) ;
                } else if (local.equals(StandardNames.USE)) {
                    useAtt = atts.getValue(a);
                } else if (local.equals(StandardNames.MATCH)) {
                    matchAtt = atts.getValue(a);
                } else if (local.equals(StandardNames.COLLATION)) {
                    collationName = Whitespace.trim(atts.getValue(a));
                } else {
        		    checkUnknownAttribute(atts.getNodeName(a));
        	    }
            } else if (local.equals("range-key") && uri.equals(NamespaceConstant.SAXON)) {
                String rangeKeyAtt = Whitespace.trim(atts.getValue(a));
                if ("yes".equals(rangeKeyAtt)) {
                    rangeKey = true;
                } else if ("no".equals(rangeKeyAtt)) {
                    rangeKey = false;
                } else {
                    compileError("saxon:range-key must be 'yes' or 'no'", "XTSE0020");
                }
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (nameAtt==null) {
            reportAbsence("name");
            return;
        }
        try {
            keyName = makeQName(nameAtt);
            setObjectName(keyName);
        } catch (NamespaceException err) {
            compileError(err.getMessage(), "XTSE0280");
        } catch (XPathException err) {
            compileError(err);
        }

        if (matchAtt==null) {
            reportAbsence("match");
            matchAtt = "*";
        }
        match = makePattern(matchAtt);
        if (match == null) {
            // error has been reported
            match = new ItemTypePattern(ErrorType.getInstance());
        }

        if (useAtt!=null) {
            use = makeExpression(useAtt);
        }
    }

    public StructuredQName getKeyName() {
    	//We use null to mean "not yet evaluated"
        try {
        	if (getObjectName()==null) {
        		// allow for forwards references
        		String nameAtt = getAttributeValue("", StandardNames.NAME);
        		if (nameAtt != null) {
        			setObjectName(makeQName(nameAtt));
                }
            }
            return getObjectName();
        } catch (NamespaceException err) {
            return null;          // the errors will be picked up later
        } catch (XPathException err) {
            return null;
        }
    }

    public void validate(Declaration decl) throws XPathException {

        stackFrameMap = getConfiguration().makeSlotManager();
        checkTopLevel("XTSE0010");
        if (use!=null) {
            // the value can be supplied as a content constructor in place of a use expression
            if (hasChildNodes()) {
                compileError("An xsl:key element with a @use attribute must be empty", "XTSE1205");
            }
            try {
                RoleLocator role =
                    new RoleLocator(RoleLocator.INSTRUCTION, "xsl:key/use", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                use = TypeChecker.staticTypeCheck(
                                use,
                                SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_MORE),
                                false, role, makeExpressionVisitor());
            } catch (XPathException err) {
                compileError(err);
            }
        } else {
            if (!hasChildNodes()) {
                compileError("An xsl:key element must either have a @use attribute or have content", "XTSE1205");
            }
        }
        use = typeCheck("use", use);
        match = typeCheck("match", match);

        // Do a further check that the use expression makes sense in the context of the match pattern
        if (use != null) {
            use = makeExpressionVisitor().typeCheck(use, new ExpressionVisitor.ContextItemType(match.getItemType(), false));
        }

        if (collationName != null) {
            URI collationURI;
            try {
                collationURI = new URI(collationName);
                if (!collationURI.isAbsolute()) {
                    URI base = new URI(getBaseURI());
                    collationURI = base.resolve(collationURI);
                    collationName = collationURI.toString();
                }
            } catch (URISyntaxException err) {
                compileError("Collation name '" + collationName + "' is not a valid URI");
                //collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
        } else {
            collationName = getDefaultCollationName();
        }
    }

    protected void index(Declaration decl, PrincipalStylesheetModule top) throws XPathException {
        StructuredQName keyName = getKeyName();
        if (keyName != null) {
            top.getPreparedStylesheet().getKeyManager().preRegisterKeyDefinition(keyName);
        }
    }

    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {
        StaticContext env = getStaticContext();
        Configuration config = env.getConfiguration();
        StringCollator collator = null;
        if (collationName != null) {
            collator = getPrincipalStylesheetModule().findCollation(collationName, getBaseURI());
            if (collator==null) {
                compileError("The collation name " + Err.wrap(collationName, Err.URI) + " is not recognized", "XTSE1210");
                collator = CodepointCollator.getInstance();
            }
            if (collator instanceof CodepointCollator) {
                // if the user explicitly asks for the codepoint collation, treat it as if they hadn't asked
                collator = null;
                collationName = null;

            } else if (!Configuration.getPlatform().canReturnCollationKeys(collator)) {
                compileError("The collation used for xsl:key must be capable of generating collation keys", "XTSE1210");
            }
        }

        if (use==null) {
            Expression body = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), true);

            try {
                ExpressionVisitor visitor = makeExpressionVisitor();
                use = Atomizer.makeAtomizer(body);
                use = visitor.simplify(use);
            } catch (XPathException e) {
                compileError(e);
            }

            try {
                RoleLocator role =
                    new RoleLocator(RoleLocator.INSTRUCTION, "xsl:key/use", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                use = TypeChecker.staticTypeCheck(
                        use,
                        SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, StaticProperty.ALLOWS_ZERO_OR_MORE),
                        false, role, makeExpressionVisitor());
                // Do a further check that the use expression makes sense in the context of the match pattern
                assert match != null;
                use = makeExpressionVisitor().typeCheck(use, new ExpressionVisitor.ContextItemType(match.getItemType(),false));


            } catch (XPathException err) {
                compileError(err);
            }
        }

        ExpressionVisitor visitor = makeExpressionVisitor();
        ExpressionVisitor.ContextItemType contextItemType = new ExpressionVisitor.ContextItemType(match.getItemType(), false);
        use = use.optimize(visitor, contextItemType);
        final TypeHierarchy th = config.getTypeHierarchy();
        ItemType useItemType = use.getItemType(th);
        if (useItemType == ErrorType.getInstance()) {
            useItemType = BuiltInAtomicType.STRING; // corner case, prevents crashing
        }
        BuiltInAtomicType useType = (BuiltInAtomicType)useItemType.getPrimitiveItemType();
        if (xPath10ModeIsEnabled()) {
            if (!useType.equals(BuiltInAtomicType.STRING) && !useType.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                use = new AtomicSequenceConverter(use, BuiltInAtomicType.STRING);
                ((AtomicSequenceConverter)use).allocateConverter(config, false);
                useType = BuiltInAtomicType.STRING;
            }
        }
        allocateSlots(use);
        // first slot in pattern is reserved for current()
        int nextFree = 0;
        if ((match.getDependencies() & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
            nextFree = 1;
        }
        int slots = match.allocateSlots(stackFrameMap, nextFree);
        allocatePatternSlots(slots);

        KeyManager km = getPreparedStylesheet().getKeyManager();
        KeyDefinition keydef = new KeyDefinition(match, use, collationName, collator);
        keydef.setRangeKey(rangeKey);
        keydef.setIndexedItemType(useType);
        keydef.setStackFrameMap(stackFrameMap);
        keydef.setLocation(getSystemId(), getLineNumber());
        keydef.setExecutable(getPreparedStylesheet());
        keydef.setBackwardsCompatible(xPath10ModeIsEnabled());
        try {
            km.addKeyDefinition(keyName, keydef, exec.getConfiguration());
        } catch (XPathException err) {
            compileError(err);
        }
    }

    /**
     * Optimize the stylesheet construct
     * @param declaration this xsl:key declaration
     */

    public void optimize(Declaration declaration) throws XPathException {
        // already done earlier
    }
}
