package com.iastate.atlas.scripts;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import javax.swing.JOptionPane;

import com.ensoftcorp.atlas.c.core.query.Attr;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.H;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.highlight.Highlighter.ConflictStrategy;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.markup.MarkupFromH;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.atlas.ui.viewer.graph.SaveUtil;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.iastate.atlas.efg.EventFlowGraphTransformation;
import com.iastate.verifier.internal.CFGDisconnectedException;
import com.iastate.verifier.internal.EFGCreationException;
import com.iastate.verifier.internal.Stater;
import com.iastate.verifier.internal.Utils;
import com.iastate.verifier.internal.Verifier;

public class LinuxScripts {
	/**
	 * An attribute to tag the duplicated nodes that have multiple verification statuses
	 */
	final public static String DUPLICATE_NODE = "duplicateNode";
	
	/**
	 * An attribute to tag the duplicated edges that connect duplicated nodes
	 */
	final public static String DUPLICATE_EDGE = "duplicateEdge";
	
	/**
	 * Should be enable when debugging to show a step-by-step visualization graphs for data flow, control flow and other graphs
	 */
	public static boolean SHOW_GRAPHS = false;
	
	/**
	 * Enabling feasibility analysis to reduce false positives
	 */
	public static boolean Feasibility_Enabled = false;
	
	/**
	 * Enable saving for graphs for manual validation
	 */
	private static boolean SAVE_GRAPHS = false;
	
	private static boolean SAVE_LOCK_GRAPHS = true;
	public static int LOCK_PROGRESS_COUNT = 0;
	public static String GRAPH_RESULTS_PATH;
	
	/**
	 * The workspace directory path, where all data reside and results will be written
	 */
	public static String WORKSPACE_PATH = "/media/overflow/atamrawi/latest-kernels-workspaces/linux-3.19-rc1-workspace/";
	
	
	private static HashMap<String, long[]> cfg_efg_nodes_stats = new HashMap<String, long[]>();
	
	/**
	 * How to use these scripts:
	 * 1- Change parameter SHOW_GRAPHS to your needs.
	 * 2- Put the workspace path for the project you would like to analyze in WORKSPACE_PATH.
	 * 3- Call (var x = new LinuxScripts())
	 * 4- Call either (x.verifyMutex()) or (x.verifySpin)
	 * 5- If you want to enforce feasibility annotation, delete the corresponding files (mutex_feasibility_mapping.txt) and (spin_feasibility_mapping.txt) 
	 */

