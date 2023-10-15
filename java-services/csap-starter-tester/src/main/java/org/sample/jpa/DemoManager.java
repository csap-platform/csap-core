package org.sample.jpa ;

import java.util.Iterator ;
import java.util.List ;

import javax.persistence.EntityManager ;
import javax.persistence.MappedSuperclass ;
import javax.persistence.PersistenceContext ;
import javax.persistence.Query ;
import javax.persistence.TypedQuery ;
import javax.persistence.criteria.CriteriaBuilder ;
import javax.persistence.criteria.CriteriaDelete ;
import javax.persistence.criteria.CriteriaQuery ;
import javax.persistence.criteria.ParameterExpression ;
import javax.persistence.criteria.Predicate ;
import javax.persistence.criteria.Root ;

import org.csap.helpers.CSAP ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.stereotype.Service ;
import org.springframework.transaction.annotation.Propagation ;
import org.springframework.transaction.annotation.Transactional ;

import com.fasterxml.jackson.databind.ObjectMapper ;
import com.fasterxml.jackson.databind.node.ArrayNode ;
import com.fasterxml.jackson.databind.node.ObjectNode ;
import com.uaihebert.uaicriteria.UaiCriteria ;
import com.uaihebert.uaicriteria.UaiCriteriaFactory ;

import io.micrometer.core.annotation.Timed ;

/**
 * 
 * 
 * @see <a href="http://en.wikibooks.org/wiki/Java_Persistence"> JPA @
 *      wikibooks</a>
 * 
 *      Simple JPA DAO - Note the use of the @MappedSuperclass that enables DAO
 *      to define named queries that typically span multiple entities.
 * 
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/orm.html#orm-jpa">
 *      Spring Jpa docs </a>
 * 
 * 
 * @see <a href="http://www.ibm.com/developerworks/java/library/j-typesafejpa/">
 *      Critical to understand JPA Criteria API </a>
 * 
 * 
 * @see <a href="http://docs.oracle.com/javaee/6/tutorial/doc/bnbtg.html"> JEE
 *      JPA tutorial</a>
 * 
 * @see <a href=
 *      "http://paddyweblog.blogspot.com/2010/04/some-examples-of-criteria-api-jpa-20.html">
 *      JPQL and Criteria API comparisons </a>
 * 
 * 
 * @author pnightin
 * 
 */
@Transactional ( readOnly = true )
@Service
@MappedSuperclass
public class DemoManager {

	public static final String FIND_ALL = "HelloDao.FindAll" ;
	public static final String DELETE_ALL = "HelloDao.DeleteAll" ;
	public static final String COUNT_ALL = "HelloDao.CountAll" ;
	public static final String FILTER_PARAM = "FILTER_PARAM" ;

	public static final String TEST_TOKEN = "Test_Token" ;

	final private Logger logger = LoggerFactory.getLogger( getClass( ) ) ;

	@PersistenceContext
	private EntityManager entityManager ;

	// Bad form to put transaction directly into DAO - should go into biz
	// delegate

	@Timed ( description = "db load test: add via jpa entity manager" , value = "csap.db.add-item" )
	@Transactional ( readOnly = false , propagation = Propagation.REQUIRED )
	public DemoEvent addSchedule ( DemoEvent event ) {

		logger.debug( "Got Here" ) ;

		try {

			// event.setCreateDate( createDate );
			entityManager.persist( event ) ;

			if ( event.getCategory( ) == null ) {

				event.setCategory( TEST_TOKEN ) ;

			}

			// Thread.sleep( 5000 );
			// Never inside here - this causes hib do choke since id is only
			// available after the commit
			// logger.info("Persisted entity, id via seq from db is: "
			// + jobSchedule.getScheduleObjid());
		} catch ( Exception e ) {

			logger.error( "Failed to persist: ", e ) ;

		}

		return event ;

	}

	@Transactional ( readOnly = false , propagation = Propagation.REQUIRED )
	public String removeBulkDataJpql ( String eventFilter ) {

		logger.debug( "Got Here" ) ;
		StringBuilder resultsBuf = new StringBuilder( "\nUsers deleted using "
				+ this.getClass( ).getName( ) + ".removeTestData\nFilter \n"
				+ eventFilter + "===>\n" ) ;

		try {

			Query q = entityManager.createQuery(
					"delete " + DemoEvent.class.getSimpleName( ) +
							" j where j." + DemoEvent.FILTER_COLUMN + " like :" + FILTER_PARAM ) ;

			q.setParameter( FILTER_PARAM, "%" + eventFilter + "%" ) ;
			int deletedEntities = q.executeUpdate( ) ;

			resultsBuf.append( "Total Items deleted: " + deletedEntities ) ;

		} catch ( Throwable e ) {

			String reason = CSAP.buildFilteredStack( e, "sample" ) ;
			resultsBuf.append( "\n Failed deleting test data:" + reason ) ;
			logger.error( resultsBuf.toString( ) ) ;

		}

		return resultsBuf.toString( ) ;

	}

