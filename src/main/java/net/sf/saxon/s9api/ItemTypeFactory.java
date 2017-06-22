////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.*;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

/**
 * This class is used for creating ItemType objects.
 *
 * <p>The <code>ItemTypeFactory</code> class is thread-safe.</p>
 */
public class ItemTypeFactory {

    private Processor processor;

    /**
     * Create an ItemTypeFactory
     * @param processor the processor used by this ItemTypeFactory. This must be supplied
     * in the case of user-defined types or types that reference element or attribute names;
     * for built-in types it can be omitted.
     */

    public ItemTypeFactory(Processor processor) {
        this.processor = processor;
    }

    /**
     * Get an item type representing an atomic type. This may be a built-in type in the
     * XML Schema namespace, or a user-defined atomic type. The resulting type will use
     * the conversion rules configured for the supplied <code>Processor</code>.
     *
     * <p>It is undefined whether two calls supplying the same QName will return the same ItemType
     * object.</p>
     * @param name the name of the built-in or user-defined atomic type required
     * @return an ItemType object representing this built-in  or user-defined atomic type
     * @throws SaxonApiException if the type name is not known, or if the type identified by the
     * name is not an atomic type.
     */

    public ItemType getAtomicType(QName name) throws SaxonApiException {
        String uri = name.getNamespaceURI();
        String local = name.getLocalName();
        if (NamespaceConstant.SCHEMA.equals(uri)) {
            int fp = StandardNames.getFingerprint(uri, local);
            Configuration config = processor.getUnderlyingConfiguration();
            if (config.getXsdVersion() == Configuration.XSD10 && config.getXMLVersion() == Configuration.XML10) {
                return getBuiltInAtomicType(fp);
            } else {
                return ItemType.BuiltInAtomicItemType.makeVariant(
                        (ItemType.BuiltInAtomicItemType)getBuiltInAtomicType(fp), config.getConversionRules());
            }
        } else {
            Configuration config = processor.getUnderlyingConfiguration();
            int fp = config.getNamePool().allocate("", name.getNamespaceURI(), local) & NamePool.FP_MASK;
            SchemaType type = config.getSchemaType(fp);
            if (type == null || !type.isAtomicType()) {
                throw new SaxonApiException("Unknown atomic type " + name.getClarkName());
            }
            return new ConstructedItemType((AtomicType)type, processor);
        }
    }


