package com.kcsl.lsap.feasibility;

import java.util.ArrayList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.list.AtlasArrayList;
import com.ensoftcorp.atlas.core.db.list.AtlasList;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.kcsl.lsap.utils.LSAPUtils;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BuDDyFactory;

/**
 * This class performs intra-procedural feasibility check of a given path of {@link XCSG#ControlFlow_Node}s
 */
public class FeasibilityChecker {

	/**
	 * The {@link XCSG#Function} for which this {@link FeasibilityChecker} will work.
	 */
	private Node functionNode;
	
	/**
	 * A list of all paths in a loop-free CFG of {@link #functionNode}.
	 */
	private ArrayList<AtlasList<Node>> allCFGPaths;
	
	/**
	 * The CFG association with {@link #functionNode} that is DAG (i.e., without {@link XCSG#ControlFlowBackEdge}s).
	 */
	private Graph functionCFG;
	
	/**
	 * Constructs a new instance of {@link FeasibilityChecker} for the given <code>function</code>.
	 * 
	 * @param function The {@link XCSG#Function} associated with this {@link FeasibilityChecker}.
	 */
	public FeasibilityChecker(Q function) {
		this.functionNode = function.eval().nodes().one();
		this.functionCFG = this.loopFreeCFG().eval();
		this.allCFGPaths = new ArrayList<AtlasList<Node>>();
		this.findAllCFGPaths(this.functionCFG.nodes().one(XCSG.controlFlowRoot), new AtlasArrayList<Node>());
	}
	
	/**
	 * Finds the DAG CFG for {@link #functionNode}.
	 * 
	 * @return A {@link Q} corresponding to the CFG of {@link #functionNode} without the {@link XCSG#ControlFlowBackEdge}s.
	 */
	private Q loopFreeCFG(){
		Q cfg = CommonQueries.cfg(this.functionNode);
		Q cfgBackEdges = cfg.edges(XCSG.ControlFlowBackEdge);
		return cfg.differenceEdges(cfgBackEdges);
	}
	
	/**
	 * Traverses the {@link #functionCFG} in a depth-first search manner to find all paths and store them into {@link #allCFGPaths}.
	 * 
	 * @param currentNode A {@link XCSG#ControlFlow_Node}.
	 * @param path A list of {@link XCSG#ControlFlow_Node}s.
	 */
	private void findAllCFGPaths(Node currentNode, AtlasList<Node> path) {
		path.add(currentNode);
		AtlasSet<Node> successors = Common.toQ(this.functionCFG).successors(Common.toQ(currentNode)).eval().nodes();
		if(successors.isEmpty()) {
			this.allCFGPaths.add(path);
		}else {
			for(Node successor : successors) {
				AtlasList<Node> newPath = new AtlasArrayList<Node>(path);
				this.findAllCFGPaths(successor, newPath);
			}
		}
	}
	
	/**
	 * Checks the path feasibility between <code>firstNode</code> and <code>secondNode</code> without going through the <code>excludedNodes</code>.
	 * 
	 * @param firstNode A {@link XCSG#ControlFlow_Node} to start the path with.
	 * @param secondNode A {@link XCSG#ControlFlow_Node} to end the path with.
	 * @param excludedNodes A list of {@link XCSG#ControlFlow_Node}s that should not present along the path from <code>firstNode</code> to <code>secondNode</code>.
	 * @return true: if the path is feasible, otherwise false.
	 */
	public boolean checkPathFeasibility(Node firstNode, Node secondNode, AtlasSet<Node> excludedNodes){
		ArrayList<AtlasList<Node>> allPaths = this.getPathsContainingNodes(firstNode, secondNode, excludedNodes);
		
		List<Constraint> constraints = null;
		if(allPaths.isEmpty()){
			LSAPUtils.log("INFEASIBLE: No Constraints!");
			return false;
		}
		
		for(AtlasList<Node> path : allPaths){
			// TODO: Check if checking whether a path ends with the exit node makes the result more correct
			// In function (uinput_read): mutex_lock_interruptible cannot be dangling in a feasible path
			Node exitNode = path.get(path.size() - 1);
			if(!exitNode.taggedWith(XCSG.controlFlowExitPoint))
				continue;
			constraints = this.getConditionsSetFromPath(path);
			if(this.isConstraintsSatisfiable(constraints)){
				LSAPUtils.log("FEASIBLE: " + LSAPUtils.serialize(path));
				LSAPUtils.log("FEASIBLE: " + this.serializeConstraints(constraints));
				return true;
			}
		}
		LSAPUtils.log("INFEASIBLE: " + (constraints == null ? "No Constraints!" : serializeConstraints(constraints)));
		return false;
	}
	
