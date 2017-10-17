package com.kcsl.lsap.core;

import java.util.ArrayList;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.pcg.common.PCG;
import com.kcsl.lsap.feasibility.FeasibilityChecker;
import com.kcsl.lsap.utils.LSAPUtils;

/**
 * A class corresponding to the function summary for a function.
 */
public class FunctionSummary {

	/**
	 * The {@link Node} for the {@link XCSG#Function} which this instance belongs to.
	 */
	private Node function;
	
	/**
	 * The {@link PCG} instance associated with this instance.
	 */
	private PCG pcg;
	
	/**
	 * A list of {@link Q}s for the different events of interest in this {@link #function} graphs.
	 */
	private List<Q> allEvents;
	
	/**
	 * A set of {@link Node}s corresponding to all lock function calls in this {@link #function}.
	 */
	private AtlasSet<Node> lockFunctionCallEvents;
	
	/**
	 * A set of {@link Node}s corresponding to all unlock function calls in this {@link #function}.
	 */
	private AtlasSet<Node> unlockFunctionCallEvents;
	
	/**
	 * A set of {@link Node}s corresponding to all lock function calls in this {@link #function} and have multi-state.
	 */
	private AtlasSet<Node> multiStateLockFunctionCallEvents;
	
	/**
	 * A set of {@link Node}s corresponding to all call-sites function calls in this {@link #function} that eventually call lock/unlock.
	 */
	private AtlasSet<Node> callSiteEvents;
	
	/**
	 * A {@link Node} corresponding to the entry node for the {@link PCG} instance.
	 */
	private Node entryNode;
	
	/**
	 * A {@link Node} corresponding to the exit node for the {@link PCG} instance.
	 */
	private Node exitNode;
	
	/**
	 * A mapping between a callsite {@link Node} and its corresponding {@link XCSG#Function} node.
	 */
	private AtlasMap<Node, Node> callEventsFunctionsMap;
	
	/**
	 * An instance of {@link FeasibilityChecker} to perform feasibility operations.
	 */
	private FeasibilityChecker feasibilityChecker;
	
	private int rets;
	private AtlasSet<Node> retl;
	private int outs;
	private AtlasSet<Node> outl;
	
	/**
	 * A mapping of {@link Node} corresponding to a lock function call to a set of its {@link MatchingPair}s.
	 */
	private AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap;

	public FunctionSummary(Node function, PCG pcg, List<Q> events) {
		this.setFunction(function);
		this.setPCG(pcg);
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
	
	public AtlasMap<Node, Node> getCallEventsFunctionsMap(){
		return this.callEventsFunctionsMap;
	}

	public AtlasSet<Node> getE1Events() {
		return lockFunctionCallEvents;
	}

	public void setE1Events(AtlasSet<Node> e1Events) {
		this.lockFunctionCallEvents = e1Events;
	}

	public AtlasSet<Node> getE2Events() {
		return unlockFunctionCallEvents;
	}

	public void setE2Events(AtlasSet<Node> e2Events) {
		this.unlockFunctionCallEvents = e2Events;
	}

	public AtlasSet<Node> getE1MayEvents() {
		return multiStateLockFunctionCallEvents;
	}

	public void setE1MayEvents(AtlasSet<Node> e1MayEvents) {
		this.multiStateLockFunctionCallEvents = e1MayEvents;
	}

	public AtlasSet<Node> getCallEvents() {
		return callSiteEvents;
	}

	public void setCallEvents(AtlasSet<Node> callEvents) {
		this.callSiteEvents = callEvents;
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
	
	public void setCallEventsFunctionsMap(AtlasMap<Node, Node> callEventsFunctionsMap) {
		this.callEventsFunctionsMap = callEventsFunctionsMap;
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

	public PCG getPCG() {
		return this.pcg;
	}

	public void setPCG(PCG pcg) {
		this.pcg = pcg;
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

	public void setMatchingPairsList(AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap) {
		this.setMatchingPairsMap(matchingPairsMap);
	}

	public AtlasMap<Node, ArrayList<MatchingPair>> getMatchingPairsMap() {
		return matchingPairsMap;
	}

	public void setMatchingPairsMap(AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap) {
		this.matchingPairsMap = matchingPairsMap;
	}

	public List<Q> getAllEvents() {
		return allEvents;
	}

	public void setAllEvents(List<Q> allEvents) {
		this.allEvents = allEvents;
	}
	
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("#######################################################################\n");
		result.append("FunctionSummary for [" + this.getFunction().getAttr(XCSG.name) + "]\n");
		result.append("E1 Events: [" + LSAPUtils.serialize(this.getE1Events()) + "]\n");
		result.append("E2 Events: [" + LSAPUtils.serialize(this.getE2Events()) + "]\n");
		result.append("#######################################################################\n");
		return result.toString();
	}
	
}
