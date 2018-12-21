package de.hub.se.cfg;

import java.io.*;
import java.util.*;

import org.apache.bcel.Repository;
import org.apache.bcel.generic.*;

import org.apache.bcel.classfile.*;

/**
 * This class builds the control flow graph. Implementation based on jpf-memoize by Guowei Yang (guoweiyang@utexas.edu).
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 * 
 */

public class CFGBuilder {

    private String className;

    Map<String, CFG> cfgMap = new HashMap<String, CFG>();

    private JavaClass javaClass;
    private Method[] methods;
    private ConstantPoolGen CPG;
    private TreeSet<InstructionHandle> leaders;
    private TreeSet<InstructionHandle> ends;
    private TreeSet<InstructionHandle> calls;
    private TreeSet<InstructionHandle> branches;

    private static final boolean DEBUG = false;

    MethodGen mg;

    /*************************************************************************
     * default constructor.
     */
    public CFGBuilder() {
        Comparator<Object> c = new IHandleComparator();
        leaders = new TreeSet<>(c);
        ends = new TreeSet<>(c);
        calls = new TreeSet<>(c);
        branches = new TreeSet<>(c);
    }

    /**
     * Parses the class using BCEL.
     * 
     * @param className
     * @return true for successful parsing, otherwise false
     * @throws Exception
     */
    public boolean parseClass(String className) throws Exception {
        try {
            javaClass = Repository.lookupClass(className);
        } catch (ClassNotFoundException e) {
            // Maybe it's an absolute (or otherwise qualified) path
            File f = new File(className);
            if (!f.exists()) {
                throw new Exception("Cannot find class: " + className);
            }
            javaClass = new ClassParser(className).parse();
        }

        if (javaClass.isInterface()) {
            // throw new Exception("Cannot build graphs " + "for interface");
            System.out.println("We cannot build CFG for interface: " + javaClass.getClassName());
            return false;
        }

        this.className = javaClass.getClassName();
        CPG = new ConstantPoolGen(javaClass.getConstantPool());
        methods = javaClass.getMethods();
        if (methods.length == 0) {
            throw new Exception("Error loading class");
        }

        return true;
    }

    /**
     * generate complete method name
     * 
     * @param methodIndex
     * @return null for abstract or native methods
     */
    private String getCompleteMethodName(int methodIndex) {
        if (methodIndex < 0 || methodIndex >= methods.length) {
            throw new RuntimeException("Method index out of range");
        }
        if (methods[methodIndex].isAbstract() || methods[methodIndex].isNative()) {
            return null;
        }
        reset();

        this.mg = new MethodGen(methods[methodIndex], className, CPG);
        String completeMethodName = CFGUtility.getCompleteMethodName(className, methods[methodIndex]);
        return completeMethodName;
    }

    public MethodGen getMethodGen() {
        return mg;
    }

    /**
     * Builds a control flow graph for a method
     */
    private CFG buildCFG(int methodIndex) throws Exception {

        String completeMethodName = getCompleteMethodName(methodIndex);

        if (completeMethodName == null) {
            /*
             * completeMethodName might be null for abstract or native methods, in such a case we don't want to or
             * cannot build a cfg
             */
            return null;
        }

        CFG cfg = cfgMap.get(completeMethodName);
        if ((cfg != null)) {
            return cfg;
        }
        List<Object> pendingInference = new ArrayList<Object>();

        cfg = new CFG(completeMethodName);
        cfgMap.put(completeMethodName, cfg);

        MethodGen mg = new MethodGen(methods[methodIndex], className, CPG);
        formNodes(cfg, mg.getInstructionList(), mg.getLineNumberTable(CPG));
        formEdges(cfg, pendingInference);
        checkBranchInstruction(cfg);
        checkCalls(cfg);

        return cfg;
    }

    /**
     * Retrieves a control flow graph for a method from the cache using a complete method name
     * 
     */
    public CFG getCFG(String completeMethodName) throws Exception {
        CFG cfg = cfgMap.get(completeMethodName);

        if ((cfg != null)) {
            return cfg;
        }

        if (cfg == null) {
            int methodIndex = getMethodIndex(completeMethodName);
            if (methodIndex < 0) {
                throw new Exception("No such method");
            }
            if (methods[methodIndex].isAbstract() || methods[methodIndex].isNative()) {
                return null;
            }
            cfg = buildCFG(methodIndex);
        }

        return cfg;
    }

