package com.kcsl.lsap.atlas.deprecated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.kcsl.lsap.verifier.deprecated.Utils;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BuDDyFactory;

public class FeasibilityChecker {

	private ArrayList<ArrayList<Node>> paths;
	private HashMap<String, Integer> constraintsMap;
	private Node entryNode;
	
	//private HashMap<GraphElement, String> nodeConstraintMap;
	private Graph functionCFG;
	public Graph getFunctionCFG() {
		return functionCFG;
	}

	public void setFunctionCFG(Graph functionCFG) {
		this.functionCFG = functionCFG;
	}

	private String functionName;
	
	public FeasibilityChecker(String functionName) {
		this.functionCFG = Queries.LoopFreeCFG(Queries.function(functionName)).eval();
		this.functionName = functionName;
		init();
	}
	
	public FeasibilityChecker(Q function) {
		this.functionCFG = Queries.LoopFreeCFG(function).eval();
		this.functionName = (String) function.eval().nodes().getFirst().attr().get(XCSG.name);
		init();
	}
	
	public FeasibilityChecker() {
	}
	
	private void init(){
		boolean isDAG = Utils.isDirectedAcyclicGraph(this.functionCFG);
		if(!isDAG){
			Utils.error(0, "%%%%%%%%%% Function [" + this.functionName + "] has a CFG that is not DAG!");
			DisplayUtil.displayGraph(Common.extend(Common.toQ(this.functionCFG), XCSG.Contains).eval(), new Highlighter(), "Cyclic CFG-" + this.functionName);
			return;
		}
		//Utils.debug(0, "Started!");
		this.paths = new ArrayList<ArrayList<Node>>();
		this.constraintsMap = new HashMap<String, Integer>();
		//this.nodeConstraintMap = new HashMap<GraphElement, String>();
		Node entry = this.functionCFG.nodes().taggedWithAll(XCSG.controlFlowRoot).getFirst();
		this.entryNode = entry;
		traverse(this.functionCFG, this.entryNode, new ArrayList<Node>());
		
		//Utils.debug(0, "There are [" + paths.size() + "] paths!");
		
		int conditionsCount = 0;
		for(Node node : this.functionCFG.nodes()){
			if(node.tags().contains(XCSG.ControlFlowCondition)){
				String conditionString = (String) node.attr().get(XCSG.name);
				if(!constraintsMap.containsKey(conditionString)){
					constraintsMap.put(conditionString, conditionsCount);
					conditionsCount++;
				}
			}
		}
		
		//for(String constraint : constraintsMap.keySet()){
		//	Utils.debug(0, "[" + constraint + "] : [" + constraintsMap.get(constraint) + "]");
		//}
		
		//processConstraints(cfg);
	}
	
	public void checkFeasibility(){
		for(Node node : this.functionCFG.nodes()){
			testFeasibility(this.functionCFG, node);
		}
		//Utils.debug(0, "DONE!");
	}
	
	public boolean checkPathFeasibility(List<Node> path, Node node){
		ArrayList<ArrayList<Node>> allPaths = new ArrayList<ArrayList<Node>>();
		for(ArrayList<Node> p : paths){
			if(p.containsAll(path)){
				allPaths.add(p);
			}
		}
		
		List<Condition> constraints = null;
		for(ArrayList<Node> p : allPaths){
			constraints = getConditionsSetFromPath(this.functionCFG, p, node);
			if(isPathFeasible(constraints)){
				Utils.debug(0, "FEASIBLE: " + toString(constraints));
				return true;
			}
			Utils.debug(0, "INFEASIBLE: " + toString(constraints));
		}
		return false;
	}
	
	public boolean checkFeasibility(Node e1, Node e2){
		ArrayList<ArrayList<Node>> allPaths = new ArrayList<ArrayList<Node>>();
		for(ArrayList<Node> path : paths){
			if(path.contains(e1) && !path.contains(e2)){
				allPaths.add(path);
			}
		}
		
		List<Condition> constraints = null;
		for(ArrayList<Node> path : allPaths){
			constraints = getConditionsSetFromPath(this.functionCFG, path, path.get(path.size() - 1));
			if(isPathFeasible(constraints))
				return true;
		}
		return false;
	}
	