	/**
	 * Finds all the paths between <code>firstNode</code> and <code>secondNode</code> without going through the <code>excludedNodes</code>.
	 * 
	 * @param firstNode A {@link XCSG#ControlFlow_Node} to start the path with.
	 * @param secondNode A {@link XCSG#ControlFlow_Node} to end the path with.
	 * @param excludedNodes A list of {@link XCSG#ControlFlow_Node}s that should not present along the path from <code>firstNode</code> to <code>secondNode</code>.
	 * @return A list of paths.
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<AtlasList<Node>> getPathsContainingNodes(Node firstNode, Node secondNode, AtlasSet<Node> excludedNodes){
		AtlasList<Node> excludedNodesList = (AtlasList<Node>) LSAPUtils.toAtlasList(excludedNodes);
		ArrayList<AtlasList<Node>> allPaths = new ArrayList<AtlasList<Node>>();
		for (AtlasList<Node> path : this.allCFGPaths) {
			if (firstNode == null && secondNode == null) {
				allPaths.add(path);
			} else if (firstNode == null && secondNode != null) {
				int indexOfNode = path.indexOf(secondNode);
				if (indexOfNode >= 0) {
					AtlasArrayList<Node> subPath = new AtlasArrayList<Node>();
					subPath.addAll(path.subList(0, indexOfNode));
					int size = subPath.size();
					subPath.removeAll(excludedNodesList);
					if (subPath.size() == size) {
						allPaths.add(path);
					}
				}
			} else if (firstNode != null && secondNode == null) {
				int indexOfNode = path.indexOf(firstNode);
				if (indexOfNode >= 0) {
					AtlasArrayList<Node> subPath = new AtlasArrayList<Node>();
					subPath.addAll(path.subList(indexOfNode + 1, path.size()));
					int size = subPath.size();
					subPath.removeAll(excludedNodesList);
					if (subPath.size() == size) {
						allPaths.add(path);
					}
				}
			} else if (firstNode != null && secondNode != null) {
				int indexOfFirstNode = path.indexOf(firstNode);
				int indexOfSecondNode = path.indexOf(secondNode);
				if (indexOfFirstNode <= indexOfSecondNode && indexOfFirstNode >= 0 && indexOfSecondNode >= 0) {
					if (firstNode.equals(secondNode)) {
						allPaths.add(path);
					} else {
						AtlasArrayList<Node> subPath = new AtlasArrayList<Node>();
						subPath.addAll(path.subList(indexOfFirstNode + 1, indexOfSecondNode));
						int size = subPath.size();
						subPath.removeAll(excludedNodesList);
						if (subPath.size() == size) {
							allPaths.add(path);
						}
					}
				}
			}
		}
		return allPaths;
	}
	
	/**
	 * Retrieves a list of {@link Constraint}s along the <code>path</code>.
	 * 
	 * @param path A list of {@link XCSG#ControlFlow_Node}s.
	 * @return A list of {@link Constraint}s.
	 */
	private List<Constraint> getConditionsSetFromPath(AtlasList<Node> path) {
		List<Constraint> constraints = new ArrayList<Constraint>();
		int count = -1;
		for (Node element : path) {
			++count;
			if (element.equals(path.get(path.size() - 1)))
				break;
			if (element.taggedWith(XCSG.ControlFlowCondition)) {
				Node nextNode = path.get(count + 1);
				Edge edge = null;
				AtlasList<Edge> edges = LSAPUtils.findDirectEdgesBetweenNodes(this.functionCFG, element, nextNode);
				if (edges.size() == 1) {
					edge = edges.get(0);
				} else if (edges.size() > 1) {
					for (Edge e : edges) {
						if (e.hasAttr(XCSG.conditionValue)) {
							edge = e;
							break;
						}
					}
				}
				if (edge == null || !edge.hasAttr(XCSG.conditionValue)) {
					continue;
				}
				String conditionValue = edge.getAttr(XCSG.conditionValue).toString();

				Constraint constraint = null;
				if (conditionValue.toLowerCase().equals("false")) {
					constraint = new Constraint(element, false);
				} else if (conditionValue.toLowerCase().equals("true")) {
					constraint = new Constraint(element, true);
				} else {
					// TODO: Handle switch cases and other control flow conditions that have more
					// than 2 branches
					LSAPUtils.log("Cannot know the exact condition value for [" + element.getAttr(XCSG.name) + "]");
				}
				if (constraint != null && !constraints.contains(constraint)) {
					constraints.add(constraint);
				}
			}
		}
		return constraints;
	}
	
