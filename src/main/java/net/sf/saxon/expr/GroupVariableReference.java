////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.ForEachGroup;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;

import java.util.List;

/**
 * This is a variant of LocalVariableReference used when the variable in question represents the group
 * bound by the bind-group attribute of xsl:for-each-group. It differs in that the streaming behavior is
 * different
 */
public class GroupVariableReference extends LocalVariableReference {

    private ForEachGroup controllingExpression;

    public GroupVariableReference() {
        super();
    }

    public GroupVariableReference(Binding binding) {
        super(binding);
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        return super.optimize(visitor, contextItemType);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
     * Create a clone copy of this VariableReference
     * @return the cloned copy
     */

    /*@NotNull*/
    public Expression copy() {
        if (binding == null) {
            throw new UnsupportedOperationException("Cannot copy a variable reference whose binding is unknown");
        }
        GroupVariableReference ref = new GroupVariableReference();
        ref.binding = binding;
        ref.staticType = staticType;
        ref.slotNumber = slotNumber;
        ref.constantValue = constantValue;
        ref.displayName = displayName;
        ref.controllingExpression = controllingExpression;
        binding.addReference(isInLoop());
        ExpressionTool.copyLocationInfo(this, ref);
        return ref;
    }

    public void setControllingExpression(ForEachGroup feg) {
        this.controllingExpression = feg;
        resetLocalStaticProperties();
    }

    public ForEachGroup getControllingExpression() {
        return controllingExpression;
    }

    /**
     * Determine the special properties of this expression. The properties such as document-ordering are the same as
     * the properties of the grouping population as a whole.
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NON_CREATIVE} (unless the variable is assignable using saxon:assign)
     */
    @Override
    public int computeSpecialProperties() {
        if (controllingExpression == null) {
            return 0;
        } else {
            return controllingExpression.getSelectExpression().getSpecialProperties();
        }
    }

    /**
     * Get the static cardinality
     */
    @Override
    public int computeCardinality() {
        return StaticProperty.ALLOWS_ONE_OR_MORE;
    }

    

}

