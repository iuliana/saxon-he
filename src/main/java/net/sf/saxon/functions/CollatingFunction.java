////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Abstract superclass for all functions that take an optional collation argument
 */

// Supports string comparison using a collation

public abstract class CollatingFunction extends SystemFunctionCall {

    // The default collation, or if it is known statically, the actual collation requested in the call
    protected StringCollator staticCollation = null;
    // The base URI from the static context
    private URI expressionBaseURI = null;
    // The AtomicComparer to be used if known statically (not used by all collating functions)
    private AtomicComparer atomicComparer = null;

    /**
     * Bind aspects of the static context on which the particular function depends
     *
     * @param env the static context of the function call
     * @throws net.sf.saxon.trans.XPathException
     *          if execution with this static context will inevitably fail
     */
    @Override
    public void bindStaticContext(StaticContext env) throws XPathException {
        expressionBaseURI = ExpressionTool.getBaseURI(env, this, false);
        staticCollation = env.getCollation(env.getDefaultCollationName());
    }

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */

    protected abstract int getCollationArgument();

    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        preEvaluateCollation(visitor.getStaticContext());
    }

    /**
     * Get the saved static base URI
     * @return the static base URI
     */

    public URI getExpressionBaseURI() {
        return expressionBaseURI;
    }

    /**
     * Get the collation if known statically, as a StringCollator object
     * @return a StringCollator. Return null if the collation is not known statically.
     */

    public StringCollator getStaticCollation() {
        return staticCollation;
    }

    /**
     * Pre-evaluate the collation argument if its value is known statically
     * @throws XPathException if execution of the function is bound to fail
     */

    private void preEvaluateCollation(StaticContext env) throws XPathException {
        if ((getDetails().properties & StandardFunction.DCOLL) == 0) {
            final Expression collationExp = argument[getNumberOfArguments() - 1];
            final GroundedValue collationVal = (collationExp instanceof Literal ? ((Literal)collationExp).getValue() : null);
            if (collationVal instanceof AtomicValue) {
                // Collation is supplied as a constant
                String collationName = ((AtomicValue)collationVal).getStringValue();
                URI collationURI;
                try {
                    collationURI = new URI(collationName);
                    if (!collationURI.isAbsolute()) {
                        if (expressionBaseURI == null) {
                            XPathException err = new XPathException("The collation name is a relative URI, but the base URI is unknown");
                            err.setErrorCode("XPST0001");
                            err.setIsStaticError(true);
                            err.setLocator(this);
                            throw err;
                        }
                        URI base = expressionBaseURI;
                        collationURI = base.resolve(collationURI);
                    }
                    collationName = collationURI.toString();
                    StringCollator selectedCollation = env.getCollation(collationName);
                    if (selectedCollation == null) {
                        XPathException err = new XPathException("Unknown collation " + Err.wrap(collationName, Err.URI));
                        err.setErrorCode("FOCH0002");
                        //err.setIsStaticError(true);
                        err.setLocator(this);
                        throw err;
                    } else {
                        staticCollation = selectedCollation;
                    }
                    // following lines removed 28/1/2013: breaks JUnit test CzechCollation
//                    Expression[] args = new Expression[argument.length - 1];
//                    System.arraycopy(argument, 0, args, 0, args.length);
//                    argument = args;

                } catch (URISyntaxException e) {
                    XPathException err = new XPathException("Collation name '" + collationName + "' is not a valid URI");
                    err.setErrorCode("FOCH0002");
                    //err.setIsStaticError(true);
                    err.setLocator(this);
                    throw err;
                }
            } else {
                staticCollation = null;
                // collation isn't known until run-time
            }
        } else {
            // Use the default collation : no action needed
        }
    }


    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    public Expression copy() {
        CollatingFunction d = (CollatingFunction)super.copy();
        d.expressionBaseURI = expressionBaseURI;
        d.staticCollation = staticCollation;
        d.atomicComparer = atomicComparer;
        return d;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof CollatingFunction &&
                super.equals(o) &&
                equalOrNull(expressionBaseURI, ((CollatingFunction)o).expressionBaseURI) &&
                equalOrNull(staticCollation, ((CollatingFunction)o).staticCollation);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (staticCollation != null ? staticCollation.hashCode() : 0);
        result = 31 * result + (expressionBaseURI != null ? expressionBaseURI.hashCode() : 0);
        return result;
    }

    /**
     * Get a collator suitable for comparing strings. Returns the collator specified in the
     * given function argument if present, otherwise returns the default collator. This method is
     * called by subclasses at run time.
     *
     * <p>This method is used only when evaluating static function calls.</p>
     *
     *
     * @param context The dynamic context
     * @return a StringCollator
     * @throws XPathException if a failure occurs evaluating the collation argument, or if the
     * specified collation is not recognized
     */

    protected StringCollator getCollator(XPathContext context) throws XPathException {
        if (staticCollation != null) {
            return staticCollation;
        }
        int arg = getCollationArgument();
        AtomicValue av = (AtomicValue) argument[arg].evaluateItem(context);
        StringValue collationValue = (StringValue) av;
        String collationName = collationValue.getStringValue();
        try {
            collationName = expandCollationURI(collationName, expressionBaseURI);
        } catch (XPathException err) {
            err.setLocator(this);
        }
        StringCollator collator = context.getCollation(collationName);
        if (collator == null) {
            throw new XPathException("Unrecognized collation: " + collationName, "FOCH0002");
        }
        return collator;
    }

    /**
     * Get a collator suitable for comparing strings. Returns the collator specified in the
     * given function argument if present, otherwise returns the default collator. This method is
     * called by subclasses at run time.
     *
     * <p>This method is used only when evaluating dynamic function calls.</p>
     *
     * @param arguments     The arguments supplied to the function call
     * @param arg           The position of the argument (base 0) containing the collation name
     * @param context The dynamic context
     * @return a StringCollator
     * @throws XPathException if a failure occurs evaluating the collation argument, or if the
     * specified collation is not recognized
     */

    protected StringCollator getCollatorFromLastArgument(Sequence[] arguments, int arg, XPathContext context)
    throws XPathException {
        if (arguments.length > arg) {
            // collation is specified explicitly in the call
            String collationName = arguments[arg].head().getStringValue();
            try {
                collationName = expandCollationURI(collationName, expressionBaseURI);
            } catch (XPathException err) {
                err.setLocator(this);
            }
            return context.getCollation(collationName);
        } else {
            // use the default collation
            return staticCollation;
        }
    }

    /**
     * Expand a collation URI, which may be a relative URI reference
     *
     * @param collationName the collation URI as provided
     * @param expressionBaseURI the base URI against which the collation URI will be resolved if it is relative
     * @return the resolved (expanded) absolute collation URI
     * @throws XPathException if the collation URI cannot be resolved
     */

    public static String expandCollationURI(String collationName, URI expressionBaseURI) throws XPathException {
        try {
            URI collationURI = new URI(collationName);
            if (!collationURI.isAbsolute()) {
                if (expressionBaseURI == null) {
                    throw new XPathException("Cannot resolve relative collation URI '" + collationName +
                            "': unknown or invalid base URI", "FOCH0002");
                }
                collationURI = expressionBaseURI.resolve(collationURI);
                collationName = collationURI.toString();
            }
        } catch (URISyntaxException e) {
            throw new XPathException("Collation name '" + collationName + "' is not a valid URI", "FOCH0002");
        }
        return collationName;
    }

    /**
     * During static analysis, if types are known and the collation is known, pre-allocate a comparer
     * for comparing atomic values. Called by some collating functions during type-checking
     * @param type0 the type of the first comparand
     * @param type1 the type of the second comparand
     * @param env the static context
     * @param NaNequalsNaN true if two NaN values are to be considered equal
     */

    protected void preAllocateComparer(AtomicType type0, AtomicType type1, StaticContext env, boolean NaNequalsNaN)  {
        StringCollator collation;
        if (argument.length <= getCollationArgument()) {
            // use the default collation
            collation = staticCollation;
        } else if (argument[argument.length-1] instanceof StringLiteral) {
            // use the collation given as a string literal in the last argument
            String collationName = ((StringLiteral)argument[argument.length-1]).getStringValue();
            try {
                collationName = expandCollationURI(collationName, expressionBaseURI);
                collation = env.getCollation(collationName);
                if (collation == null) {
                    throw new XPathException("Unrecognized collation: " + collationName, "FOCH0002");
                }
            } catch (XPathException err) {
                err.setLocator(this);
                argument[argument.length-1] = new ErrorExpression(err);
                return;
            }
        } else {
            return;
        }

        if (NaNequalsNaN) {
            atomicComparer = AtomicSortComparer.makeSortComparer(
                    collation, type0.getPrimitiveType(), env.makeEarlyEvaluationContext());
        } else {
            atomicComparer = GenericAtomicComparer.makeAtomicComparer(
                    (BuiltInAtomicType)type0.getBuiltInBaseType(), (BuiltInAtomicType)type1.getBuiltInBaseType(),
                    staticCollation, env.makeEarlyEvaluationContext());
        }
    }


    /**
     * Get the pre-allocated atomic comparer, if available
     * @return the preallocated atomic comparer, or null
     */

    public AtomicComparer getPreAllocatedAtomicComparer() {
        return atomicComparer;
    }

    /**
     * During evaluation, get the pre-allocated atomic comparer if available, or allocate a new one otherwise
     * @return the pre-allocated comparer if one is available; otherwise, a newly allocated one, using the specified
     * StringCollator for comparing strings
     */

    protected AtomicComparer getAtomicComparer(StringCollator collator, XPathContext context) {
        if (atomicComparer != null) {
            return atomicComparer.provideContext(context);
        } else {
            return new GenericAtomicComparer(collator, context);
        }
    }
}