    /**
     * retrieves the index of the provided method using a complete method name
     */
    private int getMethodIndex(String completeMethodName) {
        if (!completeMethodName.startsWith(className)) {
            return -1;
        }
        String methodName = completeMethodName.substring(className.length() + 1);
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (methodName.equals(m.getName() + m.getSignature())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds the control flow graphs for all methods in the class
     */
    public Map<String, CFG> buildCFGForAll() throws Exception {
        for (int i = 0; i < methods.length; i++) {
            buildCFG(i);// hack
        }
        return cfgMap;
    }

    /**
     * reset data structures
     */
    private void reset() {
        leaders.clear();
        ends.clear();
        calls.clear();
        branches.clear();
    }

    /**
     * Compute the nodes
     */
    private void formNodes(CFG cfg, InstructionList il, LineNumberTable lineNumberTable) {
        InstructionHandle ih, target, prev_ih, next_ih;
        Instruction insn;
        String methodClass;

        leaders.add(il.getStart());
        for (ih = il.getStart(); ih != null; ih = ih.getNext()) {
            if (calls.contains(ih) && (leaders.contains(ih) || ends.contains(ih))) {
                continue;
            }

            insn = ih.getInstruction();
            if (DEBUG) {
                System.out.println(insn.toString(true));
            }

            if (insn instanceof BranchInstruction) {
                branches.add(ih);

                if ((insn instanceof GotoInstruction) || (insn instanceof IfInstruction)) {
                    ends.add(ih);

                    // target
                    target = ((BranchInstruction) insn).getTarget();
                    leaders.add(target);

                    // instruction prior to target
                    prev_ih = target.getPrev();
                    if (prev_ih != null) {
                        ends.add(prev_ih);
                    }

                    // instruction after branch
                    next_ih = ih.getNext();
                    if (next_ih != null) {
                        leaders.add(ih.getNext());
                    }
                } else if (insn instanceof Select) {
                    Select selectInstr = (Select) insn;
                    ends.add(ih);

                    // default target
                    target = selectInstr.getTarget();
                    leaders.add(target);

                    // instruction before default target
                    prev_ih = target.getPrev();
                    if (prev_ih != null) {
                        ends.add(prev_ih);
                    }

                    // case targets
                    InstructionHandle[] targets = selectInstr.getTargets();
                    for (int k = 0; k < targets.length; k++) {
                        leaders.add(targets[k]);
                        prev_ih = targets[k].getPrev();
                        if (prev_ih != null) {
                            ends.add(prev_ih);
                        }
                    }

                    // instruction immediately following
                    leaders.add(ih.getNext());
                }
            } else if (insn instanceof InvokeInstruction) {
                InvokeInstruction invokeInstr = (InvokeInstruction) insn;
                methodClass = invokeInstr.getReferenceType(CPG).toString();
                if (DEBUG) {
                    System.out.println("instruction: " + methodClass + " " + invokeInstr.getMethodName(CPG));
                }
                if ((methodClass.indexOf("java.lang.System") != -1) && invokeInstr.getMethodName(CPG).equals("exit")) {
                    // System.exit
                    ends.add(ih);
                    leaders.add(ih.getNext());
                } else {
                    // Add to the call list
                    calls.add(ih);

                    /* YN: TODO FIXME trying to also add calls as nodes */

                }
            } else if (insn instanceof ReturnInstruction) {
                ends.add(ih);
                if (ih.getNext() != null) {
                    leaders.add(ih.getNext());
                }
            }
        }

        // TODO: Athrow
        // TODO: RET

        ends.add(il.getEnd());

        // virtual entry node,
        cfg.addVirtualNode(new CFGNode(0, 0, cfg.getMethodName(), -1, -1), true);

        // actual nodes
        Iterator<InstructionHandle> leadersIt = leaders.iterator();
        Iterator<InstructionHandle> endIt = ends.iterator();
        while (leadersIt.hasNext() && endIt.hasNext()) {
            InstructionHandle startHandle = leadersIt.next();
            InstructionHandle endHandle = endIt.next();

            /* Get all source line numbers for between the handles. */
            int firstHandledLineNumber = lineNumberTable.getSourceLine(startHandle.getPosition());
            int lastHandledLineNumber = firstHandledLineNumber;
            for (int i = startHandle.getPosition() + 1; i <= endHandle.getPosition(); i++) {
                int currentLineNumber = lineNumberTable.getSourceLine(i);
                if (currentLineNumber < firstHandledLineNumber) {
                    firstHandledLineNumber = currentLineNumber;
                    continue;
                }
                if (currentLineNumber > lastHandledLineNumber) {
                    lastHandledLineNumber = currentLineNumber;
                }
            }

            /*
             * Create new node, use min and max calculation for sourcecode line because they might not be in order.
             */
            CFGNode node = new CFGNode(startHandle.getPosition(), endHandle.getPosition(), cfg.getMethodName(),
                    firstHandledLineNumber, lastHandledLineNumber);
            cfg.addNode(node);
        }

        // virtual exit node
        InstructionHandle endHandle = (InstructionHandle) ends.last();
        int offset = endHandle.getPosition();
        CFGNode virtualExitNode = new CFGNode(offset, offset, cfg.getMethodName(), -1, -1);
        cfg.addVirtualNode(virtualExitNode, false);

    }

    /**
     * Computes the edges between basic blocks
     */
    private void formEdges(CFG cfg, List<Object> pendingInference) {
        int targetOffset = 0;

        int nodeId = cfg.getFirstRealNodeId();

        // edge between entry block and first actual block
        cfg.addEdge(new CFGEdge(nodeId, cfg.getEntryBlockNodeId(), -1));

        for (InstructionHandle ih : ends) {
            Instruction insn = ih.getInstruction();

            if (insn instanceof Select) { // Switch
                Select selectInstr = (Select) insn;
                InstructionHandle[] targets = selectInstr.getTargets();
                int[] matches = selectInstr.getMatchs();
                if (matches.length != targets.length) {
                    throw new ClassFormatError("Invalid switch instruction: " + ih.toString().trim());
                }
                CFGNode node;
                for (int j = 0; j < targets.length; j++) {
                    node = (CFGNode) cfg.nodeOffsetMap.get(targets[j].getPosition());
                    cfg.addEdge(new CFGEdge(node.getId(), nodeId, j));
                }
                node = (CFGNode) cfg.nodeOffsetMap.get(selectInstr.getTarget().getPosition());
                cfg.addEdge(new CFGEdge(node.getId(), nodeId, matches.length));
            }

            else if (insn instanceof GotoInstruction) { // GOTO
                BranchInstruction branchInstr = (BranchInstruction) insn;
                InstructionHandle target = branchInstr.getTarget();
                targetOffset = target.getPosition();
                CFGNode successor = (CFGNode) cfg.nodeOffsetMap.get(targetOffset);
                cfg.addEdge(new CFGEdge(successor.getId(), nodeId, -1));
            }

            else if (insn instanceof IfInstruction) { // IF
                CFGNode targetNode;
                // Target - true
                targetOffset = ((IfInstruction) insn).getTarget().getPosition();
                targetNode = (CFGNode) cfg.nodeOffsetMap.get(targetOffset);
                cfg.addEdge(new CFGEdge(targetNode.getId(), nodeId, 1));// true branch
                // Target - false
                cfg.addEdge(new CFGEdge(nodeId + 1, nodeId, 0));// false branch
            }

            else if ((insn instanceof ReturnInstruction) || // RETURN
                    ((insn instanceof INVOKESTATIC) && // system.exit()
                            (((INVOKESTATIC) insn).getReferenceType(CPG).toString().indexOf("java.lang.System") != -1)
                            && (((INVOKESTATIC) insn).getMethodName(CPG).equals("exit")))) {
                cfg.addEdge(new CFGEdge(cfg.getExitNodeId(), nodeId, -1));
            }

            else { // regular flow
                cfg.addEdge(new CFGEdge(nodeId + 1, nodeId, -1));
            }
            nodeId++;
        }

    }

    /**
     * Find branch instructions and maps to their corresponding node ids
     */
    private void checkBranchInstruction(CFG cfg) {
        int nodeId = cfg.getFirstRealNodeId();
        int exitNodeId = cfg.getExitNodeId();
        for (InstructionHandle ih : branches) {
            int pos = ih.getPosition();
            while (nodeId < exitNodeId) {
                CFGNode node = cfg.getNodeById(nodeId);
                if (node.getStartOffset() <= pos && pos <= node.getEndOffset()) {
                    if (DEBUG) {
                        System.out.println("Branch instruction pos: " + ih.getPosition());
                        System.out.println("Node id: " + nodeId);
                    }
                    cfg.addBranch(nodeId, pos);
                    break;
                }
                nodeId++;
            }
            if (nodeId == exitNodeId) {
                System.err.println("Error in corrsponding branches to nodes.");
            }
        }
    }

    /**
     * Checks all function calls to build the map between calls and nodes
     */
    private void checkCalls(CFG cfg) {
        int nodeId = cfg.getFirstRealNodeId();
        int exitNodeId = cfg.getExitNodeId();
        for (InstructionHandle ih : calls) {
            Instruction instr = ih.getInstruction();
            InvokeInstruction invokeInstr = (InvokeInstruction) instr;
            String methodClass = invokeInstr.getReferenceType(CPG).toString();
            String methodName = invokeInstr.getMethodName(CPG);
            String fullQualifiedMethodName = CFGUtility.getFullQualifiedMethodName(methodClass, methodName,
                    invokeInstr.getSignature(CPG));

            if (DEBUG) {
                System.out.println("Invoke instruction: " + methodClass + " " + invokeInstr.getMethodName(CPG));
            }

            int pos = ih.getPosition();
            while (nodeId < exitNodeId) {
                CFGNode node = cfg.getNodeById(nodeId);
                if (node.getStartOffset() <= pos && pos <= node.getEndOffset()) {
                    if (DEBUG) {
                        System.out.println("Invoke instruction: " + methodClass + " " + invokeInstr.getMethodName(CPG));
                        System.out.println("Node id: " + nodeId);
                    }
                    cfg.addCall(nodeId, fullQualifiedMethodName);
                    break;
                }
                nodeId++;
            }
            if (nodeId == exitNodeId) {
                System.err.println("Error in corrsponding calls to nodes.");
            }
        }
    }

    /**
     * Comparator for InstructionHandle objects.
     */
    private class IHandleComparator implements Comparator<Object> {
        public int compare(Object o1, Object o2) {
            if (o1 == o2)
                return 0;
            if (((InstructionHandle) o1).getPosition() < ((InstructionHandle) o2).getPosition()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public Map<String, CFG> getCfgMap() {
        return cfgMap;
    }

    public void setCfgMap(Map<String, CFG> cfgMap) {
        this.cfgMap = cfgMap;
    }

    /**
     * generate CFG for the specified set of classes
     * 
     * @param path
     *            - path to the classes (without package structure)
     * @param classes
     * @return Map<String, CFG>, mapping between method name and generated CFG.
     */
    public static CFGAnalysis genCFGForClasses(String path, Set<String> classes, Set<String> classesToSkip,
            String additionalClasses) {
        Map<String, CFG> map = new HashMap<>();
        Set<String> skipped = new HashSet<>();
        CFGBuilder cfgb = new CFGBuilder();

        for (String entry : classes) {

            if (classesToSkip.contains(entry)) {
                System.out.println("Skip CFG construction for class: " + entry);
                continue;
            }

            boolean parsed = false;
            try {
                parsed = cfgb.parseClass(path + "/" + entry);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }

            /* Skip class files that could be parsed, e.g. interface. */
            if (!parsed) {
                /* Remove ".class" at the end of the filename and replace "/" with "." */
                skipped.add(entry.substring(0, entry.length() - 6).replace("/", "."));
                continue;
            }

            try {
                cfgb.buildCFGForAll();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            map.putAll(cfgb.getCfgMap());
        }

        /* Add additional classes. */
        if (additionalClasses != null) {
            for (String additionalClass : additionalClasses.split(",")) {
                boolean parsed = false;
                try {
                    parsed = cfgb.parseClass(additionalClass);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                /* Skip class files that could not be parsed, e.g. interface. */
                if (!parsed) {
                    /* Remove ".class" at the end of the filename and replace "/" with "." */
                    skipped.add(additionalClass.substring(additionalClass.lastIndexOf("/") + 1,
                            additionalClass.length() - 6));
                    continue;
                }

                try {
                    cfgb.buildCFGForAll();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                map.putAll(cfgb.getCfgMap());
            }
        }

        return new CFGAnalysis(map, skipped);
    }
}
