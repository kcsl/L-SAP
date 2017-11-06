package com.kcsl.lsap.utils;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.list.AtlasArrayList;
import com.ensoftcorp.atlas.core.db.list.AtlasList;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.kcsl.lsap.VerificationProperties;

/**
 * A class containing utility and helper functions for the verification.
 */
public class LSAPUtils {
	
	/**
	 * A private constructor to prevent intentional initializations of this class.
	 * 
	 * @throws IllegalAccessException If any initialization occur to this class.
	 */
	private LSAPUtils() throws IllegalAccessException {
		throw new IllegalAccessException();
	}
	
	/**
	 * Logs a <code>message</code> terminated with "\n" to {@link VerificationProperties#getOutputLogFileWriter()}.
	 * 
	 * @param message A {@link String} corresponding to the message to be logged.
	 */
	public static void log(String message){
		try {
			VerificationProperties.getOutputLogFileWriter().write(message + "\n");
			VerificationProperties.getOutputLogFileWriter().flush();
		} catch (IOException e) {
			System.err.println("Cannot write to log file.");
		}
	}
	
	/**
	 * Converts a list of {@link String}s for function names to {@link Q} of {@link XCSG#Function}s.
	 * 
	 * @param functionsList A list {@link String}s of function names. 
	 * @return A {@link Q} of {@link XCSG#Function}s.
	 */
	public static Q functionsQ(List<String> functionsList){
		return CommonQueries.functions((String[])functionsList.toArray());
	}
	
	/**
	 * Finds all {@link XCSG#Variable}s of <code>objectType</code>.
	 * 
	 * @param objectType A {@link String} corresponding to a {@link XCSG#Type}.
	 * @return A {@link Q} of {@link XCSG#Variable}s of <code>objectType</code>.
	 */
	public static Q getSignaturesForObjectType(Q objectType){
		return universe().edges(XCSG.TypeOf, XCSG.ReferencedType, XCSG.ArrayElementType).reverse(objectType).roots().nodes(XCSG.Variable);
	}
	
	/**
	 * Converts an {@link AtlasSet} of {@link GraphElement}s to a {@link AtlasList} of {@link GraphElement}s.
	 * 
	 * @param set An {@link AtlasSet} of {@link GraphElement}s.
	 * @return A {@link AtlasList} of {@link GraphElement}s.
	 */
	public static AtlasList<? extends GraphElement> toAtlasList(AtlasSet<? extends GraphElement> set){
		AtlasList<GraphElement> list = new AtlasArrayList<GraphElement>();
		for(GraphElement element : set){
			list.add(element);
		}
		return list;
	}
	
	/**
	 * Serializes the list of {@link Node} by concatenating its {@link XCSG#name} attribute.
	 * 
	 * @param nodes A list of {@link Node}s.
	 * @return A {@link String} of concatenated {@link XCSG#name} for the {@link Node}s in <code>nodes</code>.
	 */	
	public static String serialize(Iterable<? extends Node> nodes){
		StringBuilder stringBuilder = new StringBuilder();
		for(Node node : nodes){
			stringBuilder.append(node.getAttr(XCSG.name) + "##");
		}
		return stringBuilder.toString();
	}
	
	/**
	 * Finds all containing {@link XCSG#Node} tagged with <code>containingTag</code> along the {@link XCSG#Contains} edges.
	 * 
	 * @param nodes The nodes for which the container to be found.
	 * @param containingTag The tag for the containing nodes to be found.
	 * @return A {@link Q} of nodes containing <code>nodes</code>.
	 */
	public static Q getContainingNodes(Q nodes, String containingTag) {
		AtlasSet<Node> nodeSet = nodes.eval().nodes();
		AtlasSet<Node> containingNodes = new AtlasHashSet<Node>();
		for (Node currentNode : nodeSet) {
			Node containingNode = CommonQueries.getContainingNode(currentNode, containingTag);
			if (containingNode != null){
				containingNodes.add(containingNode);
			}
		}
		return Common.toQ(containingNodes);
	}
	
	/**
	 * Finds a list of direct {@link Edge}s from <code>from</code> node to </code>to</code> node in the given <code>graph</code>.
	 * 
	 * @param graph An instance of {@link Graph} to be used to find edges.
	 * @param from An instance of {@link Node}.
	 * @param to An instance of {@link Node}.
	 * @return A list of {@link Edge}s or empty list if none is found.
	 */
	public static AtlasList<Edge> findDirectEdgesBetweenNodes(Graph graph, Node from, Node to){
		AtlasList<Edge> result = new AtlasArrayList<Edge>();
		AtlasSet<Edge> edges = graph.edges(from, NodeDirection.OUT);
		for(Edge edge : edges){
			Node node = edge.getNode(EdgeDirection.TO);
			if(node.equals(to)){
				result.add(edge);
			}
		}
		return result;
	}
	