	public ArrayList<ArrayList<Node>> getPathsContainingNodes(Node firstNode, Node secondNode, HashSet<Node> excludedNodes){
		ArrayList<ArrayList<Node>> allPaths = new ArrayList<ArrayList<Node>>();
		for(ArrayList<Node> path : paths){
			if(firstNode == null && secondNode == null){
				allPaths.add(path);
			}else if(firstNode == null && secondNode != null){
				int indexOfNode = path.indexOf(secondNode);
				if(indexOfNode >= 0){
					ArrayList<Node> subPath = new ArrayList<Node>();
					subPath.addAll(path.subList(0, indexOfNode));
					int size = subPath.size();
					subPath.removeAll(excludedNodes);
					if(subPath.size() == size){
						allPaths.add(path);
					}
				}
			}else if(firstNode != null && secondNode == null){
				int indexOfNode = path.indexOf(firstNode);
				if(indexOfNode >= 0){
					ArrayList<Node> subPath = new ArrayList<Node>();
					subPath.addAll(path.subList(indexOfNode + 1, path.size()));
					int size = subPath.size();
					subPath.removeAll(excludedNodes);
					if(subPath.size() == size){
						allPaths.add(path);
					}
				}
			}else if(firstNode != null && secondNode != null){
				int indexOfFirstNode = path.indexOf(firstNode);
				int indexOfSecondNode = path.indexOf(secondNode);
				if(indexOfFirstNode <= indexOfSecondNode && indexOfFirstNode >= 0 && indexOfSecondNode >= 0){
					if(firstNode.equals(secondNode)){
						allPaths.add(path);
					}else{
						ArrayList<Node> subPath = new ArrayList<Node>();
						subPath.addAll(path.subList(indexOfFirstNode + 1, indexOfSecondNode));
						int size = subPath.size();
						subPath.removeAll(excludedNodes);
						if(subPath.size() == size){
							allPaths.add(path);
						}
					}
				}
			}
		}
		return allPaths;
	}
	
	public boolean checkPathFeasibility(Node firstNode, Node secondNode, HashSet<Node> excludedNodes){
		ArrayList<ArrayList<Node>> allPaths = this.getPathsContainingNodes(firstNode, secondNode, excludedNodes);
		
		List<Condition> constraints = null;
		if(allPaths.isEmpty()){
			Utils.debug(0, "INFEASIBLE: No Constraints!");
			return false;
		}
		
		for(ArrayList<Node> path : allPaths){
			// TODO: Check if checking whether a path ends with the exit node makes the result more correct
			// In function (uinput_read): mutex_lock_interruptible cannot be dangling in a feasible path
			Node exitNode = path.get(path.size() - 1);
			if(!exitNode.tags().contains(XCSG.controlFlowExitPoint))
				continue;
			constraints = getConditionsSetFromPath(this.functionCFG, path, exitNode);
			if(isPathFeasible(constraints)){
				Utils.debug(0, "FEASIBLE: " + Utils.toString(path));
				Utils.debug(0, "FEASIBLE: " + toString(constraints));
				return true;
			}
		}
		Utils.debug(0, "INFEASIBLE: " + (constraints == null ? "No Constraints!" : toString(constraints)));
		return false;
	}
	
	/*
	public void processMayEvents(){
		HashSet<String> mayLockFunctions = new HashSet<String>();
		mayLockFunctions.add("mutex_trylock");
		returnMayEventsFeasibilityMap(mayLockFunctions);
	}
	*/
	
