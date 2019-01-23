package de.hub.se.cfg;

import java.io.Serializable;
import java.util.*;

/**
 * CFG class Implementation based on jpf-memoize by Guowei Yang (guoweiyang@utexas.edu).
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 * 
 */

public class CFG implements Serializable {

    private static final long serialVersionUID = 7889362340700254268L;

    protected List<CFGNode> nodes = new ArrayList<>();
    protected List<CFGEdge> edges = new ArrayList<>();
    protected String completeMethodName;
    protected Set<String> invokedMethods = new HashSet<String>(); // set of invoked methods
    protected Map<Integer, CFGNode> nodeOffsetMap = new HashMap<>(); // mapping from startOffset to node
    protected Map<Integer, Integer> branchNodeMap = new HashMap<>(); // mapping from branch position to node id
    protected Map<String, Set<Integer>> callNodeMap = new HashMap<>(); // mapping from method invocation to node id
    protected Map<Integer, Integer> nodeBranchMap = new HashMap<>(); // mapping from node id to branch position
    protected Map<Integer, Integer> edgeCoverageMap = new HashMap<>();
    protected Map<Integer, Integer> nodeCoverageMap = new HashMap<>();

    protected Map<Integer, CFGNode> idNodeMap = new HashMap<>();
    protected Map<Integer, Set<CFGNode>> nodeSourceLineMap = new HashMap<>();

    public static final int OUTGOINGMATCH = 0;
    public static final int INCOMINGMATCH = 1;

    /*************************************************************************
     * constructors
     */

    public CFG(String name) {
        this.completeMethodName = name;
    }

    protected int getEntryBlockNodeId() {
        CFGNode firstNode = nodes.get(0);
        if (!firstNode.isVirtual()) {
            /* Should be the first node, which should be virtual. */
            throw new RuntimeException("First node in cfg is not virtual!");
        }
        return firstNode.getId();
    }

    protected CFGNode getRootNode() {
        return nodes.get(0);
    }

    protected int getFirstRealNodeId() {
        CFGNode firstRealNode = null;
        for (CFGNode node : nodes) {
            if (!node.isVirtual()) {
                firstRealNode = node;
                break;
            }
        }
        if (firstRealNode == null) {
            /* There should be a real node in the CFG! */
            throw new RuntimeException("No real node in given cfg!");
        }
        return firstRealNode.getId();
    }

    protected int getExitNodeId() {
        CFGNode exitNode = nodes.get(nodes.size() - 1);
        if (!exitNode.isVirtual()) {
            /* Last added node, must be virtual! */
            throw new RuntimeException("Last node (exit node) is not virtual!");
        }
        return exitNode.getId();
    }

    protected CFGNode getExitNode() {
        CFGNode exitNode = nodes.get(nodes.size() - 1);
        if (!exitNode.isVirtual()) {
            /* Last added node, must be virtual! */
            throw new RuntimeException("Last node (exit node) is not virtual!");
        }
        return exitNode;
    }

    protected int getLastRealNodeId() {
        CFGNode lastNode = nodes.get(nodes.size() - 1);
        while (lastNode.isVirtual) {
            Set<CFGNode> predessors = lastNode.getPredecessors();
            if (predessors.size() != 1) {
                throw new RuntimeException("Virtual node has more than one predecessor?! size=" + predessors.size());
            }
            lastNode = predessors.iterator().next();
        }
        return lastNode.getId();
    }

    protected void removeNode(CFGNode n) {
        nodes.remove(n);
    }

    protected void removeEdge(CFGEdge e) {
        edges.remove(e);
    }

    public CFGEdge getEdge(CFGNode sourceNode, CFGNode sinkNode) {
        for (int i = 0; i < edges.size(); i++) {
            CFGEdge e = (CFGEdge) edges.get(i);
            if ((e.predecessorId == sourceNode.nodeId) && (e.successorId == sinkNode.nodeId)) {
                return e;
            }
        }
        throw new NoSuchElementException("No matching edge exists in graph");
    }

    /**
     * Gets an edge in the graph.
     *
     */
    public CFGEdge getEdge(int nodeId, int choice) {
        for (int i = 0; i < edges.size(); i++) {
            CFGEdge e = (CFGEdge) edges.get(i);
            if ((e.predecessorId == nodeId) && (e.choice == choice)) {
                return e;
            }
        }
        throw new NoSuchElementException("No matching edge exists in graph");
    }

    /**
     * Gets all of the edges
     */
    public CFGEdge[] getEdges() {
        CFGEdge e[] = new CFGEdge[edges.size()];
        edges.toArray(e);
        return e;
    }

    /**
     * Gets all edges which have the given source and sink nodes.
     */
    public CFGEdge[] getEdges(CFGNode sourceNode, CFGNode sinkNode, CFGEdge[] a) {
        List<Object> matchingEdges = new ArrayList<Object>();
        for (int i = 0; i < edges.size(); i++) {
            CFGEdge e = (CFGEdge) edges.get(i);
            if ((e.predecessorId == sourceNode.nodeId) && (e.successorId == sinkNode.nodeId)) {
                matchingEdges.add(e);
            }
        }
        if (matchingEdges.size() > 0) {
            return (CFGEdge[]) matchingEdges.toArray(a);
        } else {
            throw new NoSuchElementException("No matching edges " + "exist in graph");
        }
    }

