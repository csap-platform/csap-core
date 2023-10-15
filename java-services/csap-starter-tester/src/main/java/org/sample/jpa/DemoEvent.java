package org.sample.jpa ;

import java.io.Serializable ;
import java.util.Date ;

import javax.persistence.Column ;
import javax.persistence.Entity ;
import javax.persistence.GeneratedValue ;
import javax.persistence.GenerationType ;
import javax.persistence.Id ;
import javax.persistence.NamedQueries ;
import javax.persistence.NamedQuery ;
import javax.persistence.SequenceGenerator ;
import javax.persistence.Table ;
import javax.persistence.Temporal ;
import javax.persistence.TemporalType ;

import org.hibernate.annotations.CreationTimestamp ;

/**
 * The persistent class for the JOB_SCHEDULE database table.
 * 
 * @see JpaConfig
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.0.RELEASE/spring-framework-reference/htmlsingle/#orm-jpa">
 *      Spring Jpa docs </a>
 * 
 * 
 * 
 * 
 */
@Entity
@Table ( name = DemoEvent.TABLE_NAME )
//
// Hibernate 5.4.30.Final and later failed named query loading unless they are in an entity class
//
@NamedQueries ( {
		@NamedQuery ( name = DemoManager.FIND_ALL , query = "select event from DemoEvent event where event.category like :"
				+ DemoManager.FILTER_PARAM + " ORDER BY event.id" ),
		@NamedQuery ( name = DemoManager.DELETE_ALL , query = "delete DemoEvent j where j.description like :"
				+ DemoManager.FILTER_PARAM ),
		@NamedQuery ( name = DemoManager.COUNT_ALL , query = "SELECT COUNT(j) from DemoEvent j where j.description  like :"
				+ DemoManager.FILTER_PARAM )
} )
public class DemoEvent implements Serializable {
	private static final long serialVersionUID = 1L ;

	public static final String TABLE_NAME = "DEMO_EVENT" ;

	public static final String FILTER_COLUMN = "category" ;

	// Allocation size matching DB seems to be criical
	@Id
	@SequenceGenerator ( name = "generator" , sequenceName = DemoEvent.TABLE_NAME + "_SEQ" , allocationSize = 1 )
	@GeneratedValue ( strategy = GenerationType.SEQUENCE , generator = "generator" )
	@Column ( name = DemoEvent.TABLE_NAME + "_ID" )
	private Long id = -1L ;

	@Column ( name = "CATEGORY" )
	private String category ;

	@Temporal ( TemporalType.TIMESTAMP )
	@Column ( name = "CREATE_DATE" )
	@CreationTimestamp
	private Date createDate ;

	@Column ( name = "DESCRIPTION" )
	private String description ;

	@Column
	private String demoField ;

	@Temporal ( TemporalType.DATE )
	@Column
	private Date demoDate ;

	public DemoEvent ( ) {

	}

	public String getCategory ( ) {

		return this.category ;

	}

	public String getDescription ( ) {

		return this.description ;

	}

	public Long getId ( ) {

		return this.id ;

	}

	public void setEventId ( long eventId ) {

		this.id = eventId ;

	}

	public String getDemoField ( ) {

		return this.demoField ;

	}

	public Date getDemoDate ( ) {

		return this.demoDate ;

	}

	public void setCategory ( String category ) {

		this.category = category ;

	}

	public void setDescription ( String eventDescription ) {

		this.description = eventDescription ;

	}

	public void setDemoField ( String jndiName ) {

		this.demoField = jndiName ;

	}

	public void setDemoDate ( Date lastInvokeTime ) {

		this.demoDate = lastInvokeTime ;

	}

	@Override
	public String toString ( ) {

		return "DemoEvent [id=" + id + ", category=" + category + ", createDate=" + createDate + ", description="
				+ description
				+ ", demoField=" + demoField + ", demoDate=" + demoDate + "]" ;

	}

	public Date getCreateDate ( ) {

		return createDate ;

	}

	public void setCreateDate ( Date createDate ) {

		this.createDate = createDate ;

	}

}