package de.hub.se.cfg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 *
 */
public class CFGTarget implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String method;
	private int sourceLineNumber;
	
	private String toStringResult;
	
	public CFGTarget(String method, int sourceLineNumber) {
		this.method = method;
		this.sourceLineNumber = sourceLineNumber;
		this.toStringResult = method + ":" + sourceLineNumber;
	}
	
	public String getMethod() {
		return this.method;
	}
	
	public int getSourceLineNumber() {
		return this.sourceLineNumber;
	}
	
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof CFGTarget) {
        	CFGTarget anotherCFGTarget = (CFGTarget) anObject;
        	if (!this.method.equals(anotherCFGTarget.method)) {
        		return false;
        	}
        	if (this.sourceLineNumber != anotherCFGTarget.sourceLineNumber) {
        		return false;
        	}
        }
        return true;
    }
    
    public int hashCode() {
    	int result = 31 + method.hashCode();
    	result = 31 * result + sourceLineNumber;
    	return result;
    }
    
    public String toString() {
    	return toStringResult;
    }
    
    public static List<String> parseDistanceTargetArgument(String args) {
    	List<String> targets = new ArrayList<>();
		try {
			for (String target : args.split(",")) {
				target.split(":");
				targets.add(target);
			}
		} catch (PatternSyntaxException ex) {
			throw new RuntimeException("Wrong target definition: " + args + "\nComma-separated list of target specification: method:line");
		}
		return targets;
    }
    
    public static CFGTarget createCFGTargetFromString(String target) {
    	String[] splitted = target.split(":");
    	return new CFGTarget(splitted[0], Integer.parseInt(splitted[1]));
    }

}
