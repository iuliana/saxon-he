////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.TypeCheckerEnvironment;
import net.sf.saxon.expr.*;
import net.sf.saxon.functions.NumberFn;
import net.sf.saxon.functions.StringFn;
import net.sf.saxon.functions.SystemFunctionCall;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.SourceLocator;

import static net.sf.saxon.functions.Sum.SUM_EX_CODE;
import static net.sf.saxon.util.ExceptionBuilder.newEx;

/**
 * This class provides Saxon's type checking capability. It contains a static method,
 * staticTypeCheck, which is called at compile time to perform type checking of
 * an expression. This class is never instantiated.
 */

public final class TypeChecker {

    // Class is not instantiated
    private TypeChecker() {
    }

    /**
     * Check an expression against a required type, modifying it if necessary.
     * <p/>
     * <p>This method takes the supplied expression and checks to see whether it is
     * known statically to conform to the specified type. There are three possible
     * outcomes. If the static type of the expression is a subtype of the required
     * type, the method returns the expression unchanged. If the static type of
     * the expression is incompatible with the required type (for example, if the
     * supplied type is integer and the required type is string) the method throws
     * an exception (this results in a compile-time type error being reported). If
     * the static type is a supertype of the required type, then a new expression
     * is constructed that evaluates the original expression and checks the dynamic
     * type of the result; this new expression is returned as the result of the
     * method.</p>
     * <p/>
     * <p>The rules applied are those for function calling in XPath, that is, the rules
     * that the argument of a function call must obey in relation to the signature of
     * the function. Some contexts require slightly different rules (for example,
     * operands of polymorphic operators such as "+"). In such cases this method cannot
     * be used.</p>
     * <p/>
     * <p>Note that this method does <b>not</b> do recursive type-checking of the
     * sub-expressions.</p>
     *
     * @param supplied            The expression to be type-checked
     * @param req                 The required type for the context in which the expression is used
     * @param backwardsCompatible True if XPath 1.0 backwards compatibility mode is applicable
     * @param role                Information about the role of the subexpression within the
     *                            containing expression, used to provide useful error messages
     * @param visitor             An expression visitor
     * @return The original expression if it is type-safe, or the expression
     *         wrapped in a run-time type checking expression if not.
     * @throws XPathException if the supplied type is statically inconsistent with the
     *                        required type (that is, if they have no common subtype)
     */

