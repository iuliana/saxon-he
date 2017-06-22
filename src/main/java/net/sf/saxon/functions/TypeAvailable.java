////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.BuiltInListType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.StringValue;

import java.util.Set;

/**
* This class supports the XSLT element-available and function-available functions.
*/

public class TypeAvailable extends Available implements Callable {

    private Set<String> importedSchemaNamespaces;
    private boolean isXSLT20basic;

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        isXSLT20basic = !env.isSchemaAware() && !env.getXPathLanguageLevel().equals(DecimalValue.THREE);
        super.checkArguments(visitor);
        if (!(argument[0] instanceof Literal &&
                (argument.length==1 || argument[1] instanceof Literal))) {
            importedSchemaNamespaces = visitor.getStaticContext().getImportedSchemaNamespaces();
        }
    }

    /**
    * preEvaluate: this method uses the static context to do early evaluation of the function
    * if the argument is known (which is the normal case)
     * @param visitor the expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        nsContext = visitor.getStaticContext().getNamespaceResolver();
        String lexicalQName = ((Literal)argument[0]).getValue().getStringValue();
        Configuration config = visitor.getConfiguration();
        boolean b = typeAvailable(lexicalQName, config, visitor.getStaticContext().getImportedSchemaNamespaces());
        return Literal.makeLiteral(BooleanValue.get(b));
    }

    /**
     * Run-time evaluation.
    */

    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        AtomicValue av1 = (AtomicValue)argument[0].evaluateItem(context);
        StringValue nameValue = (StringValue)av1;
        boolean b = typeAvailable(nameValue.getStringValue(), context.getConfiguration(), importedSchemaNamespaces);
        return BooleanValue.get(b);
    }

    private boolean typeAvailable(String lexicalName, Configuration config, Set<String> importedSchemaNamespaces) throws XPathException {
        StructuredQName qName;
        try {
            // TODO: accept EQName syntax.
            if (lexicalName.indexOf(':') < 0) {
                String uri = nsContext.getURIForPrefix("", true);
                qName = new StructuredQName("", uri, lexicalName);
            } else {
                qName = StructuredQName.fromLexicalQName(lexicalName,
                    false, false, config.getNameChecker(),
                    nsContext);
            }
        } catch (XPathException e) {
            e.setErrorCode("XTDE1428");
            e.setLocator(this);
            throw e;
        }

        String uri = qName.getURI();
        if (uri.equals(NamespaceConstant.JAVA_TYPE)) {
            try {
                config.getClass(qName.getLocalPart(), false, null);
                return(true);
            } catch (XPathException err) {
                return(false);
            }
        } else if (uri.equals(NamespaceConstant.SCHEMA) || importedSchemaNamespaces.contains(uri)) {
            final int fp = config.getNamePool().allocate(
                    qName.getPrefix(), uri, qName.getLocalPart()) & 0xfffff;
            SchemaType type = config.getSchemaType(fp);
            return type != null &&
                    !(isXSLT20basic && (type instanceof BuiltInListType ||
                                (type instanceof BuiltInAtomicType && !((BuiltInAtomicType) type).isAllowedInBasicXSLT())));
        } else {
            return false;
        }
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments /*@NotNull*/) throws XPathException {
        String lexicalQName = arguments[0].head().getStringValue();
        return BooleanValue.get(typeAvailable(lexicalQName, context.getConfiguration(), importedSchemaNamespaces));
    }
}

