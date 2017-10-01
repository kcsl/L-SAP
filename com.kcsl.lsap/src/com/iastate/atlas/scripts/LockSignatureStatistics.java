package com.iastate.atlas.scripts;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
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
import com.ensoftcorp.atlas.ui.viewer.graph.SaveUtil;
import com.iastate.atlas.dot.DOTGraph;
import com.iastate.atlas.efg.EFGFactory;
import com.iastate.verifier.internal.MatchingPair;
import com.iastate.verifier.internal.Utils;

public class LockSignatureStatistics {
	
	private File RESULTS_DIRECTORY;
	private Node signature;
	private Graph mpg;
	private Q verifiedLocks;
	private Q doubleLocks;
	private Q danglingLocks;
	private Q partiallyLocks;
	private HashMap<Node, HashSet<MatchingPair>> pairs;
	
	private final String IMAGE_EXTENSION = ".png";
	private final boolean SAVE_DOT_FORMAT = false;
	private final String DOT_EXTENSION = ".dot";
	
	public enum STATUS {
		PAIRED, PARTIALLY_PAIRED, DEADLOCK, UNPAIRED
	}
	
	public LockSignatureStatistics(Node sig, Graph envelope, HashMap<Node, HashSet<MatchingPair>> matchingPairs, Q vLocks, Q dLocks, Q dnLocks, Q pLocks) {
		this.setSignature(sig);
		this.setMpg(envelope);
		this.setPairs(matchingPairs);
		this.setVerifiedLocks(vLocks);
		this.setDoubleLocks(dLocks);
		this.setDanglingLocks(dnLocks);
		this.setPartiallyLocks(pLocks);
	}
	
	public void process(){
		// A paired lock is never partially paired or unpaired or deadlock
		Q pairedLocks = this.getVerifiedLocks().difference(this.getPartiallyLocks(), this.getDanglingLocks(), this.getDoubleLocks());
		for(Node lock : pairedLocks.eval().nodes()){
			this.processLock(lock, STATUS.PAIRED);
		}
		
		// A partially paired lock should not be a deadlock
		Q partiallyPairedLocks = this.getPartiallyLocks().difference(this.getDoubleLocks());
		for(Node lock : partiallyPairedLocks.eval().nodes()){
			this.processLock(lock, STATUS.PARTIALLY_PAIRED);
		}
		
		// A deadlock lock is only if it has deadlock
		Q deadlockPairedLocks = this.getDoubleLocks();
		for(Node lock : deadlockPairedLocks.eval().nodes()){
			this.processLock(lock, STATUS.DEADLOCK);
		}
		
		// An unpaired lock cannot be partially paired or paired
		Q unpairedLocks = this.getDanglingLocks().difference(this.getVerifiedLocks(), this.getPartiallyLocks());
		for(Node lock : unpairedLocks.eval().nodes()){
			this.processLock(lock, STATUS.UNPAIRED);
		}
	}
	