	/**
	 * Use a named query to delete bulk data. Latest hibernate seems to choke on
	 * this
	 * 
	 * @param eventFilter
	 * @return
	 */
	@Transactional ( readOnly = false , propagation = Propagation.REQUIRED )
	public String removeBulkDataJpqlNamed ( String eventFilter ) {

		logger.debug( "Got Here" ) ;
		StringBuilder resultsBuf = new StringBuilder( "\nUsers deleted using "
				+ this.getClass( ).getName( ) + ".removeTestData\nFilter \n"
				+ eventFilter + "===>\n" ) ;

		try {

			// JPA named queries in hibernate 4.3 through a illegal state
			Query q = entityManager
					.createNamedQuery( DELETE_ALL ) ;

			q.setParameter( FILTER_PARAM, "%" + eventFilter + "%" ) ;

			int deletedEntities = q.executeUpdate( ) ;

			resultsBuf.append( "Total Items deleted: " + deletedEntities ) ;

		} catch ( Throwable e ) {

			resultsBuf.append( "\n *** Got Exception: "
					+ CSAP.buildFilteredStack( e, "sample" ) ) ;
			logger.error( resultsBuf.toString( ) ) ;

		}

		return resultsBuf.toString( ) ;

	}

	/**
	 * 
	 * Much awaited bulk apis in JPA 2.1
	 * 
	 * @see http://en.wikibooks.org/wiki/Java_Persistence/Criteria#CriteriaDelete_.28JPA_2.1.29
	 * 
	 * @param eventFilter
	 * @return
	 * 
	 */
	@Transactional ( readOnly = false , propagation = Propagation.REQUIRED )
	public String removeBulkDataWithCriteria ( String eventFilter ) {

		logger.debug( "Got Here" ) ;
		StringBuilder resultsBuf = new StringBuilder( "\nUsers deleted using "
				+ this.getClass( ).getName( ) + ".removeTestData\nFilter \n"
				+ eventFilter + "===>\n" ) ;

		try {

			CriteriaBuilder cb = entityManager.getCriteriaBuilder( ) ;

			// Deletes all Employee's making more than 100,000.
			CriteriaDelete<DemoEvent> delete = cb.createCriteriaDelete( DemoEvent.class ) ;
			Root jobScheduleEntity = delete.from( DemoEvent.class ) ;
			delete.where( cb.like( jobScheduleEntity.get( DemoEvent.FILTER_COLUMN ), "%" + eventFilter + "%" ) ) ;
			Query query = entityManager.createQuery( delete ) ;
			int deletedEntities = query.executeUpdate( ) ;

			resultsBuf.append( "Total Items deleted: " + deletedEntities ) ;

		} catch ( Throwable e ) {

			resultsBuf.append( "\n *** Got Exception: "
					+ CSAP.buildFilteredStack( e, "sample" ) ) ;
			logger.error( CSAP.buildFilteredStack( e, "sample" ) ) ;

		}

		return resultsBuf.toString( ) ;

	}

	/**
	 * @param filter
	 * @return
	 */
	@Transactional ( readOnly = false , propagation = Propagation.REQUIRED )
	public String removeTestDataOneByOne ( String filter ) {

		logger.debug( "Got Here" ) ;
		StringBuilder resultsBuf = new StringBuilder( "\nUsers deleted using "
				+ this.getClass( ).getName( ) + ".removeTestData\nFilter \n"
				+ filter + "===>\n" ) ;

		try {

			// Query q =
			// entityManager.createQuery("select j from JobSchedule j where
			// j.DemoEvent.FILTER_COLUMN like '%*******%'");
			Query q = entityManager.createQuery( filter ) ;

			@SuppressWarnings ( "unchecked" )
			Iterator<DemoEvent> jobSchedIter = (Iterator<DemoEvent>) q
					.getResultList( ).iterator( ) ;

			logger.error( "Horrible way to delete data - this should be a bulk delete" ) ;
			int i = 0 ;

			while ( jobSchedIter.hasNext( ) ) {

				DemoEvent jobSched = jobSchedIter.next( ) ;

				if ( i++ < 10 )
					resultsBuf.append( jobSched + "\n" ) ;

				entityManager.remove( jobSched ) ;

			}

			resultsBuf.append( "Total Items deleted: " + i ) ;

		} catch ( Throwable e ) {

			resultsBuf.append( "\n *** Got Exception: "
					+ CSAP.buildFilteredStack( e, "sample" ) ) ;
			logger.error( CSAP.buildFilteredStack( e, "sample" ) ) ;

		}

		return resultsBuf.toString( ) ;

	}

