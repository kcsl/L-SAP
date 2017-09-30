package com.kcsl.lsap.atlas.scripts;

import java.util.HashSet;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class StandardQueries {
	
	public static Q functionNodes = Common.universe().nodesTaggedWithAll(XCSG.Function, "isDef");
	public static Q containsEdges = Common.universe().edgesTaggedWithAll(XCSG.Contains);

	public static Q function(String name) { 
		return functionNodes.selectNode(XCSG.name, name);
	}
	
	public static Q functions(HashSet<String> names){
		return Common.toQ(functionNodes.eval().nodes().filter(XCSG.name, names.toArray()));	
	}
	
	public static Q getContainingMethod(GraphElement ge) {
		return getContainingMethods(Common.toQ(ge));
	}

	public static Q getContainingMethods(Q nodes) {
		return containsEdges.predecessors(nodes).nodesTaggedWithAll(XCSG.Function, "isDef");
	}
}
