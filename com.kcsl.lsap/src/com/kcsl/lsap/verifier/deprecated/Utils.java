package com.kcsl.lsap.verifier.deprecated;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Id;
import com.alexmerz.graphviz.objects.Node;
import com.alexmerz.graphviz.objects.PortNode;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.SimpleAddress;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.kcsl.lsap.atlas.deprecated.LinuxScripts;
import com.kcsl.lsap.atlas.deprecated.Queries;

public class Utils {
	
	public static FileWriter writer = null;
    
	public static String LOG_FILE;
	
    public static int DEBUG_LEVEL;
    
    public static int ERROR_LEVEL;
    
    public static int RANDOM_ADDRESS = 123456;
	
	static{
		DEBUG_LEVEL = 2;
		ERROR_LEVEL = 10;
		LOG_FILE = "/home/atamrawi/Desktop/results.log";
		try {
			writer = new FileWriter(LOG_FILE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String toString(com.ensoftcorp.atlas.core.db.graph.Node node){
		if(node.attr().containsKey(XCSG.sourceCorrespondence)){
			SourceCorrespondence sc = (SourceCorrespondence) node.attr().get(XCSG.sourceCorrespondence);
			if(sc != null){
				return node.attr().get(XCSG.name) + "@" + sc.offset;
			}
		}
		return (String) node.attr().get(XCSG.name);
	}
	
	public static String toString(com.ensoftcorp.atlas.core.db.graph.Node node, boolean reportSourceCorrespondence){
		if(node.attr().containsKey(XCSG.sourceCorrespondence)){
			SourceCorrespondence sc = (SourceCorrespondence) node.attr().get(XCSG.sourceCorrespondence);
			if(sc != null){
				return node.attr().get(XCSG.name) + "@" + sc.toString();
			}
		}
		return (String) node.attr().get(XCSG.name);
	}	
	
	public static String toString(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> path){
		List<com.ensoftcorp.atlas.core.db.graph.Node> list = new ArrayList<com.ensoftcorp.atlas.core.db.graph.Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Node n : path){
			list.add(n);
		}
		return toString(list);
	}
	
	public static String toString(List<com.ensoftcorp.atlas.core.db.graph.Node> path) {
		StringBuilder result = new StringBuilder();
		for(int i = 0; i < path.size(); i++){
			result.append(Utils.toString(path.get(i)));
			if(i < path.size() - 1){
				result.append(" >>> ");
			}
		}
		result.append("\n");
		return result.toString();
	}
	
	public static HashSet<com.ensoftcorp.atlas.core.db.graph.Node> toHashSet(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> set){
		HashSet<com.ensoftcorp.atlas.core.db.graph.Node> result = new HashSet<com.ensoftcorp.atlas.core.db.graph.Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Node node : set){
			result.add(node);
		}
		return result;
	}
	
	public static void addEFGToIndex(com.ensoftcorp.atlas.core.db.graph.Node functionNode, com.ensoftcorp.atlas.core.db.graph.Graph cfg, Graph efg){
		HashMap<String, com.ensoftcorp.atlas.core.db.graph.Node> nodeAddressMap = new HashMap<String, com.ensoftcorp.atlas.core.db.graph.Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Node node : cfg.nodes()){
			nodeAddressMap.put(node.address().toAddressString(), node);
		}
		
		com.ensoftcorp.atlas.core.db.graph.Node entryNode = com.ensoftcorp.atlas.core.db.graph.Graph.U.createNode();
		entryNode.attr().put(XCSG.name, Queries.EVENT_FLOW_ENTRY_NODE);
		entryNode.attr().put(Queries.EVENT_FLOW_FOR_FUNCTION, functionNode.attr().get(XCSG.name));
		entryNode.tags().add(Queries.EVENT_FLOW_NODE);
		entryNode.tags().add(XCSG.controlFlowRoot);
		nodeAddressMap.put("cfg0", entryNode);
		com.ensoftcorp.atlas.core.db.graph.Edge e = com.ensoftcorp.atlas.core.db.graph.Graph.U.createEdge(functionNode, entryNode);
		e.tags().add(XCSG.Contains);
		
		
		com.ensoftcorp.atlas.core.db.graph.Node exitNode = com.ensoftcorp.atlas.core.db.graph.Graph.U.createNode();
		exitNode.attr().put(XCSG.name, Queries.EVENT_FLOW_EXIT_NODE);
		exitNode.attr().put(Queries.EVENT_FLOW_FOR_FUNCTION, functionNode.attr().get(XCSG.name));
		exitNode.tags().add(Queries.EVENT_FLOW_NODE);
		exitNode.tags().add(XCSG.controlFlowExitPoint);
		nodeAddressMap.put("cfg1", exitNode);
		e = com.ensoftcorp.atlas.core.db.graph.Graph.U.createEdge(functionNode, exitNode);
		e.tags().add(XCSG.Contains);
		for(Edge edge : efg.getEdges()){
			Node fromNode = edge.getSource().getNode();
			String fromNodeAddress = fromNode.getLabel();
			com.ensoftcorp.atlas.core.db.graph.Node newFromNode = nodeAddressMap.get(fromNodeAddress);
			if(newFromNode != null){
				newFromNode.tags().add(Queries.EVENT_FLOW_NODE);
			}
			
			Node toNode = edge.getTarget().getNode();
			String toNodeAddress = toNode.getLabel();
			com.ensoftcorp.atlas.core.db.graph.Node newToNode = nodeAddressMap.get(toNodeAddress);
			if(newToNode != null){
				newToNode.tags().add(Queries.EVENT_FLOW_NODE);
			}
			
			if(newFromNode != null && newToNode != null){
				SimpleAddress address = new SimpleAddress();
				com.ensoftcorp.atlas.core.db.graph.Edge newEdge = com.ensoftcorp.atlas.core.db.graph.Graph.U.createEdge(address, newFromNode, newToNode);
				newEdge.tags().add(Queries.EVENT_FLOW_EDGE);
			}
		}
	}
	
