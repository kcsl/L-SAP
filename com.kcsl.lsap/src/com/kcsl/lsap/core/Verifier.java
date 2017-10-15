package com.kcsl.lsap.core;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.map.AtlasGraphKeyHashMap;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG;
import com.kcsl.lsap.core.LockVerificationGraphsGenerator.STATUS;
import com.kcsl.lsap.utils.LSAPUtils;
import com.kcsl.lsap.verifier.MatchingPair.VerificationResult;

public class Verifier {
	private Path graphsOutputDirectoryPath;
	private Node signatureNode;
	private String verificationInstanceId;
	
	/**
	 * An MPG with leaves being the lock/unlock function calls.
	 */
	private Q fullMpg;
	
	/**
	 * An MPG without the leaves of the {@link #fullMpg}.
	 */
	private Q mpg;
	private HashMap<Node, PCG> functionsPCGMap;
	private HashMap<Node, List<Q>> functionEventsMap;
	private HashMap<Node, FunctionSummary> summaries;
	private HashMap<Node, Boolean> mayEventsFeasibility;
	public AtlasMap<Node, HashSet<MatchingPair>> matchingPairsMap;
	
	/**
	 * Data structures to save LOCK results
	 */
	private AtlasSet<Node> verifiedLocks;
	private AtlasSet<Node> danglingLocks;
	private AtlasSet<Node> doubleLocks;
	private AtlasSet<Node> partiallyLocks;
	
	private AtlasSet<Node> e1Events;
	private AtlasSet<Node> e2Events;
	private AtlasSet<Node> e1MayEvents;
	
	public Verifier(Node signatureNode, Q mpg, HashMap<Node, PCG> functionsPCGMap, HashMap<Node, List<Q>> functionEventsMap, HashMap<Node, Boolean> mayEventsFeasibility, Path graphsOutputDirectoryPath){
		this.signatureNode = signatureNode;
		this.verificationInstanceId = this.signatureNode.getAttr(XCSG.name) + "(" + this.signatureNode.addressBits() + ")";;
		this.fullMpg = mpg;
		this.mpg = this.fullMpg.difference(this.fullMpg.leaves());
		this.functionsPCGMap = functionsPCGMap;
		this.functionEventsMap = functionEventsMap;
		this.mayEventsFeasibility = mayEventsFeasibility;
		this.matchingPairsMap = new AtlasGraphKeyHashMap<Node, HashSet<MatchingPair>>();
		this.summaries = new HashMap<Node, FunctionSummary>();
		
		this.verifiedLocks = new AtlasHashSet<Node>();
		this.danglingLocks = new AtlasHashSet<Node>();
		this.doubleLocks = new AtlasHashSet<Node>();
		this.partiallyLocks = new AtlasHashSet<Node>();
		
		this.e1Events = new AtlasHashSet<Node>();
		this.e1MayEvents = new AtlasHashSet<Node>();
		this.e2Events = new AtlasHashSet<Node>();
		this.graphsOutputDirectoryPath = graphsOutputDirectoryPath;
	}
	
	public Reporter verify(Node lockNode){
		LSAPUtils.log("MPG has ["+ this.mpg.eval().nodes().size() +"] nodes.");
		Reporter reporter = new Reporter();
		
		List<Node> functions = LSAPUtils.topologicalSort(this.mpg);
		Collections.reverse(functions);
		
		for(Node function : functions){
			LSAPUtils.log("Generating Summary For Function:" + function.attr().get(XCSG.name));
			long outDegree = this.mpg.successors(Common.toQ(function)).eval().nodes().size();
			LSAPUtils.log("Function's outdegree:" + outDegree);
			this.summaries.put(function, this.summarizeAndVerifyFunction(function));
		}
		
		this.aggregateVerificationResults(reporter);
		reporter.done();
		
		if(reporter != null && VerificationProperties.isSaveVerificationGraphs()){
			this.saveLockVerificationGraphs(lockNode);
		}
		
		return reporter;
	}
	
