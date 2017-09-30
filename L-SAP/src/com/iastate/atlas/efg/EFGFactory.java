package com.iastate.atlas.efg;


import static com.ensoftcorp.atlas.core.script.Common.universe;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.iastate.atlas.scripts.Queries;

public class EFGFactory {

	/**
	 * Construct the EFG for the given a method and the events of interest
	 * @param method
	 * @param events
	 * @return EFG
	 */
	public static Q EFG(Q cfg, Q events){
		//EventFlowGraphTransformation.undoAllEFGTransformations();
		EventFlowGraphTransformation transformations = new EventFlowGraphTransformation(cfg.eval(), events.eval().nodes());
		Q efg = transformations.constructEFG();
		return efg;
	}
	
	public static Q test(){
		Q function = Queries.function("w83793_probe");
		Q cfg = Queries.CFG(function);
		Q events = events(cfg);
		return EFG(cfg, events);
	}
	
	public static Q events(Q cfg){
		Q events = Common.empty();
		Q eventFunctions = Queries.function("mutex_lock_nested").union(Queries.function("mutex_unlock"));
		Q callSites = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(cfg).nodesTaggedWithAll(XCSG.CallSite);
		
		AtlasSet<Node> callsiteNodes = callSites.eval().nodes();
		
		for(Node callsiteNode : callsiteNodes){
			Q calledFunctions = universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).successors(Common.toQ(callsiteNode));
			if(!calledFunctions.intersection(eventFunctions).eval().nodes().isEmpty()){
				Q cfgNode = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(Common.toQ(callsiteNode)).nodesTaggedWithAll(XCSG.ControlFlow_Node);
				events = events.union(cfgNode);
			}			
		}
		return events;
	}

}