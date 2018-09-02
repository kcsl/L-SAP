package com.kcsl.lsap.verifier;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.map.AtlasGraphKeyHashMap;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.pcg.common.PCG;

/**
 * A class containing the logic for the actual verification of a given function.
 */
public class FunctionVerifier {

	/**
	 * A {@link XCSG#Function} node for the current function being verified.
	 */
	private Node currentFunction;
	
	/**
	 * The {@link PCG} associated with this {@link #currentFunction}.
	 */
	private PCG pcg;
	
	/**
	 * An instance of {@link FunctionSummary} for this {@link #currentFunction}.
	 */
	private FunctionSummary summary;
	
	/**
	 * A mapping from {@link XCSG#Function} that is successor of {@link #currentFunction} in MPG to its {@link FunctionSummary}.
	 */
	private AtlasMap<Node, FunctionSummary> successorsFunctionSummaries;
	
	/**
	 * A list of {@link Node}s that calls lock.
	 */
	private AtlasSet<Node> lockEventNodes;
	
	private AtlasSet<Node> multiStateLockEventNodes;
	
	/**
	 * A list of {@link Node}s that calls unlock.
	 */
	private AtlasSet<Node> unlockEventNodes;
	
	private AtlasMap<Node, Node> callSiteEventNodesMap;

	/**
	 * A mapping between {@link Node} to its list of {@link MatchingPair}s.
	 */
    private AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap;
	
	/**
	 * A list of {@link Q}s containing the events of interest. the first element contains the events calling lock, the second element contains the events calls unlock, 
	 * the third element contains calls to MPG functions, the last element contains all events.
	 */
	private List<Q> eventsOfInterest;
	
	private Map<Node, Summary> preNodeSummaryCache = new HashMap<Node, Summary>();
	
	/**
	 * Constructs a new instance of {@link FunctionVerifier} for the given <code>function</code> and its corresponding <code>pcg</code> and <code>functionSummary</code>.
	 * 
	 * @param function A {@link XCSG#Function}.
	 * @param pcg A {@link PCG} for the given <code>function</code>.
	 * @param summary A {@link FunctionSummary} for the given <code>function</code>.
	 * @param events A list of {@link Q}s where the first element contains the events calling lock, the second element contains the events calls unlock, 
	 * the third element contains calls to MPG functions, the last element contains all events.
	 */
	public FunctionVerifier(Node function, PCG pcg, List<Q> events, AtlasMap<Node, FunctionSummary> successorsFunctionSummaries) {
		this.currentFunction = function;
		this.successorsFunctionSummaries = successorsFunctionSummaries;
		this.pcg = pcg;
		this.matchingPairsMap = new AtlasGraphKeyHashMap<Node, ArrayList<MatchingPair>>();
		this.eventsOfInterest = events;
		this.setupPreNodeEventSetCache();
	}
	
	private void setupPreNodeEventSetCache() {
		this.preNodeSummaryCache = new HashMap<Node, Summary>();
		AtlasSet<Node> nodes = this.pcg.getPCG().eval().nodes();
		for(Node node: nodes) {
			this.preNodeSummaryCache.put(node, new Summary());
		}
	}
	
	/**
	 * Runs the verification on {@link #currentFunction}
	 * 
	 * @return An instance of {@link FunctionSummary} for {@link #currentFunction}.
	 */
	public FunctionSummary run() {
		this.lockEventNodes = this.eventsOfInterest.get(0).eval().nodes();
		this.unlockEventNodes = this.eventsOfInterest.get(1).eval().nodes();
		this.multiStateLockEventNodes = this.eventsOfInterest.get(3).eval().nodes();
		this.callSiteEventNodesMap = new AtlasGraphKeyHashMap<Node, Node>();

		AtlasGraphKeyHashMap<Node, FunctionSummary> summary = new AtlasGraphKeyHashMap<Node, FunctionSummary>();
		AtlasSet<Node> nodes = this.eventsOfInterest.get(2).eval().nodes();
		for (Node node : nodes) {
			for (Node calledFunction : this.successorsFunctionSummaries.keySet()) {
				Q callSitesQuery = universe().edges(XCSG.Contains).forward(Common.toQ(node)).nodes(XCSG.CallSite);
				AtlasSet<Node> callSites = callSitesQuery.eval().nodes();
				for (Node callSite : callSites) {
					Node targetForCallSite = CallSiteAnalysis.getTargets(callSite).one();
					if (targetForCallSite.equals(calledFunction)) {
						summary.put(node, this.successorsFunctionSummaries.get(calledFunction));
						this.callSiteEventNodesMap.put(node, calledFunction);
					}
				}
			}
		}
		this.successorsFunctionSummaries.clear();
		this.successorsFunctionSummaries = summary;

		Summary masterEntryPreNodeSummary = new Summary();
		masterEntryPreNodeSummary.addAll(Event.NONE, new AtlasHashSet<Node>());
		this.traverse(this.pcg.getMasterEntry(), masterEntryPreNodeSummary);
		
		this.summary = new FunctionSummary(this.currentFunction, this.pcg, this.lockEventNodes, this.multiStateLockEventNodes, this.unlockEventNodes, this.successorsFunctionSummaries);
		this.summary.compute();
		this.summary.storeMatchingPairs(this.matchingPairsMap);

		return this.summary;
	}
	