	/**
	 * Checks whether the list of {@link Constraint}s are satisfiable using {@link BDD}.
	 * 
	 * @param constraints A list of {@link Constraint}s
	 * @return true: if <code>constraints</code> is satisfiable, otherwise false.
	 */
	private boolean isConstraintsSatisfiable(List<Constraint> constraints) {
		BDDFactory bddFactory = BuDDyFactory.init(constraints.size() + 10, constraints.size() + 1000);
		bddFactory.setVarNum(constraints.size() + 10);
		BDD result = null;
		for (Constraint constraint : constraints) {
			if (result == null) {
				result = this.convertConstraintToBDDNode(constraint, bddFactory);
			} else {
				BDD tmp = this.convertConstraintToBDDNode(constraint, bddFactory);
				result = result.and(tmp);
			}
		}
		if (result == null) {
			bddFactory.done();
			return true;
		}

		result = result.satOne();
		if (result.nodeCount() > 0) {
			bddFactory.done();
			return true;
		}
		bddFactory.done();
		return false;
	}
	
	/**
	 * Constructs a new instance of {@link BDD} for the given <code>constraint</code>.
	 * 
	 * @param constraint A {@link Constraint}.
	 * @param bddFactory A {@link BDDFactory} to be used to create the {@link BDD}.
	 * @return A new instance of {@link BDD}.
	 */
	private BDD convertConstraintToBDDNode(Constraint constraint, BDDFactory bddFactory) {
		BDD var;
		if (constraint.getValue()) {
			var = bddFactory.ithVar(constraint.hashCode());
		} else {
			var = bddFactory.nithVar(constraint.hashCode());
		}
		return var;
	}
	
	/**
	 * Serialize the given <code>constraints</code>.
	 * 
	 * @param constraints A list of {@link Constraint}s.
	 * @return A {@link String} serialization of <code>constraints</code>.
	 */
	private String serializeConstraints(List<Constraint> constraints) {
		StringBuilder constraintsStringBuilder = new StringBuilder();
		for (Constraint constraint : constraints) {
			constraintsStringBuilder.append(constraint.toString());
			constraintsStringBuilder.append(" && ");
		}

		if (constraintsStringBuilder.length() != 0) {
			constraintsStringBuilder.insert(0, "[");
			constraintsStringBuilder.append("]");
		}
		return constraintsStringBuilder.toString();
	}
	
	/**
	 * Returns the DAG CFG associated with this {@link FeasibilityChecker}.
	 * @return A directed acyclic {@link Graph}.
	 */
	public Graph getLoopFreeCFGGraph() {
		return this.functionCFG;
	}
}
