package com.kcsl.lsap.efg.deprecated;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.HashMap;
import java.util.HashSet;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.kcsl.lsap.dominance.deprecated.ControlFlowGraph;
import com.kcsl.lsap.dominance.deprecated.DominanceAnalysis;
import com.kcsl.lsap.dominance.deprecated.DominanceAnalysis.Multimap;

/**
 * A class that implements the event flow graph transformations to transform a given CFG into EFG
 * @author Ahmed Tamrawi
 *
 */
public class EventFlowGraphTransformation{
	
	/**
	 * All nodes in the given CFG.
	 * Superset of the nodes in the EFG.
	 * Nodes which are retained by the EFG are tagged EFG_NODE.
	 */
	private AtlasSet<Node> nodes;
	
	/**
	 * The edges in the EFG.
	 * Initially the edges in the given CFG.
	 * Edges may be removed or inserted based on the events of interest.
	 */
	private AtlasSet<Edge> edges;
	
	/**
	 * The given event nodes for constructing the EFG.
	 * 
	 */
	private AtlasSet<Node> eventNodes;
	
	/**
	 * The CFG to operate on
	 */
	private ControlFlowGraph cfg;
	
	/**
	 * Tag applied to a node retained in the EFG
	 */
	public static final String EFG_NODE = "EFG_NODE";
	
	/**
	 * Tag applied to an edge retained in the EFG
	 */
	public static final String EFG_EDGE = "EFG_EDGE";
	
	/**
	 * Tag applied to an edge newly created in the EFG 
	 */
	public static final String NEW_EFG_EDGE = "NEW_EFG_EDGE";
	
	public EventFlowGraphTransformation(Graph g, AtlasSet<Node> eventNodes) {
		// Ahmed: Undo previous EFG transformations on this graph (if any)
		// We should have a better way to index EFG edges by introducing an edge address that is associated with eventNodes
		this.undoPreviousEFGTransformation(g);
		this.cfg = new ControlFlowGraph(g);
		this.nodes = new AtlasHashSet<Node>(this.cfg.getGraph().nodes());
		this.edges = new AtlasHashSet<Edge>(this.cfg.getGraph().edges());
		this.eventNodes = eventNodes;
	}
	/**
	 * Undo previous EFG transformations on the given graph
	 * @param g
	 */
	private void undoPreviousEFGTransformation(Graph g){
		Q cfgEdges = universe().edgesTaggedWithAll(XCSG.ControlFlow_Edge);
		
		// Remove nodes tagged with "CFG_MASTER_ENTRY_NODE"
		// Remove edges tagged with "CFG_ENTRY_EDGE"
		AtlasSet<Node> rootCFGNodes = g.nodes().taggedWithAll(XCSG.controlFlowRoot);
		Q masterEntryGraph = cfgEdges.reverseStep(Common.toQ(rootCFGNodes));
		Q masterEntryNodes = masterEntryGraph.nodesTaggedWithAll(ControlFlowGraph.CFG_MASTER_ENTRY_NODE);
		Q masterEntryEdges = masterEntryGraph.edgesTaggedWithAll(ControlFlowGraph.CFG_ENTRY_EDGE);
		this.deleteEdgesFromIndex(masterEntryEdges.eval().edges());
		this.deleteNodesFromIndex(masterEntryNodes.eval().nodes());
		
		// Remove nodes tagged with "CFG_MASTER_EXIT_NODE"
		// Remove edges tagged with "CFG_EXIT_EDGE"
		AtlasSet<Node> exitCFGNodes = g.nodes().taggedWithAll(XCSG.controlFlowExitPoint);
		Q masterExitGraph = cfgEdges.forwardStep(Common.toQ(exitCFGNodes));
		Q masterExitNodes = masterExitGraph.nodesTaggedWithAll(ControlFlowGraph.CFG_MASTER_EXIT_NODE);
		Q masterExitEdges = masterExitGraph.edgesTaggedWithAll(ControlFlowGraph.CFG_EXIT_EDGE);
		this.deleteEdgesFromIndex(masterExitEdges.eval().edges());
		this.deleteNodesFromIndex(masterExitNodes.eval().nodes());
		
		AtlasSet<Node> efgNodes = g.nodes().taggedWithAll(EventFlowGraphTransformation.EFG_NODE);
		Q efgGraph = universe().edgesTaggedWithAll(EventFlowGraphTransformation.EFG_EDGE).forward(Common.toQ(efgNodes)); 		
		
		// Remove edges tagged with "NEW_EFG_EDGE"
		AtlasSet<Edge> newEFGEdges = efgGraph.edgesTaggedWithAny(EventFlowGraphTransformation.NEW_EFG_EDGE).eval().edges();
		this.deleteEdgesFromIndex(newEFGEdges);
		
		// Remove tag "EFG_EDGE" from all edges
		AtlasSet<Edge> efgEdges = efgGraph.edgesTaggedWithAll(EventFlowGraphTransformation.EFG_EDGE).eval().edges();
		for(Edge edge : efgEdges){
			edge.untag(EventFlowGraphTransformation.EFG_EDGE);
		}
		
		// Remove tag "EFG_NODE" from all nodes
		for(Node node : efgNodes){
			node.untag(EventFlowGraphTransformation.EFG_NODE);
		}
	}
	
