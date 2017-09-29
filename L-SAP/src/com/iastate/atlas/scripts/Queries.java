package com.iastate.atlas.scripts;

import static com.ensoftcorp.atlas.core.script.Common.universe;
import static com.ensoftcorp.atlas.java.core.script.Common.edges;
import static com.ensoftcorp.atlas.java.core.script.CommonQueries.methodParameter;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.H;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.highlight.Highlighter.ConflictStrategy;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.iastate.atlas.efg.EFGFactory;

public class Queries {
	
	public static Q callEdgesContext = Common.resolve(null, universe().edgesTaggedWithAny(XCSG.Call));
	
	public static final String EVENT_FLOW_FOR_FUNCTION = "EFG Function Name";

	public static String EVENT_FLOW_EDGE = "eventFlow";
	
	public static String EVENT_FLOW_NODE = "eventFlow";
	
	public static String EVENT_FLOW_ENTRY_NODE = "EFG Entry";
	
	public static String EVENT_FLOW_EXIT_NODE = "EFG Exit";
	
	/**
	 * Returns the set of nodes that correspond to a definition of the function given by "name"
	 * @param name: The name of function
	 * @return The set of nodes representing a definition for that function
	 */
	public static Q function(String name) { 
		return universe().nodesTaggedWithAll(XCSG.Function).selectNode(XCSG.name, name); 
	}
	
	/**
	 * Returns the set of functions that correspond to the passed names
	 * @param names
	 * @return The set of nodes representing the function names in arguments
	 */
	public static  Q includes(String... names){
		Q functions = Common.empty();
		for(String n : names){
			functions = functions.union(function(n));
		}
		return functions;
	}
	
	/**
	 * Returns the global variable given by the name
	 * @param name
	 * @return global variable by the given name
	 */
	public static  Q global(String name){
		return universe().nodesTaggedWithAll(XCSG.GlobalVariable).selectNode(XCSG.name, name);
	}
	
	/**
	 * Returns the type node that represents the structure given by the passed name
	 * @param name
	 * @return The structure node for the given name
	 */
	public static  Q Type(String name){
		return universe().nodesTaggedWithAll(XCSG.C.Struct).selectNode(XCSG.name, "struct " + name);
	}
	
	/**
	 * Returns the set of functions referencing (read from or write to) the given global variable or type
	 * @param object of interest
	 * @return The set of functions referencing the passed object
	 */
	public static  Q ref(Q object){
		if(object.eval().nodes().getFirst().tags().contains(XCSG.GlobalVariable)){
			return refVariable(object);
		}
		if(object.eval().nodes().getFirst().tags().contains(XCSG.C.Struct)){
			return refType(object);
		}
		return null;
	}
	
	/**
	 * The set of functions referencing a given type
	 * @param type
	 * @return
	 */
	public static  Q refType(Q type){
		Q ref = Common.edges(XCSG.TypeOf, XCSG.ArrayElementType, "arrayOf", XCSG.ReferencedType).reverse(type);
		ref = Common.extend(ref, XCSG.Contains);
		return ref.nodesTaggedWithAll(XCSG.Function, "isDef").induce(callEdgesContext);
	}
	
	/**
	 * The set of functions referencing a given global variable
	 * @param variable
	 * @return
	 */
	public static  Q refVariable(Q variable){
		Q read =  universe().edgesTaggedWithAny(XCSG.ReadsVariable, XCSG.Reads, XCSG.ReadsFunctionAddress, XCSG.readOnly).reverseStep(variable);
		Q write = universe().edgesTaggedWithAll(XCSG.Writes).reverseStep(variable);
		Q all = read.union(write);
		all = Common.extend(all, XCSG.Contains);
		return all.nodesTaggedWithAll(XCSG.Function, "isDef").induce(callEdgesContext);
	}
	
	/**
	 * Returns the CFG of a given function name
	 * @param name: The name of function
	 * @return The CFG of a function
	 */
//	public static  Q CFG(String name){
//		Q method = function(name);
//		Q cfg = edges(XCSG.Contains).forward(method).nodesTaggedWithAny(XCSG.ControlFlow_Node).induce(edges(XCSG.ControlFlow_Edge));
//		return cfg;
//	}
	
