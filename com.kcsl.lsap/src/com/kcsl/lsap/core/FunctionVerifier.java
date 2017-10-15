package com.kcsl.lsap.core;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.pcg.common.PCG;
import com.kcsl.lsap.utils.LSAPUtils;

public class FunctionVerifier {

	private Node currentFunction;
	private PCG pcg;
	private FunctionSummary summary;
	private HashMap<Node, FunctionSummary> childrenFunctionSummaries;
	private AtlasSet<Node> e1EventNodes;
	private AtlasSet<Node> e2EventNodes;
    private Node entryNode;
    private Node exitNode;
    private HashMap<Node, ArrayList<MatchingPair>> matchingPairsMap;
	
	private HashMap<Node, Integer> pathToS;
	private HashMap<Node, Integer> pathBackS;
	private HashMap<Node, AtlasSet<Node>> pathToE1;
	private HashMap<Node, AtlasSet<Node>> pathBackE1;

	
	public FunctionVerifier(Node function, PCG pcg, HashMap<Node, FunctionSummary> summary) {
		this.currentFunction = function;
		this.childrenFunctionSummaries = summary;
		this.pcg = pcg;
		this.matchingPairsMap = new HashMap<Node, ArrayList<MatchingPair>>();
		
        this.pathToS = new HashMap<Node, Integer>();
        this.pathToE1 = new HashMap<Node, AtlasSet<Node>>();
        
        this.pathBackS = new HashMap<Node, Integer>();
        this.pathBackE1 = new HashMap<Node, AtlasSet<Node>>();
	}
	
	public FunctionSummary run(List<Q> events){
		this.summary = new FunctionSummary(this.currentFunction, this.pcg, events);
		this.e1EventNodes = events.get(0).eval().nodes();
		this.e2EventNodes = events.get(1).eval().nodes();
		
		HashMap<Node, Node> callEventsFunctionsMap = new HashMap<Node, Node>();
		
		HashMap<Node, FunctionSummary> summary = new HashMap<Node, FunctionSummary>();
		AtlasSet<Node> nodes = events.get(2).eval().nodes();
		for(Node node : nodes){
			for(Node calledFunction : this.childrenFunctionSummaries.keySet()){
				Q callSitesQuery = universe().edges(XCSG.Contains).forward(Common.toQ(node)).nodes(XCSG.CallSite);
				String calledFunctionName = (String) calledFunction.attr().get(XCSG.name);
				AtlasSet<Node> callSites = callSitesQuery.eval().nodes();
				for(Node callSite : callSites){
					String name = (String) callSite.attr().get(XCSG.name);
					if(name.contains(calledFunctionName + "(")){
						summary.put(node, this.childrenFunctionSummaries.get(calledFunction));
						callEventsFunctionsMap.put(node, calledFunction);
					}
				}
			}
		}
		this.childrenFunctionSummaries.clear();
		this.childrenFunctionSummaries = summary;
		
    	this.duplicateMultipleStatusFunctions();
    	
    	this.verify();
    	
    	this.summary.setEntryNode(this.entryNode);
    	this.summary.setExitNode(this.exitNode);
    	this.summary.setCallEventsFunctionsMap(callEventsFunctionsMap);
    	this.summary.setMatchingPairsList(this.matchingPairsMap);
        return this.summary;
	}
	
	@SuppressWarnings("unchecked")
	private void verify(){   
    	this.entryNode = this.pcg.getMasterEntry();
    	this.exitNode = this.pcg.getMasterExit();
    	
    	Object [] returns = this.traverse(this.entryNode, PathStatus.THROUGH, new AtlasHashSet<Node>());

    	this.summary.setRets((int) returns[0]);
    	this.summary.setRetl((AtlasSet<Node>) returns[1]);
    	this.summary.setOuts(this.pathToS.get(this.exitNode));
    	this.summary.setOutl(this.pathToE1.get(this.exitNode));
    }
    

