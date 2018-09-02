package com.kcsl.lsap.utils;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ensoftcorp.atlas.c.core.query.Attr;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.map.AtlasGraphKeyHashMap;
import com.ensoftcorp.atlas.core.db.map.AtlasMap;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.markup.Markup;
import com.ensoftcorp.atlas.core.markup.MarkupProperty;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.pcg.common.PCG;
import com.ensoftcorp.open.pcg.common.PCGFactory;
import com.kcsl.lsap.VerificationProperties;
import com.kcsl.lsap.reporting.Reporter;
import com.kcsl.lsap.verifier.Verifier;

/**
 * A class containing utility functions to initiate the signature verification.
 */
public class SignatureVerificationUtils {
	
	/**
	 * A private constructor to prevent intentional initializations of this class.
	 * 
	 * @throws IllegalAccessException If any initialization occur to this class.
	 */
	private SignatureVerificationUtils() throws IllegalAccessException {
		throw new IllegalAccessException();
	}

	/**
	 * Verifies the given <code>signatures</code> with the context of the <code>lockFunctionCalls</code> and <code>unlockFunctionCalls</code>.
	 * 
	 * @param signatures The signatures that will be used to start the verification for the associated locks/unlocks.
	 * @param lockFunctionCallsQ A {@link Q} corresponding to the functions performing the actual lock on the given <code>signatures</code>.
	 * @param unlockFunctionCallsQ A {@link Q} corresponding to the functions performing the actual unlock on the given <code>signatures</code>.
	 * @param graphsOutputDirectoryPath A {@link Path} to where the verification graphs to be stored.
	 */
	public static void verifySignatures(Q signatures, Q lockFunctionCallsQ, Q unlockFunctionCallsQ, Path graphsOutputDirectoryPath){
		verifySignatures(null, signatures, lockFunctionCallsQ, unlockFunctionCallsQ, graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the given <code>signatures</code> with the context of the <code>lockFunctionCalls</code> and <code>unlockFunctionCalls</code>.
	 * 
	 * @param lockNode A {@link XCSG#ControlFlow_Node} corresponding to a call to lock.
	 * @param signatures The signatures that will be used to start the verification for the associated locks/unlocks.
	 * @param lockFunctionCallsQ A {@link Q} corresponding to the functions performing the actual lock on the given <code>signatures</code>.
	 * @param unlockFunctionCallsQ A {@link Q} corresponding to the functions performing the actual unlock on the given <code>signatures</code>.
	 * @param graphsOutputDirectoryPath A {@link Path} to where the verification graphs to be stored.
	 */
	public static void verifySignatures(Node lockNode, Q signatures, Q lockFunctionCallsQ, Q unlockFunctionCallsQ, Path graphsOutputDirectoryPath){
		Reporter reporter = new Reporter("Overall Results");
		double totalRunningTime = 0;
		double totalRunningTimeWithDF = 0;
		Q lockUnlockFunctionCallsQ = lockFunctionCallsQ.union(unlockFunctionCallsQ);
		Q functionsToExclude = LSAPUtils.functionsQ(VerificationProperties.getFunctionsToExclude());

		Q dataFlowContext = universe().edges(XCSG.DataFlow_Edge, Attr.Edge.ADDRESS_OF, Attr.Edge.POINTER_DEREFERENCE);
		
		// 1. Find {@link XCSG#CallSite} for <code>lockUnlockFunctionCallsQ</code>.
		Q lockUnlockFunctionCallSites = CallSiteAnalysis.getCallSites(lockUnlockFunctionCallsQ);
		
		// 2. Find the {@link XCSG#ParameterPass} nodes at {@link XCSG#parameterIndex} "0" that are passed to <code>lockUnlockFunctionCallSites</code>.
		Q parametersPassedToLockUnlockFunctionCallSites = universe().edges(XCSG.ParameterPassedTo).predecessors(lockUnlockFunctionCallSites).selectNode(XCSG.parameterIndex, 0);

		// sort signatures by source correspondence
		AtlasSet<Node> signatureNodes = signatures.eval().nodes();
		List<Node> sortedSignatures = new ArrayList<Node>();
		for(Node signatureNode : signatureNodes){
			sortedSignatures.add(signatureNode);
		}
		Collections.sort(sortedSignatures, new NodeSourceCorrespondenceSorter());
		
		int signatureProcessingIndex = 0;
		for(Node signatureNode : sortedSignatures){
			long analysisStartTime = System.currentTimeMillis();
			LSAPUtils.log("Processing signature [" + signatureNode.getAttr(XCSG.name) + "] " + (++signatureProcessingIndex) + "/" + signatureNodes.size());
			Q signature = Common.toQ(signatureNode);
			
			// Skip processing the signature node if it has no data flow to the parameters passed to a lock/unlock function call.
			Q dataFlowExistenceTest = dataFlowContext.successors(signature);
			if(dataFlowExistenceTest.eval().nodes().isEmpty()){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] - as it has no data flow to the parameters passed to a lock/unlock function call.");
				continue;					
			}
			
			// 3. Find the {@link XCSG#ReturnValue} nodes that will be excluded from the data flow computations.
			Q functionsToExcludeReturnCallSites = functionsToExclude.contained().nodes(XCSG.ReturnValue);
			
			// 4. Check whether the signature is connected through <code>dataFlowContext</code> edges to the <code>parametersPassedToLockUnlockFunctionCallSites</code>.
			Q dataFlowBetweenSignatureAndParameters = dataFlowContext.between(signature, parametersPassedToLockUnlockFunctionCallSites, functionsToExcludeReturnCallSites);
			
			// 5. Find the parameters associated only with this signature.
			Q parametersPassedToLockUnlockCallsFromSignature = dataFlowBetweenSignatureAndParameters.intersection(parametersPassedToLockUnlockFunctionCallSites);
			
			// Skip processing the signature node if it has no data flow to the parameters passed to a lock/unlock function call.
			if(parametersPassedToLockUnlockCallsFromSignature.eval().nodes().isEmpty()){
				LSAPUtils.log("Skipping signature [" + signatureProcessingIndex + "] - as it has no data flow to the parameters passed to a lock/unlock function call.");
				continue;					
			}
			
			Q cfgNodesContainingPassedParameters = LSAPUtils.getContainingNodes(parametersPassedToLockUnlockCallsFromSignature, XCSG.ControlFlow_Node);
			Q callSitesWithinCFGNodes = universe().edges(XCSG.Contains).forward(cfgNodesContainingPassedParameters).nodes(XCSG.CallSite);
			
			Q mpg = LSAPUtils.mpg(callSitesWithinCFGNodes, lockFunctionCallsQ, unlockFunctionCallsQ);
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
			Reporter subReporter = verifySignature(lockNode, signatureNode, mpg, cfgNodesContainingPassedParameters, lockFunctionCallsQ, unlockFunctionCallsQ, graphsOutputDirectoryPath);
			
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
		LSAPUtils.log("******************************************");
		LSAPUtils.log("Signatures Count: "  + signatureNodes.size());
		LSAPUtils.log("Total Running Time [ "  + totalRunningTime + " minutes]!" );
		LSAPUtils.log("Total Running Time With Data Flow Analysis [ "  + totalRunningTimeWithDF + " minutes]!" );
		LSAPUtils.log("******************************************");
	}
	
	/**
	 * Verifies the given <code>signatureNode</code> associated with <code>mpg</code> in the context of <code>lockFunctionCalls</code> and <code>unlockFunctionCalls</code>.
	 * 
	 * @param lockNode A {@link XCSG#ControlFlow_Node} corresponding to a call to lock.
	 * @param signatureNode A {@link Node} corresponding to the type object passed to the lock/unlock calls.
	 * @param mpg An {@link Q} corresponding to the matching pair graph associated with <code>signatureNode</code>.
	 * @param cfgNodesContainingEvents An {@link Q} containing the CFG nodes that correspond to lock/unlock call events.
	 * @param lockFunctionCallsQ A {@link Q} corresponding to the functions performing the actual lock on the given <code>signatures</code>.
	 * @param unlockFunctionCalls A {@link Q} of corresponding to the functions performing the actual unlock on the given <code>signatures</code>.
	 * @param graphsOutputDirectoryPath A {@link Path} to where the verification graphs to be stored.
	 * @return An instance of {@link Reporter} for this verification instance or null of the verification did not succeed.
	 */
	private static Reporter verifySignature(Node lockNode, Node signatureNode, Q mpg, Q cfgNodesContainingEvents, Q lockFunctionCallsQ, Q unlockFunctionCallsQ, Path graphsOutputDirectoryPath){		
		Q mpgFunctions = mpg.difference(lockFunctionCallsQ.union(unlockFunctionCallsQ));
		AtlasMap<Node, PCG> functionPCGMap = new AtlasGraphKeyHashMap<Node, PCG>();
		AtlasMap<Node, List<Q>> functionEventsMap = new AtlasGraphKeyHashMap<Node, List<Q>>();
		
		Graph mpgGraphWithoutLockUnlockCalls = mpgFunctions.eval();
		AtlasSet<Node> mpgNodes = mpgGraphWithoutLockUnlockCalls.nodes();		
		for(Node mpgNode : mpgNodes){			
			String mpgFunctionName = (String) mpgNode.getAttr(XCSG.name);
			Q cfg = CommonQueries.cfg(mpgNode);
			List<Q> events = LSAPUtils.compileCFGNodesContainingEventNodes(cfg, cfgNodesContainingEvents, mpgFunctions, lockFunctionCallsQ, unlockFunctionCallsQ);
			Q lockEvents = events.get(0);
			Q unlockEvents = events.get(1);
			Q mpgFunctionCallEvents = events.get(2);
			Q allEvents = events.get(3); 
			
			Markup markup = new Markup();
			markup.set(lockEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.RED);
			markup.set(unlockEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.GREEN);
			markup.set(mpgFunctionCallEvents, MarkupProperty.NODE_BACKGROUND_COLOR, Color.BLUE);

			LSAPUtils.log("Creating PCG for [" + mpgFunctionName + "].");
			PCG pcg = PCGFactory.create(cfg, cfg.nodes(XCSG.controlFlowRoot), cfg.nodes(XCSG.controlFlowExitPoint), allEvents, true);
			functionPCGMap.put(mpgNode, pcg);
			functionEventsMap.put(mpgNode, events);
		}
		
		Verifier verifier = new Verifier(signatureNode, mpg, functionPCGMap, functionEventsMap, new AtlasGraphKeyHashMap<>(), graphsOutputDirectoryPath);
		Reporter reporter = null;
		if(lockNode == null){
			reporter = verifier.verify();
		}else{
			reporter = verifier.verify(lockNode);
		}
		return reporter;
	}
	
}
