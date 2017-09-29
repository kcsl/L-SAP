package com.iastate.verifier.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.iastate.atlas.scripts.FeasibilityChecker;

public class FunctionSummary {

	private GraphElement function;
	private Graph flowGraph;
	private List<Q> allEvents;
	private AtlasSet<GraphElement> e1Events;
	private AtlasSet<GraphElement> e2Events;
	private AtlasSet<GraphElement> e1MayEvents;
	private AtlasSet<GraphElement> callEvents;
	
	private GraphElement entryNode;
	private GraphElement exitNode;
	
	private HashMap<GraphElement, GraphElement> callEventsFunctionsMap;
	
	private FeasibilityChecker feasibilityChecker;
	
	private int rets;
	private AtlasSet<GraphElement> retl;
	private int outs;
	private AtlasSet<GraphElement> outl;
	private HashMap<GraphElement, ArrayList<MatchingPair>> matchingPairsMap;

	public FunctionSummary(GraphElement function, Graph flowGraph, List<Q> events) {
		this.setFunction(function);
		this.setFlowGraph(flowGraph);
		this.setAllEvents(events);
		this.setE1Events(events.get(0).eval().nodes());
		this.setE2Events(events.get(1).eval().nodes());
		this.setCallEvents(events.get(2).eval().nodes());
		this.setE1MayEvents(events.get(3).eval().nodes());
    	this.setOutl(new AtlasHashSet<GraphElement>());
    	this.setRetl(new AtlasHashSet<GraphElement>());
    	this.feasibilityChecker = null;
	}
	
	public GraphElement getFunctionElementForCallEvent(GraphElement node){
		return this.callEventsFunctionsMap.get(node);
	}
	
	public HashMap<GraphElement, GraphElement> getCallEventsFunctionsMap(){
		return this.callEventsFunctionsMap;
	}

	public AtlasSet<GraphElement> getE1Events() {
		return e1Events;
	}

	public void setE1Events(AtlasSet<GraphElement> e1Events) {
		this.e1Events = e1Events;
	}

	public AtlasSet<GraphElement> getE2Events() {
		return e2Events;
	}

	public void setE2Events(AtlasSet<GraphElement> e2Events) {
		this.e2Events = e2Events;
	}

	public AtlasSet<GraphElement> getE1MayEvents() {
		return e1MayEvents;
	}

	public void setE1MayEvents(AtlasSet<GraphElement> e1MayEvents) {
		this.e1MayEvents = e1MayEvents;
	}

	public AtlasSet<GraphElement> getCallEvents() {
		return callEvents;
	}

	public void setCallEvents(AtlasSet<GraphElement> callEvents) {
		this.callEvents = callEvents;
	}

	public GraphElement getEntryNode() {
		return entryNode;
	}

	public void setEntryNode(GraphElement entryNode) {
		this.entryNode = entryNode;
	}

	public GraphElement getExitNode() {
		return exitNode;
	}

	public void setExitNode(GraphElement exitNode) {
		this.exitNode = exitNode;
	}

	public GraphElement getFunction() {
		return function;
	}

	public void setFunction(GraphElement function) {
		this.function = function;
	}
	
	public void setCallEventsFunctionsMap(HashMap<GraphElement, GraphElement> callEventsFunctionsMap) {
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

	public AtlasSet<GraphElement> getRetl() {
		return retl;
	}

	public void setRetl(AtlasSet<GraphElement> retl) {
		this.retl = retl;
	}

	public AtlasSet<GraphElement> getOutl() {
		return outl;
	}

	public void setOutl(AtlasSet<GraphElement> outl) {
		this.outl = outl;
	}

	public int getOuts() {
		return outs;
	}

	public void setOuts(int outs) {
		this.outs = outs;
	}

	public void setMatchingPairsList(HashMap<GraphElement, ArrayList<MatchingPair>> matchingPairsMap) {
		this.setMatchingPairsMap(matchingPairsMap);
	}

	public HashMap<GraphElement, ArrayList<MatchingPair>> getMatchingPairsMap() {
		return matchingPairsMap;
	}

	public void setMatchingPairsMap(HashMap<GraphElement, ArrayList<MatchingPair>> matchingPairsMap) {
		this.matchingPairsMap = matchingPairsMap;
	}

	public List<Q> getAllEvents() {
		return allEvents;
	}

	public void setAllEvents(List<Q> allEvents) {
		this.allEvents = allEvents;
	}
}
