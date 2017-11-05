package com.kcsl.lsap.core;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
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
import com.kcsl.lsap.VerificationProperties;
import com.kcsl.lsap.utils.LSAPUtils;

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
	
	/**
	 * A list of {@link Node}s that calls unlock.
	 */
	private AtlasSet<Node> unlockEventNodes;

	/**
	 * A mapping between {@link Node} to its list of {@link MatchingPair}s.
	 */
    private AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap;
	
    /**
     * A mapping between a {@link XCSG#ControlFlow_Node} to an {@link Integer} corresponding to current {@link PathStatus} at this node.
     */
	private AtlasMap<Node, Integer> nodeToPathStatusMap;
	
	/**
	 * A mapping between a {@link XCSG#ControlFlow_Node} to a list of {@link XCSG#ControlFlow_Node} containing events of interest up to the node.
	 */
	private AtlasMap<Node, AtlasSet<Node>> nodeToEventsAlongPathMap;
	
    /**
     * A mapping between a {@link XCSG#ControlFlow_Node} to an {@link Integer} corresponding to current {@link PathStatus} at this node from its successors.
     */
	private AtlasMap<Node, Integer> nodeToPathStatusFromSuccessorsMap;
	
	/**
	 * A mapping between a {@link XCSG#ControlFlow_Node} to a list of {@link XCSG#ControlFlow_Node} containing events of interest up to the node from its successors.
	 */
	private AtlasMap<Node, AtlasSet<Node>> nodeToEventsAlongPathFromSuccessorsMap;
	
	/**
	 * A list of {@link Q}s containing the events of interest. the first element contains the events calling lock, the second element contains the events calls unlock, 
	 * the third element contains calls to MPG functions, the last element contains all events.
	 */
	private List<Q> eventsOfInterest;
	
	/**
	 * Constructs a new instance of {@link FunctionVerifier} for the given <code>function</code> and its corresponding <code>pcg</code> and <code>functionSummary</code>.
	 * 
	 * @param function A {@link XCSG#Function}.
	 * @param pcg A {@link PCG} for the given <code>function</code>.
	 * @param summary A {@link FunctionSummary} for the given <code>function</code>.
	 * @param events A list of {@link Q}s where the first element contains the events calling lock, the second element contains the events calls unlock, 
	 * the third element contains calls to MPG functions, the last element contains all events.
	 */
	public FunctionVerifier(Node function, PCG pcg, AtlasMap<Node, FunctionSummary> summary, List<Q> events) {
		this.currentFunction = function;
		this.successorsFunctionSummaries = summary;
		this.pcg = pcg;
		this.matchingPairsMap = new AtlasGraphKeyHashMap<Node, ArrayList<MatchingPair>>();
		this.nodeToPathStatusMap = new AtlasGraphKeyHashMap<Node, Integer>();
		this.nodeToEventsAlongPathMap = new AtlasGraphKeyHashMap<Node, AtlasSet<Node>>();
		this.nodeToPathStatusFromSuccessorsMap = new AtlasGraphKeyHashMap<Node, Integer>();
		this.nodeToEventsAlongPathFromSuccessorsMap = new AtlasGraphKeyHashMap<Node, AtlasSet<Node>>();
		this.eventsOfInterest = events;
	}
	
	/**
	 * Runs the verification on {@link #currentFunction}
	 * 
	 * @return An instance of {@link FunctionSummary} for {@link #currentFunction}.
	 */
	@SuppressWarnings("unchecked")
	public FunctionSummary run() {
		this.summary = new FunctionSummary(this.currentFunction, this.pcg, this.eventsOfInterest);
		this.lockEventNodes = this.eventsOfInterest.get(0).eval().nodes();
		this.unlockEventNodes = this.eventsOfInterest.get(1).eval().nodes();

		AtlasMap<Node, Node> callEventsFunctionsMap = new AtlasGraphKeyHashMap<Node, Node>();

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
						callEventsFunctionsMap.put(node, calledFunction);
					}
				}
			}
		}
		this.successorsFunctionSummaries.clear();
		this.successorsFunctionSummaries = summary;

		this.duplicateMultipleStatusFunctions();

		Object[] returns = this.traverse(this.pcg.getMasterEntry(), PathStatus.THROUGH, new AtlasHashSet<Node>());

		this.summary.setNodeToPathStatusFromSuccessors((int) returns[0]);
		this.summary.setNodeToEventsAlongPathFromSuccessors((AtlasSet<Node>) returns[1]);
		this.summary.setNodeToPathStatus(this.nodeToPathStatusMap.get(this.pcg.getMasterExit()));
		this.summary.setNodeToEventsAlongPath(this.nodeToEventsAlongPathMap.get(this.pcg.getMasterExit()));
		this.summary.setCallEventsFunctionsMap(callEventsFunctionsMap);
		this.summary.setMatchingPairsList(this.matchingPairsMap);

		return this.summary;
	}
    
	/**
	 * Recursively traverses the {@link #pcg} from <code>node</code> in depth-first search way.
	 * 
	 * @param node The starting {@link XCSG#ControlFlow_Node} for traversal.
	 * @param pathStatus The current {@link PathStatus} up to this <code>node</code>. 
	 * @param nodesOfInterest A list of {@link Node}s of interest along the path to <code>node</code>.
	 * @return An array of two elements: the first element corresponds to a {@link PathStatus} from successors at this <code>node</code>, 
	 * the second element corresponds to the list of nodes of interest along the path from successors to this <code>node</code>.
	 */
	@SuppressWarnings("unchecked")
	private Object[] traverse(Node node, int pathStatus, AtlasSet<Node> nodesOfInterest) {
		int rets = 0, outs, childrens, childs;
		AtlasSet<Node> retl = new AtlasHashSet<Node>();
		AtlasSet<Node> outl = new AtlasHashSet<Node>();
		AtlasSet<Node> childrenl = new AtlasHashSet<Node>();
		AtlasSet<Node> childl = new AtlasHashSet<Node>();

		if (this.successorsFunctionSummaries.containsKey(node)) {
			FunctionSummary nodeSummary = this.successorsFunctionSummaries.get(node);
			rets = nodeSummary.getNodeToPathStatusFromSuccessors();
			retl = nodeSummary.getNodeToEventsAlongPathFromSuccessors();
			outs = nodeSummary.getNodeToPathStatus();
			outl = nodeSummary.getNodeToEventsAlongPath();
			if ((pathStatus & PathStatus.LOCK) != 0 && (rets & PathStatus.LOCK) != 0) {
				// Here we catch the raced e1 events (hopefully)
				this.appendMatchingPairs(nodesOfInterest, outl);
			}
		} else if (this.lockEventNodes.contains(node)) {
			outl.add(node);
			if ((pathStatus & PathStatus.LOCK) != 0) {
				// Here we catch the raced e1 events (hopefully)
				this.appendMatchingPairs(nodesOfInterest, outl);
			}
			rets = PathStatus.LOCK;
			retl = new AtlasHashSet<Node>();
			outs = rets;
		} else if (this.unlockEventNodes.contains(node)) {
			rets = PathStatus.UNLOCK;
			retl.add(node);
			outs = rets;
			outl = new AtlasHashSet<Node>();
		} else {
			outs = pathStatus;
			outl = nodesOfInterest;
		}

		boolean goon = false;
		if (this.nodeToPathStatusMap.containsKey(node)) { // visited before
			if (!this.lockEventNodes.contains(node) && !this.unlockEventNodes.contains(node)
					&& !this.successorsFunctionSummaries.containsKey(node)) {
				// Normal node
				goon = false;
				if (!this.isSubSet(outl, this.nodeToEventsAlongPathMap.get(node))) {
					// new Lock on the path
					goon = true;
					this.nodeToEventsAlongPathMap.get(node).addAll(outl);
				}
				if ((outs | this.nodeToPathStatusMap.get(node)) != this.nodeToPathStatusMap.get(node)) {
					// in status on the path
					goon = true;
					this.nodeToPathStatusMap.put(node, outs | this.nodeToPathStatusMap.get(node));
				}
				if (goon) {
					AtlasSet<Node> successors = this.pcg.getPCG().successors(Common.toQ(node)).eval().nodes();
					childrenl = new AtlasHashSet<Node>();
					if (successors.size() == 0) {
						childrens = PathStatus.THROUGH;
						// if (pathStatus != PathStatus.LOCK)
						// this.enableRemainingLNodes = false;
					} else {
						childrens = PathStatus.UNKNOWN;
						for (Node child : successors) {
							Object[] returns = this.traverse(child, outs, outl);
							childs = (Integer) returns[0];
							childl = (AtlasSet<Node>) returns[1];
							childrens |= childs;
							childrenl.addAll(childl);
						}
					}
					this.nodeToPathStatusFromSuccessorsMap.put(node, childrens);
					this.nodeToEventsAlongPathFromSuccessorsMap.put(node, new AtlasHashSet<Node>(childrenl));
					return new Object[] { childrens, childrenl };
				} else {
					// !goon, visited before with same information
					if (this.nodeToPathStatusFromSuccessorsMap.get(node) != null)
						return new Object[] { this.nodeToPathStatusFromSuccessorsMap.get(node),
								this.nodeToEventsAlongPathFromSuccessorsMap.get(node) };
					return new Object[] { PathStatus.UNKNOWN, new AtlasHashSet<Node>() };
				}
			} else {
				// Lock or Unlock node or special node, stop here either way
				return new Object[] { rets, retl };
			}
		} else {
			// First visit on this path
			this.nodeToPathStatusMap.put(node, outs);
			this.nodeToEventsAlongPathMap.put(node, new AtlasHashSet<Node>(outl));
			AtlasSet<Node> successors = this.pcg.getPCG().successors(Common.toQ(node)).eval().nodes();
			childrenl = new AtlasHashSet<Node>();
			if (successors.size() == 0) {
				childrens = PathStatus.THROUGH;
			} else {
				childrens = PathStatus.UNKNOWN;
				for (Node child : successors) {
					Object[] returns = this.traverse(child, outs, outl);
					childs = (Integer) returns[0];
					childl = (AtlasSet<Node>) returns[1];
					childrens |= childs;
					childrenl.addAll(childl);
				}
			}

			if (this.successorsFunctionSummaries.containsKey(node)) {
				// special node
				// outs is only PathStatus.LOCK
				if ((outs & PathStatus.LOCK) != 0) {
					if (childrenl.size() != 0) {
						this.appendMatchingPairs(outl, childrenl);
					}
				}

			} else if (this.lockEventNodes.contains(node)) {
				if (childrenl.size() != 0) {
					this.appendMatchingPairs(outl, childrenl);
				}

			} else if (!this.unlockEventNodes.contains(node)) {
				rets = childrens;
				retl = new AtlasHashSet<Node>(childrenl);
			}
			this.nodeToPathStatusFromSuccessorsMap.put(node, rets);
			this.nodeToEventsAlongPathFromSuccessorsMap.put(node, new AtlasHashSet<Node>(retl));
			return new Object[] { rets, retl };
		}
	}
	
	/**
	 * Adds new {@link MatchingPair}s to {@link #matchingPairsMap} for <code>nodes</code> and their corresponding <code>matchingNodes</code>.
	 * 
	 * @param nodes A list of {@link Node}s.
	 * @param matchingNodes A list of {@link Node}s matched with the nodes in <code>nodes</code>.
	 */
	private void appendMatchingPairs(AtlasSet<Node> nodes, AtlasSet<Node> matchingNodes) {
		for (Node node : nodes) {
			ArrayList<MatchingPair> matchingPairs = new ArrayList<MatchingPair>();
			if (this.matchingPairsMap.containsKey(node)) {
				matchingPairs = this.matchingPairsMap.get(node);
			}
			for (Node matchingNode : matchingNodes) {
				matchingPairs.add(new MatchingPair(node, matchingNode, null));
			}
			this.matchingPairsMap.put(node, matchingPairs);
		}
	}
	
    
    /**
     * Duplicate a node in the CFG if its a called function with a summary that contains multiple statuses such as: locked and unlocked. 
     */
	private void duplicateMultipleStatusFunctions() {
		HashMap<Node, FunctionSummary> duplicatedNodesSummaries = new HashMap<Node, FunctionSummary>();
		for (Node functionNode : this.successorsFunctionSummaries.keySet()) {
			FunctionSummary functionSummary = this.successorsFunctionSummaries.get(functionNode);
			int status = functionSummary.getNodeToPathStatusFromSuccessors();

			if ((status | PathStatus.THROUGH) == status) {
				FunctionSummary newFunctionSummary = new FunctionSummary(functionSummary.getFunction(),
						functionSummary.getPCG(), functionSummary.getAllEvents());
				newFunctionSummary.setNodeToPathStatusFromSuccessors(functionSummary.getNodeToPathStatusFromSuccessors() & ~PathStatus.THROUGH);
				newFunctionSummary.setNodeToPathStatus(functionSummary.getNodeToPathStatus() & ~PathStatus.THROUGH);
				Node newFunctionNode = this.duplicateNode(functionNode);
				duplicatedNodesSummaries.put(newFunctionNode, newFunctionSummary);
			}
		}

		for (Node newFunctionNode : duplicatedNodesSummaries.keySet()) {
			this.successorsFunctionSummaries.put(newFunctionNode, duplicatedNodesSummaries.get(newFunctionNode));
		}
	}

	/**
	 * Duplicates the <code>node</code> in {@link #pcg}.
	 * 
	 * @param node The {@link Node} to be duplicated.
	 * @return A newly duplicated {@link Node}.
	 */
	private Node duplicateNode(Node node) {
		Node newNode = Graph.U.createNode();
		newNode.putAllAttr(node.attr());
		newNode.tag(VerificationProperties.DUPLICATE_NODE);
		newNode.tags().addAll(node.tags().explicitElements());

		Edge e = Graph.U.createEdge(this.currentFunction, newNode);
		e.tag(XCSG.Contains);

		Q pcg = this.pcg.getPCG();
		Graph pcgGraph = pcg.eval();

		AtlasSet<Node> successors = pcg.successors(Common.toQ(node)).eval().nodes();

		for (Node successor : successors) {
			Edge currentEdge = LSAPUtils.findDirectEdgesBetweenNodes(pcgGraph, node, successor).get(0);
			this.createDuplicateEdge(currentEdge, newNode, successor);
		}

		AtlasSet<Node> predecessors = pcg.predecessors(Common.toQ(node)).eval().nodes();
		for (Node predecessor : predecessors) {
			Edge currentEdge = LSAPUtils.findDirectEdgesBetweenNodes(pcgGraph, predecessor, node).get(0);
			this.createDuplicateEdge(currentEdge, predecessor, newNode);
		}
		return newNode;
	}
    
	/**
	 * Creates a duplicated edges similar to <code>edge</code> in {@link #pcg} that connects <code>from</code> and <code>to</code>.
	 * 
	 * @param edge The edge that needs to be duplicated.
	 * @param from The from {@link Node}.
	 * @param to The to {@link Node}.
	 * @return The newly duplicated {@link Edge}.
	 */
	private Edge createDuplicateEdge(Edge edge, Node from, Node to) {
		Edge newEdge = Graph.U.createEdge(from, to);
		newEdge.tags().add(VerificationProperties.DUPLICATE_EDGE);
		newEdge.putAllAttr(edge.attr());
		newEdge.tags().addAll(edge.tags().explicitElements());
		return newEdge;
	}

	/**
	 * Tests whether <code>aSet</code> of <code>bSet</code>.
	 * 
	 * @param aSet The {@link AtlasSet} that needs to be tested to be part of <code>bSet</code>.
	 * @param bSet The containing {@link AtlasSet}.
	 * @return true if <code>aSet</code> is a subset of <code>bSet</code>.
	 */
	private boolean isSubSet(AtlasSet<Node> aSet, AtlasSet<Node> bSet) {
		for (Node aNode : aSet) {
			if (!bSet.contains(aNode))
				return false;
		}
		return true;
	}
	
	/**
	 * A public class corresponding to the verification status at a given node.
	 */
	public class PathStatus {
		final public static int UNKNOWN = 0;
		final public static int MATCH = 1;
		final public static int LOCK = 2;
		final public static int UNLOCK = 4;
		final public static int THROUGH = 8;
		final public static int START = 16;
		final public static int ERROR = 32;
	}
	
}
