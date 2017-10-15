package com.kcsl.lsap.utils;

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
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class DOTGraphUtils {
	
	public static Graph dotify(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> nodes, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges, Markup markup) {
		Graph graph = createEmptyDotGraph();
		
		HashMap<com.ensoftcorp.atlas.core.db.graph.Node, Node> nodesMap = new HashMap<com.ensoftcorp.atlas.core.db.graph.Node, Node>();
		int nodeIdCounter = 0;
		for(com.ensoftcorp.atlas.core.db.graph.Node atlasNode : nodes){
			String nodeLabel = atlasNode.getAttr(XCSG.name).toString();
			Color nodeColor = null;
			if(markup != null){
				nodeColor = markup.get(atlasNode).get(MarkupProperty.NODE_BACKGROUND_COLOR);
			}
			boolean isCondition = atlasNode.taggedWith(XCSG.ControlFlowCondition);
			Node newNode = createNode((++nodeIdCounter + ""), nodeLabel, nodeColor, isCondition);
			graph.addNode(newNode);
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
			Edge newEdge = createEdge(edgeLabel, newFromNode, newToNode, graph.getType());
			graph.addEdge(newEdge);
		}
		return graph;
	}
	
	private static Graph createEmptyDotGraph(){
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
	
	public static void saveDOTGraph(Graph graph, File parentDirectory, String fileName){
		GraphUtils.write(graph, parentDirectory.getAbsoluteFile() + "/" + fileName);
	}
	
	private static Node createNode(String nodeId, String nodeLabel, Color nodeColor, boolean isCondition){
		Node newNode = new Node();
    	Id id = new Id();
    	id.setId(nodeId);
    	newNode.setId(id);
    	newNode.setAttribute("label", escape(nodeLabel));
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
    	return newNode;
	}
	
	private static Edge createEdge(String edgeLabel, Node from, Node to, int graphType){
		Edge newEdge = new Edge(new PortNode(from), new PortNode(to), graphType);
		newEdge.setAttribute("label", escape(edgeLabel));
		return newEdge;		
	}
	
	private static String escape(String str){
		return str.replace("\"", "''").replace("\n", "\t");
	}
}
