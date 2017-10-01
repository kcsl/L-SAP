package com.kcsl.lsap.utilities;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class Utilities {

	public static Q method(String name) {
		return Common.universe().nodesTaggedWithAll(XCSG.Function).selectNode(XCSG.name, name); 
	}
	
	public static Q methodReturn(Q methods) {
		return Common.universe().edgesTaggedWithAll(XCSG.Contains).forwardStep(methods).nodesTaggedWithAll(XCSG.MasterReturn);
	}
	
	public static Q MPG(Q callsites, Q lockMethods, Q unlockMethods, boolean includeLockUnlockMethods){
		Q callEdges = Common.universe().edgesTaggedWithAll(XCSG.Call);
		Q mpg = Common.empty();
		Q callL = Common.empty();
		Q callU = Common.empty();
		
		AtlasSet<Node> callsiteElements = callsites.eval().nodes();
		for(Node callsite : callsiteElements){
			Q method = getMethodContainingCallsite(Common.toQ(callsite));
			Q calledMethods = calledMethods(Common.toQ(callsite));
			Q calledLockMethods = calledMethods.intersection(lockMethods);
			Q calledUnlockMethods = calledMethods.intersection(unlockMethods);
			if(!calledLockMethods.eval().nodes().isEmpty()){
				callL = callL.union(method);
				if(includeLockUnlockMethods){
					mpg = mpg.union(callEdges.betweenStep(method, calledLockMethods));
				}
			}else if(!calledUnlockMethods.eval().nodes().isEmpty()){
				callU = callU.union(method);
				if(includeLockUnlockMethods){
					mpg = mpg.union(callEdges.betweenStep(method, calledUnlockMethods));
				}
			}
		}
		Q rcg_lock = callEdges.reverse(callL);
		Q rcg_unlock = callEdges.reverse(callU);
		Q rcg_both = rcg_lock.intersection(rcg_unlock);
		Q rcg_c = rcg_lock.union(rcg_unlock);
		Q rcg_lock_only = rcg_lock.difference(rcg_both);
		Q rcg_unlock_only = rcg_unlock.difference(rcg_both);
		Q call_lock_only = callL.union(callEdges.reverseStep(rcg_lock_only));
		Q call_unlock_only = callU.union(callEdges.reverseStep(rcg_unlock_only));
		Q call_c_only = call_lock_only.union(call_unlock_only);
		Q balanced = call_c_only.intersection(rcg_both);
		Q ubc = balanced.union(rcg_lock_only, rcg_unlock_only);
		mpg = mpg.union(rcg_c.intersection(callEdges.forward(ubc)));
		return mpg;
	}
	
	public static Q calledMethods(Q callsite){
		return Common.universe().edgesTaggedWithAll(XCSG.InvokedFunction).successors(callsite);
	}
	
	public static Q getMethodContainingCallsite(Q callsite){
		Q containsEdges = Common.universe().edgesTaggedWithAll(XCSG.Contains);
		Q cfgNode = containsEdges.predecessors(callsite);
		Q method = containsEdges.predecessors(cfgNode);
		return method;
	}
}
