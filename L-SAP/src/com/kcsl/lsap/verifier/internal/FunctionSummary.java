package com.kcsl.lsap.verifier.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.kcsl.lsap.atlas.scripts.FeasibilityChecker;

public class FunctionSummary {

	private Node function;
	private Graph flowGraph;
	private List<Q> allEvents;
	private AtlasSet<Node> e1Events;
	private AtlasSet<Node> e2Events;
	private AtlasSet<Node> e1MayEvents;
	private AtlasSet<Node> callEvents;
	
	private Node entryNode;
	private Node exitNode;
	
	private HashMap<Node, Node> callEventsFunctionsMap;
	
	private FeasibilityChecker feasibilityChecker;
	
	private int rets;
	private AtlasSet<Node> retl;
	private int outs;
	private AtlasSet<Node> outl;
	private HashMap<Node, ArrayList<MatchingPair>> matchingPairsMap;

	public FunctionSummary(Node function, Graph flowGraph, List<Q> events) {
		this.setFunction(function);
		this.setFlowGraph(flowGraph);
		this.setAllEvents(events);
		this.setE1Events(events.get(0).eval().nodes());
		this.setE2Events(events.get(1).eval().nodes());
		this.setCallEvents(events.get(2).eval().nodes());
		this.setE1MayEvents(events.get(3).eval().nodes());
    	this.setOutl(new AtlasHashSet<Node>());
    	this.setRetl(new AtlasHashSet<Node>());
    	this.feasibilityChecker = null;
	}
	
	public Node getFunctionElementForCallEvent(Node node){
		return this.callEventsFunctionsMap.get(node);
	}
	
	public HashMap<Node, Node> getCallEventsFunctionsMap(){
		return this.callEventsFunctionsMap;
	}

	public AtlasSet<Node> getE1Events() {
		return e1Events;
	}

	public void setE1Events(AtlasSet<Node> e1Events) {
		this.e1Events = e1Events;
	}

	public AtlasSet<Node> getE2Events() {
		return e2Events;
	}

	public void setE2Events(AtlasSet<Node> e2Events) {
		this.e2Events = e2Events;
	}

	public AtlasSet<Node> getE1MayEvents() {
		return e1MayEvents;
	}

	public void setE1MayEvents(AtlasSet<Node> e1MayEvents) {
		this.e1MayEvents = e1MayEvents;
	}

	public AtlasSet<Node> getCallEvents() {
		return callEvents;
	}

	public void setCallEvents(AtlasSet<Node> callEvents) {
		this.callEvents = callEvents;
	}

	public Node getEntryNode() {
		return entryNode;
	}

	public void setEntryNode(Node entryNode) {
		this.entryNode = entryNode;
	}

	public Node getExitNode() {
		return exitNode;
	}

	public void setExitNode(Node exitNode) {
		this.exitNode = exitNode;
	}

	public Node getFunction() {
		return function;
	}

	public void setFunction(Node function) {
		this.function = function;
	}
	
	public void setCallEventsFunctionsMap(HashMap<Node, Node> callEventsFunctionsMap) {
		this.callEventsFunctionsMap = callEventsFunctionsMap;
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("#######################################################################\n");
		result.append("FunctionSummary for [" + Utils.toString(this.getFunction()) + "]\n");
		result.append("E1 Events: [" + Utils.toString(this.getE1Events()) + "]\n");
		result.append("E2 Events: [" + Utils.toString(this.getE2Events()) + "]\n");
		result.append("#######################################################################\n");
		return result.toString();
	}

	public FeasibilityChecker getFeasibilityChecker() {
		if(this.feasibilityChecker == null){
			this.feasibilityChecker = new FeasibilityChecker(Common.toQ(Common.toGraph(this.getFunction())));
		}
		return feasibilityChecker;
	}

	public void setFeasibilityChecker(FeasibilityChecker feasibilityChecker) {
		this.feasibilityChecker = feasibilityChecker;
	}

	public Graph getFlowGraph() {
		return flowGraph;
	}

	public void setFlowGraph(Graph flowGraph) {
		this.flowGraph = flowGraph;
	}

	public int getRets() {
		return rets;
	}

	public void setRets(int rets) {
		this.rets = rets;
	}

	public AtlasSet<Node> getRetl() {
		return retl;
	}

	public void setRetl(AtlasSet<Node> retl) {
		this.retl = retl;
	}

	public AtlasSet<Node> getOutl() {
		return outl;
	}

	public void setOutl(AtlasSet<Node> outl) {
		this.outl = outl;
	}

	public int getOuts() {
		return outs;
	}

	public void setOuts(int outs) {
		this.outs = outs;
	}

	public void setMatchingPairsList(HashMap<Node, ArrayList<MatchingPair>> matchingPairsMap) {
		this.setMatchingPairsMap(matchingPairsMap);
	}

	public HashMap<Node, ArrayList<MatchingPair>> getMatchingPairsMap() {
		return matchingPairsMap;
	}

	public void setMatchingPairsMap(HashMap<Node, ArrayList<MatchingPair>> matchingPairsMap) {
		this.matchingPairsMap = matchingPairsMap;
	}

	public List<Q> getAllEvents() {
		return allEvents;
	}

	public void setAllEvents(List<Q> allEvents) {
		this.allEvents = allEvents;
	}
}
