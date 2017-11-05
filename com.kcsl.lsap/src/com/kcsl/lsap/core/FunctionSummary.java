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
import com.kcsl.lsap.core.FunctionVerifier.PathStatus;
import com.kcsl.lsap.feasibility.FeasibilityChecker;
import com.kcsl.lsap.utils.LSAPUtils;

/**
 * A class corresponding to the function summary for a {@link XCSG#Function}.
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
	 * A mapping between a callsite {@link Node} and its corresponding {@link XCSG#Function} node.
	 */
	private AtlasMap<Node, Node> callEventsFunctionsMap;
	
	/**
	 * An instance of {@link FeasibilityChecker} to perform feasibility operations.
	 */
	private FeasibilityChecker feasibilityChecker;
	
    /**
     * An integer corresponding to the current {@link PathStatus} for {@link #function}.
     */
	private int nodeToPathStatus;
	
	/**
	 * A list of {@link XCSG#ControlFlow_Node} containing events of interest in {@link #function}.
	 */
	private AtlasSet<Node> nodeToEventsAlongPath;
	
    /**
     * A integer corresponding to current {@link PathStatus} at {@link #function} from its successors.
     */
	private int nodeToPathStatusFromSuccessors;
	
	/**
	 * A list of {@link XCSG#ControlFlow_Node} containing events of interest up to {@link #function} from its successors.
	 */
	private AtlasSet<Node> nodeToEventsAlongPathFromSuccessors;
	
	/**
	 * A mapping of {@link Node} corresponding to a lock function call to a set of its {@link MatchingPair}s.
	 */
	private AtlasMap<Node, ArrayList<MatchingPair>> matchingPairsMap;

	/**
	 * Constructs a new instance of {@link FunctionSummary} for <code>function</code>, its <code>pcg</code> and <code>events</code>.
	 * @param function
	 * @param pcg
	 * @param events
	 */
	public FunctionSummary(Node function, PCG pcg, List<Q> events) {
		this.setFunction(function);
		this.setPCG(pcg);
		this.setAllEvents(events);
		this.setLockFunctionCallEvents(events.get(0).eval().nodes());
		this.setUnlockFunctionCallEvents(events.get(1).eval().nodes());
		this.setCallSiteEvents(events.get(2).eval().nodes());
		this.setMultiStateLockFunctionCallEvents(events.get(3).eval().nodes());
		this.setNodeToEventsAlongPath(new AtlasHashSet<Node>());
		this.setNodeToEventsAlongPathFromSuccessors(new AtlasHashSet<Node>());
		this.setFeasibilityChecker(null);
	}
	
	public Node getFunctionElementForCallEvent(Node node){
		return this.callEventsFunctionsMap.get(node);
	}
	
	public AtlasMap<Node, Node> getCallEventsFunctionsMap(){
		return this.callEventsFunctionsMap;
	}

	public AtlasSet<Node> getLockFunctionCallEvents() {
		return lockFunctionCallEvents;
	}

	public void setLockFunctionCallEvents(AtlasSet<Node> e1Events) {
		this.lockFunctionCallEvents = e1Events;
	}

	public AtlasSet<Node> getUnlockFunctionCallEvents() {
		return unlockFunctionCallEvents;
	}

	public void setUnlockFunctionCallEvents(AtlasSet<Node> e2Events) {
		this.unlockFunctionCallEvents = e2Events;
	}

	public AtlasSet<Node> getE1MayEvents() {
		return multiStateLockFunctionCallEvents;
	}

	public void setMultiStateLockFunctionCallEvents(AtlasSet<Node> e1MayEvents) {
		this.multiStateLockFunctionCallEvents = e1MayEvents;
	}

	public AtlasSet<Node> getCallEvents() {
		return callSiteEvents;
	}

	public void setCallSiteEvents(AtlasSet<Node> callEvents) {
		this.callSiteEvents = callEvents;
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
			this.feasibilityChecker = new FeasibilityChecker(Common.toQ(this.getFunction()));
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

	public int getNodeToPathStatusFromSuccessors() {
		return nodeToPathStatusFromSuccessors;
	}

	public void setNodeToPathStatusFromSuccessors(int nodeToPathStatusFromSuccessors) {
		this.nodeToPathStatusFromSuccessors = nodeToPathStatusFromSuccessors;
	}

	public AtlasSet<Node> getNodeToEventsAlongPathFromSuccessors() {
		return nodeToEventsAlongPathFromSuccessors;
	}

	public void setNodeToEventsAlongPathFromSuccessors(AtlasSet<Node> nodeToEventsAlongPathFromSuccessors) {
		this.nodeToEventsAlongPathFromSuccessors = nodeToEventsAlongPathFromSuccessors;
	}

	public AtlasSet<Node> getNodeToEventsAlongPath() {
		return nodeToEventsAlongPath;
	}

	public void setNodeToEventsAlongPath(AtlasSet<Node> nodeToEventsAlongPath) {
		this.nodeToEventsAlongPath = nodeToEventsAlongPath;
	}

	public int getNodeToPathStatus() {
		return nodeToPathStatus;
	}

	public void setNodeToPathStatus(int nodeToPathStatus) {
		this.nodeToPathStatus = nodeToPathStatus;
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
		result.append("Lock Events: [" + LSAPUtils.serialize(this.getLockFunctionCallEvents()) + "]\n");
		result.append("Unlock Events: [" + LSAPUtils.serialize(this.getUnlockFunctionCallEvents()) + "]\n");
		result.append("#######################################################################\n");
		return result.toString();
	}
	
}
