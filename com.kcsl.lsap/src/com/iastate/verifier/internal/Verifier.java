package com.iastate.verifier.internal;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.iastate.verifier.internal.MatchingPair.VerificationResult;

public class Verifier {
	public static boolean FEASIBILITY_ENABLED = true;
	private String verificationInstanceId;
	private Graph envelope;
	private HashMap<Node, Graph> functionsGraphMap;
	private HashMap<Node, List<Q>> functionEventsMap;
	private HashMap<Node, FunctionSummary> summaries;
	private HashMap<Node, Boolean> mayEventsFeasibility;
	public HashMap<Node, HashSet<MatchingPair>> matchingPairsMap;
	
	/**
	 * Data structures to save LOCK results
	 */
	public HashSet<Node> verifiedLocks = new HashSet<Node>();
	public HashSet<Node> danglingLocks = new HashSet<Node>();
	public HashSet<Node> doubleLocks = new HashSet<Node>();
	public HashSet<Node> partiallyLocks = new HashSet<Node>();
	
	
	private AtlasSet<Node> e1Events;
	private AtlasSet<Node> e2Events;
	private AtlasSet<Node> e1MayEvents;
	
	public Verifier(String verificationId, Graph callGraph, HashMap<Node, Graph> functionsGraphMap, HashMap<Node, List<Q>> functionEventsMap, HashMap<Node, Boolean> mayEventsFeasibility){
		this.verificationInstanceId = verificationId;
		this.envelope = callGraph;
		this.functionsGraphMap = functionsGraphMap;
		this.functionEventsMap = functionEventsMap;
		this.mayEventsFeasibility = mayEventsFeasibility;
		this.matchingPairsMap = new HashMap<Node, HashSet<MatchingPair>>();
		this.summaries = new HashMap<Node, FunctionSummary>();
		
		this.e1Events = new AtlasHashSet<Node>();
		this.e1MayEvents = new AtlasHashSet<Node>();
		this.e2Events = new AtlasHashSet<Node>();
	}
	
