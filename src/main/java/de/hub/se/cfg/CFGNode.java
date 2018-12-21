package de.hub.se.cfg;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 
 * Implementation based on jpf-memoize by Guowei Yang (guoweiyang@utexas.edu).
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 *
 */
public class CFGNode implements Serializable {

	private static final long serialVersionUID = 4625833866485308480L;
	private static int globalCurrentNodeID = 0;

	protected int nodeId;

	protected Set<CFGNode> successors = new HashSet<CFGNode>();
	protected Set<CFGNode> predecessors = new HashSet<CFGNode>();
	protected boolean isVirtual;
	protected boolean isRootNode;

	// start and end offsets of the node
	protected int startOffset;
	protected int endOffset;

	protected String fullQualifiedMethodName;

	// line number in source code
	protected int startSourceLineNumber;
	protected int endSourceLineNumber;

	/* Maps target node id to distance. */
	protected Map<Integer, Integer> distances = new HashMap<>();

	/*************************************************************************
	 * Creates a new node.
	 */
	public CFGNode(int startOffset, int endOffset, String fullQualifiedMethodName, int startSourceLineNumber,
			int endSourceLineNumber) {
		this.nodeId = generateNewNodeID();
		this.fullQualifiedMethodName = fullQualifiedMethodName;
		this.isVirtual = false;
		this.isRootNode = false;
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.startSourceLineNumber = startSourceLineNumber;
		this.endSourceLineNumber = endSourceLineNumber;
	}

	public static int generateNewNodeID() {
		return globalCurrentNodeID++;
	}

	/*************************************************************************
	 * Gets this node's id.
	 *
	 */
	public int getId() {
		return nodeId;
	}

	public void setFullQualifiedMethodName(String fullQualifiedMethodName) {
		this.fullQualifiedMethodName = fullQualifiedMethodName;
	}

	public String getFullQualifiedMethodName() {
		return this.fullQualifiedMethodName;
	}

	/*************************************************************************
	 * Adds a node to this node's successor list.
	 */
	public void addSuccessor(CFGNode n) {
		successors.add(n);
	}

	/*************************************************************************
	 * Removes a node from this node's successor list.
	 */
	public void removeSuccessor(CFGNode n) {
		successors.remove(n);
	}

	/*************************************************************************
	 * Adds a node to this node's predecessor list.
	 */
	public void addPredecessor(CFGNode n) {
		predecessors.add(n);
	}

	/*************************************************************************
	 * Removes a node from this node's predecessor list.
	 */
	public void removePredecessor(CFGNode n) {
		predecessors.remove(n);
	}

	/*************************************************************************
	 * Sets the start offset of the node
	 */
	public void setStartOffset(int startOffset) {
		this.startOffset = startOffset;
	}

	/*************************************************************************
	 * Gets the start offset for this node.
	 */
	public int getStartOffset() {
		return startOffset;
	}

	/*************************************************************************
	 * Sets the end offset of this node.
	 */
	public void setEndOffset(int endOffset) {
		this.endOffset = endOffset;
	}

	/*************************************************************************
	 * Gets the end offset for this node.
	 */
	public int getEndOffset() {
		return endOffset;
	}

	/*************************************************************************
	 * Gets this node's predecessors set
	 */
	public Set<CFGNode> getPredecessors() {
		return predecessors;
	}

	/*************************************************************************
	 * Gets this node's successors set
	 */
	public Set<CFGNode> getSuccessors() {
		return successors;
	}

	/*************************************************************************
	 * Sets if the node is virtual or not
	 */
	public void setVirtual(boolean flag) {
		this.isVirtual = flag;
	}

	/*************************************************************************
	 * Gets if the node is virtual or not
	 */
	public boolean isVirtual() {
		return this.isVirtual;
	}

	public void setRootNode(boolean flag) {
		this.isRootNode = flag;
	}

	public boolean isRootNode() {
		return this.isRootNode;
	}

	/*************************************************************************
	 * Returns a string representation of this node.
	 */
	public String toString() {
		// return new String(nodeId + "(" + super.toString() +
		// ", (" +
		// "), " + "[" + startOffset + ", " + endOffset +
		// "]," + ")");
		return new String(
				"[" + nodeId + ", [" + startOffset + ", " + endOffset + "], "
						+ ((startSourceLineNumber == endSourceLineNumber) ? startSourceLineNumber
								: (startSourceLineNumber + "-" + endSourceLineNumber))
						+ "]" + (isVirtual() ? "v" : ""));
	}

	public void setSourceLineNumber(int startLineNumber, int endLineNumber) {
		this.startSourceLineNumber = startLineNumber;
		this.endSourceLineNumber = endLineNumber;
	}

	public int getStartSourceLineNumber() {
		return this.startSourceLineNumber;
	}

	public int getEndSourceLineNumber() {
		return this.endSourceLineNumber;
	}

	public void setDistance(int targetNodeId, int distance) {
		distances.put(targetNodeId, distance);
	}

	public boolean setDistanceIfBetter(int targetNodeId, int newDistance) {
		Integer existingDistance = distances.get(targetNodeId);
		if (existingDistance != null && existingDistance < newDistance) {
			return false;
		}
		distances.put(targetNodeId, newDistance);
		return true;
	}

	public Integer getDistance(int targetNodeId) {
		return distances.get(targetNodeId);
	}

}