	public static Q CFG(Q method){
		Q cfgRoots = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(method).nodesTaggedWithAll(XCSG.controlFlowRoot);
		Q cfgExits = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(method).nodesTaggedWithAll(XCSG.controlFlowExitPoint);
		Q cfg = Common.universe().edgesTaggedWithAll(XCSG.ControlFlow_Edge).between(cfgRoots, cfgExits);
		//Q cfg = edges(XCSG.Contains).forward(method).nodesTaggedWithAny(XCSG.ControlFlow_Node).induce(edges(XCSG.ControlFlow_Edge));
		return cfg;
	}
	 
	public static Q LoopFreeCFG(Q function){
		Q cfg = CFG(function);
		Q cfgBackEdges = cfg.edgesTaggedWithAll(XCSG.ControlFlowBackEdge);
		return cfg.differenceEdges(cfgBackEdges);
	}
	
	/**
	 * Returns the Event Flow Graph of a given function name
	 * @param name: The name of function
	 * @return The EFG of a function
	 */
//	public static  Q EFG(String name){
//		Q method = function(name);
//		Q efg = universe().edgesTaggedWithAny(XCSG.Contains, EVENT_FLOW_EDGE).forward(method).nodesTaggedWithAny(EVENT_FLOW_NODE).induce(universe().edgesTaggedWithAll(EVENT_FLOW_EDGE));
//		return efg;
//	}
	
	public static  Q EFG(Q method){
	Q efg = universe().edgesTaggedWithAny(XCSG.Contains, EVENT_FLOW_EDGE).forward(method).nodesTaggedWithAny(EVENT_FLOW_NODE).induce(universe().edgesTaggedWithAll(EVENT_FLOW_EDGE));
	return efg;
}
	
	/**
	 * Returns the call graph for the given function
	 * @param function
	 * @return the set of functions called directly/indirectly by a given function
	 */
	public static  Q cg(Q function){
		return callEdgesContext.forward(function);
	}
	
	/**
	 * Returns the reverse call graph of a given function
	 * @param function
	 * @return the set of functions the directly/indirectly call a given function
	 */
	public static  Q rcg(Q function){
		return callEdgesContext.reverse(function);
	}
	
	/**
	 * Returns the set of function directly calling a given function
	 * @param function
	 * @return direct callers of a given function
	 */
	public static  Q call(Q function){
		return callEdgesContext.reverseStep(function).roots();
	}
	
	/**
	 * Returns the set of functions that are directly called  by a given function
	 * @param function
	 * @return direct callees by a given function
	 */
	public static  Q calledby(Q function){
		return callEdgesContext.forwardStep(function).leaves();
	}
	
	/**
	 * Returns the set of arguments passed to the given function
	 * @param function
	 * @param index
	 * @return the set of variables passed as a parameter to a given argument
	 */
	public static  Q argto(Q function, int index){
		return methodParameter(function, 0).roots();
	}
	
	/**
	 * Returns the call site nodes for a given function
	 * @param functions
	 * @return The call site nodes
	 */
	public static  Q functionReturn(Q functions) {
		return edges(XCSG.Contains).forwardStep(functions).nodesTaggedWithAll(XCSG.MasterReturn);
	}
	
	/**
	 * Returns the function node that contains the given query
	 * @param q
	 * @return The function node
	 */
	public static  Q getFunctionContainingElement(Q q){
		Q declares = edges(XCSG.Contains).reverseStep(q);
		declares = edges(XCSG.Contains).reverseStep(declares);
		return declares.nodesTaggedWithAll(XCSG.Function, "isDef");
	}
	
	public static  Q getFunctionContainingElement(GraphElement e){
		return getFunctionContainingElement(Common.toQ(Common.toGraph(e)));
	}
	
	public static  Q script(){
		Q getbuf = function("getbuf");
		Q freebuf = function("freebuf");
		Q dreq = Type("dreq");
		return mpg(getbuf, freebuf, dreq);
	}
	
	/**
	 * Returns the call graph between the roots and leaves
	 * @param roots
	 * @param leaves
	 * @return
	 */
	public static  Q graph(Q roots, Q leaves){
		 return rcg(leaves).between(roots, leaves);
	}
	
	/**
	 * Returns the type of for a given query
	 * @param q
	 * @return The type nodes
	 */
	public static  Q typeOf(Q q) {
		Q res = Common.edges(XCSG.TypeOf, XCSG.ArrayElementType, "arrayOf", XCSG.ReferencedType).forward(q);
		return res;
	}
	
