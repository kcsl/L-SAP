package com.kcsl.lsap.core;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.list.AtlasList;
import com.ensoftcorp.atlas.core.db.map.AtlasGraphKeyHashMap;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG;
import com.kcsl.lsap.VerificationProperties;
import com.kcsl.lsap.core.FunctionVerifier.PathStatus;
import com.kcsl.lsap.core.LockVerificationGraphsGenerator.VerificationStatus;
import com.kcsl.lsap.core.MatchingPair.VerificationResult;
import com.kcsl.lsap.utils.LSAPUtils;

/**
 * A class that sorts out the verification process and aggregate the verification results.
 */
public class Verifier {
	
	/**
	 * An instance of {@link Path} corresponding to the root output directory where the verification graphs will be saved.
	 */
	private Path graphsOutputDirectoryPath;
	
	/**
	 * An instance of {@link Node} for the signature.
	 */
	private Node signatureNode;
	
	/**
	 * A {@link String} representing a unique verification id for debugging purposed.
	 */
	private String verificationInstanceId;
	
	/**
	 * An MPG with leaves being the lock/unlock function calls.
	 */
	private Q fullMpg;
	
	/**
	 * An MPG without the leaves of the {@link #fullMpg}.
	 */
	private Q mpg;
	
	/**
	 * A mapping of {@link Node} corresponding to an {@link XCSG#Function} to its {@link PCG} instance.
	 */
	private AtlasMap<Node, PCG> functionsPCGMap;
	
	/**
	 * A mapping of {@link Node} corresponding to an {@link XCSG#Function} to its set of events in the CFG and PCG graphs.
	 */
	private AtlasMap<Node, List<Q>> functionEventsMap;
	
	/**
	 * A mapping of {@link Node} corresponding to an {@link XCSG#Function} to its {@link FunctionSummary} instance.
	 */
	private AtlasMap<Node, FunctionSummary> summaries;
	
	/**
	 * A mapping of {@link Node} corresponding to multi-state lock node to whether the true branch where the lock occurs.
	 */
	private AtlasMap<Node, Boolean> mayEventsFeasibility;
	
	/**
	 * A mapping of {@link Node} corresponding to a lock function call to a set of its {@link MatchingPair}s.
	 */
	private AtlasMap<Node, HashSet<MatchingPair>> matchingPairsMap;
	
	/**
	 * A list of {@link Node}s corresponding to verified locks.
	 */
	private AtlasSet<Node> verifiedLocks;
	
	/**
	 * A list of {@link Node}s corresponding to dangling locks (not paired locks).
	 */
	private AtlasSet<Node> danglingLocks;
	
	/**
	 * A list of {@link Node}s corresponding to deadlocked locks.
	 */
	private AtlasSet<Node> deadlockedLocks;
	
	/**
	 * A list of {@link Node}s corresponding to partially verified locks.
	 */
	private AtlasSet<Node> partiallyLocks;
	
	/**
	 * A set of {@link Node}s corresponding to all lock function calls in this {@link #mpg}.
	 */
	private AtlasSet<Node> lockFunctionCallEvents;
	
	/**
	 * A set of {@link Node}s corresponding to all unlock function calls in this {@link #mpg}.
	 */
	private AtlasSet<Node> unlockFunctionCallEvents;
	
	/**
	 * A set of {@link Node}s corresponding to all lock function calls in this {@link #mpg} that are multi-state.
	 */
	private AtlasSet<Node> multiStateLockFunctionCallEvents;
	
	/**
	 * Constructs a new instance of {@link Verifier}.
	 * 
	 * @param signatureNode See corresponding field for details.
	 * @param mpg See corresponding field for details.
	 * @param functionsPCGMap See corresponding field for details.
	 * @param functionEventsMap See corresponding field for details.
	 * @param mayEventsFeasibility See corresponding field for details.
	 * @param graphsOutputDirectoryPath See corresponding field for details.
	 */
	public Verifier(Node signatureNode, Q mpg, AtlasMap<Node, PCG> functionsPCGMap, AtlasMap<Node, List<Q>> functionEventsMap, AtlasMap<Node, Boolean> mayEventsFeasibility, Path graphsOutputDirectoryPath){
		this.signatureNode = signatureNode;
		this.verificationInstanceId = this.signatureNode.getAttr(XCSG.name) + "(" + this.signatureNode.addressBits() + ")";;
		this.fullMpg = mpg;
		this.mpg = this.fullMpg.difference(this.fullMpg.leaves());
		this.functionsPCGMap = functionsPCGMap;
		this.functionEventsMap = functionEventsMap;
		this.mayEventsFeasibility = mayEventsFeasibility;
		this.matchingPairsMap = new AtlasGraphKeyHashMap<Node, HashSet<MatchingPair>>();
		this.summaries = new AtlasGraphKeyHashMap<Node, FunctionSummary>();
		this.verifiedLocks = new AtlasHashSet<Node>();
		this.danglingLocks = new AtlasHashSet<Node>();
		this.deadlockedLocks = new AtlasHashSet<Node>();
		this.partiallyLocks = new AtlasHashSet<Node>();
		this.lockFunctionCallEvents = new AtlasHashSet<Node>();
		this.multiStateLockFunctionCallEvents = new AtlasHashSet<Node>();
		this.unlockFunctionCallEvents = new AtlasHashSet<Node>();
		this.graphsOutputDirectoryPath = graphsOutputDirectoryPath;
	}
	
