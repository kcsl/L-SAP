package com.iastate.verifier.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.iastate.atlas.scripts.FeasibilityChecker;
import com.iastate.atlas.scripts.Queries;

public class MatchingPair {

	private GraphElement firstEvent; // The first event is always a LOCKing event
	private GraphElement secondEvent;
	private List<GraphElement> path;
	private HashSet<GraphElement> excludedNodes;
	private VerificationResult result;
	
	 public enum VerificationResult{
		 SAFE, DOUBLE_LOCK, DANGLING_LOCK, NOT_VALID
	 }

	public MatchingPair(GraphElement e1, GraphElement e2, List<GraphElement> path) {
		this.result = null;
		this.setFirstEvent(e1);
		this.setSecondEvent(e2);
		//TODO: Report actual path
		this.path = new ArrayList<GraphElement>();
		this.path.add(this.getFirstEvent());
		this.path.add(this.getSecondEvent());
	}
	
	/**
	 * Verify a given path and returns the verification result
	 * @param e1Events
	 * @param e2Events
	 * @param mayEventsFeasibility 
	 * @param summaries
	 * @return
	 */
	public void verify(AtlasSet<GraphElement> e1Events, AtlasSet<GraphElement> e2Events, HashMap<GraphElement, Boolean> mayEventsFeasibility, HashMap<GraphElement, FunctionSummary> summaries){
		if(this.excludedNodes == null){
			this.excludedNodes = new HashSet<GraphElement>();
			for(GraphElement node : e1Events){
				excludedNodes.add(node);
			}
			
			for(GraphElement node : e2Events){
				excludedNodes.add(node);
			}
		}
		
		// The first event correspond to a (mayEvent). That means, it may be not an actual event on specific path
		if(Verifier.FEASIBILITY_ENABLED){
			if(mayEventsFeasibility.containsKey(this.getFirstEvent())){
				boolean lockOnTrueBranch = mayEventsFeasibility.get(this.getFirstEvent());
				GraphElement containingFunction = this.getContainingFunction(this.getFirstEvent(), summaries);
				FunctionSummary s = summaries.get(containingFunction);
				ArrayList<GraphElement> p = this.getPathContainingNode(s.getFeasibilityChecker(), summaries);
				if(p == null || p.isEmpty()){
					this.setResult(VerificationResult.DOUBLE_LOCK);
					return;					
				}
				
				GraphElement nextElement = p.get(p.indexOf(this.getFirstEvent()) + 1);
				
				GraphElement edge = Utils.findEdge(s.getFeasibilityChecker().getFunctionCFG(), this.getFirstEvent(), nextElement);
				String conditionValue = edge.attr().get(XCSG.conditionValue).toString().toLowerCase();
				//Utils.debug(0, "$$$$$$$$$$$$$$:\t" + node.attr().get(XCSG.name) + "\t" +  conditionValue + "\t" + lockOnTrueBranch);
				if(conditionValue.equals("true") && !lockOnTrueBranch){
					this.setResult(VerificationResult.NOT_VALID);
					return;
				}else if(conditionValue.equals("false") && lockOnTrueBranch){
					this.setResult(VerificationResult.NOT_VALID);
					return;
				}else{
					//TODO: Handle other cases specially (switch) statements
				}
			}
		}
		
		// The first event is a locking event
		if(this.getSecondEvent().attr().get(XCSG.name).equals(Queries.EVENT_FLOW_EXIT_NODE)){
			// Lock is not followed by Unlock (Error Case)
			if(!Verifier.FEASIBILITY_ENABLED || this.checkPathFeasibility(summaries)){
				// Path is (Feasible) >> An actual (Error Case)
				this.setResult(VerificationResult.DANGLING_LOCK);
			}else{
				this.setResult(VerificationResult.SAFE);
			}
		}else{
			if(e1Events.contains(this.getSecondEvent())){
				// Lock followed by Lock (Error Case)
				if(!Verifier.FEASIBILITY_ENABLED || this.checkPathFeasibility(summaries)){
					// Path is (Feasible) >> An actual (Error Case)
					this.setResult(VerificationResult.DOUBLE_LOCK);
				}else{
					this.setResult(VerificationResult.SAFE);
				}
			}else{
				// Lock followed by Unlock (Safe Case)
				this.setResult(VerificationResult.SAFE);
			}
		}
	}
	