	public static Graph createEventFlowGraph(com.ensoftcorp.atlas.core.db.graph.Graph graph, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> highlightedNodes){
		Graph newGraph = Utils.transformAtlasGraph(graph, highlightedNodes);
		EventFlowGraphCreator efgCreator = new EventFlowGraphCreator(newGraph);
		efgCreator.create();
		return efgCreator.getFlowGraph(); 
	}
	
	/**
	 * Transform Atlas Graph into DOT Graph
	 */
	public static Graph transformAtlasGraph(com.ensoftcorp.atlas.core.db.graph.Graph graph, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> highlightedNodes){
		Graph newGraph = createNewGraph();
		
		for(com.ensoftcorp.atlas.core.db.graph.Edge edge : graph.edges()){
			com.ensoftcorp.atlas.core.db.graph.Node fromNode = edge.getNode(EdgeDirection.FROM);
			Node newFromNode = createNode(newGraph, fromNode.address().toAddressString());
			newFromNode.setAttribute("label", escape(fromNode.attr().get(XCSG.name).toString()));
			
			com.ensoftcorp.atlas.core.db.graph.Node toNode = edge.getNode(EdgeDirection.TO);
			Node newToNode = createNode(newGraph, toNode.address().toAddressString());
			newToNode.setAttribute("label", escape(toNode.attr().get(XCSG.name).toString()));
			
			Edge newEdge = createEdge(newGraph, newFromNode, newToNode);
			if(edge.attr().containsKey("conditionValue")){
				newEdge.setAttribute("label", escape(edge.attr().get("conditionValue").toString()));
			}
			
			if(highlightedNodes.contains(fromNode)){
				newFromNode.setAttribute("style", "filled");
				newFromNode.setAttribute("fillcolor", "red");
			}
			if(highlightedNodes.contains(toNode)){
				newToNode.setAttribute("style", "filled");
				newToNode.setAttribute("fillcolor", "red");					
			}
		}
		
		if(graph.edges().isEmpty() && graph.nodes().size() == 1){
			com.ensoftcorp.atlas.core.db.graph.Node node = graph.nodes().getFirst();
			Node newNode = createNode(newGraph, node.address().toAddressString());
			newNode.setAttribute("label", escape(node.attr().get(XCSG.name).toString()));
			if(highlightedNodes.contains(node)){
				newNode.setAttribute("style", "filled");
				newNode.setAttribute("fillcolor", "red");					
			}
		}
		
		Node START_NODE = createNode(newGraph, "cfg0");
		START_NODE.setAttribute("cfg_type", "START");
		
		Node EXIT_NODE = createNode(newGraph, "cfg1");
		EXIT_NODE.setAttribute("cfg_type", "END");
		
		for(com.ensoftcorp.atlas.core.db.graph.Node node : graph.nodes()){
			if(node.tags().containsAny(XCSG.controlFlowRoot)){
				createEdge(newGraph, START_NODE, newGraph.findNode(node.address().toAddressString()));
			}
			
			if(node.tags().containsAny(XCSG.controlFlowExitPoint)){
				if(graph.nodes().size() > 1 && getParentNodes(graph, node).isEmpty()){
					// If parents is not empty: FIX for the cases where dead return statements exist
					continue;
				}
				createEdge(newGraph, newGraph.findNode(node.address().toAddressString()), EXIT_NODE);
			}
		}
		
		return newGraph;
	}
	