	public Stater verify(){
		Utils.debug(1, "Verification envelope has ["+ this.envelope.nodes().size() +"] nodes.");
		
		Stater stater = new Stater();
		
		this.summarizeAndVerifyFunctions();
		
		this.aggregateVerificationResults(stater);
		
		stater.done();
		
		try {
			Utils.writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return stater;
	}
	
	private void summarizeAndVerifyFunctions(){
		List<Node> functions = Utils.topologicalSort(this.envelope);
		Collections.reverse(functions);
		
		for(Node function : functions){
			Utils.debug(2, "Generating Summary For Function:" + function.attr().get(XCSG.name));
			Utils.debug(2, "Function's outdegree:" + Utils.getChildNodes(this.envelope, function).size());
			this.summaries.put(function, this.summarizeAndVerifyFunction(function));
			//Utils.debug(0, this.summaries.get(function).toString());
		}
	}

	public FunctionSummary summarizeAndVerifyFunction(Node function){
		Graph flowGraph = this.functionsGraphMap.get(function);
		List<Q> events = this.functionEventsMap.get(function);
		
		HashMap<Node, FunctionSummary> childrenFunctionSummaries = this.getChildrenFunctionSummaries(function);
		FunctionVerifier functionVerifier = new FunctionVerifier(function, flowGraph, childrenFunctionSummaries);	
		FunctionSummary summary = functionVerifier.run(events);
		this.e1Events.addAll(summary.getE1Events());
		this.e1MayEvents.addAll(summary.getE1MayEvents());
		this.e2Events.addAll(summary.getE2Events());
		
		for(Node node : summary.getMatchingPairsMap().keySet()){
			HashSet<MatchingPair> matchingPairs = new HashSet<MatchingPair>();
			if(this.matchingPairsMap.containsKey(node)){
				matchingPairs = this.matchingPairsMap.get(node);
			}
			matchingPairs.addAll(summary.getMatchingPairsMap().get(node));
			this.matchingPairsMap.put(node, matchingPairs);
		}
		
		return summary;
	}
	
	private void aggregateVerificationResults(Stater stater){
		
		stater.setE1Events(Utils.toHashSet(this.e1Events));
		stater.setE2Events(Utils.toHashSet(this.e2Events));
		
		int outStatus;
		for(Node function : this.summaries.keySet()){
			if(Utils.getParentNodes(this.envelope, function).size() == 0){
				outStatus = this.summaries.get(function).getOuts();
				if(((outStatus & PathStatus.LOCK) != 0) || (outStatus == PathStatus.LOCK || outStatus == (PathStatus.LOCK | PathStatus.THROUGH))){
					this.appendMatchingPairs(this.summaries.get(function).getOutl());
				}
			}
		}
		
		if(this.matchingPairsMap.keySet().size() != this.e1Events.size()){
			Utils.error(0, "The matching pair map contains [" + this.matchingPairsMap.size() + "] while the size of e1Events is [" + this.e1Events.size() + "]!");
		}
		
		HashSet<Node> danglingE1Events = new HashSet<Node>();
		HashSet<Node> safeE1Events = new HashSet<Node>();
		HashSet<Node> doubleE1Events = new HashSet<Node>();
		
		for(Node e1Event : this.matchingPairsMap.keySet()){
			Utils.debug(0, "##########################################");
			Utils.debug(0, "Matching Pairs for Event [" + Utils.toString(e1Event) + "] in function [" + Utils.toString(this.getContainingFunction(e1Event)) + "]:");
			Utils.debug(0, "##########################################");
			HashSet<MatchingPair> pairs = this.matchingPairsMap.get(e1Event);
			int count = 0;
			for(MatchingPair pair : pairs){
				pair.verify(this.e1Events, this.e2Events, this.mayEventsFeasibility, this.summaries);
				Utils.debug(0, (++count) + pair.toString());
				switch(pair.getResult()){
				case DANGLING_LOCK:
					danglingE1Events.add(e1Event);
					break;
				case DOUBLE_LOCK:
					doubleE1Events.add(e1Event);
					break;
				case SAFE:
					safeE1Events.add(e1Event);
					break;
				case NOT_VALID:
					break;
				default:
					// TODO: Handle switch case
					break;
				}
			}
			Utils.debug(0, "------------------------------------------");
		}
		
		HashSet<Node> verifiedE1Events = new HashSet<Node>(safeE1Events);
		verifiedE1Events.removeAll(danglingE1Events);
		verifiedE1Events.removeAll(doubleE1Events);
		
		stater.setVerifiedE1Events(verifiedE1Events);
		for(Node e1Event : verifiedE1Events){
			this.setIntraNInterProceduralCasesCount(e1Event, stater);
		}
		
		this.verifiedLocks.addAll(verifiedE1Events);
		
		HashSet<Node> partiallyVerifiedE1Events = new HashSet<Node>(safeE1Events);
		partiallyVerifiedE1Events.removeAll(verifiedE1Events);
		stater.setPartiallyVerifiedE1Events(partiallyVerifiedE1Events);
		
		this.partiallyLocks.addAll(partiallyVerifiedE1Events);
		
		stater.setRacedE1Events(doubleE1Events);
		HashSet<Node> actualRacedEvents = new HashSet<Node>(doubleE1Events);
		actualRacedEvents.removeAll(partiallyVerifiedE1Events);
		stater.setActualRacedE1Events(actualRacedEvents);
		
		this.doubleLocks.addAll(doubleE1Events);
		
		stater.setNotVerifiedE1Events(danglingE1Events);
		HashSet<Node> actualDanglingEvents = new HashSet<Node>(danglingE1Events);
		actualDanglingEvents.removeAll(partiallyVerifiedE1Events);
		stater.setActualNotVerifiedE1Events(actualDanglingEvents);
		
		this.danglingLocks.addAll(danglingE1Events);
		
		
		stater.printResults("[" + this.verificationInstanceId + "]");
		
		HashSet<Node> missingE1Events = new HashSet<Node>();
		for(Node node : this.e1Events){
			missingE1Events.add(node);
		}
		missingE1Events.removeAll(this.matchingPairsMap.keySet());
		if(!missingE1Events.isEmpty()){
			Utils.debug(0, "##########################################");
			Utils.debug(0, "Missing (e1) Events");
			Utils.debug(0, "##########################################");
			AtlasSet<Node> missing = new AtlasHashSet<Node>();
			for(Node e : missingE1Events){
				missing.add(e);
			}
			DisplayUtil.displayGraph(Common.extend(Common.toQ(Common.toGraph(missing)), XCSG.Contains).eval(), new Highlighter(), "Missing Events");
			this.logMatchingResultsForEvents(missingE1Events, null);
			Utils.debug(0, "------------------------------------------");
		}
		
		Utils.debug(0, "##########################################");
		Utils.debug(0, "Verified (e1) Events");
		Utils.debug(0, "##########################################");
		this.logMatchingResultsForEvents(verifiedE1Events, VerificationResult.SAFE);
		Utils.debug(0, "------------------------------------------");
		
		if(!partiallyVerifiedE1Events.isEmpty()){
			Utils.debug(0, "##########################################");
			Utils.debug(0, "Partially Verified (e1) Events");
			Utils.debug(0, "##########################################");
			this.logMatchingResultsForEvents(partiallyVerifiedE1Events, null);
			Utils.debug(0, "------------------------------------------");
		}
		
		if(!actualDanglingEvents.isEmpty()){
			Utils.debug(0, "##########################################");
			Utils.debug(0, "Dangling (Not-Verified) (e1) Events");
			Utils.debug(0, "##########################################");
			this.logMatchingResultsForEvents(danglingE1Events, VerificationResult.DANGLING_LOCK);
			Utils.debug(0, "------------------------------------------");
		}
		
		if(!actualRacedEvents.isEmpty()){
			Utils.debug(0, "##########################################");
			Utils.debug(0, "Raced (e1) Events");
			Utils.debug(0, "##########################################");
			this.logMatchingResultsForEvents(doubleE1Events, VerificationResult.DOUBLE_LOCK);
			Utils.debug(0, "------------------------------------------");
		}
	}
	
	private void appendMatchingPairs(AtlasSet<Node> nodes){
		for(Node node : nodes){
			HashSet<MatchingPair> matchingPairs = new HashSet<MatchingPair>();
	    	if(this.matchingPairsMap.containsKey(node)){
	    		matchingPairs = this.matchingPairsMap.get(node);
	    	}
	    	FunctionSummary summary = this.summaries.get(this.getContainingFunction(node));
	    	matchingPairs.add(new MatchingPair(node, summary.getExitNode(), null));
	    	this.matchingPairsMap.put(node, matchingPairs);
		}
	}
	
	private HashMap<Node, FunctionSummary> getChildrenFunctionSummaries(Node function){
		HashMap<Node, FunctionSummary> childrenFunctionSummaries = new HashMap<Node, FunctionSummary>();
		
		AtlasSet<Node> children = Utils.getChildNodes(this.envelope, function);

		for(Node child : children)
			childrenFunctionSummaries.put(child, this.summaries.get(child));
		
		return childrenFunctionSummaries;	
	}
	
	private void setIntraNInterProceduralCasesCount(Node e1Event, Stater stater) {
		HashSet<MatchingPair> pairs = this.matchingPairsMap.get(e1Event);
		for(MatchingPair pair : pairs){
			if(pair.getSecondEvent() != null){
				if(!this.getContainingFunction(pair.getSecondEvent()).equals(this.getContainingFunction(pair.getFirstEvent()))){
					//stater.setInterproceduralVerification(stater.getInterproceduralVerification() + 1);
					HashSet<Node> cases = stater.getInterproceduralVerification();
					cases.add(e1Event);
					stater.setInterproceduralVerification(cases);
					return;
				}
			}
		}
		//stater.setIntraproceduralVerification(stater.getIntraproceduralVerification() + 1);
		HashSet<Node> cases = stater.getIntraproceduralVerification();
		cases.add(e1Event);
		stater.setIntraproceduralVerification(cases);
	}
	
	public Node getContainingFunction(Node node){
		for(Node function : this.summaries.keySet()){
			if(this.summaries.get(function).getFlowGraph().nodes().contains(node))
				return function;
		}
		return null;
	}
	
	private void logMatchingResultsForEvents(HashSet<Node> e1Events, VerificationResult verificationResult) {
		for(Node e1Event : e1Events){
			Utils.debug(0, "\t(e1) Event: [" + Utils.toString(e1Event, true) + "] in Function [" + Utils.toString(this.getContainingFunction(e1Event)) + "]");
			HashSet<MatchingPair> pairs = this.matchingPairsMap.get(e1Event);
			if(pairs == null){
				Utils.debug(0, "\t\tNo Matchings!");
				continue;
			}
			for(MatchingPair pair : pairs){
				if(verificationResult == null || pair.getResult().equals(verificationResult)){
					Utils.debug(0, "\t\tMatched with Event: [" + Utils.toString(pair.getSecondEvent()) + "] in Function [" + (pair.getSecondEvent() == null ? Utils.toString(this.getContainingFunction(e1Event)) : Utils.toString(this.getContainingFunction(pair.getSecondEvent()))) + "]");
					//Utils.debug(0, "\t\tPath [" + (++count) + "] has [" + pair.getResult().name() + "]: " +  pair.getPath());
				}
			}
		}
	}
}