	/**
	 * Returns the Matching Pair Graph for given object and event functions
	 * @param e1Functions
	 * @param e2Functions
	 * @param object
	 * @return the matching pair graph for object (object)
	 */
	public static  Q mpg(Q e1Functions, Q e2Functions, Q object){
		Q callL = call(e1Functions);
		Q callU = call(e2Functions);
		if(object.eval().nodes().getFirst().tags().contains(XCSG.GlobalVariable)){
			callL = callL.intersection(refVariable(object));
			callU = callU.intersection(refVariable(object));
		}else if(object.eval().nodes().getFirst().tags().contains(XCSG.C.Struct)){
			callL = callL.intersection(refType(object));
			callU = callU.intersection(refType(object));
		}
		Q rcg_lock = callEdgesContext.reverse(callL);
		Q rcg_unlock = callEdgesContext.reverse(callU);
		Q rcg_both = rcg_lock.intersection(rcg_unlock);
		Q rcg_c = rcg_lock.union(rcg_unlock);
		Q rcg_lock_only = rcg_lock.difference(rcg_both);
		Q rcg_unlock_only = rcg_unlock.difference(rcg_both);
		Q call_lock_only = callL.union(callEdgesContext.reverseStep(rcg_lock_only));
		Q call_unlock_only = callU.union(callEdgesContext.reverseStep(rcg_unlock_only));
		Q call_c_only = call_lock_only.union(call_unlock_only);
		Q balanced = call_c_only.intersection(rcg_both);
		Q ubc = balanced.union(rcg_lock_only, rcg_unlock_only);
		Q mpg = rcg_c.intersection(callEdgesContext.forward(ubc));
		mpg = mpg.union(e1Functions, e2Functions);
		mpg = mpg.induce(callEdgesContext);
		return mpg;
	}
	
	public static Q mpg(Q callL, Q callU){
		Highlighter h = new Highlighter();
		h.highlight(callL, Color.RED);
		h.highlight(callU, Color.GREEN);
		
		Q rcg_lock = callEdgesContext.reverse(callL);
		Q rcg_unlock = callEdgesContext.reverse(callU);
		Q rcg_both = rcg_lock.intersection(rcg_unlock);
		Q rcg_c = rcg_lock.union(rcg_unlock);
		Q rcg_lock_only = rcg_lock.difference(rcg_both);
		Q rcg_unlock_only = rcg_unlock.difference(rcg_both);
		Q call_lock_only = callL.union(callEdgesContext.reverseStep(rcg_lock_only));
		Q call_unlock_only = callU.union(callEdgesContext.reverseStep(rcg_unlock_only));
		Q call_c_only = call_lock_only.union(call_unlock_only);
		Q balanced = call_c_only.intersection(rcg_both);
		h.highlight(balanced, Color.YELLOW);
		Q ubc = balanced.union(rcg_lock_only, rcg_unlock_only);
		h.highlight(ubc, Color.MAGENTA);
		Q mpg = rcg_c.intersection(callEdgesContext.forward(ubc));
		mpg = mpg.union(callL, callL);
		mpg = mpg.induce(callEdgesContext);
		
		h.highlightEdges(mpg.reverseStep(callL), Color.RED);
		h.highlightEdges(mpg.reverseStep(callU), Color.GREEN);
		DisplayUtil.displayGraph(mpg.eval(), h);
		return mpg;
	}
	
