////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Binding;
import net.sf.saxon.expr.FilterExpression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * Represents the defining occurrence of a variable declared within a FLWOR expression,
 * for example the $p in "for $x at $p in ...". Also used for the variables bound by the
 * bind-group and bind-grouping-key attributes in xsl:for-each-group
 */

public class LocalVariableBinding implements Binding {

    private StructuredQName variableName;
    private SequenceType requiredType;
    private int slotNumber = -999;
    private int refCount = 0;

    /**
     * Create a LocalVariableBinding
     * @param name the name of the variable
     * @param type the static type of the variable
     */

    public LocalVariableBinding(StructuredQName name, SequenceType type){
        variableName = name;
        requiredType = type;
    }

    /**
     * Make a copy of this LocalVariableBinding (except for the slot number)
     * @return a copy of the binding
     */

    public LocalVariableBinding copy() {
        LocalVariableBinding lvb = new LocalVariableBinding(variableName, requiredType);
        lvb.refCount = refCount;
        lvb.slotNumber = slotNumber;
        return lvb;
    }

    /**
     * Get the name of the variable
     * @return the name of the variable
     */

    public StructuredQName getVariableQName() {
        return variableName;
    }

    /**
     * Set the required or inferred type of the variable
     * @param type the required or inferred type
     */

    public void setRequiredType(SequenceType type) {
        requiredType = type;
    }

    /**
     * Get the required type (declared type) of the variable
     * @return the required type
     */

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     */
    public IntegerValue[] getIntegerBoundsForVariable() {
        return null;
    }

    /**
     * Get the (nominal) count of the number of references to this variable
     * @return zero if there are no references, one if there is a single reference that is not in
     * a loop, some higher number if there are multiple references (or a single reference in a loop),
     * or the special value @link RangeVariable#FILTERED} if there are any references
     * in filter expressions that require searching.
     */

    public int getNominalReferenceCount() {
        return refCount;
    }

    /**
     * Register a variable reference that refers to the variable bound in this expression
     * @param isLoopingReference - true if the reference occurs within a loop, such as the predicate
     * of a filter expression
     */

    public void addReference(boolean isLoopingReference) {
        if (refCount != FilterExpression.FILTERED) {
            refCount += (isLoopingReference ? 10 : 1);
        }
    }

    /**
     * Indicate that the variable bound by this let expression should be indexable
     * (because it is used in an appropriate filter expression)
     */

    public void setIndexedVariable() {
        refCount = FilterExpression.FILTERED;
    }

    /**
     * Set the name of the variable
     * @param variableName the name of the variable
     */

    public void setVariableQName(StructuredQName variableName) {
        this.variableName = variableName;
    }

    /**
    * Set the slot number for the range variable
     * @param nr the slot number to be used
    */

    public void setSlotNumber(int nr) {
        slotNumber = nr;
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     */

    public int getLocalSlotNumber() {
        return slotNumber;
    }

    /**
    * Get the value of the range variable
    */

    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        return context.evaluateLocalVariable(slotNumber);
    }

    /**
     * Test whether it is permitted to assign to the variable using the saxon:assign
     * extension element. This will only be for an XSLT global variable where the extra
     * attribute saxon:assignable="yes" is present.
     *
     * @return true if the binding is assignable
     */

    public boolean isAssignable() {
        return false;
    }

    /**
     * Indicate whether the binding is local or global. A global binding is one that has a fixed
     * value for the life of a query or transformation; any other binding is local.
     *
     * @return true if the binding is global
     */

    public boolean isGlobal() {
        return false;
    }


}

