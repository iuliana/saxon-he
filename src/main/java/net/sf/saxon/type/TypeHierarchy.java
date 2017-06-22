////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import javax.xml.transform.SourceLocator;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class exists to provide answers to questions about the type hierarchy. Because
 * such questions are potentially expensive, it caches the answers. There is one instance of
 * this class for a Configuration.
 */

public class TypeHierarchy implements Serializable {

    private Map<ItemTypePair, Integer> map;
    protected Configuration config;

    /**
     * Constant denoting relationship between two types: A is the same type as B
     */
    public static final int SAME_TYPE = 0;
    /**
     * Constant denoting relationship between two types: A subsumes B
     */
    public static final int SUBSUMES = 1;
    /**
     * Constant denoting relationship between two types: A is subsumed by B
     */
    public static final int SUBSUMED_BY = 2;
    /**
     * Constant denoting relationship between two types: A overlaps B
     */
    public static final int OVERLAPS = 3;
    /**
     * Constant denoting relationship between two types: A is disjoint from B
     */
    public static final int DISJOINT = 4;

    //private String[] relnames = {"SAME", "SUBSUMES", "SUBSUMED_BY", "OVERLAPS", "DISJOINT"};

    /**
     * Create the type hierarchy cache for a configuration
     * @param config the configuration
     */

    public TypeHierarchy(Configuration config){
        this.config = config;
        map = new ConcurrentHashMap<ItemTypePair, Integer>();
    }

    /**
     * Apply the function conversion rules to a value, given a required type.
     * The parameter type S represents the supplied type, R the required type
     *
     *
     * @param value        a value to be converted
     * @param requiredType the required type
     * @return the converted value
     * @throws net.sf.saxon.trans.XPathException
     *          if the value cannot be converted to the required type
     */

