package edu.iastate.lsap;

import java.util.ArrayList;

import com.ensoftcorp.atlas.c.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.open.c.commons.analysis.CommonQueries;

public class NLSAP {
	public static ArrayList<String> mutexLockCalls;
	public static ArrayList<String> mutexUnlockCalls;
	
	public static ArrayList<String> spinLockCalls;
	public static ArrayList<String> spinUnlockCalls;
	
	public static Q lockMethods;
	public static Q unlockMethods;
	
	static{
		mutexLockCalls = new ArrayList<String>();
		mutexLockCalls.add("mutex_lock_nested");
		mutexLockCalls.add("mutex_trylock");
		mutexLockCalls.add("mutex_lock_interruptible_nested");
		mutexLockCalls.add("mutex_lock_killable_nested");
		mutexLockCalls.add("atomic_dec_and_mutex_lock_nested");
		mutexLockCalls.add("_mutex_lock_nest_lock");
		
		mutexUnlockCalls = new ArrayList<String>();
		mutexUnlockCalls.add("mutex_unlock");
		
		spinLockCalls = new ArrayList<String>();
		spinLockCalls.add("__raw_spin_lock");
		spinLockCalls.add("__raw_spin_trylock");
		
		spinUnlockCalls = new ArrayList<String>();
		spinUnlockCalls.add("__raw_spin_unlock");
		
		lockMethods = Common.empty();
		for(String lockCall : mutexLockCalls){
			lockMethods = lockMethods.union(Utilities.method(lockCall));
		}
		
		for(String lockCall : spinLockCalls){
			lockMethods = lockMethods.union(Utilities.method(lockCall));
		}		
		
		unlockMethods = Common.empty();
		for(String unlockCall : mutexUnlockCalls){
			unlockMethods = unlockMethods.union(Utilities.method(unlockCall));
		}
		
		for(String unlockCall : spinUnlockCalls){
			unlockMethods = unlockMethods.union(Utilities.method(unlockCall));
		}
	}

	public static String locate(String lock){
		Q callsites = Common.universe().nodesTaggedWithAll(XCSG.CallSite);
		Q cfgNodes = Common.universe().edgesTaggedWithAll(XCSG.Contains).predecessors(callsites);
		for(GraphElement node : cfgNodes.eval().nodes()){
			String sc = node.getAttr(XCSG.sourceCorrespondence).toString();
			if(sc.equals(lock)){
				DisplayUtil.displayGraph(Common.extend(Common.toQ(node), XCSG.Contains).eval());
				return "Found";
			}
		}
		return "Not Found!";
	}
	
	public static Q verify(Q selectedLockInstance){
		Q linkToField = Common.empty();
		selectedLockInstance = selectedLockInstance.nodesTaggedWithAll(XCSG.CallSite);
		linkToField = linkToField.union(selectedLockInstance);
		Q parametersPassedtoLockInstance = Common.universe().edgesTaggedWithAll(XCSG.PassedTo).reverseStep(selectedLockInstance);
		linkToField = linkToField.union(parametersPassedtoLockInstance);
		Q lockParameterPassedtoLockInstance = parametersPassedtoLockInstance.selectNode(XCSG.parameterIndex, 0);
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Edge.ADDRESS_OF, Edge.POINTER_DEREFERENCE);
		Q reverseDataFlow = dataFlowEdges.reverse(lockParameterPassedtoLockInstance);
		linkToField = linkToField.union(reverseDataFlow);
		DisplayUtil.displayGraph(Common.extend(linkToField, XCSG.Contains).eval());
		
		Q lockField = reverseDataFlow.roots();
		Q reachableCallsites = getReachableCallsites(selectedLockInstance, lockField);
		
		Q mpg = Utilities.MPG(reachableCallsites, lockMethods, unlockMethods, true);
		DisplayUtil.displayGraph(Common.extend(mpg, XCSG.Contains).eval());
		
		return linkToField;
	}
	
	private static Q getReachableCallsites(Q lockInstance, Q lockField){
		Q fieldToCallsites = Common.empty();
		Q lockUnlockMethods = lockMethods.union(unlockMethods);
		Q parameters = CommonQueries.functionParameter(lockUnlockMethods, 0);
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Edge.ADDRESS_OF, Edge.POINTER_DEREFERENCE);
		Q predecessors = dataFlowEdges.predecessors(parameters);
		Q forwardDataFlow = dataFlowEdges.between(lockField, predecessors, Utilities.methodReturn(Utilities.method("kmalloc")));
		fieldToCallsites = fieldToCallsites.union(forwardDataFlow);
		Q reachableCallsites = Common.universe().edgesTaggedWithAll(XCSG.PassedTo).forwardStep(forwardDataFlow.leaves());
		fieldToCallsites = fieldToCallsites.union(reachableCallsites);
		reachableCallsites = reachableCallsites.leaves();
		DisplayUtil.displayGraph(Common.extend(fieldToCallsites, XCSG.Contains).eval());
		return reachableCallsites;
	}
}
