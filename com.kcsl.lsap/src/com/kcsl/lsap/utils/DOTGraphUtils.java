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

/**
 * A class containing utility functions to export Atlas graphs to DOT format.
 */
public class DOTGraphUtils {
	
	/**
	 * A private constructor to prevent intentional initializations of this class.
	 * 
	 * @throws IllegalAccessException If any initialization occur to this class.
	 */
	private DOTGraphUtils() throws IllegalAccessException {
		throw new IllegalAccessException();
	}
	
	/**
	 * Converts an {@link com.ensoftcorp.atlas.core.db.graph.Graph} to {@link Graph} with the given <code>markup</code>.
	 * 
	 * @param atlasGraph An instance of {@link com.ensoftcorp.atlas.core.db.graph.Graph} to be converted.
	 * @param markup An instance of {@link Markup} on <code>atlasGraph</code>.
	 * @return An instance of {@link Graph}.
	 */
	public static Graph dotify(com.ensoftcorp.atlas.core.db.graph.Graph atlasGraph, Markup markup) {
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> nodes = atlasGraph.nodes();
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges = atlasGraph.edges();
		Graph graph = createEmptyDotGraph();

		HashMap<com.ensoftcorp.atlas.core.db.graph.Node, Node> nodesMap = new HashMap<com.ensoftcorp.atlas.core.db.graph.Node, Node>();
		int nodeIdCounter = 0;
		for (com.ensoftcorp.atlas.core.db.graph.Node atlasNode : nodes) {
			String nodeLabel = atlasNode.getAttr(XCSG.name).toString();
			Color nodeColor = null;
			if (markup != null) {
				nodeColor = markup.get(atlasNode).get(MarkupProperty.NODE_BACKGROUND_COLOR);
			}
			boolean isCondition = atlasNode.taggedWith(XCSG.ControlFlowCondition);
			Node newNode = createNode((++nodeIdCounter + ""), nodeLabel, nodeColor, isCondition);
			graph.addNode(newNode);
			nodesMap.put(atlasNode, newNode);
		}

		for (com.ensoftcorp.atlas.core.db.graph.Edge edge : edges) {
			com.ensoftcorp.atlas.core.db.graph.Node fromNode = edge.getNode(EdgeDirection.FROM);
			com.ensoftcorp.atlas.core.db.graph.Node toNode = edge.getNode(EdgeDirection.TO);

			Node newFromNode = nodesMap.get(fromNode);
			Node newToNode = nodesMap.get(toNode);
			String edgeLabel = "";
			if (edge.hasAttr(XCSG.conditionValue)) {
				edgeLabel = edge.getAttr(XCSG.conditionValue).toString();
			}
			Edge newEdge = createEdge(edgeLabel, newFromNode, newToNode);
			graph.addEdge(newEdge);
		}
		return graph;
	}
	
	/**
	 * Saves the given <code>graph</code> to <code>parentDirectory</code> with the given <code>fileName</code>.
	 * 
	 * @param graph An instance of {@link Graph} to be saved to a file.
	 * @param parentDirectory A {@link File} corresponding to the directory where the graph to be saved.
	 * @param fileName The name of the {@link File} where the graph will be saved.
	 */
	public static void saveDOTGraph(Graph graph, File parentDirectory, String fileName) {
		GraphUtils.write(graph, parentDirectory.getAbsoluteFile() + "/" + fileName);
	}
	
	/**
	 * Constructs an empty {@link Graph}.
	 * 
	 * @return An new empty {@link Graph}.
	 */
	private static Graph createEmptyDotGraph() {
		StringBuffer sb = new StringBuffer();
		sb.append("digraph G {\n");
		sb.append("\n");
		sb.append("}\n");

		Parser p = new Parser();
		try {
			p.parse(sb);
		} catch (ParseException e) {
		}
		return p.getGraphs().get(0);
	}
	
	/**
	 * Creates an instance of {@link Node} from <code>nodeId</code>, <code>nodeLabel</code> with <code>nodeColor</code>.
	 * 
	 * @param nodeId The id for the newly created node.
	 * @param nodeLabel The node label for the newly created node.
	 * @param nodeColor The node background color.
	 * @param isCondition whether this newly created node is going to be conditional node to set proper shape.
	 * @return A new instance of {@link Node}.
	 */
	private static Node createNode(String nodeId, String nodeLabel, Color nodeColor, boolean isCondition) {
		Node newNode = new Node();
		Id id = new Id();
		id.setId(nodeId);
		newNode.setId(id);
		newNode.setAttribute("label", escape(nodeLabel));
		if (nodeColor != null) {
			String color = "";
			if (nodeColor.equals(Color.RED)) {
				color = "red";
			} else if (nodeColor.equals(Color.GREEN)) {
				color = "green";
			} else if (nodeColor.equals(Color.BLUE)) {
				color = "lightblue";
			}
			if (!color.isEmpty()) {
				newNode.setAttribute("style", "filled");
				newNode.setAttribute("fillcolor", color);
			}
		}
		if (isCondition) {
			newNode.setAttribute("shape", "diamond");
		}
		return newNode;
	}
	
	/**
	 * Creates an instance of {@link Edge} from <code>edgeLabel</code> and connects <code>from</code> node and <code>to</code> node.
	 * 
	 * @param edgeLabel The edge label for the newly created edge.
	 * @param from The predecessor for this edge.
	 * @param to The successor for this edge.
	 * @return A new instance of {@link Edge}.
	 */
	private static Edge createEdge(String edgeLabel, Node from, Node to) {
		Edge newEdge = new Edge(new PortNode(from), new PortNode(to), Graph.DIRECTED);
		newEdge.setAttribute("label", escape(edgeLabel));
		return newEdge;
	}

	/**
	 * Perform proper escaping for {@link Node}s and {@link Edge}s labels.
	 * 
	 * @param string The {@link String} to be escaped.
	 * @return An escaped {@link String}.
	 */
	private static String escape(String string) {
		return string.replace("\"", "''").replace("\n", "\t");
	}
	
}