    public Sequence applyFunctionConversionRules(
            Sequence value, SequenceType requiredType,
            RoleLocator role, SourceLocator locator)
            throws XPathException {

        ItemType suppliedItemType = SequenceTool.getItemType(value, this);

        SequenceIterator iterator = value.iterate();
        final ItemType requiredItemType = requiredType.getPrimaryType();

        if (requiredItemType.isPlainType()) {

            // step 1: apply atomization if necessary

            if (!suppliedItemType.isPlainType()) {
                iterator = Atomizer.getAtomizingIterator(iterator, false);
                suppliedItemType = suppliedItemType.getAtomizedItemType();
            }

            // step 2: convert untyped atomic values to target item type

            if (relationship(suppliedItemType, BuiltInAtomicType.UNTYPED_ATOMIC) != DISJOINT) {
                final boolean nsSensitive = ((SimpleType) requiredItemType).isNamespaceSensitive();
                ItemMappingFunction converter;
                if (nsSensitive) {
                    converter = new ItemMappingFunction() {
                        public Item mapItem(Item item) throws XPathException {
                            if (item instanceof UntypedAtomicValue) {
                                ValidationFailure vf = new ValidationFailure(
                                        "Implicit conversion of untypedAtomic value to " + requiredItemType.toString() +
                                                " is not allowed");
                                vf.setErrorCode("XPTY0117");
                                throw vf.makeException();
                            } else {
                                return item;
                            }
                        }
                    };
                } else if (((SimpleType) requiredItemType).isUnionType()) {
                    final ConversionRules rules = config.getConversionRules();
                    converter = new ItemMappingFunction() {
                        public Item mapItem(Item item) throws XPathException {
                            if (item instanceof UntypedAtomicValue) {
                                try {
                                    return ((SimpleType) requiredItemType).getTypedValue(
                                            item.getStringValueCS(), null, rules).head();
                                } catch (ValidationException ve) {
                                    ve.setErrorCode("XPTY0004");
                                    throw (XPathException)ve;
                                }
                            } else {
                                return item;
                            }
                        }
                    };
                } else {
                    converter = new ItemMappingFunction() {
                        public Item mapItem(Item item) throws XPathException {
                            if (item instanceof UntypedAtomicValue) {
                                ConversionResult val = Converter.convert(
                                        ((UntypedAtomicValue) item), (AtomicType) requiredItemType, config.getConversionRules());
                                if (val instanceof ValidationFailure) {
                                    ValidationFailure vex = (ValidationFailure) val;
                                    throw vex.makeException();
                                }
                                return (Item) val;
                            } else {
                                //noinspection unchecked
                                return item;
                            }
                        }
                    };
                }
                iterator = new ItemMappingIterator(iterator, converter, true);
            }

            // step 3: apply numeric promotion

            if (requiredItemType.equals(BuiltInAtomicType.DOUBLE)) {
                ItemMappingFunction<AtomicValue, DoubleValue> promoter = new ItemMappingFunction<AtomicValue, DoubleValue>() {
                    public DoubleValue mapItem(AtomicValue item) throws XPathException {
                        if (item instanceof NumericValue) {
                            return (DoubleValue) Converter.convert(item, BuiltInAtomicType.DOUBLE, config.getConversionRules()).asAtomic();
                        } else {
                            throw new XPathException(
                                    "Cannot promote non-numeric value to xs:double", "XPTY0004");
                        }
                    }
                };
                iterator = new ItemMappingIterator(iterator, promoter, true);
            } else if (requiredItemType.equals(BuiltInAtomicType.FLOAT)) {
                ItemMappingFunction<AtomicValue, FloatValue> promoter = new ItemMappingFunction<AtomicValue, FloatValue>() {
                    public FloatValue mapItem(AtomicValue item) throws XPathException {
                        if (item instanceof DoubleValue) {
                            throw new XPathException(
                                    "Cannot promote xs:double value to xs:float", "XPTY0004");
                        } else if (item instanceof NumericValue) {
                            return (FloatValue) Converter.convert(item, BuiltInAtomicType.FLOAT, config.getConversionRules()).asAtomic();
                        } else {
                            throw new XPathException(
                                    "Cannot promote non-numeric value to xs:float", "XPTY0004");
                        }
                    }
                };
                iterator = new ItemMappingIterator(iterator, promoter, true);
            }

            // step 4: apply URI-to-string promotion

            if (requiredItemType.equals(BuiltInAtomicType.STRING) &&
                    relationship(suppliedItemType, BuiltInAtomicType.ANY_URI) != DISJOINT) {
                ItemMappingFunction promoter = new ItemMappingFunction() {
                    public Item mapItem(Item item) throws XPathException {
                        if (item instanceof AnyURIValue) {
                            return new StringValue(item.getStringValueCS());
                        } else {
                            return item;
                        }
                    }
                };
                iterator = new ItemMappingIterator(iterator, promoter, true);
            }
        }

        // step 5: apply function coercion

        iterator = applyFunctionCoercion(iterator, suppliedItemType, requiredItemType, role, locator);

        // Add a check that the values conform to the required type

        int relation = relationship(suppliedItemType, requiredItemType);

        if (!(relation == SAME_TYPE || relation == SUBSUMED_BY)) {
            ItemTypeCheckingFunction itemChecker =
                    new ItemTypeCheckingFunction<Item>(requiredItemType, role, locator, config);
            iterator = new ItemMappingIterator(iterator, itemChecker, true);
        }

        if (requiredType.getCardinality() != StaticProperty.ALLOWS_ZERO_OR_MORE) {
            iterator = new CardinalityCheckingIterator(iterator, requiredType.getCardinality(), role, locator);
        }

        return SequenceTool.toLazySequence(iterator);

    }

    /**
     * Apply function coercion when function items are supplied as arguments to a function call.
     * This does not happen in Saxon-HE, so the implementation is subclassed in Saxon-PE/EE
     * @param iterator An iterator over the supplied value of the parameter
     * @param suppliedItemType the item type of the supplied value
     * @param requiredItemType the required item type (typically a function item type)
     * @param role information for diagnostics
     * @param locator information for diagnostics
     * @return an iterator over the converted value
     */

    protected SequenceIterator applyFunctionCoercion(SequenceIterator iterator,
                                                     ItemType suppliedItemType, ItemType requiredItemType,
                                                     RoleLocator role, SourceLocator locator) {
        return iterator;
    }

    /**
     * Get the Saxon configuration to which this type hierarchy belongs
     * @return the configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Determine whether type A is type B or one of its subtypes, recursively.
     * "Subtype" here means a type that is subsumed, that is, a type whose instances
     * are a subset of the instances of the other type.
     *
     * @param subtype identifies the first type
     * @param supertype identifies the second type
     * @return true if the first type is the second type or is subsumed by the second type
     */

    public boolean isSubType(ItemType subtype, /*@NotNull*/ ItemType supertype) {
        int relation = relationship(subtype, supertype);
        return (relation==SAME_TYPE || relation==SUBSUMED_BY);
    }

