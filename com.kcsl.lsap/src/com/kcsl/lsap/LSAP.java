package com.kcsl.lsap;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.nio.file.Path;

import com.ensoftcorp.atlas.c.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.open.commons.analysis.CallSiteAnalysis;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;
import com.kcsl.lsap.utils.LSAPUtils;
import com.kcsl.lsap.utils.SignatureVerificationUtils;

public class LSAP {


	/**
	 * Verifies the spin and mutex locks in the indexed Linux kernel.
	 */
	public static void verify(){
		verifyMutexLocks(false);
		verifySpinLocks(false);
	}
	
	/**
	 * Verifies the mutex locks in the indexed Linux kernel.
	 */
	public static void verifyMutexLocks(){
		verifyMutexLocks(true);
	}
	
	/**
	 * Verifies the mutex locks in the indexed Linux kernel.
	 * 
	 * @param resetOutputLog Reset the output log file so it only shows the verification results for mutex locks.
	 */
	private static void verifyMutexLocks(boolean resetOutputLog){
		if(resetOutputLog) {
			VerificationProperties.resetOutputLogFile();
		}
		Q mutexObjectType = VerificationProperties.getMutexObjectType();
		Q signatures = LSAPUtils.getSignaturesForObjectType(mutexObjectType);
		Q lockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getMutexLockFunctionCalls());
		Q unlockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getMutexUnlockFunctionCalls());
		Path graphsOutputDirectoryPath = VerificationProperties.getMutexGraphsOutputDirectory();
		SignatureVerificationUtils.verifySignatures(signatures, lockFunctionCallsQ, unlockFunctionCallsQ, graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the spin locks in the indexed Linux kernel.
	 */
	public static void verifySpinLocks(){
		verifySpinLocks(true);
	}
	
	/**
	 * Verifies the spin locks in the indexed Linux kernel.
	 * 
	 * @param resetOutputLog Reset the output log file so it only shows the verification results for spin locks.
	 */
	private static void verifySpinLocks(boolean resetOutputLog){
		if(resetOutputLog) {
			VerificationProperties.resetOutputLogFile();
		}
		Q spinObjectType = VerificationProperties.getSpinObjectType();
		Q signatures = LSAPUtils.getSignaturesForObjectType(spinObjectType);
		Q lockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinLockFunctionCalls());
		Q unlockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinUnlockFunctionCalls());
		Path graphsOutputDirectoryPath = VerificationProperties.getSpinGraphsOutputDirectory();
		SignatureVerificationUtils.verifySignatures(signatures, lockFunctionCallsQ, unlockFunctionCallsQ, graphsOutputDirectoryPath);
	}
	
	/**
	 * Verifies the given <code>lock</code> instance.
	 * 
	 * @param lock the lock instance to be verified.
	 */
	public static void verify(Q lock){
		Path graphsOutputDirectoryPath = VerificationProperties.getInteractiveVerificationGraphsOutputDirectory();
		
		Node lockNode = lock.eval().nodes().one();
		
		// determine if its a mutex lock or a spin lock
		Q callsitesWithinLock = universe().edges(XCSG.Contains).forward(lock).nodes(XCSG.CallSite);
		Q targetsForCallsitesWithinLock = CallSiteAnalysis.getTargets(callsitesWithinLock);
		
		Q lockFunctionCallsQ = Common.empty();
		Q unlockFunctionCallsQ = Common.empty();
		
		Q mutexLockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getMutexLockFunctionCalls());
		Q spinLockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinLockFunctionCalls());
		
		Q mutexIntersection = targetsForCallsitesWithinLock.intersection(mutexLockFunctionCallsQ);
		Q spinIntersection = targetsForCallsitesWithinLock.intersection(spinLockFunctionCallsQ);
		if(mutexIntersection.eval().nodes().isEmpty()) {
			if(spinIntersection.eval().nodes().isEmpty()) {
				DisplayUtils.showError("The selected lock does not call mutex/spin locks.");
				return;
			}
			// <code>lock</code> is a spin lock
			lockFunctionCallsQ = spinLockFunctionCallsQ;
			unlockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getSpinUnlockFunctionCalls());
		}else {
			// <code>lock</code> is a mutex lock
			lockFunctionCallsQ = mutexLockFunctionCallsQ;
			unlockFunctionCallsQ = LSAPUtils.functionsQ(VerificationProperties.getMutexUnlockFunctionCalls());
		}
		
		Node callsiteOfInterestWithinLock = null;
		for(Node callsiteWithinLock : callsitesWithinLock.eval().nodes()) {
			Node targetFunctionCall = CallSiteAnalysis.getTargets(callsiteWithinLock).one();
			if(lockFunctionCallsQ.eval().nodes().contains(targetFunctionCall)) {
				callsiteOfInterestWithinLock = callsiteWithinLock;
				break;
			}
		}
		if(callsiteOfInterestWithinLock == null) {
			DisplayUtils.showError(new IllegalStateException(), "Something wrong while finding the corresponding callsite");
			return;
		}

		Q callsiteOfInterestWithinLockQ = Common.toQ(callsiteOfInterestWithinLock);		
		Q parameterPassedToCallSiteOfInterest = universe().edges(XCSG.ParameterPassedTo).predecessors(callsiteOfInterestWithinLockQ).selectNode(XCSG.parameterIndex, 0);
		Q reverseDataFlowFromParameter = universe().edges(XCSG.DataFlow_Edge, Edge.ADDRESS_OF, Edge.POINTER_DEREFERENCE).reverse(parameterPassedToCallSiteOfInterest);
		Q signature = reverseDataFlowFromParameter.nodes(XCSG.Variable);
		SignatureVerificationUtils.verifySignatures(lockNode, signature, lockFunctionCallsQ, unlockFunctionCallsQ, graphsOutputDirectoryPath);
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
