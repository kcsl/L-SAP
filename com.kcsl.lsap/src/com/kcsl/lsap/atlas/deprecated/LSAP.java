package com.kcsl.lsap.atlas.deprecated;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.c.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.H;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.highlight.Highlighter.ConflictStrategy;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.kcsl.lsap.efg.deprecated.EventFlowGraphTransformation;
import com.kcsl.lsap.verifier.deprecated.CFGDisconnectedException;
import com.kcsl.lsap.verifier.deprecated.EFGCreationException;
import com.kcsl.lsap.verifier.deprecated.Utils;
import com.kcsl.lsap.verifier.deprecated.Verifier;

public class LSAP {

	/**
	 * This will invoke L-SAP to verify all the locks contained within the indexed Linux kernel
	 */
	public static void verify(){
		LinuxScripts.verifySpin(true);
		LinuxScripts.verifyMutex(true);
	}
	
	/**
	 * This function will invoke L-SAP to verify selected instances only
	 * @param selected
	 */
	public static String verify(Q selected){
		ArrayList<String> callsitesOfInterest = new ArrayList<String>();
		callsitesOfInterest.add("mutex_lock_nested");
		callsitesOfInterest.add("mutex_trylock");
		callsitesOfInterest.add("mutex_lock_interruptible_nested");
		callsitesOfInterest.add("mutex_lock_killable_nested");
		callsitesOfInterest.add("atomic_dec_and_mutex_lock_nested");
		callsitesOfInterest.add("_mutex_lock_nest_lock");
		callsitesOfInterest.add("__raw_spin_lock");
		callsitesOfInterest.add("__raw_spin_trylock");
		callsitesOfInterest.add("mutex_unlock");
		callsitesOfInterest.add("__raw_spin_unlock");
		
		Q locks = Common.empty();
		locks = locks.union(Queries.function("mutex_lock_nested"));
		locks = locks.union(Queries.function("mutex_trylock"));
		locks = locks.union(Queries.function("mutex_lock_interruptible_nested"));
		locks = locks.union(Queries.function("mutex_lock_killable_nested"));
		locks = locks.union(Queries.function("atomic_dec_and_mutex_lock_nested"));
		locks = locks.union(Queries.function("_mutex_lock_nest_lock"));
		locks = locks.union(Queries.function("__raw_spin_lock"));
		locks = locks.union(Queries.function("__raw_spin_trylock"));
		
		Q unlocks = Queries.function("mutex_unlock").union(Queries.function("__raw_spin_unlock"));
		Q calls = locks.union(unlocks);
		
		Q callsites = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(selected).nodesTaggedWithAll(XCSG.CallSite);
		callsites = Common.universe().edgesTaggedWithAll(XCSG.InvokedFunction).predecessors(calls).nodesTaggedWithAll(XCSG.CallSite).intersection(callsites);
		Q parameter = Common.universe().edgesTaggedWithAll(XCSG.PassedTo).reverseStep(callsites).selectNode(XCSG.parameterIndex, 0);
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Edge.ADDRESS_OF, Edge.POINTER_DEREFERENCE);
		Q reverseDataFlow = dataFlowEdges.reverse(parameter);
		Q field = reverseDataFlow.roots();
		Q predecessors = dataFlowEdges.predecessors(CommonQueries.functionParameter(calls, 0));
		Q forwardDataFlow = dataFlowEdges.between(field, predecessors, Queries.functionReturn(Queries.function("kmalloc")));
		Q cfgNodes = Common.universe().edgesTaggedWithAll(XCSG.Contains).predecessors(forwardDataFlow.leaves());
		callsites = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(cfgNodes).nodesTaggedWithAll(XCSG.CallSite);
		return _verify(selected.eval().nodes().getFirst(), field, callsites, calls, cfgNodes, new HashSet<String>(callsitesOfInterest.subList(0, 8)), new HashSet<String>(callsitesOfInterest.subList(8, 10)), locks, unlocks);
	}
	
	private static String _verify(Node selectedLock, Q field, Q callsites, Q calls, Q cfgNodes, HashSet<String> locks, HashSet<String> unlocks, Q ls, Q us){
		LinuxScripts.deleteDuplicateNodes();
		Queries.deleteEFGs();
		Q mpg = Queries.mpg(callsites, locks, unlocks);	
		mpg = mpg.union(calls);
		mpg = mpg.induce(Queries.callEdgesContext);
		Q toRemoveEdges = mpg.edgesTaggedWithAny(XCSG.Call).forwardStep(calls).edgesTaggedWithAny(XCSG.Call);
		mpg = mpg.differenceEdges(toRemoveEdges);
		Q unused = mpg.roots().intersection(mpg.leaves());
		mpg = mpg.difference(unused);
		long mpgSize = mpg.eval().nodes().size();
		
		if(mpgSize > 500){
			return null;
		}
		
		if(!mpg.selectNode(XCSG.name, "setKey").eval().nodes().isEmpty()){
			return null;						
		}
		
		try {
			return verify(selectedLock, field.eval().nodes().getFirst(), mpg, cfgNodes, ls.eval().nodes(), us.eval().nodes(), locks, unlocks, new HashMap<Node, Boolean>());
		} catch (EFGCreationException | CFGDisconnectedException e) {
			return null;
		}
	}
	
	public static String verify(Node selectedLock, Node object, Q envelope, Q mainEventNodes, AtlasSet<Node> e1, AtlasSet<Node> e2, HashSet<String> e1Functions, HashSet<String> e2Functions, HashMap<Node, Boolean> feasibilityMap) throws EFGCreationException, CFGDisconnectedException{	
		HashMap<Node, Graph> functionFlowMap = new HashMap<Node, Graph>();
		HashMap<Node, List<Q>> functionEventsMap = new HashMap<Node, List<Q>>();
		
		Graph envelopeGraph = envelope.eval();
		
		if(!Utils.isDirectedAcyclicGraph(envelopeGraph)){
			Utils.error(0, "The verification envelope is a cyclic graph! Cutting cycles in graph.");
			Utils.error(0, "The verification envelope has [" + envelopeGraph.nodes().size() + "] nodes and [" + envelopeGraph.edges().size() + "] edges.");
			envelopeGraph = Utils.cutCyclesFromGraph(envelopeGraph);
			envelope = Common.toQ(envelopeGraph);
			
			if(!Utils.isDirectedAcyclicGraph(envelopeGraph)){
				Utils.error(0, "The verification envelope is a cyclic graph!");
				return null;
			}
		}
		
		HashSet<String> envelopeFunctions = new HashSet<String>();
		for(Node function : envelopeGraph.nodes()){
			if(e1.contains(function) || e2.contains(function))
				continue;
			
			String functionName = function.attr().get(XCSG.name).toString();
			envelopeFunctions.add(functionName);
		}
		
		for(Node function : envelopeGraph.nodes()){
			if(e1.contains(function) || e2.contains(function))
				continue;
			
			Q cfg = Queries.CFG(Common.toQ(Common.toGraph(function)));
			List<Q> events = LinuxScripts.getEventNodes(cfg, mainEventNodes, envelopeFunctions, e1Functions, e2Functions);
			Q e1Events = events.get(0);
			Q e2Events = events.get(1);
			Q envelopeEvents = events.get(2);
			Q eventNodes = events.get(3); 
			
			H h = new Highlighter(ConflictStrategy.COLOR);
			h.highlight(e1Events, Color.RED);
			h.highlight(e2Events, Color.GREEN);
			h.highlight(envelopeEvents, Color.BLUE);

			Graph g = cfg.eval();
			if(LinuxScripts.isGraphDisconnected(g)){
				throw new CFGDisconnectedException(function.attr().get(XCSG.name).toString());
			}
			
			try{
				Utils.addEFGToIndex(function, g, Utils.createEventFlowGraph(g, eventNodes.eval().nodes()));	
			}catch(NullPointerException e){
				throw new EFGCreationException(function.attr().get(XCSG.name).toString(), e);
			}
			
			Q efg = Queries.EFG(Common.toQ(Common.toGraph(function)));
			
			functionFlowMap.put(function, efg.eval());
			events.remove(events.size() - 1);
			AtlasSet<Node> mayEvents = new AtlasHashSet<Node>();
			for(Node n : e1Events.eval().nodes()){
				if(feasibilityMap.containsKey(n)){
					mayEvents.add(n);
				}
			}
			events.add(Common.toQ(Common.toGraph(mayEvents)));
			functionEventsMap.put(function, events);
		}
		Graph tempEnvelope = envelope.eval();
		envelope = envelope.difference(envelope.leaves());
		String signature = object.attr().get(XCSG.name) + "(" + object.address().toAddressString() + ")";
		
		Verifier verifier = new Verifier(signature, envelope.eval(), functionFlowMap, functionEventsMap, feasibilityMap);
		verifier.verify();
		Queries.deleteEFGs();
		EventFlowGraphTransformation.undoAllEFGTransformations();
		Q verifiedLocks = Common.toQ(Utils.toAtlasSet(verifier.verifiedLocks));
		Q doubleLocks = Common.toQ(Utils.toAtlasSet(verifier.doubleLocks));
		Q danglingLocks = Common.toQ(Utils.toAtlasSet(verifier.danglingLocks));
		Q partiallyLocks = Common.toQ(Utils.toAtlasSet(verifier.partiallyLocks));
		SpecificLockResults stats = new SpecificLockResults(selectedLock, object, tempEnvelope, verifier.matchingPairsMap, verifiedLocks, doubleLocks, danglingLocks, partiallyLocks);
		return stats.process();
	}
	
	/**
	 * Located a given lock and show its corresponding CFG Node
	 */
	public static String locate(String lock){
		Q callsites = Common.universe().nodesTaggedWithAll(XCSG.CallSite);
		Q cfgNodes = Common.universe().edgesTaggedWithAll(XCSG.Contains).predecessors(callsites);
		for(Node node : cfgNodes.eval().nodes()){
			String sc = node.getAttr(XCSG.sourceCorrespondence).toString();
			if(sc.equals(lock)){
				DisplayUtil.displayGraph(Common.extend(Common.toQ(node), XCSG.Contains).eval());
				return "Found";
			}
		}
		return "Not Found!";
	}
	
	public static H highlight(Q nodes){
		H h = new Highlighter(ConflictStrategy.COLOR);
		Q locks = Common.empty();
		locks = locks.union(Queries.function("mutex_lock_nested"));
		locks = locks.union(Queries.function("mutex_trylock"));
		locks = locks.union(Queries.function("mutex_lock_interruptible_nested"));
		locks = locks.union(Queries.function("mutex_lock_killable_nested"));
		locks = locks.union(Queries.function("atomic_dec_and_mutex_lock_nested"));
		locks = locks.union(Queries.function("_mutex_lock_nest_lock"));
		locks = locks.union(Queries.function("__raw_spin_lock"));
		locks = locks.union(Queries.function("__raw_spin_trylock"));
		
		Q unlocks = Queries.function("mutex_unlock").union(Queries.function("__raw_spin_unlock"));	
		
		h.highlight(locks, Color.RED);
		h.highlight(unlocks, Color.GREEN);
		return h;
	}
	
	public static Q setsFP(Q function){
		Q dataFlowEdges = Common.universe().edgesTaggedWithAll(XCSG.DataFlow_Edge);
		Q fpReaders = dataFlowEdges.forwardStep(function);
		return fpReaders;
	}
	
	public static Q field(Q reader){
		Q dataFlowEdges = Common.universe().edgesTaggedWithAll(XCSG.DataFlow_Edge);
		Q fpFieldGraph = dataFlowEdges.forwardStep(reader);
		fpFieldGraph = dataFlowEdges.forwardStep(fpFieldGraph);
		return fpFieldGraph;
	}
	
	public static Q invokesFP(Q field){
		Q dataFlowEdges = Common.universe().edgesTaggedWithAll(XCSG.DataFlow_Edge);
		Q containsEdges = Common.universe().edgesTaggedWithAll(XCSG.Contains);
		Q invokedFPs = Common.universe().nodesTaggedWithAll(XCSG.InvokedPointer);
		Q invokedFPWithField = dataFlowEdges.between(field, invokedFPs).retainEdges().leaves();
		Q containingCFGNodes = containsEdges.predecessors(invokedFPWithField);
		Q functionsInvokingFP = containsEdges.predecessors(containingCFGNodes);
		return functionsInvokingFP;
	}
	
	public static Q callReachable(Q functions, Q function){
		Q callEdges = Common.universe().edgesTaggedWithAll(XCSG.Call);
		Q rcg = callEdges.reverse(function);
		Q cg_rcg = callEdges.forward(rcg);
		Q functionsOfInterest = functions.intersection(cg_rcg);
		return functionsOfInterest;
	}
	
}
