package com.kcsl.lsap.verifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.list.AtlasArrayList;
import com.ensoftcorp.atlas.core.db.list.AtlasList;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG.PCGNode;
import com.kcsl.lsap.VerificationProperties;
import com.kcsl.lsap.feasibility.FeasibilityChecker;
import com.kcsl.lsap.utils.LSAPUtils;

/**
 * A class that records a matching results from the verification process.
 */
public class MatchingPair {

	/**
	 * The first {@link Node} matched with {@link #secondEvent}.
	 */
	private Node firstEvent;
	
	/**
	 * The second {@link Node} matched with {@link #firstEvent}.
	 */
	private Node secondEvent;
	
	/**
	 * The list of {@link Node}s corresponding to the path where this matching occurs.
	 */
	private AtlasList<Node> path;
	
	/**
	 * The other event {@link Node}s that should be excluded from the {@link #path} when processing the match.
	 */
	private AtlasSet<Node> excludedNodes;
	
	/**
	 * The {@link VerificationResult} that led to this instance of {@link MatchingPair}.
	 */
	private VerificationResult result;
	
	/**
	 * An enumeration for the possible outcomes of the verification process.
	 */
	public enum VerificationResult {
		SAFE, DEADLOCKED, DANGLING_LOCK, NOT_VALID
	}

	/**
	 * Constructs a new instance of {@link MatchingPair} with <code>firstEvent</code> and <code>secondEvent</code> nodes along the <code>path</code>.
	 * @param firstEvent See corresponding field.
	 * @param secondEvent See corresponding field.
	 * @param path See corresponding field.
	 */
	public MatchingPair(Node firstEvent, Node secondEvent, List<Node> path) {
		this.result = null;
		this.setFirstEvent(firstEvent);
		this.setSecondEvent(secondEvent);
		this.path = new AtlasArrayList<Node>();
		this.path.add(this.getFirstEvent());
		this.path.add(this.getSecondEvent());
	}
	
	/**
	 * Computes the verification results for this instance of {@link MatchingPair}.
	 * 
	 * @param lockCallEvents A list of {@link Node}s corresponding to lock call events.
	 * @param unlockCallEvents A list of {@link Node}s corresponding to unlock call events.
	 * @param mayEventsFeasibility A list of {@link Node} that has multiple lock states.
	 * @param summaries A mapping between a {@link Node} to its corresponding {@link FunctionSummary}.
	 */
	public void verify(AtlasSet<Node> lockCallEvents, AtlasSet<Node> unlockCallEvents, AtlasMap<Node, Boolean> mayEventsFeasibility, AtlasMap<Node, FunctionSummary> summaries) {
		if (this.excludedNodes == null) {
			this.excludedNodes = new AtlasHashSet<Node>();
			for (Node node : lockCallEvents) {
				excludedNodes.add(node);
			}

			for (Node node : unlockCallEvents) {
				excludedNodes.add(node);
			}
		}

		// The first event correspond to a (mayEvent). That means, it may be not an actual event on specific path
		if (VerificationProperties.isFeasibilityCheckingEnabled()) {
			if (mayEventsFeasibility.containsKey(this.getFirstEvent())) {
				boolean lockOnTrueBranch = mayEventsFeasibility.get(this.getFirstEvent());
				Node containingFunction = CommonQueries.getContainingFunction(this.getFirstEvent());
				FunctionSummary s = summaries.get(containingFunction);
				AtlasList<Node> p = this.getPathContainingNode(s.getFeasibilityChecker());
				if (p == null || p.isEmpty()) {
					this.setResult(VerificationResult.DEADLOCKED);
					return;
				}

				Node nextElement = p.get(p.indexOf(this.getFirstEvent()) + 1);

				Edge edge = LSAPUtils.findDirectEdgesBetweenNodes(s.getFeasibilityChecker().getLoopFreeCFGGraph(), this.getFirstEvent(), nextElement).get(0);
				String conditionValue = edge.getAttr(XCSG.conditionValue).toString().toLowerCase();
				if (conditionValue.equals("true") && !lockOnTrueBranch) {
					this.setResult(VerificationResult.NOT_VALID);
					return;
				} else if (conditionValue.equals("false") && lockOnTrueBranch) {
					this.setResult(VerificationResult.NOT_VALID);
					return;
				} else {
					// TODO: Handle other cases specially (switch) statements
				}
			}
		}

		// The first event is a locking event
		if (this.getSecondEvent().taggedWith(PCGNode.PCGMasterExit)) {
			// Lock is not followed by Unlock (Error Case)
			if (!VerificationProperties.isFeasibilityCheckingEnabled() || this.checkPathFeasibility(summaries)) {
				// Path is (Feasible) >> An actual (Error Case)
				this.setResult(VerificationResult.DANGLING_LOCK);
			} else {
				this.setResult(VerificationResult.SAFE);
			}
		} else {
			if (lockCallEvents.contains(this.getSecondEvent())) {
				// Lock followed by Lock (Error Case)
				if (!VerificationProperties.isFeasibilityCheckingEnabled() || this.checkPathFeasibility(summaries)) {
					// Path is (Feasible) >> An actual (Error Case)
					this.setResult(VerificationResult.DEADLOCKED);
				} else {
					this.setResult(VerificationResult.SAFE);
				}
			} else {
				// Lock followed by Unlock (Safe Case)
				this.setResult(VerificationResult.SAFE);
			}
		}
	}
	
