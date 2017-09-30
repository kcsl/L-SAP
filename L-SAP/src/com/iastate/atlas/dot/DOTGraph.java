package com.iastate.atlas.dot;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import com.alexmerz.graphviz.GraphUtils;
import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Id;
import com.alexmerz.graphviz.objects.Node;
import com.alexmerz.graphviz.objects.PortNode;
import com.ensoftcorp.atlas.core.db.graph.Address;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.H;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class DOTGraph {

	private Graph graph;
	private int counter = 0;
	
	public DOTGraph(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> nodes, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges, H h) {
		this.setGraph(this.createEmptyDotGraph());
		this.dotify(nodes, edges, h);
	}
	
	private Graph createEmptyDotGraph(){
		StringBuffer sb = new StringBuffer();
		sb.append("digraph G {\n");
		sb.append("\n");
		sb.append("}\n");
		
		Parser p = new Parser();
		try {
			p.parse(sb);
		} catch (ParseException e) {}
		return p.getGraphs().get(0);
	}
	
	private void dotify(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> nodes, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges, H h){
		HashMap<com.ensoftcorp.atlas.core.db.graph.Node, Node> nodesMap = new HashMap<com.ensoftcorp.atlas.core.db.graph.Node, Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Node atlasNode : nodes){
			String nodeLabel = atlasNode.getAttr(XCSG.name).toString();
			Address nodeAddress = atlasNode.address();
			Color nodeColor = null;
			if(h != null){
				nodeColor = h.getHighlight(nodeAddress);
			}
			boolean isCondition = atlasNode.taggedWith(XCSG.ControlFlowCondition);
			Node newNode = this.createNode(nodeLabel, nodeColor, isCondition);
			nodesMap.put(atlasNode, newNode);
		}
		
		for(com.ensoftcorp.atlas.core.db.graph.Edge edge : edges){
			com.ensoftcorp.atlas.core.db.graph.Node fromNode = edge.getNode(EdgeDirection.FROM);
			com.ensoftcorp.atlas.core.db.graph.Node toNode = edge.getNode(EdgeDirection.TO);
			
			Node newFromNode = nodesMap.get(fromNode);
			Node newToNode = nodesMap.get(toNode);
			String edgeLabel = "";
			if(edge.hasAttr(XCSG.conditionValue)){
				edgeLabel = edge.getAttr(XCSG.conditionValue).toString();
			}
			this.createEdge(edgeLabel, newFromNode, newToNode);
		}
	}
	
	public void saveGraph(File parentDirectory, String fileName){
		GraphUtils.write(this.getGraph(), parentDirectory.getAbsoluteFile() + "/" + fileName);
	}
	
	private Node createNode(String nodeLabel, Color nodeColor, boolean isCondition){
		Node newNode = new Node();
    	Id id = new Id();
    	id.setId((++this.counter) + "");
    	newNode.setId(id);
    	newNode.setAttribute("label", this.escape(nodeLabel));
    	if(nodeColor != null){
    		String color = "";
    		if(nodeColor.equals(Color.RED)){
    			color = "red";
    		} else if(nodeColor.equals(Color.GREEN)){
    			color = "green";
    		}else if(nodeColor.equals(Color.BLUE)){
    			color = "lightblue";
    		}
    		if(!color.isEmpty()){
    			newNode.setAttribute("style", "filled");
    			newNode.setAttribute("fillcolor", color);
    		}
    	}
    	if(isCondition){
    		newNode.setAttribute("shape", "diamond");
    	}
    	this.getGraph().addNode(newNode);
    	return newNode;
	}
	
	private Edge createEdge(String edgeLabel, Node from, Node to){
		Edge newEdge = new Edge(new PortNode(from), new PortNode(to), this.getGraph().getType());
		newEdge.setAttribute("label", this.escape(edgeLabel));
		this.getGraph().addEdge(newEdge);
		return newEdge;		
	}
	
	private String escape(String str){
		return str.replace("\"", "''").replace("\n", "\t");
	}

	public Graph getGraph() {
		return graph;
	}

	public void setGraph(Graph graph) {
		this.graph = graph;
	}
}