	@SuppressWarnings("unchecked")
	public Object[] traverse(Node node, int pathStatus, AtlasSet<Node> nodeInstances){
		//Utils.debug(0, "Path Status: [" + Utils.toString(node) + "] \t [" + PathStatus.PathStatusToText(pathStatus) + "]");
    	int rets = 0, outs, childrens, childs;
    	AtlasSet<Node> retl = new AtlasHashSet<Node>();
    	AtlasSet<Node> outl = new AtlasHashSet<Node>();
    	AtlasSet<Node> childrenl = new AtlasHashSet<Node>();
    	AtlasSet<Node> childl = new AtlasHashSet<Node>();
    	
		if(this.childrenFunctionSummaries.containsKey(node)){
			FunctionSummary nodeSummary = this.childrenFunctionSummaries.get(node);
	        rets = nodeSummary.getRets();
	        retl = nodeSummary.getRetl();
	        outs = nodeSummary.getOuts();
	        outl = nodeSummary.getOutl();
			if((pathStatus & PathStatus.LOCK) != 0 && (rets & PathStatus.LOCK) != 0){
				// Here we catch the raced e1 events (hopefully)
				this.appendMatchingPairs(nodeInstances, outl);
			}
		} else if(this.e1EventNodes.contains(node)){
			outl.add(node);
			if((pathStatus & PathStatus.LOCK) != 0){
				// Here we catch the raced e1 events (hopefully)
				this.appendMatchingPairs(nodeInstances, outl);
			}
        	rets = PathStatus.LOCK;
        	retl = new AtlasHashSet<Node>();
        	outs = rets;
		} else if(this.e2EventNodes.contains(node)){
        	rets = PathStatus.UNLOCK;
        	retl.add(node);
        	outs = rets;
        	outl = new AtlasHashSet<Node>();
		}else{
        	outs = pathStatus;
        	outl = nodeInstances;
        }
        
        boolean goon = false;
        if(this.pathToS.containsKey(node)){ // visited before
        	if(!this.e1EventNodes.contains(node) && !this.e2EventNodes.contains(node) && !this.childrenFunctionSummaries.containsKey(node)){
        		// Normal node
	            goon = false;
	            if(!this.isSubSet(outl, this.pathToE1.get(node))){
	            	// new Lock on the path
	                goon = true;
	                this.pathToE1.get(node).addAll(outl);
	            }
	            if ((outs | this.pathToS.get(node)) != this.pathToS.get(node)){
	            	//in status on the path
	                goon = true;
	                this.pathToS.put(node, outs | this.pathToS.get(node));
	            }
	            if (goon){
	            	AtlasSet<Node> successors = this.pcg.getPCG().successors(Common.toQ(node)).eval().nodes();
	            	childrenl = new AtlasHashSet<Node>();
	                if (successors.size() == 0){
	                    childrens = PathStatus.THROUGH;
	                    //if (pathStatus != PathStatus.LOCK)
	                    //	this.enableRemainingLNodes = false;
	                }else {
	                    childrens = PathStatus.UNKNOWN;
	                    for (Node child : successors){
	                        Object [] returns = this.traverse(child, outs, outl);
	                        childs = (Integer)returns[0];
	                        childl = (AtlasSet<Node>) returns[1];
	                        childrens |= childs;
	                        childrenl.addAll(childl);
	                    }
	                }
	                this.pathBackS.put(node, childrens);
	                this.pathBackE1.put(node, new AtlasHashSet<Node>(childrenl));
	                return new Object []{childrens, childrenl};
	            } else {
	            	// !goon, visited before with same information
	                if (this.pathBackS.get(node) != null)
	                    return new Object []{this.pathBackS.get(node), this.pathBackE1.get(node)};
	                return new Object []{PathStatus.UNKNOWN, new AtlasHashSet<Node>()};
	            }
        	}else{
        		// Lock or Unlock node or special node, stop here either way
        		return new Object []{rets, retl};
        	}
        } else{
        	// First visit on this path
            this.pathToS.put(node, outs);
            this.pathToE1.put(node, new AtlasHashSet<Node>(outl));
            AtlasSet<Node> successors = this.pcg.getPCG().successors(Common.toQ(node)).eval().nodes();
            childrenl = new AtlasHashSet<Node>();
            if (successors.size() == 0){
                childrens = PathStatus.THROUGH;
            } else{
                childrens = PathStatus.UNKNOWN;
                for(Node child : successors){
                	Object [] returns = this.traverse(child, outs, outl);
                	childs = (Integer) returns[0];
                	childl = (AtlasSet<Node>) returns[1];
                    childrens |= childs;
                    childrenl.addAll(childl);
                }
            }

            if (this.childrenFunctionSummaries.containsKey(node)){
            	//special node
                // outs is only PathStatus.LOCK
                if((outs & PathStatus.LOCK) != 0){
                	 if (childrenl.size() != 0){
                		 this.appendMatchingPairs(outl, childrenl);
                	 }
                }
                
            }else if(this.e1EventNodes.contains(node)){
                if (childrenl.size() != 0){
                	this.appendMatchingPairs(outl, childrenl);
                }
                
            }else if(!this.e2EventNodes.contains(node)){
                rets = childrens;
                retl = new AtlasHashSet<Node>(childrenl);	
            }
            this.pathBackS.put(node, rets);
            this.pathBackE1.put(node, new AtlasHashSet<Node>(retl));
            return new Object [] {rets, retl};
        }
    }
	