	@Timed ( description = "db load test using createNamedQuery" , value = "csap.db.find-by-jpql" )
	public String findUsingJpql ( String eventFilter , int maxResults ) {

		logger.debug( "Got Here" ) ;

		StringBuffer results = new StringBuffer( "\nResult from:"
				+ this.getClass( ).getName( )
				+ ".showScheduleItemsJpql() query:\n" + eventFilter + "\n" ) ;

		try {

			Query q = entityManager.createNamedQuery( FIND_ALL ).setMaxResults(
					maxResults ) ;
			q.setParameter( FILTER_PARAM, "%" + eventFilter + "%" ) ;

			@SuppressWarnings ( "unchecked" )
			Iterator<DemoEvent> sampleIter = (Iterator<DemoEvent>) q
					.getResultList( ).iterator( ) ;

			while ( sampleIter.hasNext( ) ) {

				DemoEvent user = sampleIter.next( ) ;
				results.append( user + "\n" ) ;

			}

		} catch ( Throwable e ) {

			results.append( "\n *** Got Exception: "
					+ CSAP.buildFilteredStack( e, "sample" ) ) ;
			logger.error( results.toString( ), e ) ;

		}

		return results.toString( ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper( ) ;

	/**
	 * This looks longer, so why bother? Read the references at the top of the class
	 * 
	 * @param filter
	 * @param maxResults
	 * 
	 * @return String containing the records in raw text
	 * 
	 *         {@link DemoManager#showScheduleItems(String, int)}
	 * 
	 */
	@Timed ( description = "db load test CriteriaQuery" , value = "csap.db.find-by-criteria" )
	public ObjectNode showScheduleItemsWithFilter ( String filter , int maxResults ) {

		logger.debug( "Got Here" ) ;

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;

		try {

			resultNode.put( "count", getCountCriteria( filter ) ) ;
			ArrayNode dataArrayNode = resultNode.putArray( "data" ) ;
			CriteriaBuilder builder = entityManager.getCriteriaBuilder( ) ;
			CriteriaQuery<DemoEvent> criteriaQuery = builder
					.createQuery( DemoEvent.class ) ;

			Root<DemoEvent> jobScheduleRoot = criteriaQuery
					.from( DemoEvent.class ) ;

			ParameterExpression<String> paramExp = builder.parameter(
					String.class, "paramHack" ) ;
			Predicate condition = builder.like(
					jobScheduleRoot.<String>get( DemoEvent.FILTER_COLUMN ), paramExp ) ;

			criteriaQuery.where( condition ) ;

			TypedQuery<DemoEvent> typedQuery = entityManager.createQuery(
					criteriaQuery ).setMaxResults( maxResults ) ;
			typedQuery.setParameter( "paramHack", "%" + filter + "%" ) ;

			List<DemoEvent> jobs = typedQuery.getResultList( ) ;

			Iterator<DemoEvent> sampleIter = jobs.iterator( ) ;

			while ( sampleIter.hasNext( ) ) {

				DemoEvent user = sampleIter.next( ) ;
				ObjectNode jobNode = jacksonMapper.createObjectNode( ) ;
				jobNode.put( "id", user.getId( ) ) ;
				jobNode.put( "description", user.getDescription( ) ) ;
				dataArrayNode.add( jobNode ) ;

			}

		} catch ( Throwable e ) {

			resultNode.put( "error",
					CSAP.buildFilteredStack( e, "sample" ) ) ;
			logger.error( "Failed querying db {}", CSAP.buildFilteredStack( e, "sample" ) ) ;

		}

		return resultNode ;

	}

	/**
	 * Experimental: JPA Wrapper framework - Compare lines of code with the above.
	 * 
	 * @see <a href="https://github.com/uaihebert/uaicriteria"> Ez Wrapper
	 *      Documentation</a>
	 * 
	 * @param filter
	 * @param maxResults
	 * @return
	 */
	public ObjectNode findScheduleItemsUsingCriteriaWrapper ( String filter , int maxResults ) {

		logger.warn( "\n\n =========== Experimental! Filter: " + filter + " =========================\n\n" ) ;

		ObjectNode resultNode = jacksonMapper.createObjectNode( ) ;
		resultNode.put( "availableInDb", getCountCriteria( filter ) ) ;
		resultNode.put( "maxResults", maxResults ) ;
		ArrayNode dataArrayNode = resultNode.putArray( "data" ) ;

		try {

			// 2 lines replaces 10 lines - easier to read
			UaiCriteria<DemoEvent> easyCriteria = UaiCriteriaFactory.createQueryCriteria(
					entityManager,
					DemoEvent.class ) ;

			List<DemoEvent> jobs = easyCriteria
					.andStringLike(
							DemoEvent.FILTER_COLUMN,
							"%" + filter + "%" )
					.setMaxResults( maxResults )
					.getResultList( ) ;

			Iterator<DemoEvent> sampleIter = jobs.iterator( ) ;

			while ( sampleIter.hasNext( ) ) {

				DemoEvent user = sampleIter.next( ) ;
				ObjectNode jobNode = jacksonMapper.createObjectNode( ) ;
				jobNode.put( "id", user.getId( ) ) ;
				jobNode.put( "description", user.getDescription( ) ) ;
				dataArrayNode.add( jobNode ) ;

			}

		} catch ( Throwable e ) {

			resultNode.put( "error",
					CSAP.buildFilteredStack( e, "sample" ) ) ;
			logger.error( "Failed querying db", CSAP.buildFilteredStack( e, "sample" ) ) ;

		}

		return resultNode ;

	}

	public long getCountJpql ( String eventFilter ) {

		logger.debug( "Got Here" ) ;

//		StringBuffer userBuf = new StringBuffer( "\nResult from:"
//				+ this.getClass( ).getName( ) + ".showScheduleItems() query:\n"
//				+ eventFilter + "\n" ) ;

		var recordCount = -1l ;

		try {

			var namedQuery = entityManager.createNamedQuery( COUNT_ALL ) ;
			namedQuery.setParameter( FILTER_PARAM, "%" + eventFilter + "%" ) ;

			recordCount = (Long) namedQuery.getSingleResult( ) ;

		} catch ( Exception e ) {

			logger.info( "Failed running named query: {}, {}", COUNT_ALL, CSAP.buildCsapStack( e ) ) ;

		}

		return recordCount ;

	}

	/**
	 * 
	 * @see http://www.ibm.com/developerworks/java/library/j-typesafejpa/
	 * @see http://planet.jboss.org/post/
	 *      a_more_concise_way_to_generate_the_jpa_2_metamodel_in_maven
	 * @see http ://paddyweblog.blogspot.com/2010/04/some-examples-of-criteria-api
	 *      -jpa-20.html
	 * 
	 * @param filter
	 * @return
	 */
	public long getCountCriteria ( String filter ) {

		logger.debug( "Got Here" + filter ) ;

		CriteriaBuilder builder = entityManager.getCriteriaBuilder( ) ;

		CriteriaQuery<Long> criteriaQuery = builder.createQuery( Long.class ) ;

		Root<DemoEvent> jobScheduleRoot = criteriaQuery
				.from( DemoEvent.class ) ;

		criteriaQuery.select( builder.count( jobScheduleRoot ) ) ;

		// For ease of use, jpamodelgen is not used in the reference project
		// - recommend to configure eclipse project with jpamodelgen eg. this
		// should be
		// Predicate condition = qb.gt(p.get(JobSchedule_.age), 20);
		ParameterExpression<String> paramExp = builder.parameter( String.class,
				FILTER_PARAM ) ;
		Predicate condition = builder.like(
				jobScheduleRoot.<String>get( "category" ), paramExp ) ;
		criteriaQuery.where( condition ) ;

		TypedQuery<Long> typedQuery = entityManager.createQuery( criteriaQuery ) ;
		typedQuery.setParameter( FILTER_PARAM, "%" + filter + "%" ) ;
		long num = typedQuery.getSingleResult( ) ;
		// long num =
		// entityManager.createQuery(criteriaQuery).getSingleResult();

		return num ;

	}

	/**
	 * @see <a href="https://github.com/uaihebert/uaicriteria"> Ez Wrapper
	 *      Documentation</a>
	 * 
	 * @param filter
	 * @return
	 */
	public long countRecordsUsingCriteriaWrapper ( String filter ) {

		logger.debug( "Got Here: " + filter ) ;

		// 2 lines replaces 10 lines - easier to read
		UaiCriteria<DemoEvent> easyCriteria = UaiCriteriaFactory.createQueryCriteria( entityManager,
				DemoEvent.class ) ;
		long num = easyCriteria.andStringLike( DemoEvent.FILTER_COLUMN, "%" + filter + "%" ).countRegularCriteria( ) ;

		return num ;

	}
}
