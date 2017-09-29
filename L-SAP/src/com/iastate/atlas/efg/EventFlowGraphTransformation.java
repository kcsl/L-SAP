package com.iastate.atlas.efg;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.HashMap;
import java.util.HashSet;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.iastate.atlas.dominator.ControlFlowGraph;
import com.iastate.atlas.dominator.DominanceAnalysis;
import com.iastate.atlas.dominator.DominanceAnalysis.Multimap;

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
	private AtlasSet<GraphElement> nodes;
	
	/**
	 * The edges in the EFG.
	 * Initially the edges in the given CFG.
	 * Edges may be removed or inserted based on the events of interest.
	 */
	private AtlasSet<GraphElement> edges;
	
	/**
	 * The given event nodes for constructing the EFG.
	 * 
	 */
	private AtlasSet<GraphElement> eventNodes;
	
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
	
	public EventFlowGraphTransformation(Graph g, AtlasSet<GraphElement> eventNodes) {
		// Ahmed: Undo previous EFG transformations on this graph (if any)
		// We should have a better way to index EFG edges by introducing an edge address that is associated with eventNodes
		this.undoPreviousEFGTransformation(g);
		this.cfg = new ControlFlowGraph(g);
		this.nodes = new AtlasHashSet<GraphElement>(this.cfg.getGraph().nodes());
		this.edges = new AtlasHashSet<GraphElement>(this.cfg.getGraph().edges());
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
		AtlasSet<GraphElement> rootCFGNodes = g.nodes().taggedWithAll(XCSG.controlFlowRoot);
		Q masterEntryGraph = cfgEdges.reverseStep(Common.toQ(rootCFGNodes));
		Q masterEntryNodes = masterEntryGraph.nodesTaggedWithAll(ControlFlowGraph.CFG_MASTER_ENTRY_NODE);
		Q masterEntryEdges = masterEntryGraph.edgesTaggedWithAll(ControlFlowGraph.CFG_ENTRY_EDGE);
		this.deleteElementsFromIndex(masterEntryEdges.eval().edges());
		this.deleteElementsFromIndex(masterEntryNodes.eval().nodes());
		
		// Remove nodes tagged with "CFG_MASTER_EXIT_NODE"
		// Remove edges tagged with "CFG_EXIT_EDGE"
		AtlasSet<GraphElement> exitCFGNodes = g.nodes().taggedWithAll(XCSG.controlFlowExitPoint);
		Q masterExitGraph = cfgEdges.forwardStep(Common.toQ(exitCFGNodes));
		Q masterExitNodes = masterExitGraph.nodesTaggedWithAll(ControlFlowGraph.CFG_MASTER_EXIT_NODE);
		Q masterExitEdges = masterExitGraph.edgesTaggedWithAll(ControlFlowGraph.CFG_EXIT_EDGE);
		this.deleteElementsFromIndex(masterExitEdges.eval().edges());
		this.deleteElementsFromIndex(masterExitNodes.eval().nodes());
		
		AtlasSet<GraphElement> efgNodes = g.nodes().taggedWithAll(EventFlowGraphTransformation.EFG_NODE);
		Q efgGraph = universe().edgesTaggedWithAll(EventFlowGraphTransformation.EFG_EDGE).forward(Common.toQ(efgNodes)); 		
		
		// Remove edges tagged with "NEW_EFG_EDGE"
		AtlasSet<GraphElement> newEFGEdges = efgGraph.edgesTaggedWithAny(EventFlowGraphTransformation.NEW_EFG_EDGE).eval().edges();
		this.deleteElementsFromIndex(newEFGEdges);
		
		// Remove tag "EFG_EDGE" from all edges
		AtlasSet<GraphElement> efgEdges = efgGraph.edgesTaggedWithAll(EventFlowGraphTransformation.EFG_EDGE).eval().edges();
		for(GraphElement edge : efgEdges){
			edge.untag(EventFlowGraphTransformation.EFG_EDGE);
		}
		
		// Remove tag "EFG_NODE" from all nodes
		for(GraphElement node : efgNodes){
			node.untag(EventFlowGraphTransformation.EFG_NODE);
		}
	}
	
	private void deleteElementsFromIndex(AtlasSet<GraphElement> elements){
		for(GraphElement element : elements){
			Graph.U.delete(element);
		}
	}
	
	/**
	 * Gets incoming edges to node
	 * @param node
	 * @return
	 */
	private AtlasSet<GraphElement> getInEdges(GraphElement node){
		AtlasSet<GraphElement> inEdges = new AtlasHashSet<GraphElement>();
		for(GraphElement edge : edges){
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
	private AtlasSet<GraphElement> getOutEdges(GraphElement node){
		AtlasSet<GraphElement> outEdges = new AtlasHashSet<GraphElement>();
		for(GraphElement edge : edges){
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
		AtlasSet<GraphElement> nodesToRetain = this.performDominanceAnalysis(true);
		for(GraphElement node : nodesToRetain){
			node.tag(EFG_NODE);
		}

		// Retain a stack of node that are consumed to be removed from the graph after the loop
		AtlasSet<GraphElement> toRemoveNodes = new AtlasHashSet<GraphElement>();
		for(GraphElement node : this.nodes){
			if(nodesToRetain.contains(node)){
				this.retainNode(node);
			}else{
				this.consumeNode(node);
				toRemoveNodes.add(node);
			}
			this.removeDoubleEdges(node);
		}
		
		// Remove the consumed nodes in the previous loop
		for(GraphElement node : toRemoveNodes){
			this.nodes.remove(node);
		}
		
		for(GraphElement node : this.nodes.taggedWithAll(EFG_NODE)){
			this.removeDoubleEdges(node);
		}
		
		AtlasSet<GraphElement> ns = new AtlasHashSet<GraphElement>();
		ns.addAll(this.nodes.taggedWithAll(EFG_NODE));
		
		
		// assert: edges only refer to nodes which are tagged EFG_NODE 
		AtlasSet<GraphElement> es = new AtlasHashSet<GraphElement>();
		for (GraphElement edge : this.edges) {
			if (ns.contains(edge.getNode(EdgeDirection.FROM))
				&& ns.contains(edge.getNode(EdgeDirection.TO)) ) {
				es.add(edge);
			}
		}
		
		Q efg = Common.toQ(new UncheckedGraph(ns, es));

		return efg;
	}
	
	private void retainNode(GraphElement node){
		AtlasSet<GraphElement> inEdges = this.getInEdges(node);
		for(GraphElement inEdge : inEdges){
			GraphElement predecessor = inEdge.getNode(EdgeDirection.FROM);
			if(predecessor.taggedWith(EFG_NODE)){
				inEdge.tag(EFG_EDGE);
			}
		}
		
		AtlasSet<GraphElement> outEdges = this.getOutEdges(node);
		for(GraphElement outEdge : outEdges){
			GraphElement successor = outEdge.getNode(EdgeDirection.TO);
			if(successor.taggedWith(EFG_NODE)){
				outEdge.tag(EFG_EDGE);
			}
		}
	}
	
	private void consumeNode(GraphElement node){
		// This function will consume the given node by bypassing it through connecting its predecessors with successors
		// while preserving edges contents especially for branches.
		
		// First: get the predecessors for the node
		AtlasSet<GraphElement> inEdges = this.getInEdges(node);
		HashMap<GraphElement, GraphElement> predecessorEdgeMap = new HashMap<GraphElement, GraphElement>(); 
		for(GraphElement inEdge : inEdges){
			GraphElement predecessor = inEdge.getNode(EdgeDirection.FROM);
			predecessorEdgeMap.put(predecessor, inEdge);
		}
		// Remove the case where the node has a self-loop. This will cause infinite recursion
		predecessorEdgeMap.keySet().remove(node);
		
		// Second: get the successors for the node
		AtlasSet<GraphElement> outEdges = this.getOutEdges(node);
		AtlasSet<GraphElement> successors = new AtlasHashSet<GraphElement>();
		for(GraphElement outEdge : outEdges){
			GraphElement successor = outEdge.getNode(EdgeDirection.TO);
			successors.add(successor);
		}
		// Remove the case where the node has a self-loop. This will cause infinite recursion
		successors.remove(node);
		
		for(GraphElement predecessor : predecessorEdgeMap.keySet()){
			for(GraphElement successor : successors){
				GraphElement newEdge = Graph.U.createEdge(predecessor, successor);
				GraphElement oldEdge = predecessorEdgeMap.get(predecessor);
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
		for(GraphElement inEdge : inEdges){
			this.edges.remove(inEdge);
		}
		
		// Remove original outEdges for the node
		for(GraphElement outEdge : outEdges){
			this.edges.remove(outEdge);
		}
	}
	
	private void removeDoubleEdges(GraphElement node){
		AtlasSet<GraphElement> outEdges = this.getOutEdges(node);
		AtlasSet<GraphElement> successors = new AtlasHashSet<GraphElement>();
		for(GraphElement outEdge : outEdges){
			GraphElement successor = outEdge.getNode(EdgeDirection.TO);
			if(successors.contains(successor)){
				this.edges.remove(outEdge);
			}else{
				successors.add(successor);
			}
		}
	}
	
	private AtlasSet<GraphElement> performDominanceAnalysis(boolean postDominance){
		DominanceAnalysis domTree = new DominanceAnalysis(this.cfg, postDominance);
		Multimap<GraphElement> domFront = domTree.getDominanceFrontiers();
		
		AtlasSet<GraphElement> elements = new AtlasHashSet<GraphElement>(this.eventNodes);
		AtlasSet<GraphElement> newElements = null;
		long preSize = 0;
		do{
			preSize = elements.size();
			newElements = new AtlasHashSet<GraphElement>();
			for(GraphElement element : elements){
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
		AtlasSet<GraphElement> edges = universe().edgesTaggedWithAll(EventFlowGraphTransformation.EFG_EDGE).eval().edges();
		for(GraphElement edge : edges){
			edge.untag(EventFlowGraphTransformation.EFG_EDGE);
		}
		
		// Remove tag "EFG_NODE" from all nodes
		AtlasSet<GraphElement> nodes = universe().nodesTaggedWithAll(EventFlowGraphTransformation.EFG_NODE).eval().nodes();
		for(GraphElement node : nodes){
			node.untag(EventFlowGraphTransformation.EFG_NODE);
		}
		
		// Remove edges tagged with "CFG_ENTRY_EDGE"
		// Remove edges tagged with "CFG_EXIT_EDGE"
		// Remove edges tagged with "NEW_EFG_EDGE"
		edges = universe().edgesTaggedWithAny(ControlFlowGraph.CFG_ENTRY_EDGE, ControlFlowGraph.CFG_EXIT_EDGE, EventFlowGraphTransformation.NEW_EFG_EDGE).eval().edges();
		HashSet<GraphElement> toDelete = new HashSet<GraphElement>(); 
		for(GraphElement edge : edges){
			toDelete.add(edge);
		}
		
		for(GraphElement edge : toDelete){
			Graph.U.delete(edge);
		}
		
		// Remove nodes tagged with "CFG_MASTER_ENTRY_NODE"
		// Remove nodes tagged with "CFG_MASTER_EXIT_NODE"
		nodes = universe().nodesTaggedWithAny(ControlFlowGraph.CFG_MASTER_ENTRY_NODE, ControlFlowGraph.CFG_MASTER_EXIT_NODE).eval().nodes();
		toDelete = new HashSet<GraphElement>();
		for(GraphElement node : nodes){
			toDelete.add(node);
		}
		
		for(GraphElement node : toDelete){
			Graph.U.delete(node);
		}
	}
	
	public ControlFlowGraph getCFG(){
		return this.cfg;
		
	}
}