    private ItemType getBuiltInAtomicType(int fp) throws SaxonApiException {
        switch (fp) {
            case StandardNames.XS_ANY_ATOMIC_TYPE:
                return ItemType.ANY_ATOMIC_VALUE;

            case StandardNames.XS_NUMERIC:
                return ItemType.NUMERIC;

            case StandardNames.XS_STRING:
                return ItemType.STRING;

            case StandardNames.XS_BOOLEAN:
                return ItemType.BOOLEAN;

            case StandardNames.XS_DURATION:
                return ItemType.DURATION;

            case StandardNames.XS_DATE_TIME:
                return ItemType.DATE_TIME;

            case StandardNames.XS_DATE:
                return ItemType.DATE;

            case StandardNames.XS_TIME:
                return ItemType.TIME;

            case StandardNames.XS_G_YEAR_MONTH:
                return ItemType.G_YEAR_MONTH;

            case StandardNames.XS_G_MONTH:
                return ItemType.G_MONTH;

            case StandardNames.XS_G_MONTH_DAY:
                return ItemType.G_MONTH_DAY;

            case StandardNames.XS_G_YEAR:
                return ItemType.G_YEAR;

            case StandardNames.XS_G_DAY:
                return ItemType.G_DAY;

            case StandardNames.XS_HEX_BINARY:
                return ItemType.HEX_BINARY;

            case StandardNames.XS_BASE64_BINARY:
                return ItemType.BASE64_BINARY;

            case StandardNames.XS_ANY_URI:
                return ItemType.ANY_URI;

            case StandardNames.XS_QNAME:
                return ItemType.QNAME;

            case StandardNames.XS_NOTATION:
                return ItemType.NOTATION;

            case StandardNames.XS_UNTYPED_ATOMIC:
                return ItemType.UNTYPED_ATOMIC;

            case StandardNames.XS_DECIMAL:
                return ItemType.DECIMAL;

            case StandardNames.XS_FLOAT:
                return ItemType.FLOAT;

            case StandardNames.XS_DOUBLE:
                return ItemType.DOUBLE;

            case StandardNames.XS_INTEGER:
                return ItemType.INTEGER;

            case StandardNames.XS_NON_POSITIVE_INTEGER:
                return ItemType.NON_POSITIVE_INTEGER;

            case StandardNames.XS_NEGATIVE_INTEGER:
                return ItemType.NEGATIVE_INTEGER;

            case StandardNames.XS_LONG:
                return ItemType.LONG;

            case StandardNames.XS_INT:
                return ItemType.INT;

            case StandardNames.XS_SHORT:
                return ItemType.SHORT;

            case StandardNames.XS_BYTE:
                return ItemType.BYTE;

            case StandardNames.XS_NON_NEGATIVE_INTEGER:
                return ItemType.NON_NEGATIVE_INTEGER;

            case StandardNames.XS_POSITIVE_INTEGER:
                return ItemType.POSITIVE_INTEGER;

            case StandardNames.XS_UNSIGNED_LONG:
                return ItemType.UNSIGNED_LONG;

            case StandardNames.XS_UNSIGNED_INT:
                return ItemType.UNSIGNED_INT;

            case StandardNames.XS_UNSIGNED_SHORT:
                return ItemType.UNSIGNED_SHORT;

            case StandardNames.XS_UNSIGNED_BYTE:
                return ItemType.UNSIGNED_BYTE;

            case StandardNames.XS_YEAR_MONTH_DURATION:
                return ItemType.YEAR_MONTH_DURATION;

            case StandardNames.XS_DAY_TIME_DURATION:
                return ItemType.DAY_TIME_DURATION;

            case StandardNames.XS_NORMALIZED_STRING:
                return ItemType.NORMALIZED_STRING;

            case StandardNames.XS_TOKEN:
                return ItemType.TOKEN;

            case StandardNames.XS_LANGUAGE:
                return ItemType.LANGUAGE;

            case StandardNames.XS_NAME:
                return ItemType.NAME;

            case StandardNames.XS_NMTOKEN:
                return ItemType.NMTOKEN;

            case StandardNames.XS_NCNAME:
                return ItemType.NCNAME;

            case StandardNames.XS_ID:
                return ItemType.ID;

            case StandardNames.XS_IDREF:
                return ItemType.IDREF;

            case StandardNames.XS_ENTITY:
                return ItemType.ENTITY;

            case StandardNames.XS_DATE_TIME_STAMP:
                return ItemType.DATE_TIME_STAMP;

            default:
                throw new SaxonApiException("Unknown atomic type " +
                        processor.getUnderlyingConfiguration().getNamePool().getClarkName(fp));
        }
    }

    /**
     * Get an item type that matches any node of a specified kind.
     *
     * <p>This corresponds to the XPath syntactic forms element(), attribute(),
     * document-node(), text(), comment(), processing-instruction(). It also provides
     * an option, not available in the XPath syntax, that matches namespace nodes.</p>
     *
     * <p>It is undefined whether two calls supplying the same argument value will
     * return the same ItemType object.</p>
     *
     * @param kind the kind of node for which a NodeTest is required
     * @return an item type corresponding to the specified kind of node
     */

    public ItemType getNodeKindTest(XdmNodeKind kind) {
        switch (kind) {
        case DOCUMENT:
            return new ConstructedItemType(NodeKindTest.DOCUMENT, processor);
        case ELEMENT:
            return new ConstructedItemType(NodeKindTest.ELEMENT, processor);
        case ATTRIBUTE:
            return new ConstructedItemType(NodeKindTest.ATTRIBUTE, processor);
        case TEXT:
            return new ConstructedItemType(NodeKindTest.TEXT, processor);
        case COMMENT:
            return new ConstructedItemType(NodeKindTest.COMMENT, processor);
        case PROCESSING_INSTRUCTION:
            return new ConstructedItemType(NodeKindTest.PROCESSING_INSTRUCTION, processor);
        case NAMESPACE:
            return new ConstructedItemType(NodeKindTest.NAMESPACE, processor);
        default:
            throw new IllegalArgumentException("XdmNodeKind");
        }
    }

    /**
     * Get an item type that matches nodes of a specified kind with a specified name.
     *
     * <p>This corresponds to the XPath syntactic forms element(name), attribute(name),
     * and processing-instruction(name). In the case of processing-instruction, the supplied
     * QName must have no namespace.</p>
     *
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param kind the kind of nodes that match
     * @param name the name of the nodes that match
     * @return an ItemType that matches nodes of a given node kind with a given name
     * @throws IllegalArgumentException if the node kind is other than element, attribute, or
     * processing instruction, or if the node kind is processing instruction and the name is in a namespace.
     */

