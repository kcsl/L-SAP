package com.kcsl.lsap;

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
	
	/**
	 * A {@link String} corresponding to the separator between multi-values in "config.properties" file.
	 */
	private static final String CONFIG_PROPERTIES_FILE_SEPARATOR = ",";
	
	/**
	 * An attribute to tag the duplicated nodes that have multiple verification statuses
	 */
	public static final String DUPLICATE_NODE = "DUPLICATE_NODE";
	
	/**
	 * An attribute to tag the duplicated edges that connect duplicated nodes
	 */
	public static final String DUPLICATE_EDGE = "DUPLICATE_EDGE";
	
	/**
	 * A {@link boolean} flag to indicate whether to save verification graphs in "dot" format.
	 */
	private static boolean SAVE_GRAPH_IN_DOT_FORMAT;
	
	/**
	 * A {@link String} corresponding to image file extension.
	 */
	private static String GRAPH_IMAGE_FILENAME_EXTENSION;
	
	/**
	 * A {@link String} corresponding to dot file extension.
	 */
	private static String GRAPH_DOT_FILENAME_EXTENSION;
	
	/**
	 * A {@link Path} corresponding to the root directory where interactive verification graphs to be saved.
	 */
	private static Path INTERACTIVE_VERIFICATION_GRAPHS_OUTPUT_DIRECTORY_PATH;

	/**
	 * A {@link boolean} flag to indicate whether the feasibility checking is enabled in this verification.
	 */
	private static boolean FEASIBILITY_ENABLED;
	
	/**
	 * A {@link Path} to indicate the root directory where all the verification results will be saved.
	 * <p>
	 * This will be the root directory where all sub-directories and files will be created.
	 */
	private static Path OUTPUT_DIRECTORY;
	
	/**
	 * An instance of {@link FileWriter} that will be used to report verification result to a log file.
	 */
	private static FileWriter OUTPUT_LOG_FILE_WRITER;
	
	/**
	 * An instance of {@link Path} corresponding to the output log file.
	 */
	private static Path OUTPUT_LOG_FILE_PATH;
	
	/**
	 * A list of {@link String}s corresponding to the name of functions that need to be excluded from data flow analysis computationas they are causing problems.
	 */
	private static List<String> FUNCTIONS_TO_EXCLUDE;
	
	/**
	 * A limit on the size of MPG nodes that can be considered for verification.
	 */
	private static int MPG_NODE_SIZE_LIMIT;
	
	/**
	 * A {@link boolean} flag to indicate whether to the save the verification graphs.
	 */
	private static boolean SAVE_VERIFICATION_GRAPHS;
	
	/**
	 * A {@link Q} corresponding to the type of a mutex object. This would be the signature for verification.
	 */
	private static Q MUTEX_OBJECT_TYPE;
	
	/**
	 * A {@link Path} to be used for saving the mutex verification graphs.
	 */
	private static Path MUTEX_GRAPHS_OUTPUT_DIRECTORY_PATH;
	
	/**
	 * A list of {@link String}s corresponding to the name of mutex lock function calls.
	 */
	private static List<String> MUTEX_LOCK_FUNCTION_CALLS;
	
	/**
	 * A list of {@link String}s corresponding to the name of mutex unlock function calls.
	 */
	private static List<String> MUTEX_UNLOCK_FUNCTION_CALLS;
	
	/**
	 * A list of {@link String}s corresponding to the name of mutex lock function calls that has multi-state.
	 */
	private static List<String> MUTEX_TRYLOCK_FUNCTION_CALLS;
	
	/**
	 * A {@link Q} corresponding to the type of a spin object. This would be the signature for verification.
	 */
	private static Q SPIN_OBJECT_TYPE;
	
	/**
	 * A {@link Path} to be used for saving the spin verification graphs.
	 */
	private static Path SPIN_GRAPHS_OUTPUT_DIRECTORY_PATH;
	
	/**
	 * A list of {@link String}s corresponding to the name of spin lock function calls.
	 */
	private static List<String> SPIN_LOCK_FUNCTION_CALLS;
	
	/**
	 * A list of {@link String}s corresponding to the name of spin unlock function calls.
	 */
	private static List<String> SPIN_UNLOCK_FUNCTION_CALLS;
	
	/**
	 * A list of {@link String}s corresponding to the name of spin lock function calls that has multi-state.
	 */
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
				OUTPUT_LOG_FILE_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("output_log_filename"));
				OUTPUT_LOG_FILE_WRITER = new FileWriter(OUTPUT_LOG_FILE_PATH.toFile().getAbsolutePath());
			} catch (IOException e) {
				System.err.println("Cannot open output log file for writing.");
			}
			
			FUNCTIONS_TO_EXCLUDE = Arrays.asList(properties.getProperty("function_to_exclude").split(CONFIG_PROPERTIES_FILE_SEPARATOR));
			MPG_NODE_SIZE_LIMIT = Integer.parseInt(properties.getProperty("mpg_node_size_limit"));
			SAVE_VERIFICATION_GRAPHS = Boolean.parseBoolean(properties.getProperty("save_verification_graphs"));
			SAVE_GRAPH_IN_DOT_FORMAT = Boolean.parseBoolean(properties.getProperty("save_graphs_in_dot_format"));
			GRAPH_IMAGE_FILENAME_EXTENSION = properties.getProperty("graph_image_filename_extension");
			GRAPH_DOT_FILENAME_EXTENSION = properties.getProperty("graph_dot_filename_extension");
			INTERACTIVE_VERIFICATION_GRAPHS_OUTPUT_DIRECTORY_PATH = Paths.get(OUTPUT_DIRECTORY.toFile().getAbsolutePath(), properties.getProperty("interactive_verification_graphs_output_directory_name"));
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
	
	public static void resetOutputLogFile() {
		try {
			OUTPUT_LOG_FILE_WRITER = new FileWriter(OUTPUT_LOG_FILE_PATH.toFile().getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Cannot open output log file for writing.");
		}
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
	
	public static Path getInteractiveVerificationGraphsOutputDirectory(){
		return INTERACTIVE_VERIFICATION_GRAPHS_OUTPUT_DIRECTORY_PATH;
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
