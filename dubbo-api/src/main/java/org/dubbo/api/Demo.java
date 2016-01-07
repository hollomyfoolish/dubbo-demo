package org.dubbo.api;

import java.io.Serializable;

public class Demo implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6699776396614660954L;
	
	private int id;
	private String name;
	
	public Demo(){
		
	}
	
	public Demo(int id, String name) {
        this.id = id;
        this.name = name;
    }
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
}