	private void traverse(Node currentNode, Summary preNodeSummary) { 
		Summary postNodeSummary = new Summary();
		if(this.successorsFunctionSummaries.containsKey(currentNode)) {
			// currentNode corresponds to a function call, FunctionSummary for currentNode must exist.
			FunctionSummary currentNodeFunctionSummary = this.successorsFunctionSummaries.get(currentNode);
			
			// get reachableEventsFromEntry from FunctionSummary and see if we have any pairings or deadlocks
			Summary entryNodeReachableSummary = currentNodeFunctionSummary.getEntryNodeReachableSummary();
			if(preNodeSummary.contains(Event.LOCK)) {
				if(entryNodeReachableSummary.contains(Event.LOCK)) {
					this.reportDeadlock(preNodeSummary.inducingNodesForEvent(Event.LOCK), entryNodeReachableSummary.inducingNodesForEvent(Event.LOCK));
				} else if(entryNodeReachableSummary.contains(Event.UNLOCK)) {
					this.reportPairing(preNodeSummary.inducingNodesForEvent(Event.LOCK), entryNodeReachableSummary.inducingNodesForEvent(Event.UNLOCK));
				}
			}
			
			// get reachableEventsFromExit from FunctionSummary
			Summary exitNodeReachableSummary = currentNodeFunctionSummary.getExitNodeReachableSummary();
			postNodeSummary.update(exitNodeReachableSummary);
			if(postNodeSummary.contains(Event.NONE)) {
				postNodeSummary.remove(Event.NONE);
				postNodeSummary.update(preNodeSummary);
			}
			
		} else if(this.lockEventNodes.contains(currentNode)) {
			// currentNode corresponds to a lock node.
			
			if(preNodeSummary.contains(Event.LOCK)) {
				this.reportDeadlock(preNodeSummary.inducingNodesForEvent(Event.LOCK), currentNode);
			}
			
			postNodeSummary.clear();
			postNodeSummary.add(Event.LOCK, currentNode);
			
		} else if(this.unlockEventNodes.contains(currentNode)) {
			// currentNode corresponds to an unlock node.
			
			if(preNodeSummary.contains(Event.LOCK)) {
				this.reportPairing(preNodeSummary.inducingNodesForEvent(Event.LOCK), currentNode);
			}
				
			postNodeSummary.clear();
			postNodeSummary.add(Event.UNLOCK, currentNode);
		} else {
			// currentNode is not a special node -- just pass through.
			postNodeSummary.update(preNodeSummary);
		}
		
		// Explore successors
		AtlasSet<Edge> outEdges = this.pcg.getPCG().forwardStep(Common.toQ(currentNode)).eval().edges();
		for(Edge edge: outEdges) {
			Node successor = edge.to();
			if(edge.taggedWith(PCG.PCGEdge.PCGBackEdge) || edge.taggedWith(PCG.PCGEdge.PCGReentryEdge)) {
				// previously traversed path, do we have new events along the path
				Summary successorPreNodeSummary = this.preNodeSummaryCache.get(successor);
				boolean skipVisitingSuccessor = successorPreNodeSummary.contains(postNodeSummary);
				if(!skipVisitingSuccessor) {
					successorPreNodeSummary.update(postNodeSummary);
					this.traverse(successor, successorPreNodeSummary);
				}
			} else {
				// newly traversed path
				this.traverse(successor, postNodeSummary);
			}
		}
	}
	
	private void reportDeadlock(AtlasSet<Node> lockNodes, Node matchingLockNode) {
		AtlasSet<Node> matchingLockNodes = new AtlasHashSet<Node>();
		matchingLockNodes.add(matchingLockNode);
		this.reportDeadlock(lockNodes, matchingLockNodes);
	}
	
	private void reportDeadlock(AtlasSet<Node> lockNodes, AtlasSet<Node> matchingLockNodes) {
		for (Node lockNode : lockNodes) {
			ArrayList<MatchingPair> matchingPairs = new ArrayList<MatchingPair>();
			if (this.matchingPairsMap.containsKey(lockNode)) {
				matchingPairs = this.matchingPairsMap.get(lockNode);
			}
			for (Node matchingLockNode : matchingLockNodes) {
				matchingPairs.add(new MatchingPair(lockNode, matchingLockNode, null));
			}
			this.matchingPairsMap.put(lockNode, matchingPairs);
		}
	}
	
	private void reportPairing(AtlasSet<Node> lockNodes, Node matchingUnlockNode) {
		AtlasSet<Node> matchingUnlockNodes = new AtlasHashSet<Node>();
		matchingUnlockNodes.add(matchingUnlockNode);
		this.reportPairing(lockNodes, matchingUnlockNodes);
	}
	
	private void reportPairing(AtlasSet<Node> lockNodes, AtlasSet<Node> matchingUnlockNodes) {
		for (Node lockNode : lockNodes) {
			ArrayList<MatchingPair> matchingPairs = new ArrayList<MatchingPair>();
			if (this.matchingPairsMap.containsKey(lockNode)) {
				matchingPairs = this.matchingPairsMap.get(lockNode);
			}
			for (Node matchingUnlockNode : matchingUnlockNodes) {
				matchingPairs.add(new MatchingPair(lockNode, matchingUnlockNode, null));
			}
			this.matchingPairsMap.put(lockNode, matchingPairs);
		}
	}
	
}