    /**
     * Determine the relationship of one item type to another.
     * @param t1 the first item type
     * @param t2 the second item type
     * @return {@link #SAME_TYPE} if the types are the same; {@link #SUBSUMES} if the first
     * type subsumes the second (that is, all instances of the second type are also instances
     * of the first); {@link #SUBSUMED_BY} if the second type subsumes the first;
     * {@link #OVERLAPS} if the two types overlap (have a non-empty intersection, but neither
     * subsumes the other); {@link #DISJOINT} if the two types are disjoint (have an empty intersection)
     */

    public int relationship(/*@Nullable*/ ItemType t1, /*@NotNull*/ ItemType t2) {
        if (t1 == null) {
            throw new NullPointerException();
        }
        if (t1.equals(t2)) {
            return SAME_TYPE;
        }
        ItemTypePair pair = new ItemTypePair(t1, t2);
        Integer result = map.get(pair);
        if (result == null) {
            result = computeRelationship(t1, t2);
            map.put(pair, result);
        }
        return result;
    }

    /**
     * Determine the relationship of one item type to another.
     * @param t1 the first item type
     * @param t2 the second item type
     * @return {@link #SAME_TYPE} if the types are the same; {@link #SUBSUMES} if the first
     * type subsumes the second (that is, all instances of the second type are also instances
     * of the first); {@link #SUBSUMED_BY} if the second type subsumes the first;
     * {@link #OVERLAPS} if the two types overlap (have a non-empty intersection, but neither
     * subsumes the other); {@link #DISJOINT} if the two types are disjoint (have an empty intersection)
     */