	/**
	 * Returns the Matching Pair Graph for given set of call sites
	 * @param callSiteNodes
	 * @param e1
	 * @param e2
	 * @return the matching pair Graph
	 */
	public static  Q mpg(Q callSiteNodes){
		HashSet<String> lock = new HashSet<String>();
		lock.add("mutex_lock_nested");
		
		HashSet<String> unlock = new HashSet<String>();
		unlock.add("mutex_unlock");
		return mpg(callSiteNodes, lock, unlock);
		
	}
	public static  Q mpg(Q callSiteNodes, HashSet<String> e1, HashSet<String> e2){
		AtlasSet<GraphElement> nodes = callSiteNodes.eval().nodes();
		HashMap<GraphElement, HashMap<String, AtlasSet<GraphElement>>> functionMap = new HashMap<GraphElement, HashMap<String,AtlasSet<GraphElement>>>(); 
		
		for(GraphElement node : nodes){
			Q functionQ = getFunctionContainingElement(node);
			GraphElement functionNode = functionQ.eval().nodes().getFirst();
			
			boolean callingLock = false;
			boolean callingUnlock = false;
			if(isCalling(node, e1)){
				callingLock = true;
			}
			
			if(isCalling(node, e2)){
				callingUnlock = true;
			}
			
			if(callingLock || callingUnlock){
				HashMap<String, AtlasSet<GraphElement>> luMap = new HashMap<String, AtlasSet<GraphElement>>();
				
				if(functionMap.containsKey(functionNode)){
					luMap = functionMap.get(functionNode);
				}
				
				if(callingLock){
					AtlasSet<GraphElement> callL = new AtlasHashSet<GraphElement>();
					if(luMap.containsKey("L")){
						callL = luMap.get("L");
					}
					callL.add(node);
					luMap.put("L", callL);
				}
				
				if(callingUnlock){
					AtlasSet<GraphElement> callU = new AtlasHashSet<GraphElement>();
					if(luMap.containsKey("U")){
						callU = luMap.get("U");
					}
					callU.add(node);
					luMap.put("U", callU);
				}
				functionMap.put(functionNode, luMap);
			}
		}
			
		AtlasSet<GraphElement> callL = new AtlasHashSet<GraphElement>();
		AtlasSet<GraphElement> callU = new AtlasHashSet<GraphElement>();
		AtlasSet<GraphElement> unbalanced = new AtlasHashSet<GraphElement>();
		
		for(GraphElement f : functionMap.keySet()){
			HashMap<String, AtlasSet<GraphElement>> nodesMap = functionMap.get(f);
			if(nodesMap.size() == 1 && nodesMap.keySet().contains("L")){
				callL.add(f);
				continue;
			}
			
			if(nodesMap.size() == 1 && nodesMap.keySet().contains("U")){
				callU.add(f);
				continue;
			}
			
			callL.add(f);
			callU.add(f);
			
			AtlasSet<GraphElement> lNodes = nodesMap.get("L");
			List<Integer> ls = new ArrayList<Integer>();
			for(GraphElement l : lNodes){
				SourceCorrespondence sc = (SourceCorrespondence) l.attr().get(XCSG.sourceCorrespondence);
				ls.add(sc.offset);
			}
			AtlasSet<GraphElement> uNodes = nodesMap.get("U");
			List<Integer> us = new ArrayList<Integer>();
			for(GraphElement u : uNodes){
				SourceCorrespondence sc = (SourceCorrespondence) u.attr().get(XCSG.sourceCorrespondence);
				us.add(sc.offset);
			}
			
			Collections.sort(ls);
			Collections.sort(us);
			
			if(us.get(us.size() - 1) <= ls.get(ls.size() - 1)){
				unbalanced.add(f);
				callU.remove(f);
			}
		}
		
		Q callLQ = Common.toQ(callL);
		Q callUQ = Common.toQ(callU);
		//Q callLU = callLQ.intersection(callUQ);
		Q rcg_lock = callEdgesContext.reverse(callLQ);
		Q rcg_unlock = callEdgesContext.reverse(callUQ);
		Q rcg_both = rcg_lock.intersection(rcg_unlock);
		Q rcg_c = rcg_lock.union(rcg_unlock);
		Q rcg_lock_only = rcg_lock.difference(rcg_both);
		Q rcg_unlock_only = rcg_unlock.difference(rcg_both);
		Q call_lock_only = callLQ.union(callEdgesContext.reverseStep(rcg_lock_only));
		Q call_unlock_only = callUQ.union(callEdgesContext.reverseStep(rcg_unlock_only));
		Q call_c_only = call_lock_only.union(call_unlock_only);
		Q balanced = call_c_only.intersection(rcg_both);
		Q ubc = balanced.union(rcg_lock_only, rcg_unlock_only);
		Q mpg = rcg_c.intersection(callEdgesContext.forward(ubc));
		
		return mpg;
	}
	
	public static  boolean isCalling(GraphElement node, HashSet<String> functions){
		for(String f : functions){
			if(((String) node.attr().get(XCSG.name)).contains(f + "("))
				return true;
		}
		return false;
	}
	