	/**
	 * Verifies all the locks in {@link #mpg} and stores the verification results.
	 * 
	 * @return An instance of {@link Reporter} that contains all stats about the verification process.
	 */
	public Reporter verify(){
		return this.verify(null);
	}
	
	/**
	 * Verifies all the locks in {@link #mpg} and stores the verification results.
	 * <p>
	 * If the <code>lockNode</code> is not null, then only the verification graphs for that <code>lockNode</code> will be stored and displayed to the user.
	 * 
	 * @param lockNode The {@link Node} corresponding to a lock function call.
	 * @return An instance of {@link Reporter} that contains all stats about the verification process.
	 */
	public Reporter verify(Node lockNode){
		LSAPUtils.log("MPG has ["+ this.mpg.eval().nodes().size() +"] nodes.");
		Reporter reporter = new Reporter("[" + this.verificationInstanceId + "]");
		
		AtlasList<Node> functions = LSAPUtils.topologicalSort(this.mpg);
		Collections.reverse(functions);
		
		for(Node function : functions){
			LSAPUtils.log("Generating Summary For Function:" + function.attr().get(XCSG.name));
			long outDegree = this.mpg.successors(Common.toQ(function)).eval().nodes().size();
			LSAPUtils.log("Function's outdegree:" + outDegree);
			this.summaries.put(function, this.constructFunctionSummary(function));
		}
		
		this.aggregateVerificationResults(reporter);
		
		reporter.done();
		
		boolean displayInteractiveGraphsForLock = lockNode != null;
		if((reporter != null && VerificationProperties.isSaveVerificationGraphs()) || displayInteractiveGraphsForLock){
			this.saveLockVerificationGraphs(lockNode, displayInteractiveGraphsForLock);
		}
		
		return reporter;
	}
	
