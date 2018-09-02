package com.kcsl.lsap.verifier;

import java.util.EnumMap;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;

public class Summary {

	private EnumMap<Event, AtlasSet<Node>> eventToInducingNodesMap;
	
	public Summary() {
		this.eventToInducingNodesMap = new EnumMap<Event, AtlasSet<Node>>(Event.class);
	}
	
	public EnumMap<Event, AtlasSet<Node>> getEventToInducingNodesMap() {
		return this.eventToInducingNodesMap;
	}
	
	public void update(Summary otherNodeSummary) {
		this.update(otherNodeSummary.getEventToInducingNodesMap());
	}
	
	public void update(EnumMap<Event, AtlasSet<Node>> newEventToInducingNodesMap) {
		for(Event event: newEventToInducingNodesMap.keySet()) {
			AtlasSet<Node> eventInducingNodes = newEventToInducingNodesMap.get(event);
			if(this.eventToInducingNodesMap.containsKey(event)) {
				this.eventToInducingNodesMap.get(event).addAll(eventInducingNodes);
			} else {
				this.eventToInducingNodesMap.put(event, eventInducingNodes);
			}
		}
	}
	
	public void add(Event event, Node inducingNode) {
		if(this.eventToInducingNodesMap.containsKey(event)) {
			this.eventToInducingNodesMap.get(event).add(inducingNode);
		} else {
			AtlasSet<Node> inducingNodes = new AtlasHashSet<Node>();
			inducingNodes.add(inducingNode);
			this.eventToInducingNodesMap.put(event, inducingNodes);
		}
	}
	
	public void addAll(Event event, AtlasSet<Node> inducingNodes) {
		if(this.eventToInducingNodesMap.containsKey(event)) {
			this.eventToInducingNodesMap.get(event).addAll(inducingNodes);
		} else {
			this.eventToInducingNodesMap.put(event, inducingNodes);
		}
	}
	
	public void clear() {
		this.eventToInducingNodesMap.clear();
	}
	
	public boolean contains(Event event) {
		return this.eventToInducingNodesMap.containsKey(event);
	}
	
	public void remove(Event event) {
		this.eventToInducingNodesMap.remove(event);
	}
	
	public AtlasSet<Node> inducingNodesForEvent(Event event) {
		return this.eventToInducingNodesMap.get(event);
	}
	
	public boolean contains(Summary otherNodeSummary) {
		return this.contains(otherNodeSummary.getEventToInducingNodesMap());
	}
	
	public boolean contains(EnumMap<Event, AtlasSet<Node>> givenEventToInducingNodesMap) {		
		for(Event event: givenEventToInducingNodesMap.keySet()) {
			if(this.eventToInducingNodesMap.containsKey(event)) {
				AtlasSet<Node> givenEventInducingNodes = givenEventToInducingNodesMap.get(event);
				AtlasSet<Node> eventInducingNodes = this.eventToInducingNodesMap.get(event);
				boolean contains = this.contains(eventInducingNodes, givenEventInducingNodes);
				if(contains) {
					continue;
				}
			}
			return false;
		}
		return true;
	}
	
	/**
	 * Checks whether <code>setA</code> contains all elements of <code>setB</code>.
	 * 
	 * @param setA
	 * @param setB
	 * @return
	 */
	private boolean contains(AtlasSet<Node> setA, AtlasSet<Node> setB) {
		for(Node node: setB) {
			if(!setA.contains(node)) {
				return false;
			}
		}
		return true;
	}
}