	private void saveLockVerificationGraphs(Node lockNode){
		Q verifiedLocks = Common.toQ(this.verifiedLocks);
		Q doubleLocks = Common.toQ(this.doubleLocks);
		Q danglingLocks = Common.toQ(this.danglingLocks);
		Q partiallyLocks = Common.toQ(this.partiallyLocks);
		
		LockVerificationGraphsGenerator lockVerificationGraphsGenerator = new LockVerificationGraphsGenerator(this.signatureNode, this.fullMpg, this.matchingPairsMap, this.graphsOutputDirectoryPath);
		
		// A paired lock is never partially paired or unpaired or deadlock
		Q pairedLocks = verifiedLocks.difference(partiallyLocks, danglingLocks, doubleLocks);
		for(Node lock : pairedLocks.eval().nodes()){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, STATUS.PAIRED);
			}
		}
		
		// A partially paired lock should not be a deadlock
		Q partiallyPairedLocks = partiallyLocks.difference(doubleLocks);
		for(Node lock : partiallyPairedLocks.eval().nodes()){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, STATUS.PARTIALLY_PAIRED);
			}
		}
		
		// A deadlock lock is only if it has deadlock
		for(Node lock : this.doubleLocks){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, STATUS.DEADLOCK);
			}
		}
		
		// An unpaired lock cannot be partially paired or paired
		Q unpairedLocks = danglingLocks.difference(verifiedLocks, partiallyLocks);
		for(Node lock : unpairedLocks.eval().nodes()){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, STATUS.UNPAIRED);
			}
		}
	}

	public FunctionSummary summarizeAndVerifyFunction(Node function){
		PCG pcg = this.functionsPCGMap.get(function);
		List<Q> events = this.functionEventsMap.get(function);
		
		HashMap<Node, FunctionSummary> successorFunctionSummaries = new HashMap<Node, FunctionSummary>();
		AtlasSet<Node> successors = this.mpg.successors(Common.toQ(function)).eval().nodes();
		for(Node successor : successors)
			successorFunctionSummaries.put(successor, this.summaries.get(successor));
		
		FunctionVerifier functionVerifier = new FunctionVerifier(function, pcg, successorFunctionSummaries);	
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
	
	private void aggregateVerificationResults(Reporter reporter){
		reporter.setLockEvents(this.e1Events);
		reporter.setUnlockEvents(this.e2Events);
		
		int outStatus;
		for(Node function : this.summaries.keySet()){
			if(this.mpg.predecessors(Common.toQ(function)).eval().nodes().isEmpty()){
				outStatus = this.summaries.get(function).getOuts();
				if(((outStatus & PathStatus.LOCK) != 0) || (outStatus == PathStatus.LOCK || outStatus == (PathStatus.LOCK | PathStatus.THROUGH))){
					this.appendMatchingPairs(this.summaries.get(function).getOutl());
				}
			}
		}
		
		if(this.matchingPairsMap.keySet().size() != this.e1Events.size()){
			LSAPUtils.log("The matching pair map contains [" + this.matchingPairsMap.size() + "] while the size of e1Events is [" + this.e1Events.size() + "]!");
		}
		
		AtlasSet<Node> danglingE1Events = new AtlasHashSet<Node>();
		AtlasSet<Node> safeE1Events = new AtlasHashSet<Node>();
		AtlasSet<Node> doubleE1Events = new AtlasHashSet<Node>();
		
		for(Node e1Event : this.matchingPairsMap.keySet()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Matching Pairs for Event [" + e1Event.getAttr(XCSG.name) + "] in function [" + CommonQueries.getContainingFunction(e1Event).getAttr(XCSG.name) + "]:");
			LSAPUtils.log("##########################################");
			HashSet<MatchingPair> pairs = this.matchingPairsMap.get(e1Event);
			int count = 0;
			for(MatchingPair pair : pairs){
				pair.verify(this.e1Events, this.e2Events, this.mayEventsFeasibility, this.summaries);
				LSAPUtils.log((++count) + pair.toString());
				switch(pair.getResult()){
				case DANGLING_LOCK:
					danglingE1Events.add(e1Event);
					break;
				case DEADLOCKED:
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
			LSAPUtils.log("------------------------------------------");
		}
		
		AtlasSet<Node> verifiedLockEvents = new AtlasHashSet<Node>(safeE1Events);
		verifiedLockEvents = Common.toQ(verifiedLockEvents).difference(Common.toQ(danglingE1Events).union(Common.toQ(doubleE1Events))).eval().nodes();
		
		reporter.setVerifiedLockEvents(verifiedLockEvents);
		for(Node verifiedLockEvent : verifiedLockEvents){
			this.setIntraAndInterProceduralCasesCount(verifiedLockEvent, reporter);
		}
		
		this.verifiedLocks.addAll(verifiedLockEvents);
		
		AtlasSet<Node> partiallyVerifiedE1Events = new AtlasHashSet<Node>(safeE1Events);
		partiallyVerifiedE1Events = Common.toQ(partiallyVerifiedE1Events).difference(Common.toQ(verifiedLockEvents)).eval().nodes();
		reporter.setPartiallyVerifiedLockEvents(partiallyVerifiedE1Events);
		
		this.partiallyLocks.addAll(partiallyVerifiedE1Events);
		
		reporter.setDeadlockedLockEvents(doubleE1Events);
		AtlasSet<Node> actualRacedEvents = new AtlasHashSet<Node>(doubleE1Events);
		actualRacedEvents = Common.toQ(actualRacedEvents).difference(Common.toQ(partiallyVerifiedE1Events)).eval().nodes();
		reporter.setOnlyDeadlockedLockEvents(actualRacedEvents);
		
		this.doubleLocks.addAll(doubleE1Events);
		
		reporter.setDanglingLockEvents(danglingE1Events);
		AtlasSet<Node> actualDanglingEvents = new AtlasHashSet<Node>(danglingE1Events);
		actualDanglingEvents = Common.toQ(actualDanglingEvents).difference(Common.toQ(partiallyVerifiedE1Events)).eval().nodes();
		reporter.setOnlyDandlingLockEvents(actualDanglingEvents);
		
		this.danglingLocks.addAll(danglingE1Events);
		
		
		reporter.printResults("[" + this.verificationInstanceId + "]");
		
		AtlasSet<Node> missingE1Events = new AtlasHashSet<Node>();
		for(Node node : this.e1Events){
			missingE1Events.add(node);
		}
		AtlasSet<Node> keys = new AtlasHashSet<Node>();
		for(Node keyNode : this.matchingPairsMap.keySet()){
			keys.add(keyNode);
		}
		missingE1Events = Common.toQ(missingE1Events).difference(Common.toQ(keys)).eval().nodes();
		if(!missingE1Events.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Missing (e1) Events");
			LSAPUtils.log("##########################################");
			AtlasSet<Node> missing = new AtlasHashSet<Node>();
			for(Node e : missingE1Events){
				missing.add(e);
			}
			this.logMatchingResultsForEvents(missingE1Events, null);
			LSAPUtils.log("------------------------------------------");
		}
		
		LSAPUtils.log("##########################################");
		LSAPUtils.log("Verified Lock Events");
		LSAPUtils.log("##########################################");
		this.logMatchingResultsForEvents(verifiedLockEvents, VerificationResult.SAFE);
		LSAPUtils.log("------------------------------------------");
		
		if(!partiallyVerifiedE1Events.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Partially Verified Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(partiallyVerifiedE1Events, null);
			LSAPUtils.log("------------------------------------------");
		}
		
		if(!actualDanglingEvents.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Dangling (Not-Verified) Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(danglingE1Events, VerificationResult.DANGLING_LOCK);
			LSAPUtils.log("------------------------------------------");
		}
		
		if(!actualRacedEvents.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Deadloced Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(doubleE1Events, VerificationResult.DOUBLE_LOCK);
			LSAPUtils.log("------------------------------------------");
		}
	}
	
	private void appendMatchingPairs(AtlasSet<Node> nodes){
		for(Node node : nodes){
			HashSet<MatchingPair> matchingPairs = new HashSet<MatchingPair>();
	    	if(this.matchingPairsMap.containsKey(node)){
	    		matchingPairs = this.matchingPairsMap.get(node);
	    	}
	    	FunctionSummary summary = this.summaries.get(CommonQueries.getContainingFunction(node));
	    	matchingPairs.add(new MatchingPair(node, summary.getExitNode(), null));
	    	this.matchingPairsMap.put(node, matchingPairs);
		}
	}
	
	private void setIntraAndInterProceduralCasesCount(Node e1Event, Reporter reporter) {
		HashSet<MatchingPair> pairs = this.matchingPairsMap.get(e1Event);
		for(MatchingPair pair : pairs){
			if(pair.getSecondEvent() != null){
				if(!CommonQueries.getContainingFunction(pair.getSecondEvent()).equals(CommonQueries.getContainingFunction(pair.getFirstEvent()))){
					AtlasSet<Node> cases = reporter.getInterproceduralVerificationLockEvents();
					cases.add(e1Event);
					reporter.setInterproceduralVerificationLockEvents(cases);
					return;
				}
			}
		}
		AtlasSet<Node> cases = reporter.getIntraproceduralVerificationLockEvents();
		cases.add(e1Event);
		reporter.setIntraproceduralVerificationLockEvents(cases);
	}
	
	private void logMatchingResultsForEvents(AtlasSet<Node> lockEvents, VerificationResult verificationResult) {
		for(Node lockEvent : lockEvents){
			LSAPUtils.log("\t(e1) Event: [" + lockEvent.getAttr(XCSG.name) + "] in Function [" + CommonQueries.getContainingFunction(lockEvent).getAttr(XCSG.name) + "]");
			HashSet<MatchingPair> pairs = this.matchingPairsMap.get(lockEvent);
			if(pairs == null){
				LSAPUtils.log("\t\tNo Matchings!");
				continue;
			}
			for(MatchingPair pair : pairs){
				if(verificationResult == null || pair.getResult().equals(verificationResult)){
					String secondEventName = pair.getSecondEvent() == null ? "NULL" : (String) pair.getSecondEvent().getAttr(XCSG.name);
					String containingFunctionName = pair.getSecondEvent() == null ? (String) CommonQueries.getContainingFunction(lockEvent).getAttr(XCSG.name) : (String) CommonQueries.getContainingFunction(pair.getSecondEvent()).getAttr(XCSG.name);
					LSAPUtils.log("\t\tMatched with Event: [" + secondEventName + "] in Function [" + containingFunctionName + "]");
				}
			}
		}
	}
	
	public AtlasSet<Node> getVerifiedLockEvents(){
		return this.verifiedLocks;
	}
	
	public AtlasSet<Node> getDanglingLockEvents(){
		return this.danglingLocks;
	}
	
	public AtlasSet<Node> getDeadlockedLockEvents(){
		return this.doubleLocks;
	}
	
	public AtlasSet<Node> getPartiallyVerifiedLockEvents(){
		return this.partiallyLocks;
	}
}