	/*
	public HashMap<Node, Boolean> returnMayEventsFeasibilityMap(HashSet<String> mayEvents){
		HashMap<Node, Boolean> tryEventNodesFeasibilityMap = new HashMap<Node, Boolean>();
		Q mayEventFuncs = Common.empty();
		for(String f : mayEvents){
			mayEventFuncs = mayEventFuncs.union(Queries.function(f));
		}
		
		Q functionReturn = Queries.functionReturn(mayEventFuncs);
		//TODO: Check how to get the condition nodes more efficiently and systematically
		Q leaves = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Edge.ADDRESS_OF, Edge.POINTER_DEREFERENCE).forward(functionReturn).leaves();
		Q conditionalControlFlowNodes = Common.universe().edgesTaggedWithAll(XCSG.Contains).reverse(leaves).nodesTaggedWithAll(XCSG.ControlFlowCondition);
		// Filter return or non-conditional leaves. Those nodes contribute to return value always
		Q conditions = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(conditionalControlFlowNodes).intersection(leaves);
		AtlasSet<Node> nodes = conditions.eval().nodes();
		Highlighter h = new Highlighter();
		for(Node node : nodes){
			Q condition = Common.toQ(Common.toGraph(node));
			Node controlFlowNode = Common.universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(condition).nodesTaggedWithAll(XCSG.ControlFlowCondition).eval().nodes().getFirst();
			Q operator = Common.universe().edgesTaggedWithAll(XCSG.DataFlow_Edge).reverseStep(condition).nodesTaggedWithAll(XCSG.Operator);
			Graph operatorGraph = operator.eval();
			if(operatorGraph.nodes().size() == 0){
				// There is no operator, that means the call to (mutex_trylock) is conditioned. We should make sure that taking the (FALSE) branch is (INFEASIBLE)
				tryEventNodesFeasibilityMap.put(controlFlowNode, true);
				h.highlight(condition, Color.GREEN);
			}else{
				Node operatorNode = operatorGraph.nodes().getFirst();
				if(operatorNode.tags().contains(XCSG.BinaryOperator)){
					if(operatorNode.tags().contains(XCSG.EqualTo)){
						// I have assumed that using the (==) operator with the return value from (conditional function) to be (== 0)
						tryEventNodesFeasibilityMap.put(controlFlowNode, false);
						h.highlight(condition, Color.RED);
						continue;
					}
				}else if(operatorNode.tags().contains(XCSG.UnaryOperator)){
					if(operatorNode.tags().contains(XCSG.LogicalNot) || operatorNode.tags().contains(XCSG.Negation)){
						tryEventNodesFeasibilityMap.put(controlFlowNode, false);
						h.highlight(condition, Color.RED);
						continue;
					}
				}
				Utils.error(0, "Not sure what is the type of operator: ["+ operatorNode.attr().get(XCSG.name) +"]");
				DisplayUtil.displayGraph(Common.toGraph(operatorNode), new Highlighter(), "Not Known Operator");
			}
		}
		//DisplayUtil.displayGraph(Common.extend(conditions, XCSG.Contains).eval(), h);
		return tryEventNodesFeasibilityMap;
	}
	*/
	
	private void traverse(Graph graph, Node currentNode, ArrayList<Node> path){
		path.add(currentNode);
		
		AtlasSet<Node> children = getChildNodes(graph, currentNode);
		if(children.size() > 1){
			//path.add(currentNode);
			for(Node child : children){
				ArrayList<Node> newPath = new ArrayList<Node>(path);
				traverse(graph, child, newPath);
			}
		}else if(children.size() == 1){
			traverse(graph, children.getFirst(), path);
		}else if(children.isEmpty()){
			paths.add(path);
		}
	}
	
	/*
	private void processConstraints(Graph graph){
		for(Node node : graph.nodes()){
			ArrayList<ArrayList<Node>> pathsContainingNode = getPathsContainingNode(node);
						
			List<String> constraints = new ArrayList<String>();
			for(ArrayList<Node> path : pathsContainingNode){
				String condition = toString(getConditionsSetFromPath(graph, path, node));
				if(!constraints.contains(condition))
					constraints.add(condition);
			}
			
			StringBuilder constraint = new StringBuilder();
			int i = 0;
			for(String c : constraints){
				constraint.append(c);
				if(i < constraints.size() - 1){
					constraint.append("||");
				}
				i++;
			}
			if(constraint.length() != 0){
				constraint.insert(0, "[");
				constraint.append("]");
			}
			nodeConstraintMap.put(node, constraint.toString());
		}
	}
	*/

	public ArrayList<ArrayList<Node>> getPathsContainingNode(Node node){
		ArrayList<ArrayList<Node>> result = new ArrayList<ArrayList<Node>>();
		for(ArrayList<Node> path : paths){
			if(path.contains(node)){
				result.add(path);
			}
		}
		return result;
	}
	
	public ArrayList<ArrayList<Node>> getPathsContainingNodes(Node n1, Node n2){
		ArrayList<ArrayList<Node>> result = new ArrayList<ArrayList<Node>>();
		for(ArrayList<Node> path : paths){
			if(path.contains(n1) && path.contains(n2)){
				result.add(path);
			}
		}
		return result;
	}
	