    public static Expression staticTypeCheck(Expression supplied,
                                             SequenceType req,
                                             boolean backwardsCompatible,
                                             RoleLocator role,
                                             final TypeCheckerEnvironment visitor)
            throws XPathException {

        // System.err.println("Static Type Check on expression (requiredType = " + req + "):"); supplied.display(10);

        if (supplied.implementsStaticTypeCheck()) {
            return supplied.staticTypeCheck(req, backwardsCompatible, role, visitor);
        }

        Expression exp = supplied;
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        final ItemType reqItemType = req.getPrimaryType();
        int reqCard = req.getCardinality();
        boolean allowsMany = Cardinality.allowsMany(reqCard);

        ItemType suppliedItemType = null;
        // item type of the supplied expression: null means not yet calculated
        int suppliedCard = -1;
        // cardinality of the supplied expression: -1 means not yet calculated

        boolean cardOK = (reqCard == StaticProperty.ALLOWS_ZERO_OR_MORE);
        // Unless the required cardinality is zero-or-more (no constraints).
        // check the static cardinality of the supplied expression
        if (!cardOK) {
            suppliedCard = exp.getCardinality();
            cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            // May later find that cardinality is not OK after all, if atomization takes place
        }

        boolean itemTypeOK = reqItemType instanceof AnyItemType;
        // Unless the required item type and content type are ITEM (no constraints)
        // check the static item type against the supplied expression.
        // NOTE: we don't currently do any static inference regarding the content type
        if (!itemTypeOK) {
            suppliedItemType = exp.getItemType(th);
            if (suppliedItemType instanceof ErrorType) {
                // supplied type is empty-sequence(): this can violate a cardinality constraint but not an item type constraint
                itemTypeOK = true;
            } else {
                if (reqItemType == null || suppliedItemType == null) {
                    throw new NullPointerException();
                }
                int relation = th.relationship(reqItemType, suppliedItemType);
                itemTypeOK = relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMES;
            }
        }


        // Handle the special rules for 1.0 compatibility mode
        if (backwardsCompatible && !allowsMany) {
            // rule 1
            if (Cardinality.allowsMany(suppliedCard)) {
                Expression cexp = FirstItemExpression.makeFirstItemExpression(exp);
                cexp.adoptChildExpression(exp);
                exp = cexp;
                suppliedCard = StaticProperty.ALLOWS_ZERO_OR_ONE;
                cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            }
            if (!itemTypeOK) {
                // rule 2
                if (reqItemType.equals(BuiltInAtomicType.STRING)) {
                    StringFn fn = (StringFn) SystemFunctionCall.makeSystemFunction("string", new Expression[]{exp});
                    try {
                        exp = visitor.typeCheck(visitor.simplify(fn), new ExpressionVisitor.ContextItemType(AnyItemType.getInstance(), true));
                    } catch (XPathException err) {
                        err.maybeSetLocation(exp);
                        throw err.makeStatic();
                    }
                    suppliedItemType = BuiltInAtomicType.STRING;
                    suppliedCard = StaticProperty.EXACTLY_ONE;
                    cardOK = Cardinality.subsumes(reqCard, suppliedCard);
                    itemTypeOK = true;
                }
                // rule 3
                if (reqItemType.equals(BuiltInAtomicType.NUMERIC) || reqItemType.equals(BuiltInAtomicType.DOUBLE)) {
                    NumberFn fn = (NumberFn) SystemFunctionCall.makeSystemFunction("number", new Expression[]{exp});
                    try {
                        exp = visitor.typeCheck(visitor.simplify(fn), new ExpressionVisitor.ContextItemType(AnyItemType.getInstance(), true));
                    } catch (XPathException err) {
                        err.maybeSetLocation(exp);
                        throw err.makeStatic();
                    }
                    suppliedItemType = BuiltInAtomicType.DOUBLE;
                    suppliedCard = StaticProperty.EXACTLY_ONE;
                    cardOK = Cardinality.subsumes(reqCard, suppliedCard);
                    itemTypeOK = true;
                }
            }
        }

        if (!itemTypeOK) {
            // Now apply the conversions needed in 2.0 mode

            if (reqItemType.isPlainType()) {

                // rule 1: Atomize
                if (!(suppliedItemType.isPlainType()) &&
                        !(suppliedCard == StaticProperty.EMPTY)) {
                    if (!suppliedItemType.isAtomizable()) {
                        XPathException err = new XPathException(
                                "An atomic value is required for the " + role.getMessage() +
                                        ", but the supplied value cannot be atomized", supplied);
                        err.setErrorCode("FOTY0013");
                        err.setIsTypeError(true);
                        err.setLocator(exp);
                        throw err;
                    }
                    exp = Atomizer.makeAtomizer(exp);
                    Expression cexp = visitor.simplify(exp);
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    exp = cexp;
                    suppliedItemType = exp.getItemType(th);
                    suppliedCard = exp.getCardinality();
                    cardOK = Cardinality.subsumes(reqCard, suppliedCard);
                }

                // rule 2: convert untypedAtomic to the required type
                //TODO: merge common code between 2a & 2b

                //   2a: all supplied values are untyped atomic. Convert if necessary, and we're finished.

                if ((suppliedItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC))
                        && !(reqItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || reqItemType.equals(BuiltInAtomicType.ANY_ATOMIC))) {

                    if (((SimpleType) reqItemType).isNamespaceSensitive()) {
                        // See spec bug 11964
                        XPathException err = new XPathException(
                                "An untyped atomic value cannot be converted to a QName or NOTATION as required for the " + role.getMessage(), supplied);
                        err.setErrorCode("XPTY0117");
                        err.setIsTypeError(true);
                        err.setLocator(exp);
                        throw err;
                    }
                    Expression cexp = UntypedSequenceConverter.makeUntypedSequenceConverter(visitor.getConfiguration(), exp, (PlainType) reqItemType);
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    try {
                        if (exp instanceof Literal) {
                            exp = Literal.makeLiteral(
                                    new SequenceExtent(cexp.iterate(visitor.makeDynamicContext())));
                        } else {
                            exp = cexp;
                        }
                    } catch (XPathException err) {
                        err.maybeSetLocation(exp);
                        err.setErrorCode(role.getErrorCode());
                        throw err.makeStatic();
                    }
                    itemTypeOK = true;
                    suppliedItemType = reqItemType;
                }

                //   2b: some supplied values are untyped atomic. Convert these to the required type; but
                //   there may be other values in the sequence that won't convert and still need to be checked

                if ((suppliedItemType.equals(BuiltInAtomicType.ANY_ATOMIC))
                        && !(reqItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || reqItemType.equals(BuiltInAtomicType.ANY_ATOMIC))
                        && (exp.getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC) == 0) {

                    Expression conversion;
                    if (((SimpleType) reqItemType).isNamespaceSensitive()) {
                        conversion = UntypedSequenceConverter.makeUntypedSequenceRejector(visitor.getConfiguration(), exp, (PlainType) reqItemType);
                    } else {
                        conversion = UntypedSequenceConverter.makeUntypedSequenceConverter(visitor.getConfiguration(), exp, (PlainType) reqItemType);
                    }
                    ExpressionTool.copyLocationInfo(exp, conversion);
                    try {
                        if (exp instanceof Literal) {
                            exp = Literal.makeLiteral(
                                    new SequenceExtent(conversion.iterate(visitor.makeDynamicContext())));
                        } else {
                            exp = conversion;
                        }
                        suppliedItemType = exp.getItemType(th);
                    } catch (XPathException err) {
                        err.maybeSetLocation(exp);
                        throw err.makeStatic();
                    }
                }

                // Rule 3a: numeric promotion decimal -> float -> double

                if (reqItemType instanceof AtomicType) {
                    int rt = ((AtomicType) reqItemType).getFingerprint();
                    if ((rt == StandardNames.XS_DOUBLE &&
                            th.relationship(suppliedItemType, BuiltInAtomicType.NUMERIC) != TypeHierarchy.DISJOINT)) {
                        Expression cexp = makePromoterToDouble(exp, visitor);
                        ExpressionTool.copyLocationInfo(exp, cexp);
                        exp = cexp;
                        try {
                            exp = visitor.typeCheck(visitor.simplify(exp), new ExpressionVisitor.ContextItemType(AnyItemType.getInstance(), true));
                        } catch (XPathException err) {
                            err.maybeSetLocation(exp);
                            throw err.makeStatic();
                        }
                        suppliedItemType = BuiltInAtomicType.DOUBLE;
                        suppliedCard = -1;

                    } else if (rt == StandardNames.XS_FLOAT &&
                            th.relationship(suppliedItemType, BuiltInAtomicType.NUMERIC) != TypeHierarchy.DISJOINT &&
                            !th.isSubType(suppliedItemType, BuiltInAtomicType.DOUBLE)) {
                        Expression cexp = makePromoterToFloat(exp, visitor);
                        ExpressionTool.copyLocationInfo(exp, cexp);
                        exp = cexp;
                        try {
                            exp = visitor.typeCheck(visitor.simplify(exp), new ExpressionVisitor.ContextItemType(AnyItemType.getInstance(), true));
                        } catch (XPathException err) {
                            err.maybeSetLocation(exp);
                            throw err.makeStatic();
                        }
                        suppliedItemType = BuiltInAtomicType.FLOAT;
                        suppliedCard = -1;

                    }

                    // Rule 3b: promotion from anyURI -> string

                    if (rt == StandardNames.XS_STRING && th.isSubType(suppliedItemType, BuiltInAtomicType.ANY_URI)) {
                        suppliedItemType = BuiltInAtomicType.STRING;
                        itemTypeOK = true;
                        // we don't generate code to do a run-time type conversion; rather, we rely on
                        // operators and functions that accept a string to also accept an xs:anyURI. This
                        // is straightforward, because anyURIValue is a subclass of StringValue
                    }
                }

            } else if (reqItemType instanceof FunctionItemType && !((FunctionItemType) reqItemType).isMapType()) {
                if (!(suppliedItemType instanceof FunctionItemType)) {
                    exp = new ItemChecker(exp, th.getGenericFunctionItemType(), role);
                    suppliedItemType = th.getGenericFunctionItemType();
                }
                exp = makeFunctionSequenceCoercer(exp, (FunctionItemType) reqItemType, visitor, role);
                itemTypeOK = true;

            } else if (reqItemType instanceof ExternalObjectType &&
                    Sequence.class.isAssignableFrom(((ExternalObjectType)reqItemType).getJavaClass()) &&
                    reqCard == StaticProperty.EXACTLY_ONE) {
                // special case: allow an extension function to call an instance method on the implementation type of an XDM value
                // we leave the conversion to be sorted out at run-time
                itemTypeOK = true;
            }

        }

