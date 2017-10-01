package com.kcsl.lsap.atlas;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;

import com.ensoftcorp.atlas.c.core.query.Attr;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.kcsl.lsap.verifier.Utils;

public class TryEventsFeasibilityMiner {
	
	public void t1(){
		HashSet<String> mayLockFunctions = new HashSet<String>();
		mayLockFunctions.add("mutex_trylock");
		returnMayEventsFeasibilityMap(mayLockFunctions);
	}
	
	public void t2(){
		HashSet<String> mayLockFunctions = new HashSet<String>();
		mayLockFunctions.add("__raw_spin_trylock");
		returnMayEventsFeasibilityMap(mayLockFunctions);
	}
	
	public HashMap<Node, Boolean> returnMayEventsFeasibilityMap(HashSet<String> mayEvents){
		HashMap<Node, Boolean> tryEventNodesFeasibilityMap = new HashMap<Node, Boolean>();
		Q mayEventFuncs = Common.empty();
		for(String f : mayEvents){
			mayEventFuncs = mayEventFuncs.union(Queries.function(f));
		}
		
		Q functionReturn = Queries.functionReturn(mayEventFuncs);
		//TODO: Check how to get the condition nodes more efficiently and systematically
		Q leaves = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Attr.Edge.ADDRESS_OF, Attr.Edge.POINTER_DEREFERENCE).forward(functionReturn).leaves();
		Q conditionalControlFlowNodes = Common.universe().edgesTaggedWithAll(XCSG.Contains).reverse(leaves).nodesTaggedWithAll(XCSG.ControlFlowCondition);
		// Filter return or non-conditional leaves. Those nodes contribute to return value always
		Q conditions = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(conditionalControlFlowNodes).intersection(leaves);
		AtlasSet<Node> nodes = conditions.eval().nodes();
		Highlighter h = new Highlighter();
		for(Node node : nodes){
			Q condition = Common.toQ(Common.toGraph(node));
			Node controlFlowNode = Common.universe().edgesTaggedWithAll(XCSG.Contains).reverseStep(condition).nodesTaggedWithAll(XCSG.ControlFlowCondition).eval().nodes().getFirst();
			Q operator = Common.universe().edgesTaggedWithAll(XCSG.DataFlow_Edge).reverseStep(condition).nodesTaggedWithAll(XCSG.Operator);
			Graph operatorGraph = operator.eval();
			if(operatorGraph.nodes().size() == 0){
				// There is no operator, that means the call to (mutex_trylock) is conditioned. We should make sure that taking the (FALSE) branch is (INFEASIBLE)
				tryEventNodesFeasibilityMap.put(controlFlowNode, true);
				h.highlight(condition, Color.GREEN);
			}else{
				Node operatorNode = operatorGraph.nodes().getFirst();
				if(operatorNode.tags().contains(XCSG.BinaryOperator)){
					if(operatorNode.tags().contains(XCSG.EqualTo)){
						// I have assumed that using the (==) operator with the return value from (conditional function) to be (== 0)
						tryEventNodesFeasibilityMap.put(controlFlowNode, false);
						h.highlight(condition, Color.RED);
						continue;
					}
				}else if(operatorNode.tags().contains(XCSG.UnaryOperator)){
					if(operatorNode.tags().contains(XCSG.LogicalNot) || operatorNode.tags().contains(XCSG.Negation)){
						tryEventNodesFeasibilityMap.put(controlFlowNode, false);
						h.highlight(condition, Color.RED);
						continue;
					}
				}
				Utils.error(0, "Not sure what is the type of operator: ["+ operatorNode.attr().get(XCSG.name) +"]");
				//DisplayUtil.displayGraph(Common.toGraph(operatorNode), new Highlighter(), "Not Known Operator");
			}
		}
		//DisplayUtil.displayGraph(Common.extend(conditions, XCSG.Contains).eval(), h);
		return tryEventNodesFeasibilityMap;
	}
	
	
	public Q keepUnrolling(Node element){
		Q elementQ = Common.toQ(Common.toGraph(element));
		Q dfForward = elementQ;
		Graph dfForwardGraph = dfForward.eval();
		long preNodesCount = 0;
		long preEdgesCount = 0;
		do{
			preNodesCount = dfForwardGraph.nodes().size();
			preEdgesCount = dfForwardGraph.edges().size();
			dfForward = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge, Attr.Edge.ADDRESS_OF, Attr.Edge.POINTER_DEREFERENCE).forwardStep(dfForward);
			Q leaves = dfForward.leaves();
			Q leavesConditions = leaves.nodesTaggedWithAll(XCSG.DataFlowCondition);
			if(leaves.eval().nodes() == leavesConditions.eval().nodes()){
				return dfForward;
			}
			dfForwardGraph = dfForward.eval();
		}while(dfForwardGraph.nodes().size() != preNodesCount && dfForwardGraph.edges().size() != preEdgesCount);
		return dfForward;
	}
}
