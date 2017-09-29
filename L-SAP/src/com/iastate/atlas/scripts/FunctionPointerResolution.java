package com.iastate.atlas.scripts;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.awt.Color;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.H;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.highlight.Highlighter.ConflictStrategy;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;

public class FunctionPointerResolution {

	public static void inspect1(){
		String tuName = "ddbridge-core.c";
		String functionName = "drxk_gate_ctrl";
		inspect(functionName, tuName);
	}
	public static void inspect(String functionName, String tuName){

		// Get the translation unit containing the function of interest
		Q tu = universe().nodesTaggedWithAny(XCSG.C.TranslationUnit).selectNode(XCSG.name, tuName);
		Q containsEdges = universe().edgesTaggedWithAny(XCSG.Contains);
		Q function = containsEdges.forward(tu).nodesTaggedWithAny(XCSG.Function).selectNode(XCSG.name, functionName);
		Q cfg = Queries.CFG(function);
		DisplayUtil.displayGraph(Common.extend(cfg, XCSG.Contains).eval(), new Highlighter(), "CFG [" + functionName + "]");

		// Who reads that function: Traverse the data flow edges
		Q dataFlowEdges = universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
		Q reads = dataFlowEdges.forwardStep(function);
		DisplayUtil.displayGraph(Common.extend(reads, XCSG.Contains).eval(), new Highlighter(), "Reads [" + functionName + "]");
		
		Q readers = reads.leaves();
		Q containingCFGNodes = containsEdges.predecessors(readers);
		H h = new Highlighter(ConflictStrategy.COLOR);
		h.highlight(containingCFGNodes, Color.GREEN);
		
		Q functionsSettingFP = containsEdges.predecessors(containingCFGNodes);
		AtlasSet<GraphElement> functions = functionsSettingFP.eval().nodes();
		for(GraphElement f : functions){
			Q f_cfg = Queries.CFG(Common.toQ(f));
			DisplayUtil.displayGraph(Common.extend(f_cfg, XCSG.Contains).eval(), h, "CFG [" + f.getAttr(XCSG.name).toString() + "]");
		}
		
		Q fpDefinition = dataFlowEdges.forwardStep(readers);
		if(fpDefinition.eval().nodes().taggedWithAll("funcPtr", "isTentDef").isEmpty()){
			fpDefinition = dataFlowEdges.forwardStep(fpDefinition);
		}
		DisplayUtil.displayGraph(Common.extend(fpDefinition, XCSG.Contains).eval(), h, "Function Pointer Definition");
		
		fpDefinition = fpDefinition.leaves();
		Q invokedFunctionPointers = universe().nodesTaggedWithAny(XCSG.InvokedPointer);
		invokedFunctionPointers = dataFlowEdges.between(fpDefinition, invokedFunctionPointers).retainEdges().leaves();
		Q cfgNodes = containsEdges.predecessors(invokedFunctionPointers);
		Q functionsInvokingPtr = containsEdges.predecessors(cfgNodes);

		DisplayUtil.displayGraph(Common.extend(functionsInvokingPtr, XCSG.Contains).eval(), h, "Function Pointer Definition" + functionsInvokingPtr.eval().nodes().size());
		
		Q callEdges = universe().edgesTaggedWithAny(XCSG.Call);
		Q rcg = callEdges.reverse(functionsSettingFP);
		Q cg = callEdges.forward(rcg);
		
		functionsInvokingPtr = cg.intersection(functionsInvokingPtr);
		Q intersection = callEdges.reverse(functionsInvokingPtr).intersection(rcg);
		Q callGraph = intersection.union(functionsSettingFP, functionsInvokingPtr).induce(callEdges);
		DisplayUtil.displayGraph(Common.extend(callGraph, XCSG.Contains).eval(), new Highlighter(), "Call Graph");
		
		h = new Highlighter(ConflictStrategy.COLOR);
		h.highlight(cfgNodes, Color.RED);
		functions = functionsInvokingPtr.eval().nodes();
		for(GraphElement f : functions){
			Q f_cfg = Queries.CFG(Common.toQ(f));
			DisplayUtil.displayGraph(Common.extend(f_cfg, XCSG.Contains).eval(), h, "CFG [" + f.getAttr(XCSG.name).toString() + "]");
		}
	}
}