	/**
	 * Checks the path feasibility for this instance of {@link MatchingPair}. The feasibility check in intra-procedural.
	 * 
	 * @param summaries A mapping between a {@link Node} and its corresponding {@link FunctionSummary}.
	 * @return true if the path is feasible, otherwise false.
	 */
	private boolean checkPathFeasibility(AtlasMap<Node, FunctionSummary> summaries) {
		Node functionForE1 = CommonQueries.getContainingFunction(this.getFirstEvent());
		Node functionForE2 = CommonQueries.getContainingFunction(this.getSecondEvent());
		if (functionForE1.equals(functionForE2)) {
			// The two events are in the same function
			FunctionSummary summary = summaries.get(functionForE1);
			FeasibilityChecker feasibilityChecker = summary.getFeasibilityChecker();
			if (this.getSecondEvent().taggedWith(PCGNode.PCGMasterExit)) {
				return feasibilityChecker.checkPathFeasibility(this.getFirstEvent(), null, this.excludedNodes);
			}
			return feasibilityChecker.checkPathFeasibility(this.getFirstEvent(), this.getSecondEvent(),
					this.excludedNodes);
		} else {
			// Two events are in different functions
			FunctionSummary summaryF1 = summaries.get(functionForE1);
			FeasibilityChecker feasibilityCheckerF1 = summaryF1.getFeasibilityChecker();
			boolean isFeasibleF1 = feasibilityCheckerF1.checkPathFeasibility(this.getFirstEvent(), null,
					this.excludedNodes);

			FunctionSummary summaryF2 = summaries.get(functionForE2);
			FeasibilityChecker feasibilityCheckerF2 = summaryF2.getFeasibilityChecker();
			boolean isFeasibleF2 = false;
			if (this.getSecondEvent().taggedWith(PCGNode.PCGMasterExit)) {
				isFeasibleF2 = feasibilityCheckerF2.checkPathFeasibility(null, null, this.excludedNodes);
			} else {
				isFeasibleF2 = feasibilityCheckerF2.checkPathFeasibility(null, this.getSecondEvent(),
						this.excludedNodes);
			}
			return (isFeasibleF1 && isFeasibleF2);
		}
	}
	
