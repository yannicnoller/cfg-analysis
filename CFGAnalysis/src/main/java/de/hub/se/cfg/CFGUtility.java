package de.hub.se.cfg;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.bcel.classfile.Method;

/**
 * Implementation based on jpf-memoize by Guowei Yang (guoweiyang@utexas.edu).
 * 
 * @author Yannic Noller <nolleryc@gmail.com> - YN
 *
 */
public final class CFGUtility {

	public static final String SERIALIZATION_FILE_NAME = "cfg.ser";
	
	public static final String getCompleteMethodName(String className, Method m) {
		return getFullQualifiedMethodName(className, m.getName(), m.getSignature());
	}

	public static final String getFullQualifiedMethodName(String methodClass, String methodName,
			String methodSignature) {
		return methodClass + "." + methodName + methodSignature;
	}

	public static final void serialize(CFGAnalysis cfga, String folderPath) {
		String filename = folderPath + "/" + SERIALIZATION_FILE_NAME;
		try {
			Files.createDirectories(Paths.get(filename).getParent());
			FileOutputStream file = new FileOutputStream(filename);
			ObjectOutputStream out = new ObjectOutputStream(file);
			out.writeObject(cfga);
			out.close();
			file.close();
			System.out.println("CFG serialized: " + filename);
		} catch (IOException e) {
			System.err.println("Error serializing and writing CFG file: " + filename);
			e.printStackTrace();
		}
	}

	public static final CFGAnalysis deserialize(String folderPath) {
		String filename = folderPath + "/" + SERIALIZATION_FILE_NAME;
		CFGAnalysis deseraCFGAAnalysis = null;
		try {
			FileInputStream file = new FileInputStream(filename);
			ObjectInputStream in = new ObjectInputStream(file);
			deseraCFGAAnalysis = (CFGAnalysis) in.readObject();
			in.close();
			file.close();
			System.out.println("CFG deserialized: " + filename);
		} catch (IOException e) {
			System.err.println("Error deserializing CFG file: " + filename);
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			System.err.println("Error deserializing CFG file: " + filename);
			e.printStackTrace();
		}
		return deseraCFGAAnalysis;
	}

}
