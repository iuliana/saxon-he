////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.WhitespaceTextImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * XSLT 2.0 deep-equal() function.
 * Supports deep comparison of two sequences (of nodes and/or atomic values)
 * optionally using a collation
 */

public class DeepEqual extends CollatingFunction implements Callable {

	/**
	 * Flag indicating that two elements should only be considered equal if they have the same
	 * in-scope namespaces
	 */
	public static final int INCLUDE_NAMESPACES = 1;

	/**
	 * Flag indicating that two element or attribute nodes are considered equal only if their
	 * names use the same namespace prefix
	 */
	public static final int INCLUDE_PREFIXES = 1<<1;

	/**
	 * Flag indicating that comment children are taken into account when comparing element or document nodes
	 */
	public static final int INCLUDE_COMMENTS = 1<<2;

	/**
	 * Flag indicating that processing instruction nodes are taken into account when comparing element or document nodes
	 */
	public static final int INCLUDE_PROCESSING_INSTRUCTIONS = 1<<3;

	/**
	 * Flag indicating that whitespace text nodes are ignored when comparing element nodes
	 */
	public static final int EXCLUDE_WHITESPACE_TEXT_NODES = 1<<4;

	/**
	 * Flag indicating that elements and attributes should always be compared according to their string
	 * value, not their typed value
	 */
	public static final int COMPARE_STRING_VALUES = 1<<5;

	/**
	 * Flag indicating that elements and attributes must have the same type annotation to be considered
	 * deep-equal
	 */
	public static final int COMPARE_ANNOTATIONS = 1<<6;

	/**
	 * Flag indicating that a warning message explaining the reason why the sequences were deemed non-equal
	 * should be sent to the ErrorListener
	 */
	public static final int WARNING_IF_FALSE = 1<<7;

	/**
	 * Flag indicating that adjacent text nodes in the top-level sequence are to be merged
	 */

	public static final int JOIN_ADJACENT_TEXT_NODES = 1<<8;

	/**
	 * Flag indicating that the is-id and is-idref flags are to be compared
	 */

	public static final int COMPARE_ID_FLAGS = 1<<9;

    /**
     * Flag indicating that the variety of the type of a node is to be ignored (for example, a mixed content
     * node can compare equal to an element-only content node
     */

