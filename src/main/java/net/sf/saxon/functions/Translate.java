////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntToIntHashMap;
import net.sf.saxon.z.IntToIntMap;

/**
 * Implement the XPath translate() function
 */

public class Translate extends SystemFunctionCall implements Callable {

    /*@Nullable*/ private IntToIntMap staticMap = null;
            // if the second and third arguments are known statically, we build a hash table for fast
            // lookup at run-time.

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ExpressionVisitor.ContextItemType contextItemType) throws XPathException {
        Expression e = super.typeCheck(visitor, contextItemType);
        if (e == this && argument[1] instanceof StringLiteral && argument[2] instanceof StringLiteral) {
            // second and third arguments known statically: build an index
            staticMap = buildMap((StringValue)((StringLiteral)argument[1]).getValue(),
                    (StringValue)((StringLiteral)argument[2]).getValue());
        }
        return e;
    }

    /**
    * Evaluate the function
    */

    public StringValue evaluateItem(XPathContext context) throws XPathException {

        StringValue sv1 = (StringValue)argument[0].evaluateItem(context);
        if (sv1==null) {
            return StringValue.EMPTY_STRING;
        }

        if (staticMap != null) {
            CharSequence in = sv1.getStringValueCS();
            CharSequence sb = translateUsingMap(in, staticMap);
            return new StringValue(sb);
        }

        StringValue sv2 = (StringValue)argument[1].evaluateItem(context);

        StringValue sv3 = (StringValue)argument[2].evaluateItem(context);

        return StringValue.makeStringValue(translate(sv1, sv2, sv3));
    }

    /**
     * Get the translation map built at compile time if there is one
     */

    public IntToIntMap getStaticMap() {
        return staticMap;
    }

    /**
    * Perform the translate function
    */

    public static CharSequence translate(StringValue sv0, StringValue sv1, StringValue sv2) {

        // if any string contains surrogate pairs, expand everything to 32-bit characters
        if (sv0.containsSurrogatePairs() || sv1.containsSurrogatePairs() || sv2.containsSurrogatePairs()) {
            return translateUsingMap(sv0.getStringValueCS(), buildMap(sv1, sv2));
        }

        // if the size of the strings is above some threshold, use a hash map to avoid O(n*m) performance
        if (sv0.getStringLength() * sv1.getStringLength() > 1000) {
            // Cut-off point for building the map based on some simple measurements
            return translateUsingMap(sv0.getStringValueCS(), buildMap(sv1, sv2));
        }

        CharSequence cs0 = sv0.getStringValueCS();
        CharSequence cs1 = sv1.getStringValueCS();
        CharSequence cs2 = sv2.getStringValueCS();

        String st1 = cs1.toString();
        FastStringBuffer sb = new FastStringBuffer(cs0.length());
        int s2len = cs2.length();
        int s0len = cs0.length();
        for (int i=0; i<s0len; i++) {
            char c = cs0.charAt(i);
            int j = st1.indexOf(c);
            if (j<s2len) {
                sb.append(( j<0 ? c : cs2.charAt(j) ));
            }
        }
        return sb;
    }

    /**
     * Build an index
     * @param arg1 a string containing characters to be replaced
     * @param arg2 a string containing the corresponding replacement characters
     * @return a map that maps characters to their replacements (or to -1 if the character is to be removed)
     */

    private static IntToIntMap buildMap(StringValue arg1, StringValue arg2) {
        UnicodeString a1 = UnicodeString.makeUnicodeString(arg1.getStringValueCS());
        UnicodeString a2 = UnicodeString.makeUnicodeString(arg2.getStringValueCS());
        IntToIntMap map = new IntToIntHashMap(a1.length(), 0.5);
            // allow plenty of free space, it's better for lookups (though worse for iteration)
        for (int i=0; i<a1.length(); i++) {
            if (map.find(a1.charAt(i))) {
                // no action: duplicate
            } else {
                map.put(a1.charAt(i), (i>a2.length()-1 ? -1 : a2.charAt(i)));
            }
        }
        return map;
    }

    /**
     * Implement the translate() function using an index built at compile time
     * @param in the string to be translated
     * @param map index built at compile time, mapping input characters to output characters. The map returns
     * -1 for a character that is to be deleted from the input string, Integer.MAX_VALUE for a character that is
     * to remain intact
     * @return the translated character string
     */

    public static CharSequence translateUsingMap(CharSequence in, IntToIntMap map) {
        UnicodeString us = UnicodeString.makeUnicodeString(in);
        int len = us.length();
        FastStringBuffer sb = new FastStringBuffer(len);
        for (int i=0; i<len; i++) {
            int c = us.charAt(i);
            int newchar = map.get(c);
            if (newchar == Integer.MAX_VALUE) {
                // character not in map, so is not to be translated
                newchar = c;
            }
            if (newchar == -1) {
                // no action, delete the character
            } else {
                sb.appendWideChar(newchar);
            }
        }
        return sb;
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
        StringValue sv0 = (StringValue)arguments[0].head();
        StringValue sv1 = (StringValue)arguments[1].head();
        StringValue sv2 = (StringValue)arguments[2].head();
        return new StringValue(translate(sv0, sv1, sv2));
    }


}