        // If both the cardinality and item type are statically OK, return now.
        if (itemTypeOK && cardOK) {
            return exp;
        }

        // If we haven't evaluated the cardinality of the supplied expression, do it now
        if (suppliedCard == -1) {
            suppliedCard = exp.getCardinality();
            if (!cardOK) {
                cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            }
        }

        // If an empty sequence was explicitly supplied, and empty sequence is allowed,
        // then the item type doesn't matter
        if (cardOK && suppliedCard == StaticProperty.EMPTY) {
            return exp;
        }

        // If the supplied value is () and () isn't allowed, fail now
        if (suppliedCard == StaticProperty.EMPTY && ((reqCard & StaticProperty.ALLOWS_ZERO) == 0)) {
            XPathException err = new XPathException("An empty sequence is not allowed as the " + role.getMessage(), supplied);
            err.setErrorCode(role.getErrorCode());
            err.setIsTypeError(role.isTypeError());
            err.setLocator(exp);
            throw err;
        }

        // Try a static type check. We only throw it out if the call cannot possibly succeed, unless
        // pessimistic type checking is enabled

        int relation = (itemTypeOK ? TypeHierarchy.SUBSUMED_BY : th.relationship(suppliedItemType, reqItemType));
        boolean pessimistic = false;
        // Pessimistic type checking is not working. It needs at the very least an error type (for use by
        // fn:error and by ErrorExpression) that satisfies any other type.
        if (pessimistic) {
            if (relation != TypeHierarchy.SAME_TYPE && relation != TypeHierarchy.SUBSUMED_BY) {
                XPathException err = new XPathException("Required item type of " + role.getMessage() +
                            " is " + reqItemType.toString() +
                            "; supplied value has inferred item type " +
                            suppliedItemType.toString() +
                        ". Pessimistic static typing is enabled, so this is an error", supplied);
                err.setErrorCode(role.getErrorCode());
                err.setIsTypeError(role.isTypeError());
                err.setLocator(supplied);
                throw err;
            }
            if (!Cardinality.subsumes(reqCard, suppliedCard)) {
                XPathException err = new XPathException("Required cardinality of " + role.getMessage() +
                            " is " + Cardinality.toString(reqCard) +
                            "; supplied value has inferred cardinality " +
                            Cardinality.toString(suppliedCard) +
                        ". Pessimistic static typing is enabled, so this is an error", supplied);
                err.setErrorCode(role.getErrorCode());
                err.setIsTypeError(role.isTypeError());
                err.setLocator(supplied);
                throw err;
            }
        } else {
            if (relation == TypeHierarchy.DISJOINT) {
                // The item types may be disjoint, but if both the supplied and required types permit
                // an empty sequence, we can't raise a static error. Raise a warning instead.
                if (Cardinality.allowsZero(suppliedCard) &&
                        Cardinality.allowsZero(reqCard)) {
                    if (suppliedCard != StaticProperty.EMPTY) {
                        String msg = "Required item type of " + role.getMessage() +
                                " is " + reqItemType.toString() +
                                "; supplied value has item type " +
                                suppliedItemType.toString() +
                                ". The expression can succeed only if the supplied value is an empty sequence.";
                        visitor.issueWarning(msg, supplied);
                    }
                } else {
                    XPathException err = new XPathException("Required item type of " + role.getMessage() +
                            " is " + reqItemType.toString() +
                            "; supplied value has item type " +
                            suppliedItemType.toString(), supplied);
                    err.setErrorCode(role.getErrorCode());
                    err.setIsTypeError(role.isTypeError());
                    err.setLocator(supplied);
                    throw err;
                }
            }
        }

