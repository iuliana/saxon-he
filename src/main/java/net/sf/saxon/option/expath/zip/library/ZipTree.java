////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       ZipTree.java                                                */
/*  Author:     F. Georges - fgeorges.org                                   */
/*  Date:       2009-08-03                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2009 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */

package net.sf.saxon.option.expath.zip.library;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A ZIP tree.
 *
 * TODO: Change this class to have a recursive tree structure (a tree contains
 * a list of trees...)
 * 
 * @author Florent Georges
 * @date   2009-08-03
 */
public class ZipTree {

	public ZipTree(ZipFile zip) {
		myRoot = new ZipRootNode(zip);
		for (ZipEntry e : getSortedEntries(zip)) {
			myRoot.add(e, getPath(e.getName()), 0);
		}
	}

	public ZipNode getRoot() {
		return myRoot;
	}

//    // perform a tree search
//    public ZipNode getNode(String name)
//    {
//        // TODO: Check input... (name, and path)
//        String[] path = getPath(name);
//        int pos = 0;
//        ZipNode node = myRoot;
//        while ( pos < path.length && node != null ) {
//System.err.println("NODE: " + myRoot.getName() + ", " + pos + ": " + path[pos]);
//            node = node.getNodes().get(path[pos++]);
//        }
//        return node;
//    }

	private String[] getPath(String name) {
		return name.split("/");
	}

	private ZipEntry[] getSortedEntries(ZipFile f) {
		List<ZipEntry> l = new ArrayList<>();
		for (Enumeration<? extends ZipEntry> e = f.entries(); e.hasMoreElements(); /* */ ) {
			l.add(e.nextElement());
		}
		ZipEntry[] ret = l.toArray(new ZipEntry[0]);
		Arrays.sort(ret, new Comparator() {

			public int compare(Object o1, Object o2) {
				return ((ZipEntry) o1).getName().compareTo(((ZipEntry) o2).getName());
			}
		});
		return ret;
	}

    private ZipRootNode myRoot;

	public static abstract class ZipNode {

		public abstract boolean isDirectory();

		protected Map<String, ZipNode> myNodes;
		protected String myName;
		protected ZipEntry myEntry;

		public void add(ZipEntry e, String[] path, int i) {
			// TODO: Really? Document the whole mechanism!
			if (myNodes == null) {
				return;
			}
			if (i >= path.length) {
				return;
			}
			ZipNode n = myNodes.get(path[i]);
			if (n == null) {
				// intermediate, non-existing dir
				// TODO: Maybe we will see the corresponding ZipEntry later, in
				// such a case, update the ZipEntry object on that node... Or
				// maybe not because the entries are sorted out by
				// #getSortedEntries(ZipFile)...
				if (path.length > i + 1) {
					n = new ZipDirNode(null, path, i);
				}
				// leaf (empty) directory node
				else if (e.isDirectory()) {
					n = new ZipDirNode(e, path, i);
				}
				// file node
				else {
					n = new ZipLeafNode(e, path, i);
				}
				myNodes.put(path[i], n);
			}
			n.add(e, path, i + 1);
		}

		public Map<String, ZipNode> getNodes() {
			return myNodes;
		}

		public ZipNode getNode(String name) {
			return myNodes.get(name);
		}

		public String getName() {
			return myName;
		}

		public ZipEntry getEntry() {
			return myEntry;
		}
	}

	private static class ZipRootNode extends ZipNode {

		public ZipRootNode(ZipFile f) {
			myNodes = new HashMap<>();
			myName = null;
			myEntry = null;
		}

		public boolean isDirectory() {
			return true;
		}
	}

	private static class ZipDirNode	extends ZipNode {

		public ZipDirNode(ZipEntry e, String[] path, int i) {
			assert path.length > i - 2;
			assert e.isDirectory();
			myEntry = e;
			myName = path[i];
			myNodes = new HashMap<>();
		}

		public boolean isDirectory() {
			return true;
		}
	}

	private static class ZipLeafNode extends ZipNode {

		public ZipLeafNode(ZipEntry e, String[] path, int i) {
			assert path.length == i + 1;
			assert !e.isDirectory();
			myEntry = e;
			myName = path[i];
			myNodes = null;
		}

		public boolean isDirectory() {
			return false;
		}
	}
}
