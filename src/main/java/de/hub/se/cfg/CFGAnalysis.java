package de.hub.se.cfg;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class supports analysis based on CFG classes. Implementation based on jpf-memoize by Guowei Yang
 * (guoweiyang@utexas.edu).
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 * 
 */
public class CFGAnalysis implements Serializable {

    private static final long serialVersionUID = -820897258149888704L;

    Map<String, CFG> cfgMap;
    Set<String> skippedFilesDuringAnalysis;

    /* Maps method name to all nodes, which call this method. */
    Map<String, Set<CFGNode>> callerCache = new HashMap<>();

    Set<CFGTarget> targets;

    public CFGAnalysis(Map<String, CFG> cfgMap, Set<String> skippedFilesDuringAnalysis) {
        this.cfgMap = cfgMap;
        this.skippedFilesDuringAnalysis = skippedFilesDuringAnalysis;
        this.targets = new HashSet<>();
    }

    public Collection<CFG> getAllIncludedCFG() {
        return cfgMap.values();
    }

    public Set<CFGTarget> getProcessedTargets() {
        return targets;
    }

    public boolean wasClassSkippedInCFGBuilding(String className) {
        return skippedFilesDuringAnalysis.contains(className);
    }

    public void calculateDistancesToTargets(Set<String> setOfTargets) {
        for (String target : setOfTargets) {
            String[] separatedArgument = target.split(":");
            String targetMethod = separatedArgument[0];
            int targetSourceLine = Integer.parseInt(separatedArgument[1]);

            /* Check and update already defined targets. */
            CFGTarget cfgTarget = new CFGTarget(targetMethod, targetSourceLine);
            if (!targets.add(cfgTarget)) {
                return; // already calculated;
            }

            /* Check whether target method is actually in the analyzed classes. */
            CFGNode targetNode = getNodeByMethodAndSourceLine(targetMethod, targetSourceLine);
            int globalTargetNodeId = targetNode.getId();
            targetNode.setDistance(globalTargetNodeId, 0);

            /* Start calculation */
            updateNodeAndAllPredecessorNodes(targetNode, globalTargetNodeId, true);
        }
    }

    private int updateNodeAndAllPredecessorNodes(CFGNode node, int targetId, boolean includeMethodCallers) {
        Set<CFGNode> toCheck = new HashSet<CFGNode>();
        toCheck.add(node);
        int lastDistance = node.getDistance(targetId);

        while (!toCheck.isEmpty()) {
            CFGNode currentNode = toCheck.iterator().next();
            toCheck.remove(currentNode);

            int currentDistance = currentNode.getDistance(targetId);
            lastDistance = currentDistance;

            /* Get predecessor nodes for current node. */
            Set<CFGNode> predecessorNodes;
            if (currentNode.isRootNode) {
                if (includeMethodCallers) {
                    predecessorNodes = getCallers(currentNode.getFullQualifiedMethodName());
                } else {
                    continue;
                }
            } else {
                predecessorNodes = currentNode.getPredecessors();
            }

            /* Update predecessor nodes if necessary. */
            for (CFGNode preNode : predecessorNodes) {
                int newDistance = currentDistance;

                /*
                 * Check if pre-node calls another method. Then also update their distances. CAUTION: leads to
                 * over-statement of reachability!. But do not update their distances if the preNode is the last real
                 * node here, because then we would just get back from where we just have arrived from.
                 * 
                 * TODO YN: needs improvement, we want to be more precise!
                 */
                if (preNode.isCallerNode() && isNotLastNodeInMethod(preNode)) {
                    String callingMethod = preNode.getMethodCalled();

                    /*
                     * Method must be included in analysis and it should not be the same method which we have currently
                     * analyzed, otherwise we will receive wrong results.
                     */
                    if (isMethodIncludedInAnalysis(callingMethod)) {
                        CFGNode lastNodeInCalledMethod = getLastNodeForMethod(callingMethod);

                        // last node virtual, so use currentDistance and not newDistance
                        boolean distanceUpdated = lastNodeInCalledMethod.setDistanceIfBetter(targetId, currentDistance);
                        if (distanceUpdated) {
                            newDistance = updateNodeAndAllPredecessorNodes(lastNodeInCalledMethod, targetId, false);
                        }
                    }
                }

                if (currentNode.isRootNode || !preNode.isVirtual) {
                    newDistance += 1;
                }

                boolean distanceUpdated = preNode.setDistanceIfBetter(targetId, newDistance);
                if (distanceUpdated) {
                    toCheck.add(preNode);
                }
            }
        }

        return lastDistance;
    }