	/**
	 * Finds the Matching Pair Graph (MPG) for the given <code>callSites</code> given the <code>lockFunctionCallsQ</code> and <code>unlockFunctionCallsQ</code>.
	 * 
	 * @param callSites The {@link XCSG#CallSite} for the lock/unlock function calls. 
	 * @param lockFunctionCallsQ A {@link Q} for the lock function calls.
	 * @param unlockFunctionCallsQ A {@link Q} for the unlock function calls.
	 * @return A {@link Q} corresponding to the MPG.
	 */
	public static Q mpg(Q callSites, Q lockFunctionCallsQ, Q unlockFunctionCallsQ){
		AtlasSet<Node> callSitesNodes = callSites.eval().nodes();
		HashMap<Node, HashMap<String, AtlasSet<Node>>> functionMap = new HashMap<Node, HashMap<String,AtlasSet<Node>>>(); 
		for(Node node : callSitesNodes){
			Node targetForCallSite = CallSiteAnalysis.getTargets(node).one();
			Q targetForCallSiteQ = Common.toQ(targetForCallSite);
			
			Node containingFunctionNode = CommonQueries.getContainingFunction(node);
			
			boolean callingLock = false;
			boolean callingUnlock = false;
			if(!lockFunctionCallsQ.intersection(targetForCallSiteQ).eval().nodes().isEmpty()){
				callingLock = true;
			}
			
			if(!unlockFunctionCallsQ.intersection(targetForCallSiteQ).eval().nodes().isEmpty()){
				callingUnlock = true;
			}
			
			if(callingLock || callingUnlock){
				HashMap<String, AtlasSet<Node>> luMap = new HashMap<String, AtlasSet<Node>>();
				
				if(functionMap.containsKey(containingFunctionNode)){
					luMap = functionMap.get(containingFunctionNode);
				}
				
				if(callingLock){
					AtlasSet<Node> callL = new AtlasHashSet<Node>();
					if(luMap.containsKey("L")){
						callL = luMap.get("L");
					}
					callL.add(node);
					luMap.put("L", callL);
				}
				
				if(callingUnlock){
					AtlasSet<Node> callU = new AtlasHashSet<Node>();
					if(luMap.containsKey("U")){
						callU = luMap.get("U");
					}
					callU.add(node);
					luMap.put("U", callU);
				}
				functionMap.put(containingFunctionNode, luMap);
			}
		}
			
		AtlasSet<Node> callL = new AtlasHashSet<Node>();
		AtlasSet<Node> callU = new AtlasHashSet<Node>();
		AtlasSet<Node> unbalanced = new AtlasHashSet<Node>();
		
		for(Node f : functionMap.keySet()){
			HashMap<String, AtlasSet<Node>> nodesMap = functionMap.get(f);
			if(nodesMap.size() == 1 && nodesMap.keySet().contains("L")){
				callL.add(f);
				continue;
			}
			
			if(nodesMap.size() == 1 && nodesMap.keySet().contains("U")){
				callU.add(f);
				continue;
			}
			
			callL.add(f);
			callU.add(f);
			
			AtlasSet<Node> lNodes = nodesMap.get("L");
			List<Integer> ls = new ArrayList<Integer>();
			for(Node l : lNodes){
				SourceCorrespondence sc = (SourceCorrespondence) l.attr().get(XCSG.sourceCorrespondence);
				ls.add(sc.offset);
			}
			AtlasSet<Node> uNodes = nodesMap.get("U");
			List<Integer> us = new ArrayList<Integer>();
			for(Node u : uNodes){
				SourceCorrespondence sc = (SourceCorrespondence) u.attr().get(XCSG.sourceCorrespondence);
				us.add(sc.offset);
			}
			
			Collections.sort(ls);
			Collections.sort(us);
			
			if(us.get(us.size() - 1) <= ls.get(ls.size() - 1)){
				unbalanced.add(f);
				callU.remove(f);
			}
		}
		Q callEdgesContext = Common.resolve(null, Common.universe().edges(XCSG.Call));
		Q callLQ = Common.toQ(callL);
		Q callUQ = Common.toQ(callU);
		//Q callLU = callLQ.intersection(callUQ);
		Q rcg_lock = callEdgesContext.reverse(callLQ);
		Q rcg_unlock = callEdgesContext.reverse(callUQ);
		Q rcg_both = rcg_lock.intersection(rcg_unlock);
		Q rcg_c = rcg_lock.union(rcg_unlock);
		Q rcg_lock_only = rcg_lock.difference(rcg_both);
		Q rcg_unlock_only = rcg_unlock.difference(rcg_both);
		Q call_lock_only = callLQ.union(callEdgesContext.reverseStep(rcg_lock_only));
		Q call_unlock_only = callUQ.union(callEdgesContext.reverseStep(rcg_unlock_only));
		Q call_c_only = call_lock_only.union(call_unlock_only);
		Q balanced = call_c_only.intersection(rcg_both);
		Q ubc = balanced.union(rcg_lock_only, rcg_unlock_only);
		Q mpg = rcg_c.intersection(callEdgesContext.forward(ubc));
		
		// Filtration for the MPG
		Q lockUnlockFunctionCallsQ = lockFunctionCallsQ.union(unlockFunctionCallsQ);
		mpg = mpg.union(lockUnlockFunctionCallsQ);
		mpg = mpg.induce(universe().edges(XCSG.Call));
		Q toRemoveEdges = mpg.edges(XCSG.Call).forwardStep(lockUnlockFunctionCallsQ).edges(XCSG.Call);
		mpg = mpg.differenceEdges(toRemoveEdges);
		Q unused = mpg.roots().intersection(mpg.leaves());
		mpg = mpg.difference(unused);
		return mpg;
	}
	
