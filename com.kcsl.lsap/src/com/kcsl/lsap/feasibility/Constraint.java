package com.kcsl.lsap.feasibility;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

/**
 * A class corresponding to a constraint stemmed from a {@link XCSG#ControlFlowCondition} along a path of {@link XCSG#ControlFlow_Node}s.
 */
public class Constraint {

	/**
	 * A {@link String} corresponding to the constraint contents. It corresponds to the {@link XCSG#name} attribute of {@link XCSG#ControlFlowCondition}.
	 */
	private String constraintString;
	
	/**
	 * The {@link Boolean} evaluation for this {@link Constraint} along the path of {@link XCSG#ControlFlow_Node}s.
	 */
	private boolean value;
	
	/**
	 * Constructs a new instance of {@link Constraint} with the given <code>controlFlowConditionNode</code> and <code>value</code>.
	 * @param controlFlowConditionNode A {@link XCSG#ControlFlowCondition}.
	 * @param value A {@link Boolean} value.
	 */
	public Constraint(Node controlFlowConditionNode, boolean value) {
		this.constraintString = (String) controlFlowConditionNode.getAttr(XCSG.name);
		this.value = value;
	}
	
	/**
	 * Returns the value of this {@link Constraint}.
	 * 
	 * @return The {@link Boolean} value of this {@link Constraint}. 
	 */
	public boolean getValue() {
		return this.value;
	}
	
	@Override
	public String toString() {
		return "Constraint [text=" + constraintString + ", value=" + value + "]";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((constraintString == null) ? 0 : constraintString.hashCode());
		result = prime * result + (value ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Constraint other = (Constraint) obj;
		if (constraintString == null) {
			if (other.constraintString != null)
				return false;
		} else if (!constraintString.equals(other.constraintString))
			return false;
		if (value != other.value)
			return false;
		return true;
	}
	
}
