package com.kcsl.lsap.core;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.c.core.query.Attr;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG;
import com.ensoftcorp.open.pcg.common.PCGFactory;
import com.kcsl.lsap.utils.LSAPUtils;

public class LSAP {


	/**
	 * Verifies the spin and mutex locks in the indexed Linux kernel.
	 */
	public static void verify(){
		verifyMutexLocks();
		verifySpinLocks();
	}
	
	/**
	 * Verifies the mutex locks in the indexed Linux kernel.
	 */
	public static void verifyMutexLocks(){
		Q mutexObjectType = VerificationProperties.getMutexObjectType();
		Q signatures = LSAPUtils.getSignaturesForObjectType(mutexObjectType);
		Path graphsOutputDirectoryPath = VerificationProperties.getSpinGraphsOutputDirectory();
		verifySignatures(signatures, VerificationProperties.getMutexLockFunctionCalls(), VerificationProperties.getMutexUnlockFunctionCalls(), graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the spin locks in the indexed Linux kernel.
	 */
	public static void verifySpinLocks(){
		Q spinObjectType = VerificationProperties.getSpinObjectType();
		Q signatures = LSAPUtils.getSignaturesForObjectType(spinObjectType);
		Path graphsOutputDirectoryPath = VerificationProperties.getMutexGraphsOutputDirectory();
		verifySignatures(signatures, VerificationProperties.getSpinLockFunctionCalls(), VerificationProperties.getSpinUnlockFunctionCalls(), graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the given <code>lock</code> instance.
	 * 
	 * @param lock the lock instance to be verified.
	 */
	public static void verify(Q lock){

	}
	
	/**
	 * Verifies the given <code>signatures</code> with the context of the <code>lockFunctionCalls</code> and <code>unlockFunctionCalls</code>.
	 * 
	 * @param signatures The signatures that will be used to start the verification for the associated locks/unlocks.
	 * @param lockFunctionCalls A {@link List} of Strings corresponding to the functions performing the actual lock on the given <code>signatures</code>.
	 * @param unlockFunctionCalls A {@link list} of Strings corresponding to the functions performing the actual unlock on the given <code>signatures</code>.
	 */
	private static void verifySignatures(Q signatures, List<String> lockFunctionCalls, List<String> unlockFunctionCalls, Path graphsOutputDirectoryPath){
		Reporter reporter = new Reporter();
		double totalRunningTime = 0;
		double totalRunningTimeWithDF = 0;
		Q lockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinLockFunctionCalls());
		Q unlockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinUnlockFunctionCalls());
		Q functionsToExclude = LSAPUtils.functionsQ(VerificationProperties.getFunctionsToExclude());
		AtlasSet<Node> signatureNodes = signatures.eval().nodes();
		int signatureProcessingIndex = 0;
		for(Node signatureNode : signatureNodes){
			long analysisStartTime = System.currentTimeMillis();
			Q signature = Common.toQ(signatureNode);
			
			LSAPUtils.log("Processing signature [" + signatureNode.getAttr(XCSG.name) + "] " + (++signatureProcessingIndex) + "/" + signatureNodes.size());
			
			// Skip processing the signature node if it is contained within a lock function call.
			Q functionContainingSignature = Common.toQ(CommonQueries.getContainingFunction(signatureNode));
			if(!lockFunctionCallsQ.intersection(functionContainingSignature).eval().nodes().isEmpty()){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] as it is contained with a lock function call.");
				continue;
			}
			
			// Skip processing the signature node if it has no data flow to the parameters passed to a lock/unlock function call.
			Q lockUnlockFunctionParameters = CommonQueries.functionParameter(lockFunctionCallsQ.union(unlockFunctionCallsQ), 0);
			Q dataFlowContext = universe().edges(XCSG.DataFlow_Edge, Attr.Edge.ADDRESS_OF, Attr.Edge.POINTER_DEREFERENCE);
			Q parametersPassedToLockUnlockFunctionCalls = dataFlowContext.successors(lockUnlockFunctionParameters);
			Q functionsToExcludeReturnCallSites = universe().edges(XCSG.Contains).forwardStep(functionsToExclude).nodes(XCSG.ReturnValue);
			Q dataFlowBetweenSignatureAndParameters = dataFlowContext.between(signature, parametersPassedToLockUnlockFunctionCalls, functionsToExcludeReturnCallSites);
			AtlasSet<Node> dataFlowNodes = dataFlowBetweenSignatureAndParameters.eval().nodes();
			if(dataFlowNodes.isEmpty() || (dataFlowNodes.size() == 1 && dataFlowNodes.contains(signatureNode))){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] - as it has no data flow to the parameters passed to a lock/unlock function call.");
				continue;					
			}
			
			Q parametersPassedToLockUnlockCallsFromSignature = dataFlowBetweenSignatureAndParameters.intersection(parametersPassedToLockUnlockFunctionCalls);
			Q cfgNodesContainingPassedParameters = parametersPassedToLockUnlockCallsFromSignature.containers().nodes(XCSG.ControlFlow_Node);
			Q callSitesWithinCFGNodes = universe().edges(XCSG.Contains).forward(cfgNodesContainingPassedParameters).nodes(XCSG.CallSite);
			
			Q mpg = LSAPUtils.mpg(callSitesWithinCFGNodes, lockFunctionCalls, unlockFunctionCalls);
			long mpgNodeSize = mpg.eval().nodes().size();
			if(mpgNodeSize > VerificationProperties.getMPGNodeSizeLimit()){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] - as it exceeds the mpg node size limit [" + mpgNodeSize + "].");
				continue;
			}
			
			// Skip processing the signature if it contains the functions to exclude from the analysis
			if(!mpg.intersection(functionsToExclude).eval().nodes().isEmpty()){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] -- as it contains problematic functions.");
				continue;						
			}
			
