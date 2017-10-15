package com.kcsl.lsap.feasibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.list.AtlasArrayList;
import com.ensoftcorp.atlas.core.db.list.AtlasList;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.kcsl.lsap.utils.LSAPUtils;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BuDDyFactory;

public class FeasibilityChecker {

	private ArrayList<AtlasList<Node>> paths;
	private HashMap<String, Integer> constraintsMap;
	private Node entryNode;
	private Graph functionCFG;
	public Graph getFunctionCFG() {
		return functionCFG;
	}

	public void setFunctionCFG(Graph functionCFG) {
		this.functionCFG = functionCFG;
	}

	private String functionName;
	
	public FeasibilityChecker(String functionName) {
		this.functionCFG = LSAPUtils.loopFreeCFG(CommonQueries.functions(functionName)).eval();
		this.functionName = functionName;
		init();
	}
	
	public FeasibilityChecker(Q function) {
		this.functionCFG = LSAPUtils.loopFreeCFG(function).eval();
		this.functionName = (String) function.eval().nodes().one().getAttr(XCSG.name);
		init();
	}
	
	public FeasibilityChecker() {
	}
	
	private void init(){
		boolean isDAG = LSAPUtils.isDirectedAcyclicGraph(Common.toQ(this.functionCFG));
		if(!isDAG){
			LSAPUtils.log("Feasibility Checker Error: function [" + this.functionName + "] has a CFG that is not DAG!");
			return;
		}
		this.paths = new ArrayList<AtlasList<Node>>();
		this.constraintsMap = new HashMap<String, Integer>();
		Node entry = this.functionCFG.nodes().one(XCSG.controlFlowRoot);
		this.entryNode = entry;
		traverse(this.functionCFG, this.entryNode, new AtlasArrayList<Node>());
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
	}
	
	public void checkFeasibility(){
		for(Node node : this.functionCFG.nodes()){
			testFeasibility(this.functionCFG, node);
		}
	}
	
	public boolean checkPathFeasibility(List<Node> path, Node node){
		ArrayList<AtlasList<Node>> allPaths = new ArrayList<AtlasList<Node>>();
		for(AtlasList<Node> p : paths){
			if(p.containsAll(path)){
				allPaths.add(p);
			}
		}
		
		List<Condition> constraints = null;
		for(AtlasList<Node> p : allPaths){
			constraints = getConditionsSetFromPath(this.functionCFG, p, node);
			if(isPathFeasible(constraints)){
				LSAPUtils.log("FEASIBLE: " + toString(constraints));
				return true;
			}
			LSAPUtils.log("INFEASIBLE: " + toString(constraints));
		}
		return false;
	}
	