        // Unless the type is guaranteed to match, add a dynamic type check,
        // unless the value is already known in which case we might as well report
        // the error now.

        if (!(relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMED_BY)) {
            if (exp instanceof Literal) {
                XPathException err = new XPathException("Required item type of " + role.getMessage() +
                        " is " + reqItemType.toString() +
                        "; supplied value has item type " +
                        suppliedItemType.toString(), supplied);
                err.setErrorCode(role.getErrorCode());
                err.setIsTypeError(role.isTypeError());
                err.setLocator(supplied);
                throw err;
            }
            Expression cexp = new ItemChecker(exp, reqItemType, role);
            ExpressionTool.copyLocationInfo(exp, cexp);
            exp = cexp;
        }

        if (!cardOK) {
            if (exp instanceof Literal) {
                XPathException err = new XPathException("Required cardinality of " + role.getMessage() +
                        " is " + Cardinality.toString(reqCard) +
                        "; supplied value has cardinality " +
                        Cardinality.toString(suppliedCard), supplied);
                err.setIsTypeError(role.isTypeError());
                err.setErrorCode(role.getErrorCode());
                err.setLocator(supplied);
                throw err;
            } else {
                Expression cexp = CardinalityChecker.makeCardinalityChecker(exp, reqCard, role);
                ExpressionTool.copyLocationInfo(exp, cexp);
                exp = cexp;
            }
        }

