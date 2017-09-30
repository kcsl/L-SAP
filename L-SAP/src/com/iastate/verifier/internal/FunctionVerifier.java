package com.iastate.verifier.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.SimpleAddress;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.iastate.atlas.scripts.LinuxScripts;
import com.iastate.atlas.scripts.Queries;

public class FunctionVerifier {

	private Node currentFunction;
	private Graph flowGraph;
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

	
	public FunctionVerifier(Node function, Graph flowGraph, HashMap<Node, FunctionSummary> summary) {
		this.currentFunction = function;
		this.childrenFunctionSummaries = summary;
		this.flowGraph = flowGraph;
		this.matchingPairsMap = new HashMap<Node, ArrayList<MatchingPair>>();
		
        this.pathToS = new HashMap<Node, Integer>();
        this.pathToE1 = new HashMap<Node, AtlasSet<Node>>();
        
        this.pathBackS = new HashMap<Node, Integer>();
        this.pathBackE1 = new HashMap<Node, AtlasSet<Node>>();
	}
	
	public FunctionSummary run(List<Q> events){
		this.summary = new FunctionSummary(this.currentFunction, this.flowGraph, events);
		this.e1EventNodes = events.get(0).eval().nodes();
		this.e2EventNodes = events.get(1).eval().nodes();
		
		HashMap<Node, Node> callEventsFunctionsMap = new HashMap<Node, Node>();
		
		HashMap<Node, FunctionSummary> summary = new HashMap<Node, FunctionSummary>();
		AtlasSet<Node> nodes = events.get(2).eval().nodes();
		for(Node node : nodes){
			for(Node calledFunction : this.childrenFunctionSummaries.keySet()){
				Q callSitesQuery = Common.universe().edgesTaggedWithAll(XCSG.Contains).forward(Common.toQ(Common.toGraph(node))).nodesTaggedWithAll(XCSG.CallSite);
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
    	this.entryNode = this.flowGraph.nodes().filter(XCSG.name, Queries.EVENT_FLOW_ENTRY_NODE).getFirst();
    	this.exitNode = this.flowGraph.nodes().filter(XCSG.name, Queries.EVENT_FLOW_EXIT_NODE).getFirst();
    	
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
	            if(!Utils.isSubSet(outl, this.pathToE1.get(node))){
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
	            	AtlasSet<Node> children = Utils.getChildNodes(this.flowGraph, node);
	            	childrenl = new AtlasHashSet<Node>();
	                if (children.size() == 0){
	                    childrens = PathStatus.THROUGH;
	                    //if (pathStatus != PathStatus.LOCK)
	                    //	this.enableRemainingLNodes = false;
	                }else {
	                    childrens = PathStatus.UNKNOWN;
	                    for (Node child : children){
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
            AtlasSet<Node> children = Utils.getChildNodes(this.flowGraph, node);
            childrenl = new AtlasHashSet<Node>();
            if (children.size() == 0){
                childrens = PathStatus.THROUGH;
                //if (pathStatus != PathStatus.LOCK)
                 //   this.enableRemainingLNodes = false;
            } else{
                childrens = PathStatus.UNKNOWN;
                for(Node child : children){
                	Object [] returns = this.traverse(child, outs, outl);
                	//Utils.debug(0, "Child Node [" + Utils.toString(child) + "] for node [" + Utils.toString(node) + "]:");
                	//Utils.debug(0, "Child Node [" + Utils.toString(child) + "] for node [" + Utils.toString(node) + "] childs:" + returns[0]);
                	//Utils.debug(0, "Child Node [" + Utils.toString(child) + "] for node [" + Utils.toString(node) + "] child:" + Utils.toString((AtlasSet<GraphElement>)returns[1]));
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
                
                //if (outs != PathStatus.LOCK){
                //	this.appendMatchingPairs(outl, childrenl);
                //}
                /*
                if (outs == PathStatus.LOCK){
                    //if (childrens == PathStatus.UNLOCK)
                    //    this.matchedLNodes.addAll(outl);
                    //if (childrens == PathStatus.THROUGH)
                    //    this.remainingLNodes.addAll(outl);
                }else{
                	Utils.debug(0, "1>>>>>>" + Utils.toString(outl));
                	Utils.debug(0, "1>>>>>>" + Utils.toString(childrenl));
                	Utils.debug(0, "1>>>>>>" + Utils.toString(nodeInstances));
                	
                	Utils.debug(0, "1->>>>>>" + Utils.toString(retl));
                	Utils.debug(0, "1->>>>>>" + Utils.toString(childl));
                    //this.notMatchedLNodes.addAll(outl);
                }
                */
                
            }else if(this.e1EventNodes.contains(node)){
                if (childrenl.size() != 0){
                	this.appendMatchingPairs(outl, childrenl);
                }
                
                /*
                if (childrens == PathStatus.UNLOCK)// Correct matching on all children
                    ;//this.matchedLNodes.addAll(outl);
                else if(childrens == PathStatus.THROUGH) // passed to next function
                    ;//this.remainingLNodes.addAll(outl);
                else // other non-clean cases
                {
                	Utils.debug(0, "2>>>>>>" + Utils.toString(outl));
                	Utils.debug(0, "2>>>>>>" + Utils.toString(childrenl));
                	Utils.debug(0, "2>>>>>>" + Utils.toString(nodeInstances));
                	
                	Utils.debug(0, "2->>>>>>" + Utils.toString(retl));
                	Utils.debug(0, "2->>>>>>" + Utils.toString(childl));
                    //this.notMatchedLNodes.addAll(outl);
                }
                */
                
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
    			FunctionSummary newFunctionSummary = new FunctionSummary(functionSummary.getFunction(), functionSummary.getFlowGraph(), functionSummary.getAllEvents());
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
    	SimpleAddress address = new SimpleAddress();
    	Node newNode = Graph.U.createNode(address);
    	
    	try{
    		newNode.putAllAttr(node.attr());
    	}catch(IllegalStateException e){
    		
    	}
    	
    	newNode.tags().add(LinuxScripts.DUPLICATE_NODE);
    	
		Edge e = com.ensoftcorp.atlas.core.db.graph.Graph.U.createEdge(this.currentFunction, newNode);
		e.tags().add(XCSG.Contains);
    	
    	for(String tag : node.tags()){
    		newNode.tags().add(tag);
    	}
        
        for(Node child : Utils.getChildNodes(this.flowGraph, node)){
        	Edge currentEdge = Utils.findEdge(this.flowGraph, node, child);
        	Utils.createEdge(currentEdge, newNode, child);
        }
        
        for(Node parent : Utils.getParentNodes(this.flowGraph, node)){
        	Edge currentEdge = Utils.findEdge(this.flowGraph, parent, node);
        	Utils.createEdge(currentEdge, parent, newNode);
        }
        this.flowGraph = Common.universe().edgesTaggedWithAny(XCSG.Contains, Queries.EVENT_FLOW_EDGE).forward(Common.toQ(Common.toGraph(this.currentFunction))).nodesTaggedWithAny(Queries.EVENT_FLOW_NODE).induce(Common.universe().edgesTaggedWithAll(Queries.EVENT_FLOW_EDGE)).eval();
        return newNode;
    }
}