	/**
	 * Finds whether the graph embodied by the given <code>q</code> is acyclic.
	 * 
	 * @param q {@link Q} containing a graph to be tested.
	 * @return true if the graph embodied by <code>q</code> is acyclic, otherwise false.
	 */
	public static boolean isDirectedAcyclicGraph(Q q){
		return (topologicalSort(q) != null);
	}

	/**
	 * Finds a topological sorted list of nodes in the graph embodies by <code>q</code>.
	 * 
	 * @param q A {@link Q} containing the graph of nodes to be sorted.
	 * @return A topologically sorted list of nodes in <code>q<code> or null if the graph is cyclic.
	 */
	public static AtlasList<Node> topologicalSort(Q q){
		Graph graph = q.eval();
		LinkedHashSet<Node> seenNodes = new LinkedHashSet<Node>(); 
		AtlasList<Node> exploredNodes = new AtlasArrayList<Node>();
		
		for(Node v : graph.nodes()){
			if(!exploredNodes.contains(v)){
				if(!DFS(q, seenNodes, exploredNodes, v)){
					return null;
				}
			}
		}
		return exploredNodes;
	}
	
	/**
	 * A Depth First Search (DFS) traversal from the node <code>v</code> given the <code>exploredNodes</code> and <code>seenNodes</code> along the path.
	 * 
	 * @param q A {@link Q} containing the graph to be traversed.
	 * @param seenNodes A list of {@link Node} seen before the <code>v</code> along the path.
	 * @param exploredNodes A list of {@link Node} completely explored nodes before along the path to <code>v</code>
	 * @param v A {@link Node} from which the DFS traversal will start.
	 * @return true if DFS successes to traversal to the leaves of the graphs without encountering any <code>exploredNodes</code> and <code>seenNodes</code>.
	 */
	private static boolean DFS(Q q, LinkedHashSet<Node> seenNodes, AtlasList<Node> exploredNodes, Node v){
		seenNodes.add(v);
		AtlasSet<Node> successors = q.successors(Common.toQ(v)).eval().nodes();
		for(Node node: successors){
			if(!seenNodes.contains(node)){
				if(!DFS(q, seenNodes, exploredNodes, node))
					return false;
			}
			else if(seenNodes.contains(node) && !exploredNodes.contains(node)){
				return false;
			}
		}
		exploredNodes.add(0, v);
		return true;
	}
	
	/**
	 * Eliminates cycles from the graph embedded in <code>q</code>.
	 * 
	 * @param q A {@link Q} containing a graph to be processed.
	 * @return A {@link Graph} without cycles.
	 */
	public static Graph cutCyclesFromGraph(Q q){
		Graph graph = q.eval();
		// First: remove self-loop edges
		Q selfLoopQ = Common.empty();
		for(Node node : graph.nodes()){
			Edge selfLoopEdge = null;
			AtlasSet<Edge> outEdges = graph.edges(node, NodeDirection.OUT);
			for(Edge edge : outEdges){
				if(edge.to().equals(node)){
					selfLoopEdge = edge;
					break;
				}
			}
			if(selfLoopEdge != null){
				Q selfLoopNode = Common.toQ(node);
				selfLoopNode = selfLoopNode.induce(Common.universe().edges(XCSG.Call));
				selfLoopQ = selfLoopQ.union(selfLoopNode);
			}
		}
		Q graphQ = Common.toQ(graph);
		graphQ = graphQ.differenceEdges(selfLoopQ);
		graph = graphQ.eval();
		
		// Second: cut the loop from the last node visited to the newly node visited
		for(Node node : Common.toQ(graph.roots()).eval().nodes()){
			Q backEdgedNodes = null;
			do{
				backEdgedNodes = cutLoopsInGraph(graphQ, node, new ArrayList<Node>());
				graphQ = Common.toQ(graph);
				graphQ = graphQ.differenceEdges(backEdgedNodes);
				graph = graphQ.eval();
			}while(!backEdgedNodes.eval().nodes().isEmpty());
		}
		return graph;
	}
	