	private void deleteNodesFromIndex(AtlasSet<Node> elements){
		for(Node element : elements){
			Graph.U.delete(element);
		}
	}
	
	private void deleteEdgesFromIndex(AtlasSet<Edge> elements){
		for(Edge element : elements){
			Graph.U.delete(element);
		}
	}
	
	/**
	 * Gets incoming edges to node
	 * @param node
	 * @return
	 */
	private AtlasSet<Edge> getInEdges(Node node){
		AtlasSet<Edge> inEdges = new AtlasHashSet<Edge>();
		for(Edge edge : edges){
			if(edge.getNode(EdgeDirection.TO).equals(node)){
				inEdges.add(edge);
			}
		}
		return inEdges;
	}
	
	/**
	 * Gets out-coming edges from node
	 * @param node
	 * @return
	 */
	private AtlasSet<Edge> getOutEdges(Node node){
		AtlasSet<Edge> outEdges = new AtlasHashSet<Edge>();
		for(Edge edge : edges){
			if(edge.getNode(EdgeDirection.FROM).equals(node)){
				outEdges.add(edge);
			}
		}
		return outEdges;
	}
	
	/**
	 * Given a CFG, construct EFG
	 * @return
	 */
	public Q constructEFG(){
		AtlasSet<Node> nodesToRetain = this.performDominanceAnalysis(true);
		for(Node node : nodesToRetain){
			node.tag(EFG_NODE);
		}

		// Retain a stack of node that are consumed to be removed from the graph after the loop
		AtlasSet<Node> toRemoveNodes = new AtlasHashSet<Node>();
		for(Node node : this.nodes){
			if(nodesToRetain.contains(node)){
				this.retainNode(node);
			}else{
				this.consumeNode(node);
				toRemoveNodes.add(node);
			}
			this.removeDoubleEdges(node);
		}
		
		// Remove the consumed nodes in the previous loop
		for(Node node : toRemoveNodes){
			this.nodes.remove(node);
		}
		
		for(Node node : this.nodes.taggedWithAll(EFG_NODE)){
			this.removeDoubleEdges(node);
		}
		
		AtlasSet<Node> ns = new AtlasHashSet<Node>();
		ns.addAll(this.nodes.taggedWithAll(EFG_NODE));
		
		
		// assert: edges only refer to nodes which are tagged EFG_NODE 
		AtlasSet<Node> es = new AtlasHashSet<Node>();
		for (Edge edge : this.edges) {
			if (ns.contains(edge.getNode(EdgeDirection.FROM))
				&& ns.contains(edge.getNode(EdgeDirection.TO)) ) {
				es.add(edge);
			}
		}
		
		Q efg = Common.toQ(new UncheckedGraph(ns, es));

		return efg;
	}
	
	private void retainNode(Node node){
		AtlasSet<Edge> inEdges = this.getInEdges(node);
		for(Edge inEdge : inEdges){
			Node predecessor = inEdge.getNode(EdgeDirection.FROM);
			if(predecessor.taggedWith(EFG_NODE)){
				inEdge.tag(EFG_EDGE);
			}
		}
		
		AtlasSet<Edge> outEdges = this.getOutEdges(node);
		for(Edge outEdge : outEdges){
			Node successor = outEdge.getNode(EdgeDirection.TO);
			if(successor.taggedWith(EFG_NODE)){
				outEdge.tag(EFG_EDGE);
			}
		}
	}
	