    /**
     * Gets either all the edges which originate on a given node or which are incident on a node.
     */
    public CFGEdge[] getEdges(CFGNode n, int matchType, CFGEdge[] a) {
        List<Object> matchingEdges = new ArrayList<Object>();
        for (int i = 0; i < edges.size(); i++) {
            CFGEdge e = (CFGEdge) edges.get(i);
            if ((matchType == OUTGOINGMATCH) && (e.predecessorId == n.nodeId)) {
                matchingEdges.add(e);
            } else if ((matchType == INCOMINGMATCH) && (e.successorId == n.nodeId)) {
                matchingEdges.add(e);
            }
        }
        if (matchingEdges.size() > 0) {
            return (CFGEdge[]) matchingEdges.toArray(a);
        } else {
            return null;
        }
    }

    /**
     * Gets list of edges which originate on a given node or which are incident on a node.
     */
    public List<CFGEdge> getEdges(CFGNode n, int matchType) {
        List<CFGEdge> matchingEdges = new ArrayList<CFGEdge>();
        for (int i = 0; i < edges.size(); i++) {
            CFGEdge e = (CFGEdge) edges.get(i);
            if ((matchType == OUTGOINGMATCH) && (e.predecessorId == n.nodeId)) {
                matchingEdges.add(e);
            } else if ((matchType == INCOMINGMATCH) && (e.successorId == n.nodeId)) {
                matchingEdges.add(e);
            }
        }
        return matchingEdges;
    }

    /**
     * Gets the total number of nodes contained in the graph.
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Gets the total number of edges contained in the graph.
     */
    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * Clears the graph, such that it has no nodes or edges.
     */
    protected void clear() {
        nodes.clear();
        edges.clear();
    }

    /**
     * Gets the nodes in this control flow graph.
     */
    public CFGNode[] getNodes() {
        CFGNode b[] = new CFGNode[nodes.size()];
        nodes.toArray(b);
        return b;
    }

    /**
     * Gets a node from the node list.
     *
     */
    public CFGNode getNodeById(int id) {
        CFGNode node = idNodeMap.get(id);
        if (node == null) {
            throw new NoSuchElementException();
        }
        return node;
    }

    /**
     * find a node using the node's start offset
     */
    public CFGNode getNodeByStartOffset(int startOffset) {
        Iterator<CFGNode> iterator = nodes.iterator();
        for (int i = nodes.size(); i-- > 0;) {
            CFGNode node = (CFGNode) iterator.next();
            if (node.getStartOffset() == startOffset) {
                return node;
            }
        }
        throw new NoSuchElementException();
    }

    /**
     * Adds an edge to the list of edges.
     */
    protected void addEdge(CFGEdge e) {
        edges.add(e);
        CFGNode fromNode = getNodeById(e.getPredecessorId());
        CFGNode toNode = getNodeById(e.getSuccessorId());
        fromNode.addSuccessor(toNode);
        toNode.addPredecessor(fromNode);
    }

    /**
     * Adds a node to the CFG, creating an entry in the offset map so the node can be retrieved by its start offset
     * later.
     */
    protected void addNode(CFGNode newNode) {
        nodes.add(newNode);
        nodeOffsetMap.put(newNode.getStartOffset(), newNode);
        updateIdNodeMapping(newNode);

        /* Update mapping from source line to CFG node. */
        int startSourceLineNumber = newNode.getStartSourceLineNumber();
        int endSourceLineNumber = newNode.getEndSourceLineNumber();

        if (startSourceLineNumber > -1) {
            for (int i = startSourceLineNumber; i <= endSourceLineNumber; i++) {
                Set<CFGNode> nodeSet = nodeSourceLineMap.get(i);
                if (nodeSet == null) {
                    nodeSet = new HashSet<>();
                    nodeSourceLineMap.put(i, nodeSet);
                }
                nodeSet.add(newNode);
            }
        }
    }

    protected void addVirtualNode(CFGNode n, boolean isRootNode) {
        nodes.add(n);
        n.setVirtual(true);
        n.setRootNode(isRootNode);
        updateIdNodeMapping(n);
    }

    private void updateIdNodeMapping(CFGNode newNode) {
        if (idNodeMap.containsKey(newNode.getId())) {
            throw new RuntimeException("Node id " + newNode.getId() + " does already exist!");
        }
        idNodeMap.put(newNode.getId(), newNode);
    }

    /**
     * Returns the name of the method with which this control flow graph is associated.
     *
     */
    public String getMethodName() {
        return completeMethodName;
    }

    /**
     * Checks if one node is reachable from another node local in the cfg
     */
    public boolean isReachable(int fromId, int toId) {
        return isReachable(getNodeById(fromId), getNodeById(toId));
    }