        return exp;
    }

    private static Expression makeFunctionSequenceCoercer(Expression exp, FunctionItemType reqItemType, final TypeCheckerEnvironment visitor, RoleLocator role) throws XPathException {
        // Apply function coercion as defined in XPath 3.0. To avoid retaining the entire static context
        // at run-time, we create a cut-down version of the expression visitor with just enough information
        // to do type-checking and nothing much else

        TypeCheckerEnvironment tce = new TypeCheckerEnvironment() {
            final Configuration config = visitor.getConfiguration();
            final CollationMap collationMap = visitor.getCollationMap();

            public Configuration getConfiguration() {
                return config;
            }

            public void issueWarning(String message, SourceLocator locator) {
                // no action
            }

            public CollationMap getCollationMap() {
                return collationMap;
            }

            public XPathContext makeDynamicContext() {
                return new EarlyEvaluationContext(config, collationMap);
            }

            public Expression simplify(Expression exp) throws XPathException {
                return exp;
            }

            public Expression typeCheck(Expression exp, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
                return exp;
            }
        };
        exp = reqItemType.makeFunctionSequenceCoercer(exp, role, tce);
        return exp;
    }

    /**
     * Check an expression against a required type, modifying it if necessary. This
     * is a variant of the method {@link #staticTypeCheck} used for expressions that
     * declare variables in XQuery. In these contexts, conversions such as numeric
     * type promotion and atomization are not allowed.
     *
     * @param supplied The expression to be type-checked
     * @param req      The required type for the context in which the expression is used
     * @param role     Information about the role of the subexpression within the
     *                 containing expression, used to provide useful error messages
     * @param env      The static context containing the types being checked. At present
     *                 this is used only to locate a NamePool
     * @return The original expression if it is type-safe, or the expression
     *         wrapped in a run-time type checking expression if not.
     * @throws XPathException if the supplied type is statically inconsistent with the
     *                        required type (that is, if they have no common subtype)
     */

    public static Expression strictTypeCheck(Expression supplied,
                                             SequenceType req,
                                             RoleLocator role,
                                             StaticContext env)
            throws XPathException {

        // System.err.println("Strict Type Check on expression (requiredType = " + req + "):"); supplied.display(10);

        Expression exp = supplied;
        final TypeHierarchy th = env.getConfiguration().getTypeHierarchy();

        ItemType reqItemType = req.getPrimaryType();
        int reqCard = req.getCardinality();

        ItemType suppliedItemType = null;
        // item type of the supplied expression: null means not yet calculated
        int suppliedCard = -1;
        // cardinality of the supplied expression: -1 means not yet calculated

        boolean cardOK = (reqCard == StaticProperty.ALLOWS_ZERO_OR_MORE);
        // Unless the required cardinality is zero-or-more (no constraints).
        // check the static cardinality of the supplied expression
        if (!cardOK) {
            suppliedCard = exp.getCardinality();
            cardOK = Cardinality.subsumes(reqCard, suppliedCard);
        }

        boolean itemTypeOK = req.getPrimaryType() instanceof AnyItemType;
        // Unless the required item type and content type are ITEM (no constraints)
        // check the static item type against the supplied expression.
        // NOTE: we don't currently do any static inference regarding the content type
        if (!itemTypeOK) {
            suppliedItemType = exp.getItemType(th);
            int relation = th.relationship(reqItemType, suppliedItemType);
            itemTypeOK = relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMES;
        }

        // If both the cardinality and item type are statically OK, return now.
        if (itemTypeOK && cardOK) {
            return exp;
        }

        // If we haven't evaluated the cardinality of the supplied expression, do it now
        if (suppliedCard == -1) {
            if (suppliedItemType instanceof ErrorType) {
                suppliedCard = StaticProperty.EMPTY;
            } else {
                suppliedCard = exp.getCardinality();
            }
            if (!cardOK) {
                cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            }
        }

        // If an empty sequence was explicitly supplied, and empty sequence is allowed,
        // then the item type doesn't matter
        if (cardOK && suppliedCard == StaticProperty.EMPTY) {
            return exp;
        }

        // If we haven't evaluated the item type of the supplied expression, do it now
        if (suppliedItemType == null) {
            suppliedItemType = exp.getItemType(th);
        }

        if (suppliedCard == StaticProperty.EMPTY && ((reqCard & StaticProperty.ALLOWS_ZERO) == 0)) {
            XPathException err = new XPathException("An empty sequence is not allowed as the " + role.getMessage(), supplied);
            err.setErrorCode(role.getErrorCode());
            err.setIsTypeError(role.isTypeError());
            err.setLocator(exp);
            throw err;
        }

        // Try a static type check. We only throw it out if the call cannot possibly succeed.

        int relation = th.relationship(suppliedItemType, reqItemType);
        if (relation == TypeHierarchy.DISJOINT) {
            // The item types may be disjoint, but if both the supplied and required types permit
            // an empty sequence, we can't raise a static error. Raise a warning instead.
            if (Cardinality.allowsZero(suppliedCard) &&
                    Cardinality.allowsZero(reqCard)) {
                if (suppliedCard != StaticProperty.EMPTY) {
                    String msg = "Required item type of " + role.getMessage() +
                            " is " + reqItemType.toString() +
                            "; supplied value has item type " +
                            suppliedItemType.toString() +
                            ". The expression can succeed only if the supplied value is an empty sequence.";
                    env.issueWarning(msg, supplied);
                }
            } else {
                XPathException err = new XPathException("Required item type of " + role.getMessage() +
                        " is " + reqItemType.toString() +
                        "; supplied value has item type " +
                        suppliedItemType.toString(), supplied);
                err.setErrorCode(role.getErrorCode());
                err.setIsTypeError(role.isTypeError());
                err.setLocator(exp);
                throw err;
            }
        }

        // Unless the type is guaranteed to match, add a dynamic type check,
        // unless the value is already known in which case we might as well report
        // the error now.

        if (!(relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMED_BY)) {
            Expression cexp = new ItemChecker(exp, reqItemType, role);
            cexp.adoptChildExpression(exp);
            exp = cexp;
        }

        if (!cardOK) {
            if (exp instanceof Literal) {
                XPathException err = new XPathException("Required cardinality of " + role.getMessage() +
                        " is " + Cardinality.toString(reqCard) +
                        "; supplied value has cardinality " +
                        Cardinality.toString(suppliedCard), supplied);
                err.setIsTypeError(role.isTypeError());
                err.setErrorCode(role.getErrorCode());
                throw err;
            } else {
                Expression cexp = CardinalityChecker.makeCardinalityChecker(exp, reqCard, role);
                cexp.adoptChildExpression(exp);
                exp = cexp;
            }
        }

        return exp;
    }

    /**
     * Test whether a given value conforms to a given type
     *
     * @param val          the value
     * @param requiredType the required type
     * @param context      XPath dynamic context
     * @return an XPathException describing the error condition if the value doesn't conform;
     *         or null if it does.
     * @throws XPathException if a failure occurs reading the value
     */

    /*@Nullable*/
    public static XPathException testConformance(
            Sequence val, SequenceType requiredType, XPathContext context)
            throws XPathException {
        ItemType reqItemType = requiredType.getPrimaryType();
        final Configuration config = context.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        SequenceIterator iter = val.iterate();
        int count = 0;
        while (true) {
            Item item = iter.next();
            if (item == null) {
                break;
            }
            count++;
            if (!reqItemType.matchesItem(item, false, config)) {
                XPathException err = new XPathException("Required type is " + reqItemType +
                        "; supplied value has type " + new SequenceExtent(val.iterate()).getItemType(th));
                err.setIsTypeError(true);
                err.setErrorCode("XPTY0004");
                return err;
            }
        }

        int reqCardinality = requiredType.getCardinality();
        if (count == 0 && !Cardinality.allowsZero(reqCardinality)) {
            XPathException err = new XPathException(
                    "Required type does not allow empty sequence, but supplied value is empty");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            return err;
        }
        if (count > 1 && !Cardinality.allowsMany(reqCardinality)) {
            XPathException err = new XPathException(
                    "Required type requires a singleton sequence; supplied value contains " + count + " items");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            return err;
        }
        if (count > 0 && reqCardinality == StaticProperty.EMPTY) {
            XPathException err = new XPathException(
                    "Required type requires an empty sequence, but supplied value is non-empty");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            return err;
        }
        return null;
    }

    /**
     * Test whether a given expression is capable of returning a value that has an effective boolean
     * value.
     *
     * @param exp the given expression
     * @param th  the type hierarchy cache
     * @return null if the expression is OK (optimistically), an exception object if not
     */

    public static XPathException ebvError(Expression exp, TypeHierarchy th) {
        if (Cardinality.allowsZero(exp.getCardinality())) {
            return null;
        }
        ItemType t = exp.getItemType(th);
        if (th.relationship(t, Type.NODE_TYPE) == TypeHierarchy.DISJOINT &&
                th.relationship(t, BuiltInAtomicType.BOOLEAN) == TypeHierarchy.DISJOINT &&
                th.relationship(t, BuiltInAtomicType.STRING) == TypeHierarchy.DISJOINT &&
                th.relationship(t, BuiltInAtomicType.ANY_URI) == TypeHierarchy.DISJOINT &&
                th.relationship(t, BuiltInAtomicType.UNTYPED_ATOMIC) == TypeHierarchy.DISJOINT &&
                th.relationship(t, BuiltInAtomicType.NUMERIC) == TypeHierarchy.DISJOINT &&
                !(t instanceof ExternalObjectType)) {

            return newEx("Effective boolean value is defined only for sequences containing " +
                    "booleans, strings, numbers, URIs, or nodes", true, SUM_EX_CODE);
        }
        return null;
    }

    private static Expression makePromoterToDouble(Expression exp, TypeCheckerEnvironment visitor) {
        AtomicSequenceConverter asc = new AtomicSequenceConverter(exp, BuiltInAtomicType.DOUBLE);
        Converter converter = new Converter.PromoterToDouble();
        converter.setConversionRules(visitor.getConfiguration().getConversionRules());
        asc.setConverter(converter);
        ExpressionTool.copyLocationInfo(exp, asc);
        return asc;
    }

    private static Expression makePromoterToFloat(Expression exp, TypeCheckerEnvironment visitor) {
        AtomicSequenceConverter asc = new AtomicSequenceConverter(exp, BuiltInAtomicType.FLOAT);
        Converter converter = new Converter.PromoterToFloat();
        converter.setConversionRules(visitor.getConfiguration().getConversionRules());
        asc.setConverter(converter);
        ExpressionTool.copyLocationInfo(exp, asc);
        return asc;
    }

}
