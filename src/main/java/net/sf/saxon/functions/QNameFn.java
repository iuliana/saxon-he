////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.StringValue;


/**
* This class supports the fn:QName() function (previously named fn:expanded-QName())
*/

public class QNameFn extends SystemFunctionCall {

    /**
     * Pre-evaluate a function at compile time. Functions that do not allow
     * pre-evaluation, or that need access to context information, can override this method.
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        try {
            XPathContext early = visitor.getStaticContext().makeEarlyEvaluationContext();
            final Item item1 = argument[1].evaluateItem(early);
            final String lex = item1.getStringValue();
            final Item item0 = argument[0].evaluateItem(early);
            String uri;
            if (item0 == null) {
                uri = "";
            } else {
                uri = item0.getStringValue();
            }
            final NameChecker checker = visitor.getConfiguration().getNameChecker();
            final String[] parts = checker.getQNameParts(lex);
            // The QNameValue constructor does not check the prefix
            if (parts[0].length() != 0 && !checker.isValidNCName(parts[0])) {
                XPathException err = new XPathException("Malformed prefix in QName: '" + parts[0] + '\'');
                err.setErrorCode("FOCA0002");
                throw err;
            }
            return Literal.makeLiteral(
                    new QNameValue(parts[0], uri, parts[1], BuiltInAtomicType.QNAME, checker));
        } catch (QNameException e) {
            XPathException err = new XPathException(e.getMessage(), this);
            err.setErrorCode("FOCA0002");
            err.setLocator(this);
            throw err;
        } catch (XPathException err) {
            if (err.getErrorCodeLocalPart().equals("FORG0001")) {
                err.setErrorCode("FOCA0002");
            }
            err.maybeSetLocation(this);
            throw err;
        }
    }

    /**
    * Evaluate the expression
    */

    /*@Nullable*/ public QNameValue evaluateItem(XPathContext context) throws XPathException {
        StringValue arg0 = (StringValue)argument[0].evaluateItem(context);
        StringValue arg1 = (StringValue)argument[1].evaluateItem(context);
        return expandedQName(arg0, arg1, context.getConfiguration().getNameChecker());
    }

    public QNameValue expandedQName(StringValue namespace, StringValue lexical, NameChecker checker) throws XPathException {

        String uri;
        if (namespace == null) {
            uri = null;
        } else {
            uri = namespace.getStringValue();
        }

        try {
            final String lex = lexical.getStringValue();
            final String[] parts = checker.getQNameParts(lex);
            // The QNameValue constructor does not check the prefix
            if (parts[0].length() != 0 && !checker.isValidNCName(parts[0])) {
                XPathException err = new XPathException("Malformed prefix in QName: '" + parts[0] + '\'');
                err.setErrorCode("FOCA0002");
                throw err;
            }
            return new QNameValue(parts[0], uri, parts[1], BuiltInAtomicType.QNAME, checker);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), "FOCA0002");
        } catch (XPathException err) {
            if (err.getErrorCodeLocalPart().equals("FORG0001")) {
                err.setErrorCode("FOCA0002");
            }
            err.maybeSetLocation(this);
            throw err;
        }
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return expandedQName(
                (StringValue)arguments[0].head(),
                (StringValue)arguments[1].head(),
                context.getConfiguration().getNameChecker());
    }


}

