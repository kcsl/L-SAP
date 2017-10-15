package com.kcsl.lsap.atlas.deprecated;

public class Condition {

	private int ID;
	private String text;
	private boolean value;
	
	public Condition(int id, boolean value, String text) {
		this.setID(id);
		this.setText(text);
		this.setValue(value);
	}

	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public boolean getValue() {
		return value;
	}

	public void setValue(boolean value) {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return (this.getValue() ? "T" : "F") + "[C" + this.getID() + "]";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ID;
		result = prime * result + ((text == null) ? 0 : text.hashCode());
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
		Condition other = (Condition) obj;
		if (ID != other.ID)
			return false;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
			return false;
		if (value != other.value)
			return false;
		return true;
	}
}