    private int computeRelationship(/*@NotNull*/ ItemType t1, /*@NotNull*/ ItemType t2) {
        //System.err.println("computeRelationship " + t1 + ", " + t2);
        if (t1 == t2) {
            return SAME_TYPE;
        }
        if (t1 instanceof AnyItemType) {
            if (t2 instanceof AnyItemType) {
                return SAME_TYPE;
            } else {
                return SUBSUMES;
            }
        } else if (t2 instanceof AnyItemType) {
            return SUBSUMED_BY;
         } else if (t1 == ErrorType.getInstance()) {
                return SUBSUMED_BY;
            } else if (t2 == ErrorType.getInstance()) {
                return SUBSUMES;
        } else if (t1.isPlainType()) {
            if (t2 instanceof NodeTest || t2 instanceof FunctionItemType || t2 instanceof ExternalObjectType) {
                return DISJOINT;
            } else if (t2 instanceof ExternalObjectType) {
                if (((AtomicType)t1).getFingerprint() == StandardNames.XS_ANY_ATOMIC_TYPE) {
                    return SUBSUMES;
                } else {
                    return DISJOINT;
                }
            } else if (t1 == BuiltInAtomicType.ANY_ATOMIC && t2.isPlainType()) {
                return SUBSUMES;
            } else if (t2 == BuiltInAtomicType.ANY_ATOMIC) {
                return SUBSUMED_BY;
            } else if (t1 instanceof AtomicType && t2 instanceof AtomicType) {
                if (((AtomicType)t1).getFingerprint() == ((AtomicType)t2).getFingerprint()) {
                    return SAME_TYPE;
                }
                ItemType t = t2;
                while (t.isPlainType()) {
                    if (((AtomicType)t1).getFingerprint() == ((AtomicType)t).getFingerprint()) {
                        return SUBSUMES;
                    }
                    t = t.getSuperType(this);
                }
                t = t1;
                while (t.isPlainType()) {
                    if (((AtomicType)t).getFingerprint() == ((AtomicType)t2).getFingerprint()) {
                        return SUBSUMED_BY;
                    }
                    t = t.getSuperType(this);
                }
                return DISJOINT;

            } else if (!(t1.isAtomicType()) && t2 instanceof PlainType) {
                // relationship(union, atomic) or relationship(union, union)
                Set<PlainType> s1 = ((PlainType)t1).getPlainMemberTypes();
                Set<PlainType> s2 = ((PlainType)t2).getPlainMemberTypes();

                boolean gt = s1.containsAll(s2);
                boolean lt = s2.containsAll(s1);
                if (gt && lt) {
                    return SAME_TYPE;
                } else if (gt) {
                    return SUBSUMES;
                } else if (lt) {
                    return SUBSUMED_BY;
                } else {
                    // TODO: we could return a more precise result, e.g. union(string, decimal) subsumes union(string, int)
                    boolean allSubsumed = true;
                    boolean foundOverlap = false;
                    for (PlainType a1 : s1) {
                        boolean foundSupertype = false;
                        for (PlainType a2 : s2) {
                            int rel = relationship(a1, a2);
                            if (rel == SUBSUMED_BY || rel == SAME_TYPE) {
                                foundSupertype = true;
                                break;
                            }
                            if (rel == SUBSUMES || rel == OVERLAPS) {
                                foundOverlap = true;
                            }
                        }
                        if (!foundSupertype) {
                            allSubsumed = false;
                            break;
                        }
                    }
                    if (allSubsumed) {
                        return SUBSUMED_BY;
                    }
                    allSubsumed = true;
                    for (PlainType a2 : s2) {
                        boolean foundSupertype = false;
                        for (PlainType a1 : s1) {
                            int rel = relationship(a1, a2);
                            if (rel == SUBSUMES || rel == SAME_TYPE) {
                                foundSupertype = true;
                                break;
                            }
                            if (rel == SUBSUMED_BY || rel == OVERLAPS) {
                                foundOverlap = true;
                            }
                        }
                        if (!foundSupertype) {
                            allSubsumed = false;
                            break;
                        }
                    }
                    if (allSubsumed) {
                        return SUBSUMES;
                    }
                    if (foundOverlap) {
                        return OVERLAPS;
                    }
                    return DISJOINT;
                }
            } else if (t1 instanceof AtomicType) {
                // relationship (atomic, union)
                int r = relationship(t2, t1);
                return inverseRelationship(r);

            } else {
                // all options exhausted
                throw new IllegalStateException();
            }
        } else if (t1 instanceof NodeTest) {
            if (t2.isPlainType() || (t2 instanceof FunctionItemType) || (t2 instanceof ExternalObjectType)) {
                return DISJOINT;
            } else {
                // both types are NodeTests
                if (t1 instanceof AnyNodeTest) {
                    if (t2 instanceof AnyNodeTest) {
                        return SAME_TYPE;
                    } else {
                        return SUBSUMES;
                    }
                } else if (t2 instanceof AnyNodeTest) {
                    return SUBSUMED_BY;
//                } else if (t1 instanceof ErrorType) {
//                    return DISJOINT;
//                } else if (t2 instanceof ErrorType) {
//                    return DISJOINT;
                } else {
                    // first find the relationship between the node kinds allowed
                    int nodeKindRelationship;
                    int m1 = ((NodeTest)t1).getNodeKindMask();
                    int m2 = ((NodeTest)t2).getNodeKindMask();
                    if ((m1 & m2) == 0) {
                        return DISJOINT;
                    } else if (m1 == m2) {
                        nodeKindRelationship = SAME_TYPE;
                    } else if ((m1 & m2) == m1) {
                        nodeKindRelationship = SUBSUMED_BY;
                    } else if ((m1 & m2) == m2) {
                        nodeKindRelationship = SUBSUMES;
                    } else {
                        nodeKindRelationship = OVERLAPS;
                    }

                    // now find the relationship between the node names allowed. Note that although
                    // NamespaceTest and LocalNameTest are NodeTests, they do not occur in SequenceTypes,
                    // so we don't need to consider them.
                    int nodeNameRelationship;
                    IntSet n1 = ((NodeTest)t1).getRequiredNodeNames(); // null means all names allowed
                    IntSet n2 = ((NodeTest)t2).getRequiredNodeNames(); // null means all names allowed
                    if (n1 == null) {
                        if (n2 == null) {
                            nodeNameRelationship = SAME_TYPE;
                        } else {
                            nodeNameRelationship = SUBSUMES;
                        }
                    } else if (n2 == null) {
                        nodeNameRelationship = SUBSUMED_BY;
                    } else if (n1.containsAll(n2)) {
                        if (n1.size() == n2.size()) {
                            nodeNameRelationship = SAME_TYPE;
                        } else {
                            nodeNameRelationship = SUBSUMES;
                        }
                    } else if (n2.containsAll(n1)) {
                        nodeNameRelationship = SUBSUMED_BY;
                    } else if (IntHashSet.containsSome(n1, n2)) {
                        nodeNameRelationship = OVERLAPS;
                    } else {
                        nodeNameRelationship = DISJOINT;
                    }

                    // now find the relationship between the content types allowed

                    int contentRelationship = computeContentRelationship(t1, t2, n1, n2);

                    // now analyse the three different relationsships

                    if (nodeKindRelationship == SAME_TYPE &&
                            nodeNameRelationship == SAME_TYPE &&
                            contentRelationship == SAME_TYPE) {
                        return SAME_TYPE;
                    } else if ((nodeKindRelationship == SAME_TYPE || nodeKindRelationship == SUBSUMES) &&
                            (nodeNameRelationship == SAME_TYPE || nodeNameRelationship == SUBSUMES) &&
                            (contentRelationship == SAME_TYPE || contentRelationship == SUBSUMES)) {
                        return SUBSUMES;
                    } else if ((nodeKindRelationship == SAME_TYPE || nodeKindRelationship == SUBSUMED_BY) &&
                            (nodeNameRelationship == SAME_TYPE || nodeNameRelationship == SUBSUMED_BY) &&
                            (contentRelationship == SAME_TYPE || contentRelationship == SUBSUMED_BY)) {
                        return SUBSUMED_BY;
                    } else if (nodeKindRelationship == DISJOINT ||
                            nodeNameRelationship == DISJOINT ||
                            contentRelationship == DISJOINT) {
                        return DISJOINT;
                    } else {
                        return OVERLAPS;
                    }
                }
            }
        } else if (t1 instanceof ExternalObjectType) {
            if (t2 instanceof ExternalObjectType) {
                return ((ExternalObjectType)t1).getRelationship((ExternalObjectType)t2);
            } else {
                return DISJOINT;
            }
        } else {
            // t1 is a FunctionItemType
            if (t2 instanceof FunctionItemType) {
                return ((FunctionItemType)t1).relationship((FunctionItemType)t2, this);
            } else {
                return DISJOINT;
            }
        }

    }