	/**
	 * Eliminates loops from the graph embedded in <code>q</code> on the given <code>path</code>.
	 * 
	 * @param q A {@link Q} containing the <code>path<code> that has the loop to be eliminated.
	 * @param node A {@link Node} rooted at the loop.
	 * @param path A list of {@link Node}s containing the loop.
	 * @return A {@link Q} modified from the given <code>q</code> without a loop along the given <code>path</code>.
	 */
	private static Q cutLoopsInGraph(Q q, Node node, ArrayList<Node> path){
		Q backEdgeQ = Common.empty();
		path.add(node);
		AtlasSet<Node> successors = q.successors(Common.toQ(node)).eval().nodes();
		for(Node child : successors){
			if(path.contains(child)){
				Q nodeQ = Common.toQ(Common.toGraph(node));
				Q childQ = Common.toQ(Common.toGraph(child));
				Q bothNodes = nodeQ.union(childQ).induce(Common.universe().edges(XCSG.Call));
				Q backEdgedNodes = Common.universe().edges(XCSG.Call).forwardStep(nodeQ).intersection(bothNodes);
				backEdgeQ = backEdgeQ.union(backEdgedNodes);
				return backEdgeQ;
			}
			backEdgeQ = backEdgeQ.union(cutLoopsInGraph(q, child, new ArrayList<Node>(path)));
		}
		return backEdgeQ;
	}
	
	/**
	 * Finds the events of interest in the given <code>cfg</code> based on <code>mpgFunctions</code>, <code>lockFunctionCalls</code> and <code>unlockFunctionCalls</code>.
	 * 
	 * @param cfg A {@link Q} corresponding to the Control Flow Graph of a function.
	 * @param cfgNodesContainingEventsQ A {@link Q} of {@link XCSG#ControlFlow_Node}s containing events of interest.
	 * @param mpgFunctions A {@link Q} of {@link XCSG#Function} contained within the MPG.
	 * @param lockFunctionCalls A {@link Q} of lock function calls.
	 * @param unlockFunctionCalls A {@link Q} of unlock function calls.
	 * @return A list of {@link Q}s where the first element contains the events calling lock, the second element contains the events calls unlock, 
	 * the third element contains calls to MPG functions, the last element contains all events.
	 */
	public static List<Q> compileCFGNodesContainingEventNodes(Q cfg, Q cfgNodesContainingEventsQ, Q mpgFunctions, Q lockFunctionCalls, Q unlockFunctionCalls){
		Q cfgNodesQ = cfg.nodes(XCSG.ControlFlow_Node);
		Q callSitesQ = universe().edges(XCSG.Contains).forward(cfgNodesQ).nodes(XCSG.CallSite);
		AtlasSet<Node> callSitesNodes = callSitesQ.eval().nodes();
		Q lockEvents = Common.empty();
		Q unlockEvents = Common.empty();
		Q mpgFunctionCallEvents = Common.empty();
		
		AtlasSet<Node> cfgNodesContainingEvents = cfgNodesContainingEventsQ.eval().nodes();
		for(Node node : callSitesNodes){
			Node callSiteTargetFunction = CallSiteAnalysis.getTargets(node).one();
			Node controlFlowNode = CommonQueries.getContainingControlFlowNode(node);
			Q controlFlowNodeQ = Common.toQ(controlFlowNode);
			if(cfgNodesContainingEvents.contains(controlFlowNode)){
				if(lockFunctionCalls.eval().nodes().contains(callSiteTargetFunction)) {
					lockEvents = lockEvents.union(controlFlowNodeQ);
				}
				
				if(unlockFunctionCalls.eval().nodes().contains(callSiteTargetFunction)){
					unlockEvents = unlockEvents.union(controlFlowNodeQ);
				}
			}
			
			if(mpgFunctions.eval().nodes().contains(callSiteTargetFunction)){
				mpgFunctionCallEvents = mpgFunctionCallEvents.union(controlFlowNodeQ);
			}
		}
		List<Q> result = new ArrayList<Q>();
		result.add(lockEvents);
		result.add(unlockEvents);
		result.add(mpgFunctionCallEvents);
		result.add(lockEvents.union(unlockEvents, mpgFunctionCallEvents));
		return result;
	}
	
}