    public ItemType getItemType(XdmNodeKind kind, QName name) {
        int k = kind.getNumber();
        if (k == Type.ELEMENT || k == Type.ATTRIBUTE || k == Type.PROCESSING_INSTRUCTION) {
            if (k == Type.PROCESSING_INSTRUCTION && name.getNamespaceURI().length()==0) {
                throw new IllegalArgumentException("The name of a processing instruction must not be in a namespace");
            }
            NameTest type = new NameTest(k,
                    name.getNamespaceURI(), name.getLocalName(), processor.getUnderlyingConfiguration().getNamePool());
            return new ConstructedItemType(type, processor);
        } else {
            throw new IllegalArgumentException("Node kind must be element, attribute, or processing-instruction");
        }
    }

    /**
     * Make an ItemType representing an element declaration in the schema. This is the
     * equivalent of the XPath syntax <code>schema-element(element-name)</code>
     *
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name the element name
     * @return the ItemType
     * @throws SaxonApiException if the schema does not contain a global element declaration
     * for the given name
     */

    public ItemType getSchemaElementTest(QName name) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        int fingerprint = config.getNamePool().allocate("", name.getNamespaceURI(), name.getLocalName());
        SchemaDeclaration decl = config.getElementDeclaration(fingerprint);
        if (decl == null) {
            throw new SaxonApiException("No global declaration found for element " + name.getClarkName());
        }
        NodeTest test = decl.makeSchemaNodeTest();
        return new ConstructedItemType(test, processor);
    }

    /**
     * Make an ItemType that tests an element name and/or schema type. This is the
     * equivalent of the XPath syntax <code>element(element-name, type)</code>
     *
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name the element name, or null if there is no constraint on the name (equivalent to
     * specifying <code>element(*, type)</code>)
     * @param schemaType the name of the required schema type, or null if there is no constraint
     * on the type (equivalent to specifying <code>element(name)</code>)
     * @param nillable if a nilled element is allowed to match the type (equivalent to specifying
     * "?" after the type name). The value is ignored if schemaType is null.
     * @return the constructed ItemType
     * @throws SaxonApiException if the schema does not contain a global element declaration
     * for the given name
     */

    public ItemType getElementTest(/*@Nullable*/ QName name, QName schemaType, boolean nillable) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        NameTest nameTest = null;
        ContentTypeTest contentTest = null;
        if (name != null) {
            int elementFP = config.getNamePool().allocate("", name.getNamespaceURI(), name.getLocalName());
            nameTest = new NameTest(Type.ELEMENT, elementFP, config.getNamePool());
        }
        if (schemaType != null) {
            int typeFP = config.getNamePool().allocate("", schemaType.getNamespaceURI(), schemaType.getLocalName());
            SchemaType type = config.getSchemaType(typeFP);
            if (type == null) {
                throw new SaxonApiException("Unknown schema type " + schemaType.getClarkName());
            }
            contentTest = new ContentTypeTest(Type.ELEMENT, type, config, nillable);
        }
        if (contentTest == null) {
            if (nameTest == null) {
                return getNodeKindTest(XdmNodeKind.ELEMENT);
            } else {
                return new ConstructedItemType(nameTest, processor);
            }
        } else {
            if (nameTest == null) {
                return new ConstructedItemType(contentTest, processor);
            } else {
                CombinedNodeTest combo = new CombinedNodeTest(
                        nameTest,
                        Token.INTERSECT,
                        contentTest);
                return new ConstructedItemType(combo, processor);
            }
        }
    }

    /**
     * Get an ItemType representing an attribute declaration in the schema. This is the
     * equivalent of the XPath syntax <code>schema-attribute(attribute-name)</code>
     *
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name the attribute name
     * @return the ItemType
     * @throws SaxonApiException if the schema does not contain a global attribute declaration
     * for the given name
     */

    public ItemType getSchemaAttributeTest(QName name) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        int fingerprint = config.getNamePool().allocate("", name.getNamespaceURI(), name.getLocalName());
        SchemaDeclaration decl = config.getAttributeDeclaration(fingerprint);
        if (decl == null) {
            throw new SaxonApiException("No global declaration found for attribute " + name.getClarkName());
        }
        NodeTest test = decl.makeSchemaNodeTest();
        return new ConstructedItemType(test, processor);
    }

    /**
     * Get an ItemType that tests an element name and/or schema type. This is the
     * equivalent of the XPath syntax <code>element(element-name, type)</code>
     *
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name the element name, or null if there is no constraint on the name (equivalent to
     * specifying <code>element(*, type)</code>)
     * @param schemaType the name of the required schema type, or null of there is no constraint
     * on the type (equivalent to specifying <code>element(name)</code>)
     * @return the constructed ItemType
     * @throws SaxonApiException if the schema does not contain a global element declaration
     * for the given name
     */

    public ItemType getAttributeTest(QName name, QName schemaType) throws SaxonApiException {
        NameTest nameTest = null;
        ContentTypeTest contentTest = null;
        Configuration config = processor.getUnderlyingConfiguration();
        if (name != null) {
            int attributeFP = config.getNamePool().allocate("", name.getNamespaceURI(), name.getLocalName());
            nameTest = new NameTest(Type.ATTRIBUTE, attributeFP, config.getNamePool());
        }
        if (schemaType != null) {
            int typeFP = config.getNamePool().allocate("", schemaType.getNamespaceURI(), schemaType.getLocalName());
            SchemaType type = config.getSchemaType(typeFP);
            if (type == null) {
                throw new SaxonApiException("Unknown schema type " + schemaType.getClarkName());
            }
            contentTest = new ContentTypeTest(Type.ATTRIBUTE, type, config, false);
        }
        if (contentTest == null) {
            if (nameTest == null) {
                return getNodeKindTest(XdmNodeKind.ATTRIBUTE);
            } else {
                return new ConstructedItemType(nameTest, processor);
            }
        } else {
            if (nameTest == null) {
                return new ConstructedItemType(contentTest, processor);
            } else {
                CombinedNodeTest combo = new CombinedNodeTest(
                        nameTest,
                        Token.INTERSECT,
                        contentTest);
                return new ConstructedItemType(combo, processor);
            }
        }
    }

    /**
     * Make an ItemType representing a document-node() test with an embedded element test.
     * This reflects the XPath syntax <code>document-node(element(N, T))</code> or
     * <code>document-node(schema-element(N))</code>.
     *
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param elementTest the elementTest. An IllegalArgumentException is thrown if the supplied
     * ItemTest is not an elementTest or schemaElementTest.
     * @return a new ItemType representing the document test
     */

    public ItemType getDocumentTest(ItemType elementTest) {
        net.sf.saxon.type.ItemType test = elementTest.getUnderlyingItemType();
        if (test.getPrimitiveType() != Type.ELEMENT) {
            throw new IllegalArgumentException("Supplied itemType is not an element test");
        }
        DocumentNodeTest docTest = new DocumentNodeTest((NodeTest)test);
        return new ConstructedItemType(docTest, processor);
    }

    /**
     * Get an ItemType representing the type of a Java object when used as an external object
     * for use in conjunction with calls on extension/external functions.
     * @param externalClass a Java class
     * @return the ItemType representing the type of external objects of this class
     */

    public ItemType getExternalObjectType(Class externalClass) {
        ExternalObjectType type = new ExternalObjectType(externalClass, processor.getUnderlyingConfiguration());
        return new ConstructedItemType(type, processor);
    }

    /**
     * Factory method to construct an "external object". This is an XDM value that wraps a Java
     * object. Such values can be passed as parameters to stylesheets or queries, for use in conjunction
     * with external (extension) functions.
     *
     * <p>Changed in Saxon 9.5 to return XdmItem rather than XdmAtomicValue, since wrapped
     * external objects are now modelled as a separate type of item, rather than as an atomic value.</p>
     *
     * @param object the value to be wrapped as an external object. Must not be null.
     * @return the object, wrapped as an XdmAtomicValue
     */

    public XdmItem getExternalObject(Object object) {
        return (XdmItem)XdmItem.wrap(new ObjectValue(object));
    }

    /**
     * Get an ItemType representing the type of a supplied XdmItem. If the supplied item is
     * an atomic value, the returned ItemType will reflect the most specific atomic type of the
     * item. If the supplied item is a node, the returned item type will reflect the node kind,
     * and if the node has a name, then its name. It will not reflect the type annotation.
     * @param item the supplied item whose type is required
     * @return the type of the supplied item
     */

    public ItemType getItemType(XdmItem item) {
        Configuration config = processor.getUnderlyingConfiguration();
        if (item.isAtomicValue()) {
            AtomicValue value = (AtomicValue)item.getUnderlyingValue();

            AtomicType type = (AtomicType)value.getItemType();
            return new ConstructedItemType(type, processor);
        } else {
            NodeInfo node = (NodeInfo)item.getUnderlyingValue();
            int kind = node.getNodeKind();
            int fp = node.getFingerprint();
            if (fp == -1) {
                return new ConstructedItemType(NodeKindTest.makeNodeKindTest(kind), processor);
            } else {
                return new ConstructedItemType(new NameTest(kind, fp, config.getNamePool()), processor);
            }
        }
    }

}