	public static  boolean isCalling(GraphElement node, HashSet<String> functions, FileWriter writer){
		for(String f : functions){
			try {
				writer.write((String) node.attr().get(XCSG.name) + "\t" + f + "\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(((String) node.attr().get(XCSG.name)).contains(f + "("))
				return true;
		}
		return false;
	}
	
	/**
	 * Delete EFG tags from the index
	 */
	public static  void deleteEFGs(){
		AtlasSet<GraphElement> edges = universe().edgesTaggedWithAll(EVENT_FLOW_EDGE).eval().edges();
		HashSet<GraphElement> toDelete = new HashSet<GraphElement>(); 
		for(GraphElement edge : edges){
			toDelete.add(edge);
		}
		
		for(GraphElement edge : toDelete){
			Graph.U.delete(edge);
		}
		
		AtlasSet<GraphElement> nodes = universe().nodesTaggedWithAll(EVENT_FLOW_NODE).eval().nodes();
		
		HashSet<GraphElement> ns = new HashSet<GraphElement>();
		for(GraphElement node : nodes){
			ns.add(node);
		}
		
		toDelete = new HashSet<GraphElement>(); 
		for(GraphElement node : ns){
			node.tags().remove(EVENT_FLOW_NODE);
			String name = (String) node.attr().get(XCSG.name);
			if(name.equals(EVENT_FLOW_ENTRY_NODE) || name.equals(EVENT_FLOW_EXIT_NODE)){
				toDelete.add(node);
			}
		}
		
		for(GraphElement node : toDelete){
			Graph.U.delete(node);
		}
	}
	
	public static Object[] isQualified(Q method){
		Q cfg = CFG(method);
		HashSet<String> functions = new HashSet<String>();
		functions.add("mutex_lock_nested");
		functions.add("mutex_trylock");
		functions.add("mutex_lock_interruptible_nested");
		functions.add("mutex_lock_killable_nested");
		functions.add("atomic_dec_and_mutex_lock_nested");
		functions.add("_mutex_lock_nest_lock");
		Q lockQ = Common.empty();
		for(String function : functions){
			lockQ = lockQ.union(function(function));
		}
		
		Q unlockQ = function("mutex_unlock");
		
		Q callsites = universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).predecessors(lockQ).nodesTaggedWithAll(XCSG.CallSite);
		Q LcfgNodes = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(callsites).nodesTaggedWithAll(XCSG.ControlFlow_Node);
		LcfgNodes = LcfgNodes.intersection(cfg);
		
		callsites = universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).predecessors(unlockQ).nodesTaggedWithAll(XCSG.CallSite);
		Q UcfgNodes = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(callsites).nodesTaggedWithAll(XCSG.ControlFlow_Node);
		UcfgNodes = UcfgNodes.intersection(cfg);
		return new Object [] {!LcfgNodes.eval().nodes().isEmpty() && !UcfgNodes.eval().nodes().isEmpty(), LcfgNodes, UcfgNodes};
	}
	
	
	public static void test2(String functionName){
		Q method = function(functionName);
		HashSet<String> functions = new HashSet<String>();
		functions.add("mutex_lock_nested");
		functions.add("mutex_trylock");
		functions.add("mutex_lock_interruptible_nested");
		functions.add("mutex_lock_killable_nested");
		functions.add("atomic_dec_and_mutex_lock_nested");
		functions.add("_mutex_lock_nest_lock");
		Q lockQ = Common.empty();
		for(String function : functions){
			lockQ = lockQ.union(function(function));
		}
		
		Q unlockQ = function("mutex_unlock");
		
		Q callsites = universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).predecessors(lockQ).nodesTaggedWithAll(XCSG.CallSite);
		Q LcfgNodes = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(callsites).nodesTaggedWithAll(XCSG.ControlFlow_Node);
		
		callsites = universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).predecessors(unlockQ).nodesTaggedWithAll(XCSG.CallSite);
		Q UcfgNodes = universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(callsites).nodesTaggedWithAll(XCSG.ControlFlow_Node);
		
		Q efg = EFGFactory.EFG(CFG(method), LcfgNodes.union(UcfgNodes));
		H h = new Highlighter(ConflictStrategy.COLOR);
		h.highlight(LcfgNodes, Color.RED);
		h.highlight(UcfgNodes, Color.GREEN);
		DisplayUtil.displayGraph(efg.eval(), h);
	}
	
	public static void mem(Q method, Q malloc, Q free, Q callsite){
		Q cfg = Queries.CFG(method);
		Q efg = EFGFactory.EFG(cfg, malloc.union(free,callsite));
		H h = new Highlighter(ConflictStrategy.COLOR);
		h.highlight(malloc, Color.RED);
		h.highlight(free, Color.GREEN);
		h.highlight(callsite, Color.CYAN);
		DisplayUtil.displayGraph(Common.extend(cfg, XCSG.Contains).eval(), h, "CFG [" + method.eval().nodes().getFirst().getAttr(XCSG.name).toString() + "]");
		DisplayUtil.displayGraph(Common.extend(efg, XCSG.Contains).eval(), h, "EFG [" + method.eval().nodes().getFirst().getAttr(XCSG.name).toString() + "]");	
	}
	
}
