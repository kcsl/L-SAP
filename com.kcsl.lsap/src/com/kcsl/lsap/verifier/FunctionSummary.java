package com.kcsl.lsap.verifier;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.open.pcg.common.PCG;
import com.ensoftcorp.open.pcg.common.PCG.PCGEdge;
import com.kcsl.lsap.feasibility.FeasibilityChecker;

public class FunctionSummary {
	
	private Node function;
	
	private PCG pcg;
	
	private AtlasSet<Node> lockEventNodes;
	
	private AtlasSet<Node> multiStateLockEventNodes;
	
	private AtlasSet<Node> unlockEventNodes;
	
	private AtlasMap<Node, FunctionSummary> successorsFunctionSummaryMap;
	
	private AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap;
	
	private Summary entryNodeReachableSummary;
	
	private Summary exitNodeReachableSummary;
	
	/**
	 * An instance of {@link FeasibilityChecker} to perform feasibility operations.
	 */
	private FeasibilityChecker feasibilityChecker;
	
	public FunctionSummary(Node function, PCG pcg, AtlasSet<Node> lockEventNodes, AtlasSet<Node> multiStateLockEventNodes, AtlasSet<Node> unlockEventNodes, AtlasMap<Node, FunctionSummary> successorsFunctionSummaryMap) {
		this.function = function;
		this.pcg = pcg;
		this.lockEventNodes = lockEventNodes;
		this.unlockEventNodes = unlockEventNodes;
		this.multiStateLockEventNodes = multiStateLockEventNodes;
		this.successorsFunctionSummaryMap = successorsFunctionSummaryMap;
		this.entryNodeReachableSummary = new Summary();
		this.exitNodeReachableSummary = new Summary();
	}
	
	public Node getMasterExitNode() {
		return this.pcg.getMasterExit();
	}
	
//	public void compute() {
//		Q loopBackEdges = this.pcg.getPCG().edges(PCGEdge.PCGBackEdge, PCGEdge.PCGReentryEdge);
//		Graph acyclicPCG = this.pcg.getPCG().differenceEdges(loopBackEdges).eval();
//		Node entryNode = this.pcg.getMasterEntry();
//		Node exitNode = this.pcg.getMasterExit();
//		this.computeEntryNodeReachableSummary(acyclicPCG, entryNode, exitNode);
//		//this.computeExitNodeReachableSummary(acyclicPCG, entryNode, exitNode);
//	}
	
	public void computeEntryNodeReachableSummary() {
		Q loopBackEdges = this.pcg.getPCG().edges(PCGEdge.PCGBackEdge, PCGEdge.PCGReentryEdge);
		Graph graph = this.pcg.getPCG().differenceEdges(loopBackEdges).eval();
		Node entryNode = this.pcg.getMasterEntry();
		Node exitNode = this.pcg.getMasterExit();
		Queue<Node> queue = new LinkedList<Node>();
		queue.add(entryNode);
		while(!queue.isEmpty()) {
			Node currentNode = queue.poll();
			if(this.lockEventNodes.contains(currentNode)) {
				this.entryNodeReachableSummary.add(Event.LOCK, currentNode);
			} else if (this.unlockEventNodes.contains(currentNode)) {
				this.entryNodeReachableSummary.add(Event.UNLOCK, currentNode);
			} else if (this.successorsFunctionSummaryMap.containsKey(currentNode)) {
				Summary successorReachableSummaryFromEntryNode = this.successorsFunctionSummaryMap.get(currentNode).getEntryNodeReachableSummary();
				this.entryNodeReachableSummary.update(successorReachableSummaryFromEntryNode);
			} else if (currentNode.equals(exitNode)) {
				this.entryNodeReachableSummary.addAll(Event.NONE, new AtlasHashSet<Node>());
			} else {
				AtlasSet<Edge> outEdges = graph.edges(currentNode, NodeDirection.OUT);
				for(Edge edge: outEdges) {
					Node toNode = edge.to();
					queue.add(toNode);
				}
			}
		}
	}
	
	public void setExitNodeReachableSummary(Summary exitNodeReachableSummary) {
		this.exitNodeReachableSummary = exitNodeReachableSummary;
	}
	
//	private void computeExitNodeReachableSummary(Graph graph, Node entryNode, Node exitNode) {
//		Queue<Node> queue = new LinkedList<Node>();
//		queue.add(exitNode);
//		while(!queue.isEmpty()) {
//			Node currentNode = queue.poll();
//			if(this.lockEventNodes.contains(currentNode)) {
//				this.exitNodeReachableSummary.add(Event.LOCK, currentNode);
//			} else if (this.unlockEventNodes.contains(currentNode)) {
//				this.exitNodeReachableSummary.add(Event.UNLOCK, currentNode);
//			} else if (this.successorsFunctionSummaryMap.containsKey(currentNode)) {
//				Summary successorReachableSummaryFromExitNode = this.successorsFunctionSummaryMap.get(currentNode).getExitNodeReachableSummary();
//				this.exitNodeReachableSummary.update(successorReachableSummaryFromExitNode);
//			} else if (currentNode.equals(entryNode)) {
//				this.exitNodeReachableSummary.addAll(Event.NONE, new AtlasHashSet<Node>());
//			} else {
//				AtlasSet<Edge> inEdges = graph.edges(currentNode, NodeDirection.IN);
//				for(Edge edge: inEdges) {
//					Node fromNode = edge.from();
//					queue.add(fromNode);
//				}
//			}
//		}
//	}
	
	public void storeMatchingPairs(AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap) {
		this.matchingPairsMap = matchingPairsMap;
	}
	
	public AtlasMap<Node, ArrayList<MatchingPair>> getMatchingPairsMap() {
		return this.matchingPairsMap;
	}
	
	public AtlasSet<Node> getLockEventNodes() {
		return this.lockEventNodes;
	}
	
	public AtlasSet<Node> getMultiStateLockEventNodes() {
		return this.multiStateLockEventNodes;
	}
	
	public AtlasSet<Node> getUnlockEventNodes() {
		return this.unlockEventNodes;
	}
	
	public Summary getEntryNodeReachableSummary() {
		return this.entryNodeReachableSummary;
	}
	
	public Summary getExitNodeReachableSummary() {
		return this.exitNodeReachableSummary;
	}
	
	public FeasibilityChecker getFeasibilityChecker() {
		if(this.feasibilityChecker == null){
			this.feasibilityChecker = new FeasibilityChecker(Common.toQ(this.function));
		}
		return feasibilityChecker;
	}

	public void setFeasibilityChecker(FeasibilityChecker feasibilityChecker) {
		this.feasibilityChecker = feasibilityChecker;
	}
}
