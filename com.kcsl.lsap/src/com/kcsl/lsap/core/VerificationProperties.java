package com.kcsl.lsap.core;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class VerificationProperties {
	
	private static final String CONFIG_PROPERTIES_FILE_SEPARATOR = ",";
	
	/**
	 * An attribute to tag the duplicated nodes that have multiple verification statuses
	 */
	final public static String DUPLICATE_NODE = "duplicateNode";
	
	/**
	 * An attribute to tag the duplicated edges that connect duplicated nodes
	 */
	final public static String DUPLICATE_EDGE = "duplicateEdge";
	
	private static boolean SAVE_GRAPH_IN_DOT_FORMAT;
	private static String GRAPH_IMAGE_FILENAME_EXTENSION;
	private static String GRAPH_DOT_FILENAME_EXTENSION;

	private static boolean FEASIBILITY_ENABLED;
	
	private static Path OUTPUT_DIRECTORY;
	
	private static FileWriter OUTPUT_LOG_FILE_WRITER;
	
	private static List<String> FUNCTIONS_TO_EXCLUDE;
	
	private static int MPG_NODE_SIZE_LIMIT;
	
	private static boolean SAVE_VERIFICATION_GRAPHS;
	
	
	
	private static Q MUTEX_OBJECT_TYPE;
	
	private static Path MUTEX_GRAPHS_OUTPUT_DIRECTORY_PATH;
	
	private static List<String> MUTEX_LOCK_FUNCTION_CALLS;
	
	private static List<String> MUTEX_UNLOCK_FUNCTION_CALLS;
	
	private static List<String> MUTEX_TRYLOCK_FUNCTION_CALLS;
	
	
	private static Q SPIN_OBJECT_TYPE;
	
	private static Path SPIN_GRAPHS_OUTPUT_DIRECTORY_PATH;
	
	private static List<String> SPIN_LOCK_FUNCTION_CALLS;
	
	private static List<String> SPIN_UNLOCK_FUNCTION_CALLS;
	
	private static List<String> SPIN_TRYLOCK_FUNCTION_CALLS;
	
	static{
		Properties properties = new Properties();
		InputStream inputStream;
		try {
			inputStream = VerificationProperties.class.getClassLoader().getResourceAsStream("config.properties");
			properties.load(inputStream);
			FEASIBILITY_ENABLED = Boolean.parseBoolean(properties.getProperty("feasibility_enabled"));
			OUTPUT_DIRECTORY = Paths.get(properties.getProperty("output_directory"));
			
			try {
				Path outputLogFilePath = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("output_log_filename"));
				OUTPUT_LOG_FILE_WRITER = new FileWriter(outputLogFilePath.toFile().getAbsolutePath());
			} catch (IOException e) {
				System.err.println("Cannot open output log file for writing.");
			}
			
			FUNCTIONS_TO_EXCLUDE = Arrays.asList(properties.getProperty("function_to_exclude"));
			MPG_NODE_SIZE_LIMIT = Integer.parseInt(properties.getProperty("mpg_node_size_limit"));
			SAVE_VERIFICATION_GRAPHS = Boolean.parseBoolean(properties.getProperty("save_verification_graphs"));
			
			SAVE_GRAPH_IN_DOT_FORMAT = Boolean.parseBoolean(properties.getProperty("save_graphs_in_dot_format"));
			GRAPH_IMAGE_FILENAME_EXTENSION = properties.getProperty("graph_image_filename_extension");
			GRAPH_DOT_FILENAME_EXTENSION = properties.getProperty("graph_dot_filename_extension");
			
			SPIN_OBJECT_TYPE = universe().nodes(XCSG.TypeAlias).selectNode(XCSG.name, properties.getProperty("spin_object_typename"));
			SPIN_LOCK_FUNCTION_CALLS = Arrays.asList(properties.getProperty("spin_lock").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			SPIN_UNLOCK_FUNCTION_CALLS = Arrays.asList(properties.getProperty("spin_unlock").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			SPIN_TRYLOCK_FUNCTION_CALLS = Arrays.asList(properties.getProperty("spin_trylock").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			SPIN_GRAPHS_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("spin_graphs_output_directory_name"));
			
			
			MUTEX_OBJECT_TYPE = universe().nodes(XCSG.C.Struct).selectNode(XCSG.name, properties.getProperty("mutex_object_typename"));
			MUTEX_LOCK_FUNCTION_CALLS = Arrays.asList(properties.getProperty("mutex_lock").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			MUTEX_UNLOCK_FUNCTION_CALLS = Arrays.asList(properties.getProperty("mutex_unlock").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			MUTEX_TRYLOCK_FUNCTION_CALLS = Arrays.asList(properties.getProperty("mutex_trylock").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			MUTEX_GRAPHS_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("mutex_graphs_output_directory_name"));
			
		
		} catch (IOException e) {
			System.err.println("Cannot locate the properties file.");
		}
	}
	
	public static boolean isFeasibilityCheckingEnabled(){
		return FEASIBILITY_ENABLED;
	}
	
	public static Path getOutputDirectory(){
		return OUTPUT_DIRECTORY;
	}
	
	public static FileWriter getOutputLogFileWriter(){
		return OUTPUT_LOG_FILE_WRITER;
	}
	
	public static List<String> getFunctionsToExclude(){
		return FUNCTIONS_TO_EXCLUDE;
	}
	
	public static int getMPGNodeSizeLimit(){
		return MPG_NODE_SIZE_LIMIT;
	}
	
	public static boolean isSaveVerificationGraphs(){
		return SAVE_VERIFICATION_GRAPHS;
	}
	
	public static boolean saveGraphsInDotFormat(){
		return SAVE_GRAPH_IN_DOT_FORMAT;
	}
	
	public static String getGraphImageFileNameExtension(){
		return GRAPH_IMAGE_FILENAME_EXTENSION;
	}
	
	public static String getGraphDotFileNameExtension(){
		return GRAPH_DOT_FILENAME_EXTENSION;
	}
	
	
	public static Q getMutexObjectType(){
		return MUTEX_OBJECT_TYPE;
	}
	
	public static List<String> getMutexLockFunctionCalls(){
		return MUTEX_LOCK_FUNCTION_CALLS;
	}
	
	public static List<String> getMutexUnlockFunctionCalls(){
		return MUTEX_UNLOCK_FUNCTION_CALLS;
	}
	
	public static List<String> getMutexTrylockFunctionCalls(){
		return MUTEX_TRYLOCK_FUNCTION_CALLS;
	}
	
	public static Path getMutexGraphsOutputDirectory(){
		return MUTEX_GRAPHS_OUTPUT_DIRECTORY_PATH;
	}
	
	
	public static Q getSpinObjectType(){
		return SPIN_OBJECT_TYPE;
	}
	
	public static List<String> getSpinLockFunctionCalls(){
		return SPIN_LOCK_FUNCTION_CALLS;
	}
	
	public static List<String> getSpinUnlockFunctionCalls(){
		return SPIN_UNLOCK_FUNCTION_CALLS;
	}
	
	public static List<String> getSpinTrylockFunctionCalls(){
		return SPIN_TRYLOCK_FUNCTION_CALLS;
	}
	
	public static Path getSpinGraphsOutputDirectory(){
		return SPIN_GRAPHS_OUTPUT_DIRECTORY_PATH;
	}
	
}