	/**
	 * Constructs an instance of {@link FunctionSummary} for the given <code>function</code>.
	 * 
	 * @param function An instance of {@link Node} corresponding to an {@link XCSG#Function}.
	 * @return an instance of {@link FunctionSummary} for the given <code>function</code>.
	 */
	private FunctionSummary constructFunctionSummary(Node function){
		PCG pcg = this.functionsPCGMap.get(function);
		List<Q> events = this.functionEventsMap.get(function);
		
		AtlasMap<Node, FunctionSummary> successorFunctionSummaries = new AtlasGraphKeyHashMap<Node, FunctionSummary>();
		AtlasSet<Node> successors = this.mpg.successors(Common.toQ(function)).eval().nodes();
		for(Node successor : successors)
			successorFunctionSummaries.put(successor, this.summaries.get(successor));
		
		FunctionVerifier functionVerifier = new FunctionVerifier(function, pcg, successorFunctionSummaries, events);	
		FunctionSummary summary = functionVerifier.run();
		this.lockFunctionCallEvents.addAll(summary.getLockFunctionCallEvents());
		this.multiStateLockFunctionCallEvents.addAll(summary.getE1MayEvents());
		this.unlockFunctionCallEvents.addAll(summary.getUnlockFunctionCallEvents());
		
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
	
	/**
	 * Aggregates all the verification results and logs important information using <code>reporter</code>.
	 * 
	 * @param reporter An instance of {@link Reporter} to be used for aggregating verification stats.
	 */
	private void aggregateVerificationResults(Reporter reporter){
		reporter.setLockEvents(this.lockFunctionCallEvents);
		reporter.setUnlockEvents(this.unlockFunctionCallEvents);
		
		int outStatus;
		for(Node function : this.summaries.keySet()){
			if(this.mpg.predecessors(Common.toQ(function)).eval().nodes().isEmpty()){
				outStatus = this.summaries.get(function).getNodeToPathStatus();
				if(((outStatus & PathStatus.LOCK) != 0) || (outStatus == PathStatus.LOCK || outStatus == (PathStatus.LOCK | PathStatus.THROUGH))){
					this.appendMatchingPairs(this.summaries.get(function).getNodeToEventsAlongPath());
				}
			}
		}
		
		if(this.matchingPairsMap.keySet().size() != this.lockFunctionCallEvents.size()){
			LSAPUtils.log("The matching pair map contains [" + this.matchingPairsMap.size() + "] while the size of e1Events is [" + this.lockFunctionCallEvents.size() + "]!");
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
				pair.verify(this.lockFunctionCallEvents, this.unlockFunctionCallEvents, this.mayEventsFeasibility, this.summaries);
				LSAPUtils.log("[" + (++count) + "] " + pair.toString());
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
		
		/**
		 * Compute actual verified lock events.
		 */
		AtlasSet<Node> verifiedLockEvents = new AtlasHashSet<Node>(safeE1Events);
		verifiedLockEvents = Common.toQ(verifiedLockEvents).difference(Common.toQ(danglingE1Events).union(Common.toQ(doubleE1Events))).eval().nodes();
		
		if(!verifiedLockEvents.isEmpty()) {
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Verified Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(verifiedLockEvents, VerificationResult.SAFE);
			LSAPUtils.log("------------------------------------------");
		}
		
		reporter.setVerifiedLockEvents(verifiedLockEvents);
		for(Node verifiedLockEvent : verifiedLockEvents){
			this.setIntraAndInterProceduralCasesCount(verifiedLockEvent, reporter);
		}
		this.verifiedLocks.addAll(verifiedLockEvents);
		
		/**
		 * Compute actual partially verified lock events.
		 */
		AtlasSet<Node> partiallyVerifiedE1Events = new AtlasHashSet<Node>(safeE1Events);
		partiallyVerifiedE1Events = Common.toQ(partiallyVerifiedE1Events).difference(Common.toQ(verifiedLockEvents)).eval().nodes();
		
		if(!partiallyVerifiedE1Events.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Partially Verified Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(partiallyVerifiedE1Events, null);
			LSAPUtils.log("------------------------------------------");
		}
		
		reporter.setPartiallyVerifiedLockEvents(partiallyVerifiedE1Events);
		this.partiallyLocks.addAll(partiallyVerifiedE1Events);
		
		/**
		 * Compute actual deadlocked lock events.
		 */
		reporter.setDeadlockedLockEvents(doubleE1Events);
		AtlasSet<Node> actualRacedEvents = new AtlasHashSet<Node>(doubleE1Events);
		actualRacedEvents = Common.toQ(actualRacedEvents).difference(Common.toQ(partiallyVerifiedE1Events)).eval().nodes();
		
		if(!actualRacedEvents.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Deadloced Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(doubleE1Events, VerificationResult.DEADLOCKED);
			LSAPUtils.log("------------------------------------------");
		}
		
		reporter.setOnlyDeadlockedLockEvents(actualRacedEvents);
		this.deadlockedLocks.addAll(doubleE1Events);
		
		/**
		 * Compute actual dangling lock events.
		 */
		reporter.setDanglingLockEvents(danglingE1Events);
		AtlasSet<Node> actualDanglingEvents = new AtlasHashSet<Node>(danglingE1Events);
		actualDanglingEvents = Common.toQ(actualDanglingEvents).difference(Common.toQ(partiallyVerifiedE1Events)).eval().nodes();
		
		if(!actualDanglingEvents.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Dangling (Not-Verified) Lock Events");
			LSAPUtils.log("##########################################");
			this.logMatchingResultsForEvents(danglingE1Events, VerificationResult.DANGLING_LOCK);
			LSAPUtils.log("------------------------------------------");
		}
		
		reporter.setOnlyDandlingLockEvents(actualDanglingEvents);
		this.danglingLocks.addAll(danglingE1Events);
		
		/**
		 * Compute missing lock events from the verification.
		 */
		AtlasSet<Node> missingE1Events = Common.toQ(this.lockFunctionCallEvents).difference(Common.toQ(verifiedLocks), Common.toQ(partiallyLocks), Common.toQ(deadlockedLocks), Common.toQ(danglingLocks)).eval().nodes();
		if(!missingE1Events.isEmpty()){
			LSAPUtils.log("##########################################");
			LSAPUtils.log("Missing Lock Events");
			LSAPUtils.log("##########################################");
			AtlasSet<Node> missing = new AtlasHashSet<Node>();
			for(Node e : missingE1Events){
				missing.add(e);
			}
			this.logMatchingResultsForEvents(missingE1Events, null);
			LSAPUtils.log("------------------------------------------");
		}
	}
	
	/**
	 * Saves or displays the verification results.
	 * 
	 * @param lockNode A {@link XCSG#ControlFlow_Node} corresponding to a call to lock.
	 * @param displayInteractiveGraphsForLock A {@link Boolean} specifies whether to force display of interactive lock verification graphs.
	 */
	private void saveLockVerificationGraphs(Node lockNode, boolean displayInteractiveGraphsForLock){
		LockVerificationGraphsGenerator lockVerificationGraphsGenerator = new LockVerificationGraphsGenerator(this.signatureNode, this.fullMpg, this.matchingPairsMap, this.graphsOutputDirectoryPath);
		
		// A paired lock is never partially paired or unpaired or deadlock
		//Q pairedLocks = verifiedLocks.difference(partiallyLocks, danglingLocks, doubleLocks);
		for(Node lock : this.verifiedLocks){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, VerificationStatus.PAIRED, displayInteractiveGraphsForLock);
			}
		}
		
		// A partially paired lock should not be a deadlock
		//Q partiallyPairedLocks = partiallyLocks.difference(doubleLocks);
		for(Node lock : this.partiallyLocks){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, VerificationStatus.PARTIALLY_PAIRED, displayInteractiveGraphsForLock);
			}
		}
		
		// A deadlock lock is only if it has deadlock
		for(Node lock : this.deadlockedLocks){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, VerificationStatus.DEADLOCK, displayInteractiveGraphsForLock);
			}
		}
		
		// An unpaired lock cannot be partially paired or paired
		//Q unpairedLocks = danglingLocks.difference(verifiedLocks, partiallyLocks);
		for(Node lock : this.danglingLocks){
			if(lockNode == null || lockNode.equals(lock)){
				lockVerificationGraphsGenerator.process(lock, VerificationStatus.UNPAIRED, displayInteractiveGraphsForLock);
			}
		}
	}
	
	/**
	 * Appends the {@link MatchingPair}s for the {@link Node}s in <code>nodes</code>.
	 * 
	 * @param nodes A list of {@link Node}s.
	 */
	private void appendMatchingPairs(AtlasSet<Node> nodes){
		for(Node node : nodes){
			HashSet<MatchingPair> matchingPairs = new HashSet<MatchingPair>();
		    	if(this.matchingPairsMap.containsKey(node)){
		    		matchingPairs = this.matchingPairsMap.get(node);
		    	}
		    	FunctionSummary summary = this.summaries.get(CommonQueries.getContainingFunction(node));
		    	matchingPairs.add(new MatchingPair(node, summary.getPCG().getMasterExit(), null));
		    	this.matchingPairsMap.put(node, matchingPairs);
		}
	}
	
	/**
	 * Sets the counter for intra and inter procedural verification cases for the given <code>lockEvent</code> in <code>reporter</code>.
	 * 
	 * @param lockEvent A {@link Node} corresponding to call to lock function.
	 * @param reporter A {@link Reporter} to be updated with new counts.
	 */
	private void setIntraAndInterProceduralCasesCount(Node lockEvent, Reporter reporter) {
		HashSet<MatchingPair> pairs = this.matchingPairsMap.get(lockEvent);
		for(MatchingPair pair : pairs){
			if(pair.getSecondEvent() != null){
				if(!CommonQueries.getContainingFunction(pair.getSecondEvent()).equals(CommonQueries.getContainingFunction(pair.getFirstEvent()))){
					AtlasSet<Node> cases = reporter.getInterproceduralVerificationLockEvents();
					cases.add(lockEvent);
					reporter.setInterproceduralVerificationLockEvents(cases);
					return;
				}
			}
		}
		AtlasSet<Node> cases = reporter.getIntraproceduralVerificationLockEvents();
		cases.add(lockEvent);
		reporter.setIntraproceduralVerificationLockEvents(cases);
	}
	
	/**
	 * Logs the <code>verificationResult</code> for <code>lockEvents</code>.
	 * 
	 * @param lockEvents A list of {@link XCSG#ControlFlow_Node}s containing calls to lock. 
	 * @param verificationResult A {@link VerificationResult} associated with <code>lockEvents</code>.
	 */
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

}