	public boolean checkFeasibility(Node e1, Node e2){
		ArrayList<AtlasList<Node>> allPaths = new ArrayList<AtlasList<Node>>();
		for(AtlasList<Node> path : paths){
			if(path.contains(e1) && !path.contains(e2)){
				allPaths.add(path);
			}
		}
		
		List<Condition> constraints = null;
		for(AtlasList<Node> path : allPaths){
			constraints = getConditionsSetFromPath(this.functionCFG, path, path.get(path.size() - 1));
			if(isPathFeasible(constraints))
				return true;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<AtlasList<Node>> getPathsContainingNodes(Node firstNode, Node secondNode, AtlasSet<Node> excludedNodes){
		AtlasArrayList<Node> excludedNodesList = (AtlasArrayList<Node>) LSAPUtils.toAtlasList(excludedNodes);
		ArrayList<AtlasList<Node>> allPaths = new ArrayList<AtlasList<Node>>();
		for(AtlasList<Node> path : paths){
			if(firstNode == null && secondNode == null){
				allPaths.add(path);
			}else if(firstNode == null && secondNode != null){
				int indexOfNode = path.indexOf(secondNode);
				if(indexOfNode >= 0){
					AtlasArrayList<Node> subPath = new AtlasArrayList<Node>();
					subPath.addAll(path.subList(0, indexOfNode));
					int size = subPath.size();
					subPath.removeAll(excludedNodesList);
					if(subPath.size() == size){
						allPaths.add(path);
					}
				}
			}else if(firstNode != null && secondNode == null){
				int indexOfNode = path.indexOf(firstNode);
				if(indexOfNode >= 0){
					AtlasArrayList<Node> subPath = new AtlasArrayList<Node>();
					subPath.addAll(path.subList(indexOfNode + 1, path.size()));
					int size = subPath.size();
					subPath.removeAll(excludedNodesList);
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
						AtlasArrayList<Node> subPath = new AtlasArrayList<Node>();
						subPath.addAll(path.subList(indexOfFirstNode + 1, indexOfSecondNode));
						int size = subPath.size();
						subPath.removeAll(excludedNodesList);
						if(subPath.size() == size){
							allPaths.add(path);
						}
					}
				}
			}
		}
		return allPaths;
	}
	
	public boolean checkPathFeasibility(Node firstNode, Node secondNode, AtlasSet<Node> excludedNodes){
		ArrayList<AtlasList<Node>> allPaths = this.getPathsContainingNodes(firstNode, secondNode, excludedNodes);
		
		List<Condition> constraints = null;
		if(allPaths.isEmpty()){
			LSAPUtils.log("INFEASIBLE: No Constraints!");
			return false;
		}
		
		for(AtlasList<Node> path : allPaths){
			// TODO: Check if checking whether a path ends with the exit node makes the result more correct
			// In function (uinput_read): mutex_lock_interruptible cannot be dangling in a feasible path
			Node exitNode = path.get(path.size() - 1);
			if(!exitNode.tags().contains(XCSG.controlFlowExitPoint))
				continue;
			constraints = getConditionsSetFromPath(this.functionCFG, path, exitNode);
			if(isPathFeasible(constraints)){
				LSAPUtils.log("FEASIBLE: " + LSAPUtils.toString(path));
				LSAPUtils.log("FEASIBLE: " + toString(constraints));
				return true;
			}
		}
		LSAPUtils.log("INFEASIBLE: " + (constraints == null ? "No Constraints!" : toString(constraints)));
		return false;
	}
	
	private void traverse(Graph graph, Node currentNode, AtlasList<Node> path){
		path.add(currentNode);
		
		AtlasSet<Node> children = getChildNodes(graph, currentNode);
		if(children.size() > 1){
			for(Node child : children){
				AtlasList<Node> newPath = new AtlasArrayList<Node>(path);
				traverse(graph, child, newPath);
			}
		}else if(children.size() == 1){
			traverse(graph, children.one(), path);
		}else if(children.isEmpty()){
			paths.add(path);
		}
	}

	public ArrayList<AtlasList<Node>> getPathsContainingNode(Node node){
		ArrayList<AtlasList<Node>> result = new ArrayList<AtlasList<Node>>();
		for(AtlasList<Node> path : paths){
			if(path.contains(node)){
				result.add(path);
			}
		}
		return result;
	}
	
	public ArrayList<AtlasList<Node>> getPathsContainingNodes(Node n1, Node n2){
		ArrayList<AtlasList<Node>> result = new ArrayList<AtlasList<Node>>();
		for(AtlasList<Node> path : paths){
			if(path.contains(n1) && path.contains(n2)){
				result.add(path);
			}
		}
		return result;
	}
	
	private List<Condition> getConditionsSetFromPath(Graph graph, AtlasList<Node> path, Node node){
		List<Condition> constraints = new ArrayList<Condition>();
		int count = -1;
		for(Node element : path){
			++count;
			if(element.equals(node))
				break;
			if(element.tags().contains(XCSG.ControlFlowCondition)){
				Node nextNode = path.get(count + 1);
				Edge edge = null;
				AtlasList<Edge> edges = LSAPUtils.findDirectEdgesBetweenNodes(graph, element, nextNode);
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
				
				Condition condition = null;
				if(conditionValue.toLowerCase().equals("false")){
					condition = new Condition(constraintsMap.get(conditionString), false, conditionString);
				}else if(conditionValue.toLowerCase().equals("true")){
					condition = new Condition(constraintsMap.get(conditionString), true, conditionString);
				}else{
					//TODO: Handle switch cases and other control flow conditions that have more than 2 branches
					LSAPUtils.log("Cannot know the exact condition value for [" + element.getAttr(XCSG.name) + "]");
				}
				if(condition != null && !constraints.contains(condition)){
					constraints.add(condition);
				}
			}
		}
		return constraints;
	}
	
	private AtlasSet<Node> getChildNodes(Graph graph, Node node){
		AtlasSet<Edge> edges = graph.edges(node, NodeDirection.OUT);
		AtlasSet<Edge> backEdges = edges.tagged(XCSG.ControlFlowBackEdge);
		AtlasSet<Node> childNodes = new AtlasHashSet<Node>();
		
		for(Edge edge : edges){
			if(backEdges.contains(edge))
				continue;
			Node child = edge.getNode(EdgeDirection.TO);
			childNodes.add(child);
		}
		return childNodes.tagged(XCSG.ControlFlow_Node);
	}
	
	private void testFeasibility(Graph graph, Node node){	
		ArrayList<AtlasList<Node>> pathsContainingNode = getPathsContainingNode(node);
		
		List<Condition> constraints = null;
		for(AtlasList<Node> path : pathsContainingNode){
			constraints = getConditionsSetFromPath(graph, path, node);
			boolean isFeasible = isPathFeasible(constraints);
			LSAPUtils.log("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
			LSAPUtils.log(LSAPUtils.toString(path));
			LSAPUtils.log("PATH: " + (isFeasible ? "Feasible" : "Infeasible"));
			LSAPUtils.log(toString(constraints));
			LSAPUtils.log("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
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
