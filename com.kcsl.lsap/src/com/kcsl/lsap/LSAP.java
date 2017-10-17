package com.kcsl.lsap;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.ensoftcorp.atlas.c.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.kcsl.lsap.utils.LSAPUtils;
import com.kcsl.lsap.utils.SignatureVerificationUtils;

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
		SignatureVerificationUtils.verifySignatures(signatures, VerificationProperties.getMutexLockFunctionCalls(), VerificationProperties.getMutexUnlockFunctionCalls(), graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the spin locks in the indexed Linux kernel.
	 */
	public static void verifySpinLocks(){
		Q spinObjectType = VerificationProperties.getSpinObjectType();
		Q signatures = LSAPUtils.getSignaturesForObjectType(spinObjectType);
		Path graphsOutputDirectoryPath = VerificationProperties.getMutexGraphsOutputDirectory();
		SignatureVerificationUtils.verifySignatures(signatures, VerificationProperties.getSpinLockFunctionCalls(), VerificationProperties.getSpinUnlockFunctionCalls(), graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the given <code>lock</code> instance.
	 * 
	 * @param lock the lock instance to be verified.
	 */
	public static void verify(Q lock){
		Node lockNode = lock.eval().nodes().one();
		Path graphsOutputDirectoryPath = VerificationProperties.getInteractiveVerificationGraphsOutputDirectory();
		
		List<String> lockFunctionCalls = new ArrayList<String>();
		lockFunctionCalls.addAll(VerificationProperties.getMutexLockFunctionCalls());
		lockFunctionCalls.addAll(VerificationProperties.getSpinLockFunctionCalls());
		Q lockFunctionCallsQ = LSAPUtils.functionsQ(lockFunctionCalls);
		
		List<String> unlockFunctionCalls = new ArrayList<String>();
		unlockFunctionCalls.addAll(VerificationProperties.getMutexUnlockFunctionCalls());
		unlockFunctionCalls.addAll(VerificationProperties.getSpinUnlockFunctionCalls());
		Q unlockFunctionCallsQ = LSAPUtils.functionsQ(unlockFunctionCalls);

		Q lockUnlockFunctionCalls = lockFunctionCallsQ.union(unlockFunctionCallsQ);
		
		Q callsites = universe().edges(XCSG.Contains).forward(lock).nodes(XCSG.CallSite);
		callsites = universe().edges(XCSG.InvokedFunction).predecessors(lockUnlockFunctionCalls).nodes(XCSG.CallSite).intersection(callsites);
		Q parameter = universe().edges(XCSG.PassedTo).reverseStep(callsites).selectNode(XCSG.parameterIndex, 0);
		Q dataFlowEdges = universe().edges(XCSG.DataFlow_Edge, Edge.ADDRESS_OF, Edge.POINTER_DEREFERENCE);
		Q reverseDataFlow = dataFlowEdges.reverse(parameter);
		Q field = reverseDataFlow.roots();
		Q predecessors = dataFlowEdges.predecessors(CommonQueries.functionParameter(lockUnlockFunctionCalls, 0));
		Q functionsToExclude = LSAPUtils.functionsQ(VerificationProperties.getFunctionsToExclude());
		Q functionsToExcludeReturnCallSites = functionsToExclude.contained().nodes(XCSG.ReturnValue);
		Q forwardDataFlow = dataFlowEdges.between(field, predecessors, functionsToExcludeReturnCallSites);
		Q cfgNodesContainingPassedParameters = forwardDataFlow.leaves().containers();
		callsites = universe().edges(XCSG.Contains).forward(cfgNodesContainingPassedParameters).nodes(XCSG.CallSite);
				
		Q mpg = LSAPUtils.mpg(callsites, lockFunctionCalls, unlockFunctionCalls);
		mpg = mpg.union(lockUnlockFunctionCalls);
		mpg = mpg.induce(universe().edges(XCSG.Call));
		Q unusedEdges = mpg.edges(XCSG.Call).forwardStep(lockUnlockFunctionCalls).edges(XCSG.Call);
		mpg = mpg.differenceEdges(unusedEdges);
		Q unusedNodes = mpg.roots().intersection(mpg.leaves());
		mpg = mpg.difference(unusedNodes);
		
		long mpgNodeSize = mpg.eval().nodes().size();
		if(mpgNodeSize > VerificationProperties.getMPGNodeSizeLimit()){
			LSAPUtils.log("Skipping lock [" + lockNode.getAttr(XCSG.name) + "] - as it exceeds the mpg node size limit [" + mpgNodeSize + "].");
			return;
		}
		
		if(!mpg.intersection(functionsToExclude).eval().nodes().isEmpty()){
			LSAPUtils.log("Skipping lock [" + lockNode.getAttr(XCSG.name) + "] -- as it contains problematic functions.");
			return;						
		}
		
		Graph mpgGraph = mpg.eval();
		if(!LSAPUtils.isDirectedAcyclicGraph(mpg)){
			mpgGraph = LSAPUtils.cutCyclesFromGraph(mpg);
			mpg = Common.toQ(mpgGraph);
			// Skip processing the signature if it is cyclic graph.
			if(!LSAPUtils.isDirectedAcyclicGraph(mpg)){
				LSAPUtils.log("Skipping lock [" + lockNode.getAttr(XCSG.name) + "] -- as it contains cycles.");
				return;
			}
		}
		Node signatureNode = field.eval().nodes().one();
		SignatureVerificationUtils.verifySignature(lockNode, signatureNode, mpg, cfgNodesContainingPassedParameters, lockFunctionCalls, unlockFunctionCalls, graphsOutputDirectoryPath);
	}
	
	/**
	 * locates the CFG node containing the callsite from the corresponding {@link SourceCorrespondence} string <code>lockSourceCorrespondenceString</code>.
	 * <p>
	 * This function will display the found CFG node in a view.
	 * 
	 * @param sourceCorrespondenceString A {@link String} corresponding to a {@link SourceCorrespondence} instance.
	 * @return A {@link String} to indicate whether the callsite is found in the code map or not.
	 */
	public static String locate(String sourceCorrespondenceString){
		Q callsites = universe().nodes(XCSG.CallSite);
		SourceCorrespondence sourceCorrespondence = SourceCorrespondence.fromString(sourceCorrespondenceString);
		Q cfgNodeContainingLockCallsite = callsites.containers().selectNode(XCSG.sourceCorrespondence, sourceCorrespondence);
		if(cfgNodeContainingLockCallsite.eval().nodes().isEmpty()){
			return "Lock callsite not found at: " + sourceCorrespondenceString;
		}
		DisplayUtil.displayGraph(Common.extend(cfgNodeContainingLockCallsite, XCSG.Contains).eval());
		return "Lock callsite found at: " + sourceCorrespondenceString;
	}
	
}