    public Set<CFGNode> getNodesBySourceLineNumber(int sourceLineNumber) {
        return nodeSourceLineMap.get(sourceLineNumber);
    }

    /**
     * Checks if one node is reachable from another node local in the cfg
     */
    public boolean isReachable(CFGNode from, CFGNode to) {
        if (from == to) {
            return true;
        }

        Set<CFGNode> seen = new HashSet<CFGNode>();
        Set<CFGNode> toCheck = new HashSet<CFGNode>();
        seen.add(from);
        toCheck.add(from);

        while (true) {
            Set<CFGNode> newNodes = new HashSet<CFGNode>();
            for (CFGNode n : toCheck) {
                newNodes.addAll(n.getSuccessors());
            }

            if (seen.containsAll(newNodes)) {
                return false;
            }
            seen.addAll(newNodes);
            if (seen.contains(to)) {
                return true;
            }
            toCheck = newNodes;
        }
    }

    /**
     * Computes the distance in terms of number of edges from one node to another node
     * 
     * @return Integer.MAX_VALUE if not reachable the least number of edges otherwise
     */
    public int distance(int fromId, int toId) {
        return distance(getNodeById(fromId), getNodeById(toId));
    }

    /**
     * Computes the distance in terms of number of edges from one node to another node
     * 
     * @return Integer.MAX_VALUE if not reachable the least number of edges otherwise
     */
    public Integer distance(CFGNode from, CFGNode to) {
        int distance = 0;

        if (from == to) {
            return distance;
        }

        Set<CFGNode> seen = new HashSet<CFGNode>();
        Set<CFGNode> toCheck = new HashSet<CFGNode>();
        seen.add(from);
        toCheck.add(from);

        while (true) {
            distance++;
            Set<CFGNode> newNodes = new HashSet<CFGNode>();
            for (CFGNode n : toCheck) {
                newNodes.addAll(n.getSuccessors());
            }

            if (seen.containsAll(newNodes)) {
                return null; // not reachable
            }
            seen.addAll(newNodes);
            if (seen.contains(to)) {
                return distance;
            }
            toCheck = newNodes;
        }
    }

    /**
     * Adds a branch, and maintain the mapping between it and its node
     */
    public void addBranch(int nodeId, int pos) {
        branchNodeMap.put(pos, nodeId);
        nodeBranchMap.put(nodeId, pos);
    }

    /**
     * Adds a call, and maintain the mapping between it and its node
     */
    public void addCall(int nodeId, String methodName) {
        // update callNodeMap
        if (callNodeMap.containsKey(methodName)) {
            callNodeMap.get(methodName).add(nodeId);
        } else {
            Set<Integer> nodes = new HashSet<Integer>();
            nodes.add(nodeId);
            callNodeMap.put(methodName, nodes);
        }

        // update invokedMethods
        invokedMethods.add(methodName);
    }

    /**
     * Gets a node in the graph using branch instruction position
     *
     */
    public int getBranchNode(int pos) {
        if (branchNodeMap.containsKey(pos)) {
            return branchNodeMap.get(pos).intValue();
        } else {
            return -1;
        }
    }

    /**
     * Gets branch instruction position using a node
     *
     */
    public int getNodeBranch(int nodeId) {
        if (nodeBranchMap.containsKey(nodeId)) {
            return nodeBranchMap.get(nodeId).intValue();
        } else {
            return -1;
        }
    }

    /**
     * Clears coverage info
     *
     */
    public void clearCoverage() {
        edgeCoverageMap.clear();
        nodeCoverageMap.clear();
    }

    /**
     * Gets edge coverage info
     *
     */
    public Map<Integer, Integer> getEdgeCoverage() {
        return edgeCoverageMap;
    }

    /**
     * Sets edge coverage info
     *
     */
    public void setEdgeCoverage(Map<Integer, Integer> coverage) {
        this.edgeCoverageMap = coverage;
    }

    /**
     * Gets node coverage info
     *
     */
    public Map<Integer, Integer> getNodeCoverage() {
        return nodeCoverageMap;
    }

    /**
     * Sets node coverage info
     *
     */
    public void setNodeCoverage(Map<Integer, Integer> coverage) {
        this.nodeCoverageMap = coverage;
    }

    /**
     * Returns string representation of the control flow graph, which is a list of the edges that constitute the CFG.
     *
     */
    public String toString() {
        return edgesToString() + nodesToString();
    }

    /**
     * Gets string representation of the edges that compose the control flow graph for this method.
     */
    private String edgesToString() {
        StringBuilder sb = new StringBuilder();
        for (CFGEdge edge : edges) {
            sb.append(edge + "\n");
        }
        return sb.toString();
    }

    private String nodesToString() {
        StringBuilder sb = new StringBuilder();
        for (CFGNode node : nodes) {
            sb.append(node + "\n");
        }
        return sb.toString();
    }

    public Set<Integer> getInvocationNodesByTargetMethod(String targetMethodName) {
        return callNodeMap.get(targetMethodName);
    }
}
