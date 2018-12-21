package de.hub.se.cfg;

import java.io.Serializable;

/**
 * 
 * Implementation based on jpf-memoize by Guowei Yang (guoweiyang@utexas.edu).
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 *
 */
public class CFGEdge implements Serializable {
	
	private static final long serialVersionUID = 1515201509852115253L;
	
	private static int globalCurrentEdgeID = 0;
	public static int generateNewEdgeID() {
		return globalCurrentEdgeID++;
	} 
	
    protected int edgeId;
    protected int successorId; // id of the node where the edge ends
    protected int predecessorId; // id of the node where the edge originates
    protected int choice; // the choice of the corresponding edge

    /*************************************************************************
     * Creates an edge with the given choice
     */
    public CFGEdge(int successorId, int predecessorId, int choice) {
        this.edgeId = generateNewEdgeID();
        this.successorId = successorId;
        this.predecessorId = predecessorId;
        this.choice = choice;
    }

    /*************************************************************************
     * Gets the edgeId.
     */
    public int getId() {
        return edgeId;
    }

    /*************************************************************************
     * Sets the successor node of this edge.
     */
    public void setSuccessorId(int succId)
    {
        successorId = succId;
    }

    /*************************************************************************
     * Gets the successor node of this edge. 
     */
    public int getSuccessorId()
    {
        return successorId ;
    }

    /*************************************************************************
     * Sets the predecessor node of this edge.
     */
    public void setPredecessorId(int predId)
    {
        predecessorId = predId;
    }

    /*************************************************************************
     * Gets the predecessor node of this edge. 
     */
    public int getPredecessorId()
    {
        return predecessorId ;
    }

    /*************************************************************************
     * Sets the choice for this edge.
     */
    public void setChoice(int choice) {
        this.choice = choice;
    }

    /*************************************************************************
     * Gets the choice for this edge.
     */
    public int getChoice(){
        return choice;
    }

    /*************************************************************************
     * Returns a string representation of this edge in the form
     */
    public String toString() {
    	return new String("(" + predecessorId + " -> " + successorId) + ", " + choice + ")";
    }

}