	/**
	 * Create and adds a new node to the graph, if the node already created then return it
	 * @param graph
	 * @param label
	 * @return the created or existing node
	 */
	public static Node createNode(Graph graph, String label){
		Node newNode = graph.findNode(label);
		if(newNode != null)
			return newNode;
    	
		newNode = new Node();
    	Id id = new Id();
    	id.setLabel(label);
    	newNode.setId(id);
    	graph.addNode(newNode);
    	return newNode;
	}
	
	/**
	 * Creates and adds a new edge to the graph
	 * @param graph
	 * @param from
	 * @param to
	 * @return new created edge
	 */
	public static Edge createEdge(Graph graph, Node from, Node to){
		Edge newEdge = new Edge(new PortNode(from), new PortNode(to), graph.getType());
		graph.addEdge(newEdge);
		return newEdge;
	}
	
	public static com.ensoftcorp.atlas.core.db.graph.Edge findEdge(com.ensoftcorp.atlas.core.db.graph.Graph graph, com.ensoftcorp.atlas.core.db.graph.Node from, com.ensoftcorp.atlas.core.db.graph.Node to){
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges = graph.edges(from, NodeDirection.OUT);
		for(com.ensoftcorp.atlas.core.db.graph.Edge edge : edges){
			com.ensoftcorp.atlas.core.db.graph.Node node = edge.getNode(EdgeDirection.TO);
			if(node.equals(to)){
				return edge;
			}
		}
		return null;
	}
	
	public static ArrayList<com.ensoftcorp.atlas.core.db.graph.Edge> findEdges(com.ensoftcorp.atlas.core.db.graph.Graph graph, com.ensoftcorp.atlas.core.db.graph.Node from, com.ensoftcorp.atlas.core.db.graph.Node to){
		ArrayList<com.ensoftcorp.atlas.core.db.graph.Edge> result = new ArrayList<com.ensoftcorp.atlas.core.db.graph.Edge>();
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges = graph.edges(from, NodeDirection.OUT);
		for(com.ensoftcorp.atlas.core.db.graph.Edge edge : edges){
			com.ensoftcorp.atlas.core.db.graph.Node node = edge.getNode(EdgeDirection.TO);
			if(node.equals(to)){
				result.add(edge);
			}
		}
		return result;
	}
	
	public static com.ensoftcorp.atlas.core.db.graph.Edge createEdge(com.ensoftcorp.atlas.core.db.graph.Edge edge, com.ensoftcorp.atlas.core.db.graph.Node from, com.ensoftcorp.atlas.core.db.graph.Node to){
		com.ensoftcorp.atlas.core.db.graph.Edge newEdge = com.ensoftcorp.atlas.core.db.graph.Graph.U.createEdge(from, to);
		newEdge.tags().add(LinuxScripts.DUPLICATE_EDGE);
    	
    	for(String attr : edge.attr().keys()){
    		newEdge.attr().put(attr, edge.attr().get(attr));
    	}
    	
    	for(String tag : edge.tags()){
    		newEdge.tags().add(tag);
    	}
    	return newEdge;
	}
	
	public static String escape(String str){
		return str.replace("\"", "''").replace("\n", "\t");
	}
	