	private void appendMatchingPairs(AtlasSet<Node> nodes, AtlasSet<Node> matchingNodes){
		for(Node node : nodes){
	    	ArrayList<MatchingPair> matchingPairs = new ArrayList<MatchingPair>();
	    	if(this.matchingPairsMap.containsKey(node)){
	    		matchingPairs = this.matchingPairsMap.get(node);
	    	}
	    	for(Node matchingNode : matchingNodes){
	    		matchingPairs.add(new MatchingPair(node, matchingNode, null));
	    	}
	    	this.matchingPairsMap.put(node, matchingPairs);
		}
	}
	
    
    /**
     * Duplicate a node in the CFG if its a called function with a summary that contains multiple statuses such as: locked and unlocked. 
     */
    public void duplicateMultipleStatusFunctions(){
    	HashMap<Node, FunctionSummary> duplicatedNodesSummaries = new HashMap<Node, FunctionSummary>();
    	for(Node functionNode : this.childrenFunctionSummaries.keySet()){
    		FunctionSummary functionSummary = this.childrenFunctionSummaries.get(functionNode);
    		int status = functionSummary.getRets();
    		
    		if((status | PathStatus.THROUGH) == status){
    			FunctionSummary newFunctionSummary = new FunctionSummary(functionSummary.getFunction(), functionSummary.getPCG(), functionSummary.getAllEvents());
    			newFunctionSummary.setRets(functionSummary.getRets() & ~PathStatus.THROUGH);
    			newFunctionSummary.setOuts(functionSummary.getOuts() & ~PathStatus.THROUGH);
    			Node newFunctionNode = this.duplicateNode(functionNode);
    			duplicatedNodesSummaries.put(newFunctionNode, newFunctionSummary);
    		}
    	}
    	
    	for(Node newFunctionNode : duplicatedNodesSummaries.keySet()){
    		this.childrenFunctionSummaries.put(newFunctionNode, duplicatedNodesSummaries.get(newFunctionNode));
    	}
    }
    
    private Node duplicateNode(Node node){
    	Node newNode = Graph.U.createNode();
    	newNode.putAllAttr(node.attr());
    	newNode.tag(VerificationProperties.DUPLICATE_NODE);
    	newNode.tags().addAll(node.tags().explicitElements());
    	
		Edge e = Graph.U.createEdge(this.currentFunction, newNode);
		e.tag(XCSG.Contains);
		
		Q pcg = this.pcg.getPCG();
		Graph pcgGraph = pcg.eval();
		
		AtlasSet<Node> successors = pcg.successors(Common.toQ(node)).eval().nodes();
        
        for(Node successor : successors){
        	Edge currentEdge = LSAPUtils.findDirectEdgesBetweenNodes(pcgGraph, node, successor).get(0);
        	this.createDuplicateEdge(currentEdge, newNode, successor);
        }
        
        AtlasSet<Node> predecessors = pcg.predecessors(Common.toQ(node)).eval().nodes();
        for(Node predecessor : predecessors){
        	Edge currentEdge = LSAPUtils.findDirectEdgesBetweenNodes(pcgGraph, predecessor, node).get(0);
        	this.createDuplicateEdge(currentEdge, predecessor, newNode);
        }
        //this.flowGraph = Common.universe().edges(XCSG.Contains, Queries.EVENT_FLOW_EDGE).forward(Common.toQ(Common.toGraph(this.currentFunction))).nodesTaggedWithAny(Queries.EVENT_FLOW_NODE).induce(Common.universe().edgesTaggedWithAll(Queries.EVENT_FLOW_EDGE)).eval();
        return newNode;
    }
    
	private Edge createDuplicateEdge(Edge edge, Node from, Node to){
		Edge newEdge = Graph.U.createEdge(from, to);
		newEdge.tags().add(VerificationProperties.DUPLICATE_EDGE);
		newEdge.putAllAttr(edge.attr());
		newEdge.tags().addAll(edge.tags().explicitElements());
    	return newEdge;
	}
	
	private boolean isSubSet(AtlasSet<Node> aSet, AtlasSet<Node> bSet)
	{
		for(Node aNode : aSet){
			if(!bSet.contains(aNode))
				return false;
		}
		return true;
	}
}
