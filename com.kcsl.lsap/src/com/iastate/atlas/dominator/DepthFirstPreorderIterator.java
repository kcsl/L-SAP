package com.iastate.atlas.dominator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;

/**
 * DepthFirstPreorderIterator yields a depth-first pre-order traversal of a {@link ControlFlowGraph}.
 */
public class DepthFirstPreorderIterator implements Iterator<Node>{
    
	/**
	 * Control Flow Graph to operate on
	 */
	private ControlFlowGraph cfg;
	
    /**
     * Reverse the traversal direction in case computing post-dominators
     */
    private boolean postdom;

    /**
     * The to-be-visited stack of blocks.
     */
    private Stack<Node> toDo = new Stack<Node>();

    /**
     *  The set of edges already traversed.
     */
    private Set<Edge> visitedEdges = new HashSet<Edge>();
    
    /**
     * @param g: the control flow graph to operate on
     * @param root: the master entry node for a given control flow graph
     * @param postdom: if true reverse the direction of computing dominance
     */
    public DepthFirstPreorderIterator(ControlFlowGraph g, Node root, boolean postdom){
    	this.postdom = postdom;
        this.toDo.add(root);
        this.cfg = g;
    }


    @Override
    public boolean hasNext(){
        return !toDo.isEmpty();
    }

    @Override
    public Node next(){
        if (!hasNext())
            throw new NoSuchElementException();

        Node next = toDo.pop();
        pushSuccessors(next);
        return next;
    }

    /**
     * Traverse any previously-untraversed edges
     * by adding the destination block to the to-do stack.
     * @param b the current block.
     */
    private void pushSuccessors(Node b){
        for (Node succ_block : getSuccessors(b))
            if (visitedEdges.add(new Edge(b, succ_block)))
                toDo.push(succ_block);
    }

    @Override
    public void remove(){
        throw new UnsupportedOperationException();
    }

    /**
     * Edge is used to detect edges previously traversed.
     * It implements composite hash and equality operations
     * so it can be used as a key in a hashed collection.
     */
    private static class Edge{
        private Node from;
        private Node to;

        Edge(Node from, Node to)
        {
            this.from = from;
            this.to = to;
        }

        private static final int PRIME_MULTIPLIER = 7057;

        /**
         * Generate a composite hash code so that an Edge can be
         * used in a hashed container.
         * @return the composite hash code of the from/to vertices.
         */
        @Override
        public int hashCode()
        {
            return (from.hashCode() * PRIME_MULTIPLIER) + to.hashCode();
        }

        /**
         * Use the vertices to determine equality of an Edge so it
         * can be used in a hashed container.
         * @param other the other object to compare.
         * @return true iff other is an Edge, and both Edges' from/to
         * vertices match their corresponding field.
         */
        @Override
        public boolean equals(Object other)
        {
            if (other == this)
            {
                return true;
            }
            else if (other instanceof Edge)
            {
                Edge otherEdge = (Edge)other;
                return from == otherEdge.from && to == otherEdge.to;
            }
            return false;
        }
    }
    
    private AtlasSet<Node> getSuccessors(Node node) {
        if (postdom) {
            return this.cfg.getPredecessors(node);
        } else {
            return this.cfg.getSuccessors(node);
        }
    }
}