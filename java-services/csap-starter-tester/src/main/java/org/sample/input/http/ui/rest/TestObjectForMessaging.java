package org.sample.input.http.ui.rest ;

import java.io.Serializable ;

public class TestObjectForMessaging implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4860075338040769673L ;

	transient private String notTransfered ;

	private String name ;

	private Integer age ;

	public String getNotTransfered ( ) {

		return notTransfered ;

	}

	public void setNotTransfered ( String notTransfered ) {

		this.notTransfered = notTransfered ;

	}

	public String getName ( ) {

		return name ;

	}

	public void setName ( String name ) {

		this.name = name ;

	}

	public Integer getAge ( ) {

		return age ;

	}

	public void setAge ( Integer age ) {

		this.age = age ;

	}

	@Override
	public String toString ( ) {

		return "TestObjectForMessaging [notTransfered=" + notTransfered
				+ ", name=" + name + ", age=" + age + "]" ;

	}

}
