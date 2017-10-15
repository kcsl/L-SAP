package com.kcsl.lsap.core;

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.SaveUtil;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG;
import com.ensoftcorp.open.pcg.common.PCGFactory;
import com.kcsl.lsap.core.MatchingPair;
import com.kcsl.lsap.utils.DOTGraphUtils;
import com.kcsl.lsap.utils.LSAPUtils;

public class LockVerificationGraphsGenerator {
	
	/**
	 * The name pattern for the directory containing the graphs for the processed lock.
	 * <p>
	 * The following is the parts of the name:
	 * 1- The prefix for the folder stating the verification result.
	 * 2- The {@link Node#addressBits()} corresponding the verified lock.
	 * 3- The {@link SourceCorrespondence} serialization for the verified lock.
	 * 4- The {@link XCSG#name} corresponding to the {@link #signtureNode}.
	 */
	private static final String LOCK_GRAPH_DIRECTORY_NAME_PATTERN = "%s@@@%s@@@%s@@@%s";
	
	private static final String MPG_GRAPH_FILE_NAME_PATTERN = "mpg%s";
	
	/**
	 * The name pattern for the CFG graph.
	 * <p>
	 * The following is the parts of the name:
	 * 1- The method name corresponding to the CFG.
	 * 2- The source file where this method is defined.
	 * 3- The number of nodes in this CFG.
	 * 4- The number of edges in this CFG.
	 * 5- The number of conditions in this CFG.
	 */
	private static final String CFG_GRAPH_FILE_NAME_PATTERN = "CFG@@@%s@@@%s@@@%s@@@%s@@@%s%s";
	
	/**
	 * The name pattern for the PCG graph.
	 * <p>
	 * The following is the parts of the name:
	 * 1- The method name corresponding to the PCG.
	 * 2- The source file where this method is defined.
	 * 3- The number of nodes in this PCG.
	 * 4- The number of edges in this PCG.
	 * 5- The number of conditions in this PCG.
	 */
	private static final String PCG_GRAPH_FILE_NAME_PATTERN = "PCG@@@%s@@@%s@@@%s@@@%s@@@%s%s";
	
	/**
	 * The root output directory for all the graphs. The current class with create a directory with {@link #currentLockGraphsOutputDirectory}
	 * to store all generated graph per processed lock.
	 */
	private final Path graphsOutputDirectory;
	
	/**
	 * An instance of {@link Node} corresponding to the signature that the processed locks is associated with.
	 */
	private final Node signtureNode;
	
	/**
	 * An MPG associated with the {@link #signtureNode}.
	 */
	private final Q mpg;
	
	/**
	 * A mapping between a lock to its set of {@link MatchingPair}s.
	 */
	private final AtlasMap<Node, HashSet<MatchingPair>> pairs;
	
	/**
	 * The directory where the verification graphs for the processed lock to be stored}.
	 */
	private File currentLockGraphsOutputDirectory;
	
	public enum STATUS {
		PAIRED, PARTIALLY_PAIRED, DEADLOCK, UNPAIRED
	}
	
	public LockVerificationGraphsGenerator(Node signtureNode, Q mpg, AtlasMap<Node, HashSet<MatchingPair>> matchingPairs, Path graphsOutputDirectoryPath) {
		this.signtureNode = signtureNode;
		this.mpg = mpg;
		this.pairs = matchingPairs;
		this.graphsOutputDirectory = graphsOutputDirectoryPath;
	}
	
