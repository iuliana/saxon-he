////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

/**
* The compiled form of an xsl:attribute-set element in the stylesheet.
*/

// Note, there is no run-time check for circularity. This is checked at compile time.

public class AttributeSet extends Procedure {

    StructuredQName attributeSetName;

    private AttributeSet[] useAttributeSets;

    /**
     * Create an empty attribute set
     */

    public AttributeSet() {
        setHostLanguage(Configuration.XSLT);
    }

    /**
     * Set the name of the attribute-set
     * @param attributeSetName the name of the attribute-set
     */

    public void setName(StructuredQName attributeSetName) {
        this.attributeSetName = attributeSetName;
    }

    /**
     * Set the attribute sets used by this attribute set
     * @param useAttributeSets the set of attribute sets used by this attribute set
     */

    public void setUseAttributeSets(AttributeSet[] useAttributeSets) {
        this.useAttributeSets = useAttributeSets;
    }

    /**
     * Set the stack frame map which allocates slots to variables declared in this attribute set
     * @param stackFrameMap the stack frame map
     */

    public void setStackFrameMap(/*@Nullable*/ SlotManager stackFrameMap) {
        if (stackFrameMap != null && stackFrameMap.getNumberOfVariables() > 0) {
            super.setStackFrameMap(stackFrameMap);
        }
    }

    /**
     * Determine whether the attribute set has any dependencies on the focus
     * @return the dependencies
     */

    public int getFocusDependencies() {
        int d = 0;
        if (body != null) {
            d |= body.getDependencies() & StaticProperty.DEPENDS_ON_FOCUS;
        }
        if (useAttributeSets != null) {
            for (AttributeSet useAttributeSet : useAttributeSets) {
                d |= useAttributeSet.getFocusDependencies();
            }
        }
        return d;
    }



    /**
     * Evaluate an attribute set
     * @param context the dynamic context
     * @throws XPathException if any failure occurs
     */

    public void expand(XPathContext context) throws XPathException {
        // apply the content of any attribute sets mentioned in use-attribute-sets

        if (useAttributeSets != null) {
            AttributeSet.expand(useAttributeSets, context);
        }

        if (getStackFrameMap() != null) {
            XPathContextMajor c2 = context.newContext();
            c2.setOrigin(this);
            c2.openStackFrame(getStackFrameMap());
            getBody().process(c2);
        } else {
            getBody().process(context);
        }
    }

    /**
     * Get the type of construct. This will either be the fingerprint of a standard XSLT instruction name
     * (values in {@link net.sf.saxon.om.StandardNames}: all less than 1024)
     * or it will be a constant in class {@link net.sf.saxon.trace.Location}.
     */

    public int getConstructType() {
        return StandardNames.XSL_ATTRIBUTE_SET;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     */

    public StructuredQName getObjectName() {
        return attributeSetName;
    }

    /**
     * Expand (evaluate) an array of attribute sets
     * @param asets the attribute sets to be evaluated
     * @param context the run-time context to use
     * @throws XPathException if evaluation of any attribute-set fails with a dynamic error
     */

    public static void expand(AttributeSet[] asets, XPathContext context) throws XPathException {
        for (AttributeSet aset : asets) {
            aset.expand(context);
        }
    }
}