    /**
     * Compute the relationship between the allowed content-types of two types
     * @param t1 the first type
     * @param t2 the second types
     * @param n1 the set of element names allowed by the first type
     * @param n2 the set of element names allowed by the second type
     * @return the relationship (same type, subsumes, overlaps, subsumed-by)
     */
    protected int computeContentRelationship(ItemType t1, ItemType t2, IntSet n1, IntSet n2) {
        return SAME_TYPE;
    }

    private static int inverseRelationship(int relation) {
        switch (relation) {
            case SAME_TYPE: return SAME_TYPE;
            case SUBSUMES: return SUBSUMED_BY;
            case SUBSUMED_BY: return SUBSUMES;
            case OVERLAPS: return OVERLAPS;
            case DISJOINT: return DISJOINT;
            default: throw new IllegalArgumentException();
        }
    }

    public ItemType getGenericFunctionItemType() {
        return AnyItemType.getInstance();
    }


    /**
     * Test whether a type annotation code represents the type xs:ID or one of its subtypes
     * @param typeCode the type annotation to be tested
     * @return true if the type annotation represents an xs:ID
     */

     public boolean isIdCode(int typeCode) {
         typeCode &= NamePool.FP_MASK;
         if (typeCode == StandardNames.XS_ID) {
             return true;
         } else if (typeCode < 1024) {
             // No other built-in type is an ID
             return false;
         } else {
             SchemaType type = config.getSchemaType(typeCode);
             if (type == null) {
                 return false;      // this shouldn't happen, but there's no need to crash right here
             }
             return type.isIdType();
         }
     }

    /**
     * Test whether a type annotation code represents the type xs:IDREF, xs:IDREFS or one of their subtypes
     * @param typeCode the type annotation to be tested
     * @return true if the type annotation represents an xs:IDREF or xs:IDREFS or a subtype thereof
     */

     public boolean isIdrefsCode(int typeCode) {
         typeCode &= NamePool.FP_MASK;
         if (typeCode == StandardNames.XS_IDREF || typeCode == StandardNames.XS_IDREFS) {
             return true;
         } else if (typeCode < 1024) {
             // No other built-in type is an IDREF or IDREFS
             return false;
         } else {
             SchemaType type = config.getSchemaType(typeCode);
             if (type == null) {
                 // shouldn't happen, but we don't need to crash right now
                 return false;
             }
             return type.isIdRefType();
         }
     }

    private static class ItemTypePair implements Serializable {
        ItemType s;
        ItemType t;

        public ItemTypePair(ItemType s, ItemType t) {
            this.s = s;
            this.t = t;
        }

        /**
         * Returns a hash code value for the object.
         * @return a hash code value for this object.
         * @see Object#equals(Object)
         * @see java.util.Hashtable
         */
        public int hashCode() {
            return s.hashCode() ^ t.hashCode();
        }

        /**
         * Indicates whether some other object is "equal to" this one.
         */

        public boolean equals(Object obj) {
            if (obj instanceof ItemTypePair) {
                final ItemTypePair pair = (ItemTypePair)obj;
                return s.equals(pair.s) && t.equals(pair.t);
            } else {
                return false;
            }
        }
    }


}

