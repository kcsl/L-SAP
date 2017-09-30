package com.iastate.atlas.dominator;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

/**
 * A wrapper class that takes in a graph and adds a master entry/exit
 * @author Ahmed Tamrawi
 *
 */
public class ControlFlowGraph {
	

	/**
	 * The updated graph with the master entry/exit nodes
	 */
	private Graph graph;
	
	/**
	 * Tag applied to the master entry node
	 */
	public static final String CFG_MASTER_ENTRY_NODE = "EFG_ENTRY";
	
	/**
	 * Tag applied to the edges from the master entry node
	 */
	public static final String CFG_ENTRY_EDGE = "CFG_ENTRY_EDGE";
	
	/**
	 * Tag applied to the master exit node
	 */
	public static final String CFG_MASTER_EXIT_NODE = "EFG_EXIT";
	
	/**
	 * Tag applied to edges to the master exit node
	 */
	public static final String CFG_EXIT_EDGE = "CFG_EXIT_EDGE";

	private Node masterExitNode;

	private Node masterEntryNode;

	public ControlFlowGraph(Graph g) {
		
		AtlasSet<Node> nodes = new AtlasHashSet<Node>();
		AtlasSet<Edge> edges = new AtlasHashSet<Edge>();
		
		nodes.addAll(g.nodes());
		edges.addAll(g.edges());
		
		
		this.setupMasterEntryNode(g.nodes(), nodes, edges);
		this.setupMasterExitNode(g.nodes(), nodes, edges);
		
		this.graph = new UncheckedGraph(nodes, edges);

	}
	
	/**
	 * Creates the nodes and edges for setting up the master entry node
	 * @param inputNodes
	 * @return the master entry node along with the newly created edges
	 */
	private void setupMasterEntryNode(
			AtlasSet<Node> inputNodes, 
			AtlasSet<Node> outputNodes,
			AtlasSet<Edge> outputEdges){
		
		masterEntryNode = Graph.U.createNode();
		masterEntryNode.attr().put(XCSG.name, CFG_MASTER_ENTRY_NODE);
		masterEntryNode.tag(CFG_MASTER_ENTRY_NODE);
		masterEntryNode.tag(XCSG.ControlFlow_Node);
		outputNodes.add(masterEntryNode);
		
		Edge newEdge = Graph.U.createEdge(masterEntryNode, inputNodes.taggedWithAny(XCSG.controlFlowRoot).getFirst());
		newEdge.tag(XCSG.ControlFlow_Edge);
		newEdge.tag(CFG_ENTRY_EDGE);
		outputEdges.add(newEdge);
		
	}
	
	/**
	 * Creates the nodes and edges for setting up the master exit node
	 * @param inputNodes
	 * @return the master exit node along with the newly created edges
	 */
	private void setupMasterExitNode(
			AtlasSet<Node> inputNodes, 
			AtlasSet<Node> outputNodes,
			AtlasSet<Edge> outputEdges){
		
		masterExitNode = Graph.U.createNode();
		masterExitNode.attr().put(XCSG.name, CFG_MASTER_EXIT_NODE);
		masterExitNode.tag(CFG_MASTER_EXIT_NODE);
		masterExitNode.tag(XCSG.ControlFlow_Node);
		outputNodes.add(masterExitNode);
		
		for(Node exitNode : inputNodes.taggedWithAny(XCSG.controlFlowExitPoint)){
			Edge edge = Graph.U.createEdge(exitNode, masterExitNode);
			edge.tag(XCSG.ControlFlow_Edge);
			edge.tag(CFG_EXIT_EDGE);
			outputEdges.add(edge);
		}
		
	}
	
	public Graph getGraph(){
		return this.graph;
	}
	
	/**
	 * Gets the predecessors of a given node
	 * @param node
	 * @return Predecessors of node
	 */
	public AtlasSet<Node> getPredecessors(Node node){
		AtlasSet<Edge> edges = this.graph.edges(node, NodeDirection.IN);
		AtlasSet<Node> predecessors = new AtlasHashSet<Node>();
		for(Edge edge : edges){
			Node parent = edge.getNode(EdgeDirection.FROM);
			predecessors.add(parent);
		}
		return predecessors;
	}
	
	/**
	 * Gets the successors of a given node 
	 * @param node
	 * @return Successors of node
	 */
	public AtlasSet<Node> getSuccessors(Node node){
		AtlasSet<Edge> edges = this.graph.edges(node, NodeDirection.OUT);
		AtlasSet<Node> successors = new AtlasHashSet<Node>();
		for(Edge edge : edges){
			Node child = edge.getNode(EdgeDirection.TO);
			successors.add(child);
		}
		return successors;
	}

	/**
	 * Returns the master entry node
	 * @return the master entry node
	 */
	public Node getEntryNode(){
		return masterEntryNode;
	}
	
	/**
	 * Returns the master exit node
	 * @return the master exit node
	 */
	public Node getExitNode(){
		return masterExitNode;
	}
}