	/**
	 * Get the matching event for the {@link #firstEvent} within a function.
	 * 
	 * @return An array of two elements: the first element is {@link #firstEvent}, the second element can be {@link #secondEvent} if its within
	 * the containing function of {@link #firstEvent}, otherwise the second element is null.
	 */
	private Node[] getEventsWithRespectToFirstEvent() {
		Node functionForE1 = CommonQueries.getContainingFunction(this.getFirstEvent());
		Node functionForE2 = CommonQueries.getContainingFunction(this.getSecondEvent());
		if (functionForE1.equals(functionForE2)) {
			// The two events are in the same function
			if (this.getSecondEvent().taggedWith(PCGNode.PCGMasterExit)) {
				return new Node[] { this.getFirstEvent(), null };
			}
			return new Node[] { this.getFirstEvent(), this.getSecondEvent() };
		}
		// Two events are in different functions
		return new Node[] { this.getFirstEvent(), null };
	}
	
	/**
	 * Finds a list of {@link Node}s corresponding to path containing the {@link #firstEvent} and {@link #secondEvent}.
	 * 
	 * @param feasibilityChecker An instance of {@link FeasibilityChecker} to be used in finding the path.
	 * @return A list of {@link Node}s or null if the path cannot be found.
	 */
	private AtlasList<Node> getPathContainingNode(FeasibilityChecker feasibilityChecker) {
		Node[] nodes = this.getEventsWithRespectToFirstEvent();
		ArrayList<AtlasList<Node>> allPaths = feasibilityChecker.getPathsContainingNodes(this.getFirstEvent(), nodes[1], this.excludedNodes);
		if(allPaths.size() == 1 || !this.getFirstEvent().tags().contains(XCSG.ControlFlowCondition)){
			return allPaths.get(0);
		}
		if(!allPaths.isEmpty()){
			// If its a condition, then we need to get the paths containing the node correctly
			// For example: if we have a (mutex_trylock) T-> (EXIT Node)
			//                           (mutex_trylock) F-> (Event Node) -> (Exit Node)
			HashMap<Integer, AtlasList<Node>> pathIDs = new HashMap<Integer, AtlasList<Node>>();
			for(AtlasList<Node> returnedPath : allPaths){
				List<Node> subList = returnedPath.subList(returnedPath.indexOf(this.getFirstEvent()) + 1, returnedPath.size());
				pathIDs.put(subList.size(), new AtlasArrayList<Node>(returnedPath));
			}
			if(!pathIDs.isEmpty()){
				List<Integer> temp = new ArrayList<Integer>(pathIDs.keySet());
				Collections.sort(temp);
				return pathIDs.get(temp.get(0));
			}
		}
		LSAPUtils.log("Cannot find a path containing the first node [" + this.getFirstEvent().getAttr(XCSG.name) + "] and the second node [" + (nodes[1] == null ? "NULL" : nodes[1].getAttr(XCSG.name)) + "]!");
		return null;
	}

	public Node getFirstEvent() {
		return firstEvent;
	}

	public void setFirstEvent(Node e1Event) {
		this.firstEvent = e1Event;
	}

	public Node getSecondEvent() {
		return secondEvent;
	}

	public void setSecondEvent(Node e2Event) {
		this.secondEvent = e2Event;
	}
	
	public VerificationResult getResult() {
		return result;
	}

	public void setResult(VerificationResult result) {
		this.result = result;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((firstEvent == null) ? 0 : firstEvent.hashCode());
		result = prime * result + ((secondEvent == null) ? 0 : secondEvent.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MatchingPair other = (MatchingPair) obj;
		if (firstEvent == null) {
			if (other.firstEvent != null)
				return false;
		} else if (!firstEvent.equals(other.firstEvent))
			return false;
		if (secondEvent == null) {
			if (other.secondEvent != null)
				return false;
		} else if (!secondEvent.equals(other.secondEvent))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "\t\tMatching Pair [" + (this.getResult() == null ? "UNKNOWN" : this.getResult().toString()) + "]:" + this.getFirstEvent().getAttr(XCSG.name) + " >>> " + this.getSecondEvent() == null ? "NULL" : (String)this.getSecondEvent().getAttr(XCSG.name);
	}
	
}
