////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
 * Class containing utility methods for handling error messages
 */

public class Err {

    public static final int ELEMENT = 1;
    public static final int ATTRIBUTE = 2;
    public static final int FUNCTION = 3;
    public static final int VALUE = 4;
    public static final int VARIABLE = 5;
    public static final int GENERAL = 6;
    public static final int URI = 7;

    /**
     * Add delimiters to represent variable information within an error message
     * @param cs the variable information to be delimited
     * @return the delimited variable information
     */
    public static String wrap(CharSequence cs) {
        return wrap(cs, GENERAL);
    }

    /**
     * Add delimiters to represent variable information within an error message
     * @param cs the variable information to be delimited
     * @param valueType the type of value, e.g. element name or attribute name
     * @return the delimited variable information
     */
    public static String wrap(/*@Nullable*/ CharSequence cs, int valueType) {
        if (cs == null) {
            return "(NULL)";
        }
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.SMALL);
        int len = cs.length();
        for (int i=0; i<len; i++) {
            char c = cs.charAt(i);
            switch (c) {
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
//                case '\\':
//                    sb.append("\\\\");
//                    break;
                default:
                    if (c < 32 || c > 255) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        while (hex.length() < 4) {
                            hex = "0" + hex;
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        String s;
        if (len > 30) {
            if ((valueType == ELEMENT || valueType == ATTRIBUTE) && sb.charAt(0) == '{') {
                StructuredQName qn = StructuredQName.fromClarkName(sb.toString());
                String uri = abbreviateURI(qn.getURI());
                s = "Q{" + uri + "}" + qn.getLocalPart();
            } else if (valueType == URI) {
                s = abbreviateURI(sb.toString());
            } else {
                s = sb.toString().substring(0, 30) + "...";
            }
        } else {
            s = sb.toString();
        }
        switch (valueType) {
            case ELEMENT:
                return "<" + s + ">";
            case ATTRIBUTE:
                return "@" + s;
            case FUNCTION:
                return s + "()";
            case VARIABLE:
                return "$" + s;
            case VALUE:
                return "\"" + s + "\"";
            default:
                return "{" + s + "}";
        }
    }

    /**
     * Create a string representation of an item for use in an error message
     */

    public static CharSequence depict(Item item) {
        if (item instanceof AtomicValue) {
            CharSequence cs = item.getStringValueCS();
            if (item instanceof StringValue) {
                return '\"' + truncate30(cs).toString() + '\"';
            } else {
                return truncate30(cs);
            }
        } else if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo)item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    return "document-node()";
                case Type.ELEMENT:
                    return "<" + node.getDisplayName() + "/>";
                case Type.ATTRIBUTE:
                    return "@" + node.getDisplayName();
                case Type.TEXT:
                    return "text(\"" + truncate30(node.getStringValue()) + "\")";
                case Type.COMMENT:
                    return "<!--" + truncate30(node.getStringValue()) + "-->";
                case Type.PROCESSING_INSTRUCTION:
                    return "<?" + node.getDisplayName() + "?>";
                case Type.NAMESPACE:
                    String prefix = node.getLocalPart();
                    return "xmlns" + (prefix.equals("") ? "" : ":" + prefix) + "=\"" + node.getStringValue() + '"';
                default:
                    return "";
            }
        } else {
            return "function item";
        }
    }

    private static CharSequence truncate30(CharSequence cs) {
        if (cs.length() <= 30) {
            return Whitespace.collapseWhitespace(cs);
        } else {
            return Whitespace.collapseWhitespace(cs.subSequence(0, 30)).toString() + "...";
        }
    }

    /**
     * Abbreviate a URI for use in error messages
     * @param uri the full URI
     * @return the URI, truncated at the last slash or to the last 15 characters, with a leading ellipsis, as appropriate
     */

    public static String abbreviateURI(String uri) {
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash < 0) {
            if (uri.length() > 15) {
                uri = "..." + uri.substring(uri.length()-15);
            }
            return uri;
        } else {
            return "..." + uri.substring(lastSlash);
        }
    }

    public static String wrap(Expression exp) {
        if (ExpressionTool.expressionSize(exp) < 10 && !(exp instanceof Instruction)) {
            return "{" + exp.toString() + "}";
        } else {
            return exp.getExpressionName();
        }
    }


}
