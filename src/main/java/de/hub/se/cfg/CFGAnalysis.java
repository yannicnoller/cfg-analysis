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

    /* Maps method name to all nodes which these method. */
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
            Set<CFGNode> toCheck = new HashSet<CFGNode>();
            toCheck.add(targetNode);

            while (!toCheck.isEmpty()) {
                CFGNode currentNode = toCheck.iterator().next();
                toCheck.remove(currentNode);

                /* Get predecessor nodes for current node. */
                Set<CFGNode> predecessorNodes;
                if (currentNode.isRootNode) {
                    predecessorNodes = getCallers(currentNode.getFullQualifiedMethodName());
                } else {
                    predecessorNodes = currentNode.getPredecessors();
                }

                int currentDistance = currentNode.getDistance(globalTargetNodeId);

                /* Update predecessor nodes if necessary. */
                for (CFGNode preNode : predecessorNodes) {
                    int newDistance = currentDistance;
                    if (currentNode.isRootNode || !preNode.isVirtual) {
                        newDistance += 1;
                    }
                    boolean distanceUpdated = preNode.setDistanceIfBetter(globalTargetNodeId, newDistance);
                    if (distanceUpdated) {
                        toCheck.add(preNode);
                    }
                }
            }

        }

    }

    private Set<CFGNode> getCallers(String localTargetMethod) {
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
        CFG cfg = cfgMap.get(fullQualifiedMethodName);
        if (cfg == null) {
            throw new RuntimeException("Unknown method: " + fullQualifiedMethodName);
        }
        Set<CFGNode> nodes = cfg.getNodesBySourceLineNumber(sourceLineNumber);
        if (nodes == null || nodes.isEmpty()) {
            throw new RuntimeException("Source line number " + sourceLineNumber + " not included in CFG for method "
                    + fullQualifiedMethodName + " !");
        }
        return nodes.iterator().next();
    }

}