    public Set<CFGNode> getCallers(String localTargetMethod) {
        Set<CFGNode> callingNodes = callerCache.get(localTargetMethod);
        if (callingNodes == null) {
            callingNodes = new HashSet<>();
            /* Iterate all cfg and search for direct invocations. */
            for (CFG cfg : cfgMap.values()) {
                /* Skip the cfg for target method */
                if (cfg.getMethodName().equals(localTargetMethod)) {
                    continue;
                }
                /* Get all nodes in the current cfg, which call the target method. */
                Set<Integer> invocationNodes = cfg.getInvocationNodesByTargetMethod(localTargetMethod);
                if (invocationNodes != null) {
                    for (Integer nodeId : invocationNodes) {
                        CFGNode caller = cfg.getNodeById(nodeId);
                        callingNodes.add(caller);
                    }
                }
            }
        }
        return callingNodes;
    }

    public CFGNode getRootNodeForCurrentMethod(String fullQualifiedMethodName) {
        CFG cfg = cfgMap.get(fullQualifiedMethodName);
        if (cfg == null) {
            throw new RuntimeException("Unknown method: " + fullQualifiedMethodName);
        }
        return cfg.getRootNode();
    }

    public CFGNode getLastNodeForMethod(String fullQualifiedMethodName) {
        CFG cfg = cfgMap.get(fullQualifiedMethodName);
        if (cfg == null) {
            throw new RuntimeException("Unknown method: " + fullQualifiedMethodName);
        }
        return cfg.getExitNode();
    }

    public CFGNode getNodeByMethodAndSourceLine(String fullQualifiedMethodName, int sourceLineNumber,
            boolean muteExceptionForUnknownMethod) {
        CFG cfg = cfgMap.get(fullQualifiedMethodName);
        if (cfg == null) {
            if (muteExceptionForUnknownMethod) {
                return null;
            } else {
                throw new RuntimeException("Unknown method: " + fullQualifiedMethodName);
            }
        }
        Set<CFGNode> nodes = cfg.getNodesBySourceLineNumber(sourceLineNumber);
        if (nodes == null || nodes.isEmpty()) {
            throw new RuntimeException("Source line number " + sourceLineNumber + " not included in CFG for method "
                    + fullQualifiedMethodName + " !");
        }
        return nodes.iterator().next();
    }

    /**
     * Return the first CFG node, which is associated with the given sourceLineNumber;
     * 
     * @param fullQualifiedMethodName
     *            - String
     * @param sourceLineNumber
     *            - int
     * @return CFGNode
     */
    public CFGNode getNodeByMethodAndSourceLine(String fullQualifiedMethodName, int sourceLineNumber) {
        return getNodeByMethodAndSourceLine(fullQualifiedMethodName, sourceLineNumber, false);
    }

    protected boolean isMethodIncludedInAnalysis(String fullQualifiedMethodName) {
        return cfgMap.get(fullQualifiedMethodName) != null;
    }

    protected boolean isNotLastNodeInMethod(CFGNode node) {
        CFG cfg = cfgMap.get(node.getFullQualifiedMethodName());
        if (node.isVirtual) {
            return node.getId() != cfg.getExitNodeId();
        } else {
            Set<CFGNode> lastNodes = cfg.getLastRealNodeIds();
            if (lastNodes.isEmpty()) {
                throw new RuntimeException("No real last nodes in CFG?!");
            }
            for (CFGNode lastNode : lastNodes) {
                if (node.getId() == lastNode.getId()) {
                    return false;
                }
            }
            return true;
        }
       
    }

}