	public static void verifySpin(boolean feasibilityEnabled){
		Feasibility_Enabled = feasibilityEnabled;
		
		/**
		 * Enable logging for results in the workspace directory
		 */
		Utils.DEBUG_LEVEL = 2;
		Utils.ERROR_LEVEL = 10;
		if(Feasibility_Enabled)
			Utils.LOG_FILE = WORKSPACE_PATH + "spin-results-with-feasibility.log";
		else 
			Utils.LOG_FILE = WORKSPACE_PATH + "spin-results-without-feasibility.log";
		try {
			Utils.writer = new FileWriter(Utils.LOG_FILE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		GRAPH_RESULTS_PATH = WORKSPACE_PATH + "ivks/spin/";
		
		HashSet<String> locks = new HashSet<String>();
		locks.add("__raw_spin_lock");
		locks.add("__raw_spin_trylock");
		
		HashSet<String> mayLocks = new HashSet<String>();
		mayLocks.add("__raw_spin_trylock");
		
		HashSet<String> unlocks = new HashSet<String>();
		unlocks.add("__raw_spin_unlock");
		
		Q spinlockTypes = universe().nodesTaggedWithAll(XCSG.TypeAlias).selectNode(XCSG.name, "spinlock_t");
		
		computeGlobalDataFlowEnvelope(locks, mayLocks, unlocks, spinlockTypes, "spin_feasibility_mapping.txt");
	}
	
	public static void verifyMutex(boolean feasibilityEnabled){
		Feasibility_Enabled = feasibilityEnabled;

		/**
		 * Enable logging for results in the workspace directory
		 */
		Utils.DEBUG_LEVEL = 2;
		Utils.ERROR_LEVEL = 10;
		if(Feasibility_Enabled)
			Utils.LOG_FILE = WORKSPACE_PATH + "mutex-results-with-feasibility.log";
		else 
			Utils.LOG_FILE = WORKSPACE_PATH + "mutex-results-without-feasibility.log";
		try {
			Utils.writer = new FileWriter(Utils.LOG_FILE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		GRAPH_RESULTS_PATH = WORKSPACE_PATH + "ivks/mutex/";
		
		HashSet<String> locks = new HashSet<String>();
		locks.add("mutex_lock_nested");
		locks.add("mutex_trylock");
		locks.add("mutex_lock_interruptible_nested");
		locks.add("mutex_lock_killable_nested");
		locks.add("atomic_dec_and_mutex_lock_nested");
		locks.add("_mutex_lock_nest_lock");
		
		HashSet<String> mayLocks = new HashSet<String>();
		mayLocks.add("mutex_trylock");
		mayLocks.add("mutex_lock_interruptible_nested");
		mayLocks.add("mutex_lock_killable_nested");
		mayLocks.add("atomic_dec_and_mutex_lock_nested");
		
		HashSet<String> unlocks = new HashSet<String>();
		unlocks.add("mutex_unlock");
		
		Q mutexTypes = universe().nodesTaggedWithAll(XCSG.C.Struct).selectNode(XCSG.name, "struct mutex");
		
		computeGlobalDataFlowEnvelope(locks, mayLocks, unlocks, mutexTypes, "mutex_feasibility_mapping.txt");
	}

	public static void computeGlobalDataFlowEnvelope(HashSet<String> locks, HashSet<String> mayLocks, HashSet<String> unlocks, Q structureTypes, String feasibilityFileName) {
		AtlasSet<Node> e1 = new AtlasHashSet<Node>();
		for(String l : locks){
			e1.addAll(Queries.function(l).eval().nodes());
		}
		Q e1Functions = Common.toQ(Common.toGraph(e1));
		
		AtlasSet<Node> e2 = new AtlasHashSet<Node>();
		for(String u : unlocks){
			e2.addAll(Queries.function(u).eval().nodes());
		}
		Q e2Functions = Common.toQ(Common.toGraph(e2));
		
		HashSet<String> specials = new HashSet<String>();
		specials.add("kmalloc");
		//specials.add("kfree");
		
		AtlasSet<Node> e3 = new AtlasHashSet<Node>();
		for(String u : specials){
			e3.addAll(Queries.function(u).eval().nodes());
		}
		Q specialFunctions = Common.toQ(Common.toGraph(e3));
				
		Q params = CommonQueries.functionParameter(e1Functions.union(e2Functions), 0);

		Q dfReverseParams = universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Attr.Edge.ADDRESS_OF, Attr.Edge.POINTER_DEREFERENCE).reverseStep(params);
		Q rev = universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Attr.Edge.ADDRESS_OF, Attr.Edge.POINTER_DEREFERENCE).reverse(params);
		Q signatures = universe().edgesTaggedWithAny(XCSG.TypeOf, XCSG.ReferencedType, XCSG.ArrayElementType).reverse(structureTypes);
		signatures = signatures.roots().nodesTaggedWithAll(XCSG.Variable);
		
		compute(signatures, params, rev, specialFunctions, dfReverseParams, e1Functions, e2Functions, locks, unlocks, e1, e2, feasibilityAnnotator(mayLocks, feasibilityFileName));
	}
	
	public static void compute(Q signatures, Q params, Q rev, Q specialFunctions, Q dfReverseParams, Q e1Functions, Q e2Functions, HashSet<String> locks, HashSet<String> unlocks, AtlasSet<Node> e1, AtlasSet<Node> e2, HashMap<Node, Boolean> feasibilityMap){
		Stater stater = new Stater();
		int signaturesCount = 0;
		double totalRunningTime = 0;
		double totalRunningTimeWithDF = 0;
		int caseId = -1;
		int objectIndex = 0;
		long objectsCount = signatures.eval().nodes().size();
		for(Node object : signatures.eval().nodes()){
			Utils.debug(0, "Currently Analyzing Object: " + (++objectIndex) + "/" + objectsCount);
			deleteDuplicateNodes();
			Queries.deleteEFGs();
			
			long startTime = System.currentTimeMillis();
			
			if(e1.contains(Queries.getFunctionContainingElement(object).eval().nodes().getFirst())){
				Utils.debug(0, "Skipping Object [" + objectIndex + "] -- Empty Data-Flow Envelope!");
				continue;
			}

			{
				Q m = Common.toQ(Common.toGraph(object));
				Q forwardM = rev.forward(m);
				if(forwardM.intersection(params).eval().nodes().isEmpty()){
					Utils.debug(0, "Skipping Object [" + objectIndex + "] -- Empty Data-Flow Envelope!");
					continue;					
				}
				
				Q returns = Queries.functionReturn(specialFunctions);
				Q df = rev.between(m, params, returns);
				Graph dfEval = df.eval();
				
				if(dfEval.nodes().isEmpty() || (dfEval.nodes().size() == 1 && dfEval.nodes().contains(object))){
					Utils.debug(0, "Skipping Object [" + objectIndex + "] -- Empty Data-Flow Envelope!");
					continue;
				}
				
				Utils.debug(0, "CASE ID: " + (++caseId));
				//if(caseId == 104)
				{
					
					Q dfParams = df.intersection(dfReverseParams).roots();
					Q controlFlowNodes = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(dfParams).nodesTaggedWithAll(XCSG.ControlFlow_Node);
					Q callSites = universe().edgesTaggedWithAll(XCSG.Contains).forward(controlFlowNodes).nodesTaggedWithAll(XCSG.CallSite);
					
					Q mpg = Queries.mpg(callSites, locks, unlocks);
					
					mpg = mpg.union(e1Functions, e2Functions);
					Q set = e1Functions.union(e2Functions);
					//mpg = mpg.induce(universe().edgesTaggedWithAll(XCSG.Call));
					mpg = mpg.induce(Queries.callEdgesContext);
					Q toRemoveEdges = mpg.edgesTaggedWithAny(XCSG.Call).forwardStep(set).edgesTaggedWithAny(XCSG.Call);
					mpg = mpg.differenceEdges(toRemoveEdges);
					Q unused = mpg.roots().intersection(mpg.leaves());
					mpg = mpg.difference(unused);
					long mpgSize = mpg.eval().nodes().size();
					
					if(mpgSize > 500){
						Utils.debug(0, "Skipping Object [" + objectIndex + "] -- Call Envelope is Huge [" + mpgSize + "]!");
						continue;
					}
					
					if(!mpg.selectNode(XCSG.name, "setKey").eval().nodes().isEmpty()){
						Utils.debug(0, "Skipping Object [" + objectIndex + "] -- CFG for function [setKey] is problematic!");
						continue;						
					}
					
					double dfTime = (System.currentTimeMillis() - startTime)/(60*1000F);
					
					Stater subStater = null;
					try {
						subStater = verify(object, mpg, controlFlowNodes, e1, e2, locks, unlocks, feasibilityMap);
					} catch (EFGCreationException | CFGDisconnectedException e) {
						if(e instanceof CFGDisconnectedException){
							Utils.debug(0, "Skipping Object [" + objectIndex + "] -- CFG for function [" + e.getMessage() + "] is disconnected!");
						}else if(e instanceof EFGCreationException){
							Utils.debug(0, "Skipping Object [" + objectIndex + "] -- Cannot Create EFG for function [" + e.getMessage() + "]!");
						}
						continue;
					}
					
					if(subStater == null){
						Utils.debug(0, "Skipping Object [" + objectIndex + "] -- Verifier Returned [NULL]!");
						continue;
					}
					stater.aggregate(subStater);
					++signaturesCount;
					totalRunningTime += subStater.getProcessingTime();
					totalRunningTimeWithDF += dfTime;
					totalRunningTimeWithDF += subStater.getProcessingTime();
				}
			}
		}
		stater.done();
		stater.printResults("Overall Results");
		Utils.debug(0, "******************************************");
		Utils.debug(0, "Signatures Count: "  + signaturesCount);
		Utils.debug(0, "Total Running Time [ "  + totalRunningTime + " minutes]!" );
		Utils.debug(0, "Total Running Time With Data Flow Analysis [ "  + totalRunningTimeWithDF + " minutes]!" );
		Utils.debug(0, "******************************************");
	}
	
	public static Stater verify(Node object, Q envelope, Q mainEventNodes, AtlasSet<Node> e1, AtlasSet<Node> e2, HashSet<String> e1Functions, HashSet<String> e2Functions, HashMap<Node, Boolean> feasibilityMap) throws EFGCreationException, CFGDisconnectedException{
		if(SAVE_GRAPHS)
			clearGraphs();
		
		
		HashMap<Node, Graph> functionFlowMap = new HashMap<Node, Graph>();
		HashMap<Node, List<Q>> functionEventsMap = new HashMap<Node, List<Q>>();
		
		Graph envelopeGraph = envelope.eval();
		
		if(!Utils.isDirectedAcyclicGraph(envelopeGraph)){
			Utils.error(0, "The verification envelope is a cyclic graph! Cutting cycles in graph.");
			Utils.error(0, "The verification envelope has [" + envelopeGraph.nodes().size() + "] nodes and [" + envelopeGraph.edges().size() + "] edges.");
			envelopeGraph = Utils.cutCyclesFromGraph(envelopeGraph);
			envelope = Common.toQ(envelopeGraph);
			
			if(!Utils.isDirectedAcyclicGraph(envelopeGraph)){
				Utils.error(0, "The verification envelope is a cyclic graph!");
				return null;
			}
		}
		
		HashSet<String> envelopeFunctions = new HashSet<String>();
		for(Node function : envelopeGraph.nodes()){
			if(e1.contains(function) || e2.contains(function))
				continue;
			
			String functionName = function.attr().get(XCSG.name).toString();
			envelopeFunctions.add(functionName);
		}
		
		for(Node function : envelopeGraph.nodes()){
			if(e1.contains(function) || e2.contains(function))
				continue;
			
			String functionName = function.attr().get(XCSG.name).toString();
			Q cfg = Queries.CFG(Common.toQ(Common.toGraph(function)));
			List<Q> events = getEventNodes(cfg, mainEventNodes, envelopeFunctions, e1Functions, e2Functions);
			Q e1Events = events.get(0);
			Q e2Events = events.get(1);
			Q envelopeEvents = events.get(2);
			Q eventNodes = events.get(3); 
			
			H h = new Highlighter(ConflictStrategy.COLOR);
			h.highlight(e1Events, Color.RED);
			h.highlight(e2Events, Color.GREEN);
			h.highlight(envelopeEvents, Color.BLUE);

			Graph g = cfg.eval();
			if(isGraphDisconnected(g)){
				throw new CFGDisconnectedException(function.attr().get(XCSG.name).toString());
			}
			Utils.debug(0, "Event Flow Graph for [" + functionName + "]");
			
			if(SHOW_GRAPHS){
				if(envelopeGraph.nodes().size() < 40)
					DisplayUtil.displayGraph(Common.extend(cfg, XCSG.Contains).eval(), h, "CFG-" + functionName);
			}
			
			try{
				Utils.addEFGToIndex(function, g, Utils.createEventFlowGraph(g, eventNodes.eval().nodes()));	
			}catch(NullPointerException e){
				throw new EFGCreationException(function.attr().get(XCSG.name).toString(), e);
			}
			
			Q efg = Queries.EFG(Common.toQ(Common.toGraph(function)));
			if(SHOW_GRAPHS){
				if(envelopeGraph.nodes().size() < 40)
					DisplayUtil.displayGraph(Common.extend(efg, XCSG.Contains).eval(), h, "EFG-" + functionName);
			}
			
			functionFlowMap.put(function, efg.eval());
			events.remove(events.size() - 1);
			AtlasSet<Node> mayEvents = new AtlasHashSet<Node>();
			for(Node n : e1Events.eval().nodes()){
				if(feasibilityMap.containsKey(n)){
					mayEvents.add(n);
				}
			}
			events.add(Common.toQ(Common.toGraph(mayEvents)));
			functionEventsMap.put(function, events);
			long[] nodes_count = new long[2];
			nodes_count[0] = cfg.eval().nodes().size();
			nodes_count[1] = efg.eval().nodes().size();
			cfg_efg_nodes_stats.put(functionName +"@@@"+ function.attr().get(XCSG.sourceCorrespondence).toString(), nodes_count);
			
			
			if(SAVE_GRAPHS){
				cfgs.add(cfg.eval());
				efgs.add(efg.eval());
				methodsForCFGsEFGs.add(function);
				highlighters.add(h);
			}
		}
		
		Graph tempEnvelope = envelope.eval();
		envelope = envelope.difference(envelope.leaves());
		String signature = object.attr().get(XCSG.name) + "(" + object.address().toAddressString() + ")";
		
		Verifier verifier = new Verifier(signature, envelope.eval(), functionFlowMap, functionEventsMap, feasibilityMap);
		Verifier.FEASIBILITY_ENABLED = Feasibility_Enabled;
		Stater stater = verifier.verify();
		if(SAVE_GRAPHS && stater != null){
		//***************************************************
			saveGraphs(object, tempEnvelope);
		//***************************************************
		}
		
		if(SAVE_LOCK_GRAPHS && stater != null){
		//***************************************************
			Queries.deleteEFGs();
			EventFlowGraphTransformation.undoAllEFGTransformations();
			Q verifiedLocks = Common.toQ(Utils.toAtlasSet(verifier.verifiedLocks));
			Q doubleLocks = Common.toQ(Utils.toAtlasSet(verifier.doubleLocks));
			Q danglingLocks = Common.toQ(Utils.toAtlasSet(verifier.danglingLocks));
			Q partiallyLocks = Common.toQ(Utils.toAtlasSet(verifier.partiallyLocks));
			LockSignatureStatistics stats = new LockSignatureStatistics(object, tempEnvelope, verifier.matchingPairsMap, verifiedLocks, doubleLocks, danglingLocks, partiallyLocks);
			stats.process();
		//***************************************************			
		}
		return stater;
	}
	
	private static List<Graph> cfgs = new ArrayList<Graph>();
	private static List<Graph> efgs = new ArrayList<Graph>();
	private static List<Node> methodsForCFGsEFGs = new ArrayList<Node>();
	private static List<H> highlighters = new ArrayList<H>();
	
	private static void clearGraphs(){
		cfgs = new ArrayList<Graph>();
		efgs = new ArrayList<Graph>();
		methodsForCFGsEFGs = new ArrayList<Node>();
		highlighters = new ArrayList<H>();
	}
	
	private static void saveGraphs(Node object, Graph envelope){
		String signatureSourceFile = "<external>";
		SourceCorrespondence sigSc = (SourceCorrespondence) object.attr().get(XCSG.sourceCorrespondence);
		if(sigSc != null){
			signatureSourceFile = fixSlashes(sigSc.toString());
		}
		String mySignatureString = object.attr().get(XCSG.name) + "@@@" + object.address().toAddressString() + "@@@" + signatureSourceFile;
		String signatureDirectory = GRAPH_RESULTS_PATH + mySignatureString + "/";
		File directory = new File(signatureDirectory);
		if(!directory.mkdir()){
			Log.info("Cannot create directory + " + signatureDirectory);
			return;
		}
		
		// Save signature
		// Save MPG
		File mpgFile = new File(directory, "mpg.png");
		try{
			SaveUtil.saveGraph(mpgFile, envelope).join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Save CFGs
		for(int i = 0; i < cfgs.size(); i++){
			Graph cfg = cfgs.get(i);
			Node method = methodsForCFGsEFGs.get(i);
			String methodName = method.attr().get(XCSG.name).toString();
			H h = highlighters.get(i);
			SourceCorrespondence sc = (SourceCorrespondence) method.attr().get(XCSG.sourceCorrespondence);
			String sourceFile = "<external>";
			if(sc != null){
				sourceFile = fixSlashes(sc.toString());
			}
			File saveFile = new File(directory, "CFG@@@" + methodName + "@@@" + sourceFile + ".png");
			try{
				SaveUtil.saveGraph(saveFile, cfg, new MarkupFromH(h)).join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// Save EFGs
		for(int i = 0; i < efgs.size(); i++){
			Graph efg = efgs.get(i);
			Node method = methodsForCFGsEFGs.get(i);
			H h = highlighters.get(i);
			String methodName = method.attr().get(XCSG.name).toString();
			SourceCorrespondence sc = (SourceCorrespondence) method.attr().get(XCSG.sourceCorrespondence);
			String sourceFile = "<external>";
			if(sc != null){
				sourceFile = fixSlashes(sc.toString());
			}
			File saveFile = new File(directory, "EFG@@@" + methodName + "@@@" + sourceFile + ".png");
			try {
				SaveUtil.saveGraph(saveFile, efg, new MarkupFromH(h)).join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private static String fixSlashes(String string){
		return string.replace('/', '@');
	}
	
	/**
	 * Tests whether the current graph is disconnected if there exists a node that has zero parents and zero successors
	 * @param g Graph to be tested to discontinuity
	 * @return true if the graph is disconnected, otherwise false
	 */
	public static boolean isGraphDisconnected(Graph g){
		if(g.nodes().isEmpty()){
			return true;
		}
		for(Node node : g.nodes()){
			if(Utils.getChildNodes(g, node).isEmpty() && Utils.getParentNodes(g, node).isEmpty()){
				return true;
			}
		}
		return false;
	}
	
	public static List<Q> getEventNodes(Q cfg, Q mainEventNodes, HashSet<String> envelopeFunctions, HashSet<String> e1, HashSet<String> e2){
		Q controlFlowNodes = cfg.nodesTaggedWithAll(XCSG.ControlFlow_Node);
		Q callSites = universe().edgesTaggedWithAll(XCSG.Contains).forward(controlFlowNodes).nodesTaggedWithAll(XCSG.CallSite);
		AtlasSet<Node> nodes = callSites.eval().nodes();
		
		Q e1Events = Common.empty();
		Q e2Events = Common.empty();
		Q envelopeEvents = Common.empty();
		
		AtlasSet<Node> mainControlFlowNodes = mainEventNodes.eval().nodes();
		for(Node node : nodes){
			Q controlFlowNode = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(Common.toQ(Common.toGraph(node))).nodesTaggedWithAll(XCSG.ControlFlow_Node);
			if(mainControlFlowNodes.contains(controlFlowNode.eval().nodes().getFirst())){
				if(Queries.isCalling(node, e1)){
					e1Events = e1Events.union(controlFlowNode);
				}
				
				if(Queries.isCalling(node, e2)){
					e2Events = e2Events.union(controlFlowNode);
				}
			}
			
			if(Queries.isCalling(node, envelopeFunctions)){
				envelopeEvents = envelopeEvents.union(controlFlowNode);
			}
		}
		List<Q> result = new ArrayList<Q>();
		result.add(e1Events);
		result.add(e2Events);
		result.add(envelopeEvents);
		result.add(e1Events.union(e2Events, envelopeEvents));
		return result;
	}
	
	public static void deleteDuplicateNodes(){
		AtlasSet<Edge> edges = universe().edgesTaggedWithAll(DUPLICATE_EDGE).eval().edges();
		HashSet<Edge> toDeleteEdges = new HashSet<Edge>(); 
		for(Edge edge : edges){
			toDeleteEdges.add(edge);
		}
		
		for(Edge edge : toDeleteEdges){
			Graph.U.delete(edge);
		}
		
		AtlasSet<Node> nodes = universe().nodesTaggedWithAll(DUPLICATE_NODE).eval().nodes();
		
		HashSet<Node> toDeleteNodes = new HashSet<Node>(); 
		for(Node node : nodes){
			toDeleteNodes.add(node);
		}
		
		for(Node node : toDeleteNodes){
			Graph.U.delete(node);
		}
	}
	
	public Q displayFeasibilityGraph(String function){
		Q callSites = Queries.callEdgesContext.selectNode(XCSG.name, function + "(...)");
		Q cfgConditions = universe().edgesTaggedWithAny(XCSG.Contains).reverseStep(callSites).nodesTaggedWithAny(XCSG.ControlFlowCondition);
		return cfgConditions;
	}
	
	public static HashMap<Node, Boolean> feasibilityAnnotator(HashSet<String> mayEvents, String feasibilityFilePath){
		feasibilityFilePath = WORKSPACE_PATH + feasibilityFilePath;
		HashMap<Node, Boolean> mayEventsFeasibilityMapping = new HashMap<Node, Boolean>();
		if(!Feasibility_Enabled){
			return mayEventsFeasibilityMapping;
		}
		AtlasSet<Node> conditions = new AtlasHashSet<Node>();
		for(String function : mayEvents){
			Q callSites = universe().nodesTaggedWithAny(XCSG.CallSite).selectNode(XCSG.name, function + "(...)");
			Q cfgConditions = universe().edgesTaggedWithAny(XCSG.Contains).reverseStep(callSites).nodesTaggedWithAny(XCSG.ControlFlowCondition);
			conditions.addAll(cfgConditions.eval().nodes());
		}
		HashMap<String, Node> sourceCorrespondenceMap = new HashMap<String, Node>();
		for(Node condition : conditions){
			String sourceCorrespondence = condition.getAttr(XCSG.sourceCorrespondence).toString();
			sourceCorrespondenceMap.put(sourceCorrespondence, condition);
		}
		File feasibilityFile = new File(feasibilityFilePath);
		if(feasibilityFile.exists()){
			try {
				Scanner reader = new Scanner(feasibilityFile);
				while(reader.hasNextLine()){
					String [] parts = reader.nextLine().split("@@@");
					Node condition = sourceCorrespondenceMap.get(parts[0]);
					
					if(parts[1].equals("T")){
						mayEventsFeasibilityMapping.put(condition, true);
					}else if(parts[1].equals("F")){
						mayEventsFeasibilityMapping.put(condition, false);
					}
					
				}
				reader.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

		}else{
			int index = 0;
			try {
				FileWriter writer = new FileWriter(feasibilityFile);
				for(Node condition : conditions){
					++index;
					String sourceCorrespondence = condition.getAttr(XCSG.sourceCorrespondence).toString();
					String conditionText = condition.getAttr(XCSG.name).toString();
					// Apply Some Feasibility Heuristics
					if(conditionText.contains("mutex_lock_interruptible_nested") || conditionText.contains("mutex_lock_killable_nested")){
						if(conditionText.contains("==")){
							mayEventsFeasibilityMapping.put(condition, true);
							writer.write(sourceCorrespondence + "@@@T\n");
							writer.flush();							
						}else{
							mayEventsFeasibilityMapping.put(condition, false);
							writer.write(sourceCorrespondence + "@@@F\n");
							writer.flush();							
						}
						continue;
					}
					int dialogResult = JOptionPane.showConfirmDialog (null, conditionText, "(" + index + "/" + conditions.size() + ") Is the true branch feasible?", JOptionPane.YES_NO_CANCEL_OPTION);
					if(dialogResult == JOptionPane.YES_OPTION){
						mayEventsFeasibilityMapping.put(condition, true);
						writer.write(sourceCorrespondence + "@@@T\n");
						writer.flush();
					}else if(dialogResult == JOptionPane.NO_OPTION){
						mayEventsFeasibilityMapping.put(condition, false);
						writer.write(sourceCorrespondence + "@@@F\n");
						writer.flush();
					}else if(dialogResult == JOptionPane.CANCEL_OPTION){
					}
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mayEventsFeasibilityMapping;
	}
}