			Graph mpgGraph = mpg.eval();
			if(!LSAPUtils.isDirectedAcyclicGraph(mpg)){
				mpgGraph = LSAPUtils.cutCyclesFromGraph(mpg);
				mpg = Common.toQ(mpgGraph);
				// Skip processing the signature if it is cyclic graph.
				if(!LSAPUtils.isDirectedAcyclicGraph(mpg)){
					LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] -- as it contains cycles.");
					continue;
				}
			}
			
			double dataFlowAnalysisTime = (System.currentTimeMillis() - analysisStartTime)/(60*1000F);
			Reporter subReporter = verifySignature(signatureNode, mpg, cfgNodesContainingPassedParameters, lockFunctionCalls, unlockFunctionCalls, graphsOutputDirectoryPath);
			
			if(subReporter == null){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] - verification results on \"NULL\" status.");
				continue;
			}
			reporter.aggregate(subReporter);
			totalRunningTime += subReporter.getAnalysisProcessingTime();
			totalRunningTimeWithDF += dataFlowAnalysisTime;
			totalRunningTimeWithDF += subReporter.getAnalysisProcessingTime();
		}
		reporter.done();
		reporter.printResults("Overall Results");
		LSAPUtils.log("******************************************");
		LSAPUtils.log("Signatures Count: "  + signatureNodes.size());
		LSAPUtils.log("Total Running Time [ "  + totalRunningTime + " minutes]!" );
		LSAPUtils.log("Total Running Time With Data Flow Analysis [ "  + totalRunningTimeWithDF + " minutes]!" );
		LSAPUtils.log("******************************************");
	}
	
	/**
	 * Verifies the given <code>signatureNode</code> associated with <code>mpg</code> in the context of <code>lockFunctionCalls</code> and <code>unlockFunctionCalls</code>.
	 * 
	 * @param signatureNode A {@link Node} corresponding to the type object passed to the lock/unlock calls.
	 * @param mpg An {@link Q} corresponding to the matching pair graph associated with <code>signatureNode</code>.
	 * @param cfgNodesContainingEvents An {@link Q} containing the CFG nodes that correspond to lock/unlock call events.
	 * @param lockFunctionCalls A {@link List} of Strings corresponding to the functions performing the actual lock on the given <code>signatures</code>.
	 * @param unlockFunctionCalls A {@link list} of Strings corresponding to the functions performing the actual unlock on the given <code>signatures</code>.
	 * @return
	 */
	private static Reporter verifySignature(Node signatureNode, Q mpg, Q cfgNodesContainingEvents, List<String> lockFunctionCalls, List<String> unlockFunctionCalls, Path graphsOutputDirectoryPath){		
		Q lockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinLockFunctionCalls());
		Q unlockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinUnlockFunctionCalls());
		HashMap<Node, PCG> functionPCGMap = new HashMap<Node, PCG>();
		HashMap<Node, List<Q>> functionEventsMap = new HashMap<Node, List<Q>>();
		
		Graph mpgGraph = mpg.eval();
		AtlasSet<Node> mpgNodes = mpgGraph.nodes();
		
		List<String> mpgFunctions = new ArrayList<String>();
		for(Node mpgNode : mpgNodes){
			if(lockFunctionCallsQ.eval().nodes().contains(mpgNode) || unlockFunctionCallsQ.eval().nodes().contains(mpgNode))
				continue;
			mpgFunctions.add((String) mpgNode.getAttr(XCSG.name));
		}
		
		for(Node mpgNode : mpgNodes){
			if(lockFunctionCallsQ.eval().nodes().contains(mpgNode) || unlockFunctionCallsQ.eval().nodes().contains(mpgNode))
				continue;
			
			String mpgFunctionName = (String) mpgNode.getAttr(XCSG.name);
			Q cfg = CommonQueries.cfg(mpgNode);
			
			List<Q> events = LSAPUtils.compileCFGNodesContainingEventNodes(cfg, cfgNodesContainingEvents, mpgFunctions, lockFunctionCalls, unlockFunctionCalls);
			Q lockEvents = events.get(0);
			Q unlockEvents = events.get(1);
			Q mpgFunctionCallEvents = events.get(2);
			Q allEvents = events.get(3); 
			
			Markup markup = new Markup();
			markup.set(lockEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.RED);
			markup.set(unlockEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.GREEN);
			markup.set(mpgFunctionCallEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.BLUE);

			LSAPUtils.log("Creating PCG for [" + mpgFunctionName + "].");
			PCG pcg = PCGFactory.create(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), allEvents);
			functionPCGMap.put(mpgNode, pcg);
			events.remove(events.size() - 1);
			functionEventsMap.put(mpgNode, events);
		}
		
		Verifier verifier = new Verifier(signatureNode, mpg, functionPCGMap, functionEventsMap, new HashMap<>(), graphsOutputDirectoryPath);
		Reporter reporter = verifier.verify();
		return reporter;
	}
	
}
