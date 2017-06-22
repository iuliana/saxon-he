package net.sf.saxon.om;

/**
 * The IdentityComparable class provides a way to compare class objects for equality
 *
 *
 */
public interface IdentityComparable {

    /**
     * Determine whether two IdentityComparable objects are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * @param other
     * @return true if the two values are indentical, false otherwise
     */
    public boolean isIdentical(IdentityComparable other);
}