	/**
	 * Creates an empty jpgd graph
	 * @return
	 */
	public static Graph createNewGraph(){
		StringBuffer sb = new StringBuffer();
		sb.append("digraph G {\n");
		sb.append("\n");
		sb.append("}\n");
		
		Parser p = new Parser();
		try {
			p.parse(sb);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		List<Graph> graphs = p.getGraphs();
		if(graphs.size() != 1){
			return null;
		}
		return graphs.get(0);		
	}

	/**
	 * Get from a graph an entrance of exit node
	 * @param isEntry: true to return an entry node, otherwise, and exit node returned
	 * @return entry/exit node
	 */
	public static Node getEntryExitNodes(Graph graph, boolean isEntry)
    {
    	String label = isEntry ? "START" : "END";
    	List<Node> nodes = new ArrayList<Node>();
    	String value = "";
    	for(Node node : graph.getNodes()){
    		value = node.getAttribute("cfg_type");
    		if(value != null && value.equals(label))
    			nodes.add(node);
    	}
    	int size = nodes.size();
    	if(size == 0 | size != 1){
			System.err.println("No/Multiple Entries/Exits(" + size + ")!");
    	}
    	return nodes.get(0);
    }
	
	
	public static Graph copyGraph(Graph g){
		StringBuffer sb = new StringBuffer();
		sb.append(g.toString().replace("\\\"", "").replace("\\", "/").replace(" }", "}"));
		
		Parser p = new Parser();
		try {
			p.parse(sb);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		List<Graph> graphs = p.getGraphs();
		if(graphs.size() != 1){
			return null;
		}
		return graphs.get(0);	
	}
	
	public static boolean isDirectedAcyclicGraph(com.ensoftcorp.atlas.core.db.graph.Graph graph) 
	{
		return (topologicalSort(graph) != null);
	}

	/**
	 * Return a list of nodes in topological sort order
	 * A topological sort is a non-unique permutation of the nodes such that an edge from u to v implies that u appears before v in the topological sort order
	 * The topological sort is defined for directed graphs only, if the graph if undirected, the return value is null
	 * @param graph
	 * @return
	 */
	public static List<com.ensoftcorp.atlas.core.db.graph.Node> topologicalSort(com.ensoftcorp.atlas.core.db.graph.Graph graph){
		LinkedHashSet<com.ensoftcorp.atlas.core.db.graph.Node> seen = new LinkedHashSet<com.ensoftcorp.atlas.core.db.graph.Node>(); 
		List<com.ensoftcorp.atlas.core.db.graph.Node> explored = new ArrayList<com.ensoftcorp.atlas.core.db.graph.Node>();
		
		for(com.ensoftcorp.atlas.core.db.graph.Node v : graph.nodes()){
			if(!explored.contains(v)){
				if(!DFS(graph, seen, explored, v)){
					return null;
				}
			}
		}
		return explored;
	}
	
	public static boolean DFS(com.ensoftcorp.atlas.core.db.graph.Graph graph, LinkedHashSet<com.ensoftcorp.atlas.core.db.graph.Node> seen, List<com.ensoftcorp.atlas.core.db.graph.Node> explored, com.ensoftcorp.atlas.core.db.graph.Node v){
		seen.add(v);
		for(com.ensoftcorp.atlas.core.db.graph.Node node: getChildNodes(graph, v)){
			if(!seen.contains(node)){
				if(!DFS(graph, seen, explored, node))
					return false;
			}
			else if(seen.contains(node) && !explored.contains(node)){
				return false;
			}
		}
		explored.add(0, v);
		return true;
	}
	
	public static com.ensoftcorp.atlas.core.db.graph.Graph cutCyclesFromGraph(com.ensoftcorp.atlas.core.db.graph.Graph graph){
		// First: remove self-loop edges
		Q selfLoopQ = Common.empty();
		for(com.ensoftcorp.atlas.core.db.graph.Node node : graph.nodes()){
			com.ensoftcorp.atlas.core.db.graph.Edge selfLoopEdge = findEdge(graph, node, node);
			if(selfLoopEdge != null){
				//debug(0, "Self Loop: ["+toString(node)+"] -> ["+toString(node)+"]");
				Q selfLoopNode = Common.toQ(Common.toGraph(node));
				selfLoopNode = selfLoopNode.induce(Common.universe().edgesTaggedWithAll(XCSG.Call));
				selfLoopQ = selfLoopQ.union(selfLoopNode);
			}
		}
		Q graphQ = Common.toQ(graph);
		graphQ = graphQ.differenceEdges(selfLoopQ);
		graph = graphQ.eval();
		
		// Second: cut the loop from the last node visited to the newly node visited
		for(com.ensoftcorp.atlas.core.db.graph.Node node : Common.toQ(graph.roots()).eval().nodes()){
			Q backEdgedNodes = null;
			do{
				backEdgedNodes = cutLoopsInGraph(graph, node, new ArrayList<com.ensoftcorp.atlas.core.db.graph.Node>());
				graphQ = Common.toQ(graph);
				graphQ = graphQ.differenceEdges(backEdgedNodes);
				graph = graphQ.eval();
			}while(!backEdgedNodes.eval().nodes().isEmpty());
		}
		return graph;
	}
	
	public static Q cutLoopsInGraph(com.ensoftcorp.atlas.core.db.graph.Graph graph, com.ensoftcorp.atlas.core.db.graph.Node node, ArrayList<com.ensoftcorp.atlas.core.db.graph.Node> path){
		Q backEdgeQ = Common.empty();
		path.add(node);
		for(com.ensoftcorp.atlas.core.db.graph.Node child : getChildNodes(graph, node)){
			if(path.contains(child)){
				Q nodeQ = Common.toQ(Common.toGraph(node));
				Q childQ = Common.toQ(Common.toGraph(child));
				Q bothNodes = nodeQ.union(childQ).induce(Common.universe().edgesTaggedWithAll(XCSG.Call));
				Q backEdgedNodes = Common.universe().edgesTaggedWithAll(XCSG.Call).forwardStep(nodeQ).intersection(bothNodes);
				backEdgeQ = backEdgeQ.union(backEdgedNodes);
				
				//DisplayUtil.displayGraph(Common.extend(backEdgeQ, XCSG.Contains).eval(), new Highlighter(), "QQ Processed Envelope");
				return backEdgeQ;
			}
			backEdgeQ = backEdgeQ.union(cutLoopsInGraph(graph, child, new ArrayList<com.ensoftcorp.atlas.core.db.graph.Node>(path)));
		}
		return backEdgeQ;
	}
	
	public static AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> getChildNodes(com.ensoftcorp.atlas.core.db.graph.Graph graph, com.ensoftcorp.atlas.core.db.graph.Node node){
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges = graph.edges(node, NodeDirection.OUT);
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> childNodes = new AtlasHashSet<com.ensoftcorp.atlas.core.db.graph.Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Edge edge : edges){
			com.ensoftcorp.atlas.core.db.graph.Node child = edge.getNode(EdgeDirection.TO);
			childNodes.add(child);
		}
		return childNodes;
	}
	
	public static AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> getParentNodes(com.ensoftcorp.atlas.core.db.graph.Graph graph, com.ensoftcorp.atlas.core.db.graph.Node node){
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Edge> edges = graph.edges(node, NodeDirection.IN);
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> parentNodes = new AtlasHashSet<com.ensoftcorp.atlas.core.db.graph.Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Edge edge : edges){
			com.ensoftcorp.atlas.core.db.graph.Node parent = edge.getNode(EdgeDirection.FROM);
			parentNodes.add(parent);
		}
		return parentNodes;
	}
	
	
	public static void debug(int level, String message)
	{
		if(level <= DEBUG_LEVEL){
			try {
				writer.write("[DEBUG(" + level + ")]:" + message + "\n");
				writer.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void error(int level, String message)
	{
		if(level <= ERROR_LEVEL){
			try {
				writer.write("[ERROR(" + level + ")]:" + message + "\n");
				writer.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void closeLog(){
		try {
			writer.flush();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> difference(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> a, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> b){
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> result =  new AtlasHashSet<com.ensoftcorp.atlas.core.db.graph.Node>(a);
		for(com.ensoftcorp.atlas.core.db.graph.Node i : b)
			result.remove(i);
		return result;
	}
	
	public static boolean isSubSet(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> A, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> B)
	{
		for(com.ensoftcorp.atlas.core.db.graph.Node a : A){
			if(!B.contains(a))
				return false;
		}
		return true;
	}
	
	public static AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> intersection(AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> a, AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> b){
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> result = new AtlasHashSet<com.ensoftcorp.atlas.core.db.graph.Node>(a);
		for(com.ensoftcorp.atlas.core.db.graph.Node i : b){
			if(!result.contains(i))
				result.remove(i);
		}
		return result;
	}
	
	public static AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> toAtlasSet(HashSet<com.ensoftcorp.atlas.core.db.graph.Node> list){
		AtlasSet<com.ensoftcorp.atlas.core.db.graph.Node> result = new AtlasHashSet<com.ensoftcorp.atlas.core.db.graph.Node>();
		for(com.ensoftcorp.atlas.core.db.graph.Node element : list){
			result.add(element);
		}
		return result;
	}
}