	private boolean checkPathFeasibility(HashMap<GraphElement, FunctionSummary> summaries){
		GraphElement functionForE1 = this.getContainingFunction(this.getFirstEvent(), summaries);
		GraphElement functionForE2 = this.getContainingFunction(this.getSecondEvent(), summaries);
		if(functionForE1.equals(functionForE2)){
			// The two events are in the same function
			FunctionSummary summary = summaries.get(functionForE1);
			FeasibilityChecker feasibilityChecker = summary.getFeasibilityChecker();
			if(this.getSecondEvent().attr().get(XCSG.name).equals(Queries.EVENT_FLOW_EXIT_NODE)){
				return feasibilityChecker.checkPathFeasibility(this.getFirstEvent(), null, this.excludedNodes);
			}
			return feasibilityChecker.checkPathFeasibility(this.getFirstEvent(), this.getSecondEvent(), this.excludedNodes);
		}else{
			// Two events are in different functions
			FunctionSummary summaryF1 = summaries.get(functionForE1);
			FeasibilityChecker feasibilityCheckerF1 = summaryF1.getFeasibilityChecker();
			boolean isFeasibleF1 = feasibilityCheckerF1.checkPathFeasibility(this.getFirstEvent(), null, this.excludedNodes);
			
			FunctionSummary summaryF2 = summaries.get(functionForE2);
			FeasibilityChecker feasibilityCheckerF2 = summaryF2.getFeasibilityChecker();
			boolean isFeasibleF2 = false;
			if(this.getSecondEvent().attr().get(XCSG.name).equals(Queries.EVENT_FLOW_EXIT_NODE)){
				isFeasibleF2 = feasibilityCheckerF2.checkPathFeasibility(null, null, this.excludedNodes);
			}else{
				isFeasibleF2 = feasibilityCheckerF2.checkPathFeasibility(null, this.getSecondEvent(), this.excludedNodes);
			}
			return (isFeasibleF1 && isFeasibleF2);
		}
	}
	
	private GraphElement [] getEventsWithRespectToFirstEvent(HashMap<GraphElement, FunctionSummary> summaries){
		GraphElement functionForE1 = this.getContainingFunction(this.getFirstEvent(), summaries);
		GraphElement functionForE2 = this.getContainingFunction(this.getSecondEvent(), summaries);
		if(functionForE1.equals(functionForE2)){
			// The two events are in the same function
			if(this.getSecondEvent().attr().get(XCSG.name).equals(Queries.EVENT_FLOW_EXIT_NODE)){
				return new GraphElement [] {this.getFirstEvent(), null};
			}
			return new GraphElement [] {this.getFirstEvent(), this.getSecondEvent()};
		}
		// Two events are in different functions
		return new GraphElement [] {this.getFirstEvent(), null};
	}
	
	private GraphElement getContainingFunction(GraphElement node, HashMap<GraphElement, FunctionSummary> summaries){
		for(GraphElement function : summaries.keySet()){
			if(summaries.get(function).getFlowGraph().nodes().contains(node))
				return function;
		}
		return null;
	}
	
	public void setPath(List<GraphElement> path) {
		this.path = path;
	}
	
	public String getPath(){
		String result = "";
		for(int i = 0; i < path.size(); i++){
			result += path.get(i).attr().get(XCSG.name);
			if(i < path.size() - 1){
				result += " >>> ";
			}
		}
		return result;
	}

	public GraphElement getFirstEvent() {
		return firstEvent;
	}

	public void setFirstEvent(GraphElement e1Event) {
		this.firstEvent = e1Event;
	}

	public GraphElement getSecondEvent() {
		return secondEvent;
	}

	public void setSecondEvent(GraphElement e2Event) {
		this.secondEvent = e2Event;
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

	public VerificationResult getResult() {
		return result;
	}

	public void setResult(VerificationResult result) {
		this.result = result;
	}
	
	@Override
	public String toString() {
		return "\t\tMatching Pair [" + (this.getResult() == null ? "UNKNOWN" : this.getResult().toString()) + "]:" + Utils.toString(this.getFirstEvent()) + " >>> " + Utils.toString(this.getSecondEvent());
	}
	
	private ArrayList<GraphElement> getPathContainingNode(FeasibilityChecker feasibilityChecker, HashMap<GraphElement, FunctionSummary> summaries) {
		GraphElement [] nodes = this.getEventsWithRespectToFirstEvent(summaries);
		ArrayList<ArrayList<GraphElement>> allPaths = feasibilityChecker.getPathsContainingNodes(this.getFirstEvent(), nodes[1], this.excludedNodes);
		if(allPaths.size() == 1 || !this.getFirstEvent().tags().contains(XCSG.ControlFlowCondition)){
			return allPaths.get(0);
		}
		if(!allPaths.isEmpty()){
			// If its a condition, then we need to get the paths containing the node correctly
			// For example: if we have a (mutex_trylock) T-> (EXIT Node)
			//                           (mutex_trylock) F-> (Event Node) -> (Exit Node)
			HashMap<Integer, ArrayList<GraphElement>> pathIDs = new HashMap<Integer, ArrayList<GraphElement>>();
			for(ArrayList<GraphElement> returnedPath : allPaths){
				List<GraphElement> subList = returnedPath.subList(returnedPath.indexOf(this.getFirstEvent()) + 1, returnedPath.size());
				pathIDs.put(subList.size(), returnedPath);
			}
			if(!pathIDs.isEmpty()){
				List<Integer> temp = new ArrayList<Integer>(pathIDs.keySet());
				Collections.sort(temp);
				return pathIDs.get(temp.get(0));
			}
		}
		Utils.error(0, "Cannot find a path containing the first node [" + Utils.toString(this.getFirstEvent()) + "] and the second node [" + (nodes[1] == null ? "NULL" : Utils.toString(nodes[1])) + "]!");
		return null;
	}
}