	private void consumeNode(Node node){
		// This function will consume the given node by bypassing it through connecting its predecessors with successors
		// while preserving edges contents especially for branches.
		
		// First: get the predecessors for the node
		AtlasSet<Edge> inEdges = this.getInEdges(node);
		HashMap<Node, Edge> predecessorEdgeMap = new HashMap<Node, Edge>(); 
		for(Edge inEdge : inEdges){
			Node predecessor = inEdge.getNode(EdgeDirection.FROM);
			predecessorEdgeMap.put(predecessor, inEdge);
		}
		// Remove the case where the node has a self-loop. This will cause infinite recursion
		predecessorEdgeMap.keySet().remove(node);
		
		// Second: get the successors for the node
		AtlasSet<Edge> outEdges = this.getOutEdges(node);
		AtlasSet<Node> successors = new AtlasHashSet<Node>();
		for(Edge outEdge : outEdges){
			Node successor = outEdge.getNode(EdgeDirection.TO);
			successors.add(successor);
		}
		// Remove the case where the node has a self-loop. This will cause infinite recursion
		successors.remove(node);
		
		for(Node predecessor : predecessorEdgeMap.keySet()){
			for(Node successor : successors){
				Edge newEdge = Graph.U.createEdge(predecessor, successor);
				Edge oldEdge = predecessorEdgeMap.get(predecessor);
				newEdge.putAllAttr(oldEdge.attr());
				for(String tag : oldEdge.tags()){
					newEdge.tag(tag);
				}
				newEdge.tag(EFG_EDGE);
				newEdge.tag(NEW_EFG_EDGE);
				newEdge.untag(XCSG.ControlFlow_Edge);
				this.edges.add(newEdge);
			}
		}
		
		// Remove original inEdges for the node
		for(Edge inEdge : inEdges){
			this.edges.remove(inEdge);
		}
		
		// Remove original outEdges for the node
		for(Edge outEdge : outEdges){
			this.edges.remove(outEdge);
		}
	}
	
	private void removeDoubleEdges(Node node){
		AtlasSet<Edge> outEdges = this.getOutEdges(node);
		AtlasSet<Node> successors = new AtlasHashSet<Node>();
		for(Edge outEdge : outEdges){
			Node successor = outEdge.getNode(EdgeDirection.TO);
			if(successors.contains(successor)){
				this.edges.remove(outEdge);
			}else{
				successors.add(successor);
			}
		}
	}
	
	private AtlasSet<Node> performDominanceAnalysis(boolean postDominance){
		DominanceAnalysis domTree = new DominanceAnalysis(this.cfg, postDominance);
		Multimap<Node> domFront = domTree.getDominanceFrontiers();
		
		AtlasSet<Node> elements = new AtlasHashSet<Node>(this.eventNodes);
		AtlasSet<Node> newElements = null;
		long preSize = 0;
		do{
			preSize = elements.size();
			newElements = new AtlasHashSet<Node>();
			for(Node element : elements){
				newElements.addAll(domFront.get(element));
			}
			elements.addAll(newElements);
		}while(preSize != elements.size());
		elements.add(this.cfg.getEntryNode());
		elements.add(this.cfg.getExitNode());
		return elements;
	}
	
	/**
	 * Undo all EFG transformations on universe
	 */
	public static void undoAllEFGTransformations(){
		// Remove tag "EFG_EDGE" from all edges
		AtlasSet<Edge> edges = universe().edgesTaggedWithAll(EventFlowGraphTransformation.EFG_EDGE).eval().edges();
		for(Edge edge : edges){
			edge.untag(EventFlowGraphTransformation.EFG_EDGE);
		}
		
		// Remove tag "EFG_NODE" from all nodes
		AtlasSet<Node> nodes = universe().nodesTaggedWithAll(EventFlowGraphTransformation.EFG_NODE).eval().nodes();
		for(Node node : nodes){
			node.untag(EventFlowGraphTransformation.EFG_NODE);
		}
		
		// Remove edges tagged with "CFG_ENTRY_EDGE"
		// Remove edges tagged with "CFG_EXIT_EDGE"
		// Remove edges tagged with "NEW_EFG_EDGE"
		edges = universe().edgesTaggedWithAny(ControlFlowGraph.CFG_ENTRY_EDGE, ControlFlowGraph.CFG_EXIT_EDGE, EventFlowGraphTransformation.NEW_EFG_EDGE).eval().edges();
		HashSet<Edge> toDeleteEdges = new HashSet<Edge>(); 
		for(Edge edge : edges){
			toDeleteEdges.add(edge);
		}
		
		for(Edge edge : toDeleteEdges){
			Graph.U.delete(edge);
		}
		
		// Remove nodes tagged with "CFG_MASTER_ENTRY_NODE"
		// Remove nodes tagged with "CFG_MASTER_EXIT_NODE"
		nodes = universe().nodesTaggedWithAny(ControlFlowGraph.CFG_MASTER_ENTRY_NODE, ControlFlowGraph.CFG_MASTER_EXIT_NODE).eval().nodes();
		HashSet<Node> toDeleteNodes = new HashSet<Node>();
		for(Node node : nodes){
			toDeleteNodes.add(node);
		}
		
		for(Node node : toDeleteNodes){
			Graph.U.delete(node);
		}
	}
	
	public ControlFlowGraph getCFG(){
		return this.cfg;
		
	}
}