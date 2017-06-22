////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Converter;

import java.util.List;

/**
 * Casting Expression: abstract superclass for "cast as X" and "castable as X", which share a good deal of logic
*/

public abstract class CastingExpression extends UnaryExpression  {

    private AtomicType targetType;
    private AtomicType targetPrimitiveType;
    private boolean allowEmpty = false;
    protected Converter converter;
    protected NamespaceResolver nsResolver;
    private boolean operandIsStringLiteral = false;

    /**
     * Create a cast expression
     * @param source expression giving the value to be converted
     * @param target the type to which the value is to be converted
     * @param allowEmpty true if the expression allows an empty sequence as input, producing
     * an empty sequence as output. If false, an empty sequence is a type error.
     */

    public CastingExpression(Expression source, AtomicType target, boolean allowEmpty) {
        super(source);
        this.allowEmpty = allowEmpty;
        targetType = target;
        targetPrimitiveType = (AtomicType)target.getPrimitiveItemType();
        //derived = (targetType.getFingerprint() != targetPrimitiveType.getFingerprint());
    }



    /**
     * Get the primitive base type of the target type of the cast
     * @return the primitive type of the target type
     */

    public AtomicType getTargetPrimitiveType() {
        return targetPrimitiveType;
    }

    /**
     * Set the target type
     * @param type the target type for the cast
     */

    public void setTargetType(AtomicType type) {
        targetType = type;
    }

    /**
     * Get the target type (the result type)
     * @return the target type
     */

    public AtomicType getTargetType() {
        return targetType;
    }

    /**
     * Say whether the expression accepts an empty sequence as input (producing an empty sequence as output)
     * @param allow true if an empty sequence is accepted
     */

    public void setAllowEmpty(boolean allow) {
        allowEmpty = allow;
    }

    /**
     * Ask whether the expression accepts an empty sequence as input (producing an empty sequence as output)
     * @return true if an empty sequence is accepted
     */

    public boolean allowsEmpty() {
        return allowEmpty;
    }

    /**
     * Say whether the operand to the cast expression was supplied in the form of a string literal. This is
     * relevant only for XPath 2.0 / XQuery 1.0, and then only when the target type is a QName or NOTATION.
     * @param option true if the operand was supplied as a string literal
     */

    public void setOperandIsStringLiteral(boolean option) {
        operandIsStringLiteral = option;
    }

    /**
     * Ask whether the operand to the cast expression was supplied in the form of a string literal. This is
     * relevant only for XPath 2.0 / XQuery 1.0, and then only when the target type is a QName or NOTATION.
     * @return true if the operand was supplied as a string literal
     */

    public boolean isOperandIsStringLiteral() {
        return operandIsStringLiteral;
    }
    /**
     * Get the Converter allocated to implement this cast expression, if any
     * @return the Converter if one has been statically allocated, or null if not
     */

    public Converter getConverter() {
        return converter;
    }

    /**
     * Set the namespace resolver, if any
     * @param resolver the namespace resolver to be used if the target type is namespace-sensitive
     */

    public void setNamespaceResolver(NamespaceResolver resolver) {
        nsResolver = resolver;
    }

    /**
     * Get the namespace resolver, if any
     * @return the namespace resolver that was statically allocated if the target type is namespace-sensitive
     */

    public NamespaceResolver getNamespaceResolver() {
        return nsResolver;
    }


    /**
     * Simplify the expression
     * @return the simplified expression
     * @param visitor an expression visitor
     */

     /*@NotNull*/
     public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        if ((targetType instanceof BuiltInAtomicType) && !env.isAllowedBuiltInType((BuiltInAtomicType)targetType)) {
            // this is checked here because the ConstructorFunctionLibrary doesn't have access to the static
            // context at bind time
            XPathException err = new XPathException("The type " + targetType.getDisplayName() +
                    " is not recognized by a Basic XSLT Processor", this);
            err.setErrorCode("XPST0080");
            err.setIsStaticError(true);
            throw err;
        }
        if (nsResolver == null && targetType.isNamespaceSensitive()) {
            nsResolver = env.getNamespaceResolver();
        }
        operand = visitor.simplify(operand);
        return this;
    }


    /**
     * Determine the special properties of this expression
     * @return {@link net.sf.saxon.expr.StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NON_CREATIVE;
    }


}