	private void processLock(Node lock, STATUS status){
		Utils.debug(0, "Processing Lock:" + (++LinuxScripts.LOCK_PROGRESS_COUNT));
		
		// STEP 1: CREATE A LOCK FOLDER
		//Log.info("STEP 1: CREATING LOCK FOLDER");
		if(!this.createLockFolder(lock, status)){
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
		//Log.info("STEP 2: CREATING MPG");
		Q mpgForLock = Common.empty();
		if(status.equals(STATUS.UNPAIRED)){
			mpgForLock = Common.toQ(this.mpg).forward(Queries.getFunctionContainingElement(lock));	
		}else{
			mpgForLock = this.getMPGforLock(lock, unlocks);
		}
		mpgForLock = mpgForLock.retainEdges();
		Graph mpgGraph = mpgForLock.eval();
		//Utils.debug(0, "\tSTEP 2: CREATING MPG[" + mpgGraph.nodes().size() + "]");
		if(this.SAVE_DOT_FORMAT){
			DOTGraph mpgDotGraph = new DOTGraph(mpgGraph.nodes(), mpgGraph.edges(), null);
			mpgDotGraph.saveGraph(this.RESULTS_DIRECTORY, "mpg" + this.DOT_EXTENSION);
		}else{
			try{
				SaveUtil.saveGraph(new File(this.RESULTS_DIRECTORY, "mpg" + this.IMAGE_EXTENSION), mpgGraph).join();
			} catch (InterruptedException e) {}	
		}
		
		// STEP 3: CREATE THE CFG & EFG FOR EACH FUNCTION IN THE LOCK MPG
		//Log.info("STEP 3: CREATE THE CFG & EFG FOR EACH FUNCTION IN THE LOCK MPG");
		Q mpgFunctionsQ = mpgForLock.difference(mpgForLock.leaves());
		AtlasSet<Node> mpgFunctions = mpgFunctionsQ.eval().nodes();
		for(Node mpgFunction : mpgFunctions){
			String methodName = mpgFunction.getAttr(XCSG.name).toString();
			Utils.debug(0, "FUNCTION [" + methodName + "]");
			SourceCorrespondence sc = (SourceCorrespondence) mpgFunction.attr().get(XCSG.sourceCorrespondence);
			String sourceFile = "<external>";
			if(sc != null){
				sourceFile = fixSlashes(sc.toString());
			}
			
			Q cfg = Queries.CFG(Common.toQ(mpgFunction));
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
			H h = new Highlighter(ConflictStrategy.COLOR);
			h.highlight(lockEvents, Color.RED);
			h.highlight(unlockEvents, Color.GREEN);
			h.highlight(callsiteEvents, Color.BLUE);
			
			// STEP 3A: SAVE CFG
			long nodes = cfgGraph.nodes().size();
			long edges = cfgGraph.edges().size();
			long conditions = cfgGraph.nodes().taggedWithAll(XCSG.ControlFlowCondition).size();
			
			String cfgFileName = "CFG@@@" + methodName + "@@@" + sourceFile + "@@@"  + nodes + "@@@" + edges + "@@@" + conditions;
			//Utils.debug(0, "\tSTEP 3: SAVING CFG[" + nodes + "]");
			if(this.SAVE_DOT_FORMAT){
				DOTGraph cfgDotGraph = new DOTGraph(cfgGraph.nodes(), cfgGraph.edges(), h);
				cfgDotGraph.saveGraph(this.RESULTS_DIRECTORY, cfgFileName + this.DOT_EXTENSION);
			}else{
				try{
					SaveUtil.saveGraph(new File(this.RESULTS_DIRECTORY, cfgFileName + this.IMAGE_EXTENSION), cfgGraph, new MarkupFromH(h)).join();
				} catch (InterruptedException e) {}
			}
			
			Q efg = EFGFactory.EFG(cfg, eventNodes);
			
			// STEP 3A: SAVE EFG
			Graph efgGraph = efg.eval();
			nodes = efgGraph.nodes().size();
			edges = efgGraph.edges().size();
			conditions = 0;
			for(Node node : efg.eval().nodes()){
				if(efgGraph.edges(node, NodeDirection.OUT).size() > 1){
					conditions++;
				}
			}
			String efgFileName = "EFG@@@" + methodName + "@@@" + sourceFile + "@@@"  + nodes + "@@@" + edges + "@@@" + conditions; 
			if(this.SAVE_DOT_FORMAT){
				DOTGraph efgDotGraph = new DOTGraph(efgGraph.nodes(), efgGraph.edges(), h);
				efgDotGraph.saveGraph(this.RESULTS_DIRECTORY, efgFileName + this.DOT_EXTENSION);
			}else{
				try {
					SaveUtil.saveGraph(new File(this.RESULTS_DIRECTORY, efgFileName + this.IMAGE_EXTENSION), efgGraph, new MarkupFromH(h)).join();
				} catch (InterruptedException e) {}
			}
		}
		Utils.debug(0, "Done Processing Lock:" + (LinuxScripts.LOCK_PROGRESS_COUNT));
	}
	
	private boolean createLockFolder(Node lock, STATUS status){
		String scString = "<external>";
		SourceCorrespondence sc = (SourceCorrespondence) lock.attr().get(XCSG.sourceCorrespondence);
		if(sc != null){
			scString = fixSlashes(sc.toString());
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
		
		String lockFolderName = folderPrefix + "@@@" + lock.address().toAddressString() + "@@@" + scString + "@@@" + this.signature.attr().get(XCSG.name).toString();
		this.RESULTS_DIRECTORY = new File(LinuxScripts.GRAPH_RESULTS_PATH + lockFolderName);
		if(!RESULTS_DIRECTORY.mkdir()){
			Log.info("Cannot create directory:" + this.RESULTS_DIRECTORY.getAbsolutePath());
			return false;
		}
		return true;
	}
	
	private Q getMPGforLock(Node lock, AtlasSet<Node> unlocks){
		Q mpgForLock = this.mpg(Queries.getFunctionContainingElement(lock), Queries.getFunctionContainingElement(Common.toQ(unlocks)));
		return mpgForLock;
	}
	
	private Q mpg(Q lockFunction, Q unlockFunctions){
		Q callEdges = Common.toQ(this.mpg).retainEdges();
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
		Q leaves = Common.toQ(this.mpg).forward(lockFunction.union(unlockFunctions)).leaves();
		result = result.union(leaves);
		result = result.induce(callEdges);
		return result;
	}
	
	private ArrayList<Q> getEventNodesForCFG(Q cfg, Q locks, Q unlocks, Q mpgFunctions){
		ArrayList<Q> results = new ArrayList<Q>();
		Q cfgNodes = cfg.nodesTaggedWithAll(XCSG.ControlFlow_Node);
		
		// Lock Events
		Q locksIntersection = cfgNodes.intersection(locks);
		results.add(locksIntersection);
		
		// Unlock Events
		Q unlocksIntersection = cfgNodes.intersection(unlocks);
		results.add(unlocksIntersection);
		
		
		// Call-site Events
		Q callsiteEvents = Common.empty();
		
		Q callSites = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(cfgNodes).nodesTaggedWithAll(XCSG.CallSite);
		AtlasSet<Node> callsiteNodes = callSites.eval().nodes();
		
		for(Node callsiteNode : callsiteNodes){
			Q calledFunctions = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).successors(Common.toQ(callsiteNode));
			if(!calledFunctions.intersection(mpgFunctions).eval().nodes().isEmpty()){
				Q cfgNode = Common.universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(Common.toQ(callsiteNode)).nodesTaggedWithAll(XCSG.ControlFlow_Node);
				callsiteEvents = callsiteEvents.union(cfgNode);
			}			
		}
		results.add(callsiteEvents);
		return results;
	}
	
	private String fixSlashes(String string){
		return string.replace('/', '@');
	}

	public Node getSignature() {
		return signature;
	}

	public void setSignature(Node signature) {
		this.signature = signature;
	}

	public Graph getMpg() {
		return mpg;
	}

	public void setMpg(Graph mpg) {
		this.mpg = mpg;
	}

	public Q getVerifiedLocks() {
		return verifiedLocks;
	}

	public void setVerifiedLocks(Q verifiedLocks) {
		this.verifiedLocks = verifiedLocks;
	}

	public Q getDoubleLocks() {
		return doubleLocks;
	}

	public void setDoubleLocks(Q doubleLocks) {
		this.doubleLocks = doubleLocks;
	}

	public Q getDanglingLocks() {
		return danglingLocks;
	}

	public void setDanglingLocks(Q danglingLocks) {
		this.danglingLocks = danglingLocks;
	}

	public Q getPartiallyLocks() {
		return partiallyLocks;
	}

	public void setPartiallyLocks(Q partiallyLocks) {
		this.partiallyLocks = partiallyLocks;
	}

	public HashMap<Node, HashSet<MatchingPair>> getPairs() {
		return pairs;
	}

	public void setPairs(HashMap<Node, HashSet<MatchingPair>> pairs) {
		this.pairs = pairs;
	}
	
}