    public static final int EXCLUDE_VARIETY = 1<<10;

    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType type0 = argument[0].getItemType(th);
        ItemType type1 = argument[1].getItemType(th);
        if (type0 instanceof AtomicType && type1 instanceof AtomicType) {
            preAllocateComparer((AtomicType)type0, (AtomicType)type1, visitor.getStaticContext(), false);
        }
    }

    /**
     * Get the argument position (0-based) containing the collation name
     * @return the position of the argument containing the collation URI
     */
    @Override
    protected int getCollationArgument() {
        return 2;
    }

    /**
	 * Evaluate the expression
	 */

	public BooleanValue evaluateItem(XPathContext context) throws XPathException {

		SequenceIterator op1 = argument[0].iterate(context);
		SequenceIterator op2 = argument[1].iterate(context);
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer == null) {
            comparer = getAtomicComparer(getCollator(context), context);
        } else if(!(context instanceof EarlyEvaluationContext)) {
            comparer = comparer.provideContext(context);
        }
        try {
			return BooleanValue.get(deepEquals(op1, op2, comparer, context, 0));
		} catch (XPathException e) {
			e.maybeSetLocation(this);
			e.maybeSetContext(context);
			throw e;
		}
	}

	/*
	 * Evaluate the expression
	 * Common-up code.
	 */
	private BooleanValue deepEqual(SequenceIterator op1, SequenceIterator op2, AtomicComparer comparer, XPathContext context) throws XPathException {
		try {
			return BooleanValue.get(deepEquals(op1, op2, comparer, context, 0));
		} catch (XPathException e) {
			e.maybeSetLocation(this);
			e.maybeSetContext(context);
			throw e;
		}
	}

	/**
	 * Determine when two sequences are deep-equal
	 * @param op1 the first sequence
	 * @param op2 the second sequence
	 * @param collator the collator to be used
	 * @param context the XPathContext item
	 * @param flags bit-significant integer giving comparison options. Always zero for standard
	 * F+O deep-equals comparison.
	 * @return true if the sequences are deep-equal
	 * @throws XPathException if either sequence contains a function item
	 */

	public static boolean deepEquals(SequenceIterator op1, SequenceIterator op2,
			AtomicComparer collator, XPathContext context, int flags)
	throws XPathException {
		boolean result = true;
		StringBuilder reason = new StringBuilder();


		try {

			if ((flags & JOIN_ADJACENT_TEXT_NODES) != 0) {
				op1 = mergeAdjacentTextNodes(op1);
				op2 = mergeAdjacentTextNodes(op2);
			}
			while (true) {
				Item item1 = op1.next();
				Item item2 = op2.next();

				if (item1 == null && item2 == null) {
					break;
				}

				if (item1 == null || item2 == null) {
					result = false;
                    if (item1 == null) {
						reason = new StringBuilder("Second sequence is longer (first sequence length = ")
							.append(op2.position()).append(')');
                    } else {
						reason = new StringBuilder("First sequence is longer (second sequence length = ")
							.append(op1.position()).append(')');
                    }
                    if (item1 instanceof WhitespaceTextImpl || item2 instanceof WhitespaceTextImpl) {
                        reason.append(" (the first extra node is whitespace text)");
                    }
                    break;
				}

				if (item1 instanceof FunctionItem || item2 instanceof FunctionItem) {
					if (item1 instanceof FunctionItem && item2 instanceof FunctionItem) {
						// two maps can be deep-equal
						//XPathContext context = new EarlyEvaluationContext(config, config.getCollationMap());
						boolean fe = ((FunctionItem)item1).deepEquals(((FunctionItem)item2), context, collator, flags);
                        if (!fe) {
                            result = false;
                            reason = new StringBuilder("maps at position ").append(op1.position()).append(" differ");
                            break;
                        }
                        return fe;
					} else {
						throw new XPathException("Argument to deep-equal() contains a function item", "FOTY0015");
					}
				}

                if (item1 instanceof ObjectValue || item2 instanceof ObjectValue) {
                    if (item1 instanceof ObjectValue && item2 instanceof ObjectValue) {
                        boolean oe = item1.equals(item2);
                        if (!oe) {
                            result = false;
							reason = new StringBuilder("external objects at position ").append(op1.position())
								.append(" differ");
                            break;
                        }
                        return oe;
                    } else {
                        result = false;
                        reason = new StringBuilder("external object at position ").append(op1.position());
                        break;
                    }
                }

				if (item1 instanceof NodeInfo) {
					if (item2 instanceof NodeInfo) {
						if (!deepEquals((NodeInfo)item1, (NodeInfo)item2, collator, context.getConfiguration(), flags)) {
							result = false;
							reason = new StringBuilder("nodes at position ").append(op1.position()).append(" differ");
							break;
						}
					} else {
						result = false;
						reason = new StringBuilder("comparing a node to an atomic value at position ")
							.append(op1.position());
						break;
					}
				} else {
					if (item2 instanceof NodeInfo) {
						result = false;
						reason = new StringBuilder("comparing an atomic value to a node at position ")
							.append(op1.position());
						break;
					} else {
						AtomicValue av1 = ((AtomicValue)item1);
						AtomicValue av2 = ((AtomicValue)item2);
						if (av1.isNaN() && av2.isNaN()) {
							// treat as equal, no action
						} else if (!collator.comparesEqual(av1, av2)) {
							result = false;
							reason = new StringBuilder("atomic values at position ").append(op1.position())
								.append(" differ");
							break;
						}
					}
				}
			} // end while

		} catch (ClassCastException err) {
			// this will happen if the sequences contain non-comparable values
			// comparison errors are masked
            //err.printStackTrace();
			result = false;
			reason = new StringBuilder("sequences contain non-comparable values");
		}catch(NoDynamicContextException err){
            throw err;
        }
        catch (XPathException err) {
			// comparison errors are masked
			if ("FOTY0015".equals(err.getErrorCodeLocalPart()) && NamespaceConstant.ERR.equals(err.getErrorCodeNamespace())) {
				throw err;
			}
			result = false;
			reason = new StringBuilder("error occurred while comparing two values (").append(err.getMessage())
				.append(')');
		}

		if (!result) {
			explain(context.getConfiguration(), reason.toString(), flags, null, null);
			//                config.getErrorListener().warning(
			//                        new XPathException("deep-equal(): " + reason)
			//                );
		}

		return result;
	}

	/*
	 * Determine whether two nodes are deep-equal
	 */

	private static boolean deepEquals(NodeInfo n1, NodeInfo n2,
			AtomicComparer comparer, Configuration config, int flags)
	throws XPathException {
		// shortcut: a node is always deep-equal to itself
		if (n1.isSameNodeInfo(n2)) return true;

		if (n1.getNodeKind() != n2.getNodeKind()) {
			explain(config, "node kinds differ: comparing " + Type.displayTypeName(n1) + " to " + Type.displayTypeName(n2), flags, n1, n2);
			return false;
		}

		final NamePool pool = config.getNamePool();
		switch (n1.getNodeKind()) {
		case Type.ELEMENT:
			if (n1.getFingerprint() != n2.getFingerprint()) {
				explain(config, "element names differ: " + config.getNamePool().getClarkName(n1.getFingerprint()) +
						" != " + config.getNamePool().getClarkName(n2.getFingerprint()), flags, n1, n2);
				return false;
			}
			if (((flags & INCLUDE_PREFIXES) != 0) && (n1.getNameCode() != n2.getNameCode())) {
				explain(config, "element prefixes differ: " + n1.getPrefix() +
						" != " + n2.getPrefix(), flags, n1, n2);
				return false;
			}
			AxisIterator a1 = n1.iterateAxis(AxisInfo.ATTRIBUTE);
			AxisIterator a2 = n2.iterateAxis(AxisInfo.ATTRIBUTE);
			if (Count.count(a1.getAnother()) != Count.count(a2)) {
				explain(config, "elements have different number of attributes", flags, n1, n2);
				return false;
			}
			while (true) {
				NodeInfo att1 = a1.next();
				if (att1 == null) break;

				AxisIterator a2iter = n2.iterateAxis(AxisInfo.ATTRIBUTE,
						new NameTest(Type.ATTRIBUTE, att1.getFingerprint(), pool));
				NodeInfo att2 = a2iter.next();

				if (att2==null) {
					explain(config, "one element has an attribute " +
							config.getNamePool().getClarkName(att1.getFingerprint()) +
							", the other does not", flags, n1, n2);
					return false;
				}
				if (!deepEquals(att1, att2, comparer, config, flags)) {
					deepEquals(att1, att2, comparer, config, flags);
					explain(config, "elements have different values for the attribute " +
							config.getNamePool().getClarkName(att1.getFingerprint()), flags, n1, n2);
					return false;
				}
			}
			if ((flags & INCLUDE_NAMESPACES) != 0) {
				HashSet<NamespaceBinding> ns1 = new HashSet<NamespaceBinding>(10);
				HashSet<NamespaceBinding> ns2 = new HashSet<NamespaceBinding>(10);
				AxisIterator it1 = n1.iterateAxis(AxisInfo.NAMESPACE);
				while (true) {
					NodeInfo nn1 = it1.next();
					if (nn1 == null) {
						break;
					}
					NamespaceBinding nscode1 = new NamespaceBinding(nn1.getLocalPart(), nn1.getStringValue());
					ns1.add(nscode1);
				}
				AxisIterator it2 = n2.iterateAxis(AxisInfo.NAMESPACE);
				while (true) {
					NodeInfo nn2 = it2.next();
					if (nn2 == null) {
						break;
					}
					NamespaceBinding nscode2 = new NamespaceBinding(nn2.getLocalPart(), nn2.getStringValue());
					ns2.add(nscode2);
				}
				if (!ns1.equals(ns2)) {
					explain(config, "elements have different in-scope namespaces", flags, n1, n2);
					return false;
				}
			}

			if ((flags & COMPARE_ANNOTATIONS) != 0) {
				if (!n1.getSchemaType().equals(n2.getSchemaType())) {
					explain(config, "elements have different type annotation", flags, n1, n2);
					return false;
				}
			}

            if ((flags & EXCLUDE_VARIETY) == 0) {
                if (n1.getSchemaType().isComplexType() != n2.getSchemaType().isComplexType()) {
                     explain(config, "one element has complex type, the other simple", flags, n1, n2);
                     return false;
                }

                if (n1.getSchemaType().isComplexType()) {
                    int variety1 = ((ComplexType)n1.getSchemaType()).getVariety();
                    int variety2 = ((ComplexType)n2.getSchemaType()).getVariety();
                    if (variety1 != variety2) {
                        explain(config, "both elements have complex type, but a different variety", flags, n1, n2);
                        return false;
                    }
                }
            }

			if ((flags & COMPARE_STRING_VALUES) == 0) {
				final SchemaType type1 = n1.getSchemaType();
				final SchemaType type2 = n2.getSchemaType();
				final boolean isSimple1 = type1.isSimpleType() || ((ComplexType)type1).isSimpleContent();
				final boolean isSimple2 = type2.isSimpleType() || ((ComplexType)type2).isSimpleContent();
				if (isSimple1 != isSimple2) {
					explain(config, "one element has a simple type, the other does not", flags, n1, n2);
					return false;
				}
				if (isSimple1 && isSimple2) {
					final SequenceIterator v1 = n1.atomize().iterate();
					final SequenceIterator v2 = n2.atomize().iterate();
					return deepEquals(v1, v2, comparer, config.getConversionContext(), flags);
				}
			}

			if ((flags & COMPARE_ID_FLAGS) != 0) {
				if (n1.isId() != n2.isId()) {
					explain(config, "one element is an ID, the other is not", flags, n1, n2);
					return false;
				}
				if (n1.isIdref() != n2.isIdref()) {
					explain(config, "one element is an IDREF, the other is not", flags, n1, n2);
					return false;
				}
			}
			// fall through
		case Type.DOCUMENT:
			AxisIterator c1 = n1.iterateAxis(AxisInfo.CHILD);
			AxisIterator c2 = n2.iterateAxis(AxisInfo.CHILD);
			while (true) {
				NodeInfo d1 = c1.next();
				while (d1 != null && isIgnorable(d1, flags))  {
					d1 = c1.next();
				}
				NodeInfo d2 = c2.next();
				while (d2 != null && isIgnorable(d2, flags))  {
					d2 = c2.next();
				}
				if (d1 == null || d2 == null) {
					boolean r = (d1 == d2);
					if (!r) {
						explain(config, "nodes have different numbers of children", flags, n1, n2);
					}
					return r;
				}
				if (!deepEquals(d1, d2, comparer, config, flags)) {
					return false;
				}
			}

		case Type.ATTRIBUTE:
			if (n1.getFingerprint() != n2.getFingerprint()) {
				explain(config, "attribute names differ: " +
						config.getNamePool().getClarkName(n1.getFingerprint()) +
						" != " + config.getNamePool().getClarkName(n2.getFingerprint()), flags, n1, n2);
				return false;
			}
			if (((flags & INCLUDE_PREFIXES) != 0) && (n1.getNameCode() != n2.getNameCode())) {
				explain(config, "attribute prefixes differ: " + n1.getPrefix() +
						" != " + n2.getPrefix(), flags, n1, n2);
				return false;
			}
			if ((flags & COMPARE_ANNOTATIONS) != 0) {
				if (!n1.getSchemaType().equals(n2.getSchemaType())) {
					explain(config, "attributes have different type annotations", flags, n1, n2);
					return false;
				}
			}
			boolean ar;
			if ((flags & COMPARE_STRING_VALUES) == 0) {
				ar = deepEquals(n1.atomize().iterate(), n2.atomize().iterate(), comparer, config.getConversionContext(), 0);
			} else {
				ar = comparer.comparesEqual(
						new StringValue(n1.getStringValueCS()), 
						new StringValue(n2.getStringValueCS()));
			}
			if (!ar) {
				explain(config, "attribute values differ", flags, n1, n2);
				return false;
			}
			if ((flags & COMPARE_ID_FLAGS) != 0) {
				if (n1.isId() != n2.isId()) {
					explain(config, "one attribute is an ID, the other is not", flags, n1, n2);
					return false;
				}
				if (n1.isIdref() != n2.isIdref()) {
					explain(config, "one attribute is an IDREF, the other is not", flags, n1, n2);
					return false;
				}
			}
			return true;


		case Type.PROCESSING_INSTRUCTION:
		case Type.NAMESPACE:
			if (n1.getFingerprint() != n2.getFingerprint()) {
				explain(config, Type.displayTypeName(n1) + " names differ", flags, n1, n2);
				return false;
			}
			// drop through
		case Type.TEXT:
		case Type.COMMENT:
			boolean vr = (comparer.comparesEqual((AtomicValue)n1.atomize(), (AtomicValue)n2.atomize()));
			if (!vr && ((flags & WARNING_IF_FALSE) != 0)) {
				String v1 = n1.atomize().getStringValue();
                String v2 = n2.atomize().getStringValue();
                StringBuilder message = new StringBuilder();
                if (v1.length() != v2.length()) {
                    message.append("lengths (").append(v1.length()).append(',').append(v2.length()).append(')');
                }
                int min = Math.min(v1.length(), v2.length());

                if (v1.substring(0, min).equals(v2.substring(0, min))) {
					message.append(" different at char ").append(min).append("(\"").append(
						StringValue.diagnosticDisplay((v1.length() > v2.length() ? v1 : v2).substring(min)) + "\")");
                } else if (v1.charAt(0) != v2.charAt(0)) {
					message.append(" different at start " + "(\"").append(v1.substring(0, Math.min(v1.length(), 10)))
						.append("\", \"").append(v2.substring(0, Math.min(v2.length(), 10))).append("\")");
                } else {
                    for (int i=1; i<min; i++) {
                        if (!v1.substring(0, i).equals(v2.substring(0, i))) {
                            message.append(" different at char ").append(i).append("(\"").append(
                                v1.substring(i, Math.min(v1.length(), i+10))).append("\", \"").append(
                                v2.substring(i, Math.min(v2.length(), i+10))).append("\")");
                            break;
                        }
                    }
                }
				explain(config, Type.displayTypeName(n1) + " values differ (" +
						Navigator.getPath(n1) + ", " + Navigator.getPath(n2) + "): " +
						message, flags, n1, n2);
			}
			return vr;

		default:
			throw new IllegalArgumentException("Unknown node type");
		}
	}

	private static boolean isIgnorable(NodeInfo node, int flags) {
		final int kind = node.getNodeKind();
		if (kind == Type.COMMENT) {
			return (flags & INCLUDE_COMMENTS)==0;
		} else if (kind == Type.PROCESSING_INSTRUCTION) {
			return (flags & INCLUDE_PROCESSING_INSTRUCTIONS)==0;
		} else if (kind == Type.TEXT) {
			return ((flags & EXCLUDE_WHITESPACE_TEXT_NODES)!=0) &&
			Whitespace.isWhite(node.getStringValueCS());
		}
		return false;
	}

	private static void explain(Configuration config, String message, int flags, NodeInfo n1, NodeInfo n2) {
		try {
			if ((flags & WARNING_IF_FALSE) != 0) {
				config.getErrorListener().warning(new XPathException("deep-equal() " +
                        (n1 != null && n2 != null ?
                                "comparing " + Navigator.getPath(n1) + " to " + Navigator.getPath(n2) + ": " :
                                ": ") +
                        message));
			}
		} catch (TransformerException e) {
			//
		}
	}

	private static SequenceIterator mergeAdjacentTextNodes(SequenceIterator in) throws XPathException {
		Configuration config = null;
		List<Item> items = new ArrayList<Item>(20);
		boolean prevIsText = false;
		FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.SMALL);
		while (true) {
			Item next = in.next();
			if (next == null) {
				break;
			}
			if (next instanceof NodeInfo && ((NodeInfo)next).getNodeKind() == Type.TEXT) {
				textBuffer.append(next.getStringValueCS());
				prevIsText = true;
				config = ((NodeInfo)next).getConfiguration();
			} else {
				if (prevIsText) {
					Orphan textNode = new Orphan(config);
					textNode.setNodeKind(Type.TEXT);
					textNode.setStringValue(textBuffer.toString()); // must copy the buffer before reusing it
					items.add(textNode);
					textBuffer.setLength(0);
				}
				prevIsText = false;
				items.add(next);
			}
		}
		if (prevIsText) {
			Orphan textNode = new Orphan(config);
			textNode.setNodeKind(Type.TEXT);
			textNode.setStringValue(textBuffer.toString()); // must copy the buffer before reusing it
			items.add(textNode);
		}
		SequenceExtent<Item> extent = new SequenceExtent<Item>(items);
		return extent.iterate();
	}

    /**
     * Execute a dynamic call to the function
     * @param context the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException
     */

	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringCollator collator = getCollatorFromLastArgument(arguments, 2, context);
        GenericAtomicComparer comparer = new GenericAtomicComparer(collator, context);
		return deepEqual(arguments[0].iterate(), arguments[1].iterate(), comparer, context);
	}
}