	public void process(Node lock, STATUS status){
		// STEP 1: CREATE A LOCK FOLDER
		if(!this.createContainingDirectory(lock, status)){
			return;
		}
		
		AtlasSet<Node> unlocks = new AtlasHashSet<Node>();
		if(!status.equals(STATUS.UNPAIRED)){
			HashSet<MatchingPair> matchingPairs = this.pairs.get(lock);
			for(MatchingPair pair : matchingPairs){
				Node unlock = pair.getSecondEvent();
				if(unlock != null){
					unlocks.add(unlock);
				}
			}
		}
		
		// STEP 2: CREATE THE MPG FILE FOR THE LOCK
		Q mpgForLock = Common.empty();
		if(status.equals(STATUS.UNPAIRED)){
			Node containingFunctionNode = CommonQueries.getContainingFunction(lock);
			mpgForLock = this.mpg.forward(Common.toQ(containingFunctionNode));	
		}else{
			mpgForLock = this.mpgForLock(lock, unlocks);
		}
		mpgForLock = mpgForLock.retainEdges();
		Graph mpgGraph = mpgForLock.eval();
		if(VerificationProperties.saveGraphsInDotFormat()){
			com.alexmerz.graphviz.objects.Graph mpgDotGraph = DOTGraphUtils.dotify(mpgGraph.nodes(), mpgGraph.edges(), null);
			String mpgGraphFileName = String.format(MPG_GRAPH_FILE_NAME_PATTERN, VerificationProperties.getGraphDotFileNameExtension());
			DOTGraphUtils.saveDOTGraph(mpgDotGraph, this.currentLockGraphsOutputDirectory, mpgGraphFileName);
		}else{
			try{
				String mpgGraphFileName = String.format(MPG_GRAPH_FILE_NAME_PATTERN, VerificationProperties.getGraphImageFileNameExtension());
				SaveUtil.saveGraph(new File(this.currentLockGraphsOutputDirectory, mpgGraphFileName), mpgGraph).join();
			} catch (InterruptedException e) {}	
		}
		
		// STEP 3: CREATE THE CFG & EFG FOR EACH FUNCTION IN THE LOCK MPG
		Q mpgFunctionsQ = mpgForLock.difference(mpgForLock.leaves());
		AtlasSet<Node> mpgFunctions = mpgFunctionsQ.eval().nodes();
		for(Node mpgFunction : mpgFunctions){
			String methodName = mpgFunction.getAttr(XCSG.name).toString();
			LSAPUtils.log("Function [" + methodName + "]");
			SourceCorrespondence sc = (SourceCorrespondence) mpgFunction.attr().get(XCSG.sourceCorrespondence);
			String sourceFile = "<external>";
			if(sc != null){
				sourceFile = fixSlashes(sc.toString());
			}
			
			Q cfg = CommonQueries.cfg(mpgFunction);
			Graph cfgGraph = cfg.eval();			
			
			ArrayList<Q> results = null;
			if(status.equals(STATUS.DEADLOCK)){
			    results = this.getEventNodesForCFG(cfg, Common.toQ(lock).union(Common.toQ(unlocks)), Common.empty() , mpgFunctionsQ);
			}else{
				results = this.getEventNodesForCFG(cfg, Common.toQ(lock), Common.toQ(unlocks), mpgFunctionsQ);
			}
			Q lockEvents = results.get(0);
			Q unlockEvents = results.get(1);
			Q callsiteEvents = results.get(2);
			Q eventNodes = lockEvents.union(unlockEvents, callsiteEvents);
			
			Markup markup = new Markup();
			markup.set(lockEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.RED);
			markup.set(unlockEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.GREEN);
			markup.set(callsiteEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.BLUE);
			
			// STEP 3A: SAVE CFG
			long nodes = cfgGraph.nodes().size();
			long edges = cfgGraph.edges().size();
			long conditions = cfgGraph.nodes().tagged(XCSG.ControlFlowCondition).size();
			
			if(VerificationProperties.saveGraphsInDotFormat()){
				com.alexmerz.graphviz.objects.Graph cfgDotGraph = DOTGraphUtils.dotify(cfgGraph.nodes(), cfgGraph.edges(), markup);
				String cfgFileName = String.format(CFG_GRAPH_FILE_NAME_PATTERN, methodName, sourceFile, nodes, edges, conditions, VerificationProperties.getGraphDotFileNameExtension());
				DOTGraphUtils.saveDOTGraph(cfgDotGraph, this.currentLockGraphsOutputDirectory, cfgFileName);
			}else{
				try{
					String cfgFileName = String.format(CFG_GRAPH_FILE_NAME_PATTERN, methodName, sourceFile, nodes, edges, conditions, VerificationProperties.getGraphImageFileNameExtension());
					SaveUtil.saveGraph(new File(this.currentLockGraphsOutputDirectory, cfgFileName), cfgGraph, markup).join();
				} catch (InterruptedException e) {}
			}
			
			PCG pcg = PCGFactory.create(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), eventNodes);
			Q pcgQ = pcg.getPCG();
			Graph pcgGraph = pcgQ.eval();
			
			// STEP 3A: SAVE PCG
			nodes = pcgGraph.nodes().size();
			edges = pcgGraph.edges().size();
			conditions = 0;
			for(Node node : pcgGraph.nodes()){
				if(pcgGraph.edges(node, NodeDirection.OUT).size() > 1){
					conditions++;
				}
			}
			if(VerificationProperties.saveGraphsInDotFormat()){
				com.alexmerz.graphviz.objects.Graph efgDotGraph = DOTGraphUtils.dotify(pcgGraph.nodes(), pcgGraph.edges(), markup);
				String pcgFileName = String.format(PCG_GRAPH_FILE_NAME_PATTERN, methodName, sourceFile, nodes, edges, conditions, VerificationProperties.getGraphDotFileNameExtension());
				DOTGraphUtils.saveDOTGraph(efgDotGraph, this.currentLockGraphsOutputDirectory, pcgFileName);
			}else{
				try {
					String pcgFileName = String.format(PCG_GRAPH_FILE_NAME_PATTERN, methodName, sourceFile, nodes, edges, conditions, VerificationProperties.getGraphImageFileNameExtension());
					SaveUtil.saveGraph(new File(this.currentLockGraphsOutputDirectory, pcgFileName), pcgGraph, markup).join();
				} catch (InterruptedException e) {}
			}
		}
	}
	
	private boolean createContainingDirectory(Node lock, STATUS status){
		SourceCorrespondence sourceCorrespondence = (SourceCorrespondence) lock.getAttr(XCSG.sourceCorrespondence);
		String sourceCorrespondenceString = "<external>";
		if(sourceCorrespondence != null){
			sourceCorrespondenceString = this.fixSlashes(sourceCorrespondence.toString());
		}
		
		String folderPrefix = "";
		switch(status){
		case PAIRED:
			folderPrefix = "PAIRED";
			break;
		case PARTIALLY_PAIRED:
			folderPrefix = "PARTIALLY";
			break;
		case DEADLOCK:
			folderPrefix = "DEADLOCK";
			break;
		case UNPAIRED:
			folderPrefix = "UNPAIRED";
			break;
		}

		String containingDirectoryName = String.format(LOCK_GRAPH_DIRECTORY_NAME_PATTERN, folderPrefix, lock.addressBits(), sourceCorrespondenceString, this.signtureNode.getAttr(XCSG.name).toString());
		this.currentLockGraphsOutputDirectory = this.graphsOutputDirectory.resolve(containingDirectoryName).toFile();
		if(!this.currentLockGraphsOutputDirectory.mkdir()){
			Log.info("Cannot create directory:" + this.currentLockGraphsOutputDirectory.getAbsolutePath());
			return false;
		}
		return true;
	}
	
	private Q mpgForLock(Node lock, AtlasSet<Node> unlockFunctionCalls){
		Q lockFunction = CommonQueries.getContainingFunctions(Common.toQ(lock));
		Q unlockFunctions = CommonQueries.getContainingFunctions(Common.toQ(unlockFunctionCalls));
		Q callEdges = this.mpg.retainEdges();
		Q callL = lockFunction;
		Q callU = unlockFunctions;
		Q rcg_lock = callEdges.reverse(callL);
		Q rcg_unlock = callEdges.reverse(callU);
		Q rcg_both = rcg_lock.intersection(rcg_unlock);
		Q rcg_c = rcg_lock.union(rcg_unlock);
		Q rcg_lock_only = rcg_lock.difference(rcg_both);
		Q rcg_unlock_only = rcg_unlock.difference(rcg_both);
		Q call_lock_only = callL.union(callEdges.reverseStep(rcg_lock_only));
		Q call_unlock_only = callU.union(callEdges.reverseStep(rcg_unlock_only));
		Q call_c_only = call_lock_only.union(call_unlock_only);
		Q balanced = call_c_only.intersection(rcg_both);
		Q ubc = balanced.union(rcg_lock_only, rcg_unlock_only);
		Q result = rcg_c.intersection(callEdges.forward(ubc));
		Q leaves = this.mpg.forward(lockFunction.union(unlockFunctions)).leaves();
		result = result.union(leaves);
		result = result.induce(callEdges);
		return result;
	}
	
	private ArrayList<Q> getEventNodesForCFG(Q cfg, Q locks, Q unlocks, Q mpgFunctions){
		ArrayList<Q> results = new ArrayList<Q>();
		Q cfgNodes = cfg.nodes(XCSG.ControlFlow_Node);
		
		// Lock Events
		Q locksIntersection = cfgNodes.intersection(locks);
		results.add(locksIntersection);
		
		// Unlock Events
		Q unlocksIntersection = cfgNodes.intersection(unlocks);
		results.add(unlocksIntersection);
		
		
		// Call-site Events
		Q callsiteEvents = Common.empty();
		
		Q callSites = Common.universe().edges(XCSG.Contains).forward(cfgNodes).nodes(XCSG.CallSite);
		AtlasSet<Node> callsiteNodes = callSites.eval().nodes();
		
		for(Node callsiteNode : callsiteNodes){
			Q calledFunctions = Common.universe().edges(XCSG.InvokedFunction, XCSG.InvokedSignature).successors(Common.toQ(callsiteNode));
			if(!calledFunctions.intersection(mpgFunctions).eval().nodes().isEmpty()){
				Q cfgNode = Common.universe().edges(XCSG.Contains).reverseStep(Common.toQ(callsiteNode)).nodes(XCSG.ControlFlow_Node);
				callsiteEvents = callsiteEvents.union(cfgNode);
			}			
		}
		results.add(callsiteEvents);
		return results;
	}
	
	private String fixSlashes(String string){
		return string.replace('/', '@');
	}
	
}