	private List<Condition> getConditionsSetFromPath(Graph graph, ArrayList<Node> path, Node node){
		List<Condition> constraints = new ArrayList<Condition>();
		//List<Condition> tryConditions = new ArrayList<Condition>();
		int count = -1;
		for(Node element : path){
			++count;
			//Utils.debug(0, Utils.toString(element));
			if(element.equals(node))
				break;
			if(element.tags().contains(XCSG.ControlFlowCondition)){
				Node nextNode = path.get(count + 1);
				//Node edge = Utils.findEdge(graph, element, nextNode);
				Edge edge = null;
				ArrayList<Edge> edges = Utils.findEdges(graph, element, nextNode);
				if(edges.size() == 1){
					edge = edges.get(0);
				}else if(edges.size() > 1){
					for(Edge e : edges){
						if(e.attr().containsKey(XCSG.conditionValue)){
							edge = e;
							break;
						}
					}
				}
				if(edge == null || !edge.attr().containsKey(XCSG.conditionValue)){
					continue;
				}
				String conditionValue = edge.attr().get(XCSG.conditionValue).toString();
				String conditionString = (String) element.attr().get(XCSG.name);
				
				/*
				// Add conditions from the tryEventNodes if they do exist
				if(this.tryEventNodesFeasibilityMap.containsKey(element)){
					tryConditions.add(new Condition(constraintsMap.get(conditionString), this.tryEventNodesFeasibilityMap.get(element), conditionString));
				}
				*/
				Condition condition = null;
				if(conditionValue.toLowerCase().equals("false")){
					condition = new Condition(constraintsMap.get(conditionString), false, conditionString);
				}else if(conditionValue.toLowerCase().equals("true")){
					condition = new Condition(constraintsMap.get(conditionString), true, conditionString);
				}else{
					//TODO: Handle switch cases and other control flow conditions that have more than 2 branches
					Utils.error(0, "Cannot know the exact condition value for [" + element.attr().get(XCSG.name) + "]");
				}
				if(condition != null && !constraints.contains(condition)){
					constraints.add(condition);
				}
			}
		}
		//constraints.addAll(tryConditions);
		return constraints;
	}
	
	private AtlasSet<Node> getChildNodes(Graph graph, Node node){
		AtlasSet<Edge> edges = graph.edges(node, NodeDirection.OUT);
		AtlasSet<Edge> backEdges = edges.taggedWithAll(XCSG.ControlFlowBackEdge);
		AtlasSet<Node> childNodes = new AtlasHashSet<Node>();
		
		for(Edge edge : edges){
			if(backEdges.contains(edge))
				continue;
			Node child = edge.getNode(EdgeDirection.TO);
			childNodes.add(child);
		}
		return childNodes.taggedWithAll(XCSG.ControlFlow_Node);
	}
	
	private void testFeasibility(Graph graph, Node node){	
		ArrayList<ArrayList<Node>> pathsContainingNode = getPathsContainingNode(node);
		
		List<Condition> constraints = null;
		for(ArrayList<Node> path : pathsContainingNode){
			constraints = getConditionsSetFromPath(graph, path, node);
			boolean isFeasible = isPathFeasible(constraints);
			Utils.debug(0, "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
			Utils.debug(0, Utils.toString(path));
			Utils.debug(0, "PATH: " + (isFeasible ? "Feasible" : "Infeasible"));
			Utils.debug(0, toString(constraints));
			Utils.debug(0, "");
			Utils.debug(0, "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
		}
	}
	
	private BDD Cons2BDDNode(Condition con, BDDFactory bddFactory){
		BDD var;
		if(con.getValue()){
			var = bddFactory.ithVar(con.getID());
		}else{
			var = bddFactory.nithVar(con.getID());
		}
		return var;
	}
	
	private boolean isPathFeasible(List<Condition> conditions){
		BDDFactory bddFactory = BuDDyFactory.init(constraintsMap.size() + 10, constraintsMap.size() + 1000);
		bddFactory.setVarNum(constraintsMap.size() + 10);
		BDD result=null;
		for(Condition constraint : conditions){
			if(result == null){
				result = Cons2BDDNode(constraint,bddFactory);
			}else{
				BDD tmp = Cons2BDDNode(constraint,bddFactory);
				result = result.and(tmp);
			}
		}
		if(result == null){
			bddFactory.done();
			return true;
		}
		
		result = result.satOne();
		if(result.nodeCount() > 0){
			bddFactory.done();
			return true;
		}
		bddFactory.done();
		return false;
	}
	
	private String toString(List<Condition> conditions){
		StringBuilder constraint = new StringBuilder();
		int i = 0;
		for(Condition c : conditions){
			constraint.append(c.toString());
			if(i < conditions.size() - 1){
				constraint.append("&&");
			}
			i++;
		}
		
		if(constraint.length() != 0){
			constraint.insert(0, "[");
			constraint.append("]");
		}
		return constraint.toString();
	}
}
