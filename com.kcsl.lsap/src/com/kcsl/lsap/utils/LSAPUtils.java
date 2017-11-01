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

public class LSAPUtils {
	
	public static void log(String message){
		try {
			VerificationProperties.getOutputLogFileWriter().write(message + "\n");
			VerificationProperties.getOutputLogFileWriter().flush();
		} catch (IOException e) {
			System.err.println("Cannot write to log file.");
		}
	}
	
	public static Q functionsQ(List<String> functionsList){
		return Common.toQ(functions(functionsList));
	}
	
	public static AtlasSet<Node> functions(List<String> functionsList){
		return CommonQueries.functions((String[])functionsList.toArray()).eval().nodes();
	}
	
	public static Q getSignaturesForObjectType(Q objectType){
		return universe().edges(XCSG.TypeOf, XCSG.ReferencedType, XCSG.ArrayElementType).reverse(objectType).roots().nodes(XCSG.Variable);
	}
	
	public static AtlasList<? extends GraphElement> toAtlasList(AtlasSet<? extends GraphElement> set){
		AtlasList<GraphElement> list = new AtlasArrayList<GraphElement>();
		for(GraphElement element : set){
			list.add(element);
		}
		return list;
	}
	
	public static String toString(AtlasList<Node> path){
		StringBuilder result = new StringBuilder();
		for(int i = 0; i < path.size(); i++){
			result.append(path.get(i).getAttr(XCSG.name));
			if(i < path.size() - 1){
				result.append(" >>> ");
			}
		}
		result.append("\n");
		return result.toString();
	}
	
	public static String serialize(AtlasSet<Node> nodes){
		StringBuilder stringBuilder = new StringBuilder();
		for(Node node : nodes){
			stringBuilder.append(node.getAttr(XCSG.name) + "##");
		}
		return stringBuilder.toString();
	}
	
	public static Q getContainingNodes(Q nodes, String containingTag) {
		AtlasSet<Node> nodeSet = nodes.eval().nodes();
		AtlasSet<Node> containingMethods = new AtlasHashSet<Node>();
		for (Node currentNode : nodeSet) {
			Node function = CommonQueries.getContainingNode(currentNode, containingTag);
			if (function != null){
				containingMethods.add(function);
			}
		}
		return Common.toQ(Common.toGraph(containingMethods));
	}
	
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
	
	public static Q loopFreeCFG(Q function){
		Q cfg = CommonQueries.cfg(function);
		Q cfgBackEdges = cfg.edges(XCSG.ControlFlowBackEdge);
		return cfg.differenceEdges(cfgBackEdges);
	}
	
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
	
	public static boolean isDirectedAcyclicGraph(Q q){
		return (topologicalSort(q) != null);
	}

	public static List<Node> topologicalSort(Q q){
		Graph graph = q.eval();
		LinkedHashSet<Node> seenNodes = new LinkedHashSet<Node>(); 
		List<Node> exploredNodes = new ArrayList<Node>();
		
		for(Node v : graph.nodes()){
			if(!exploredNodes.contains(v)){
				if(!DFS(q, seenNodes, exploredNodes, v)){
					return null;
				}
			}
		}
		return exploredNodes;
	}
	
	private static boolean DFS(Q q, LinkedHashSet<Node> seenNodes, List<Node> exploredNodes, Node v){
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
