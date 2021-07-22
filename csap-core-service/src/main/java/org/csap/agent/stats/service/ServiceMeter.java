package org.csap.agent.stats.service ;

import com.fasterxml.jackson.databind.node.ObjectNode ;

public class ServiceMeter {

	private String collectionId ;
	private JavaCollectionType meterType = JavaCollectionType.notFound ;

	private String mbeanName ;
	private String mbeanAttribute ;
	private String httpAttribute ;
	private String title ;
	private long multiplyBy = 1 ;
	private boolean delta = false ;
	private long divideBy = 1 ;
	private boolean useDivideCollectionInterval = false ;
	private int decimals = 0 ;
	private boolean ignoreErrors = false ;

	public ServiceMeter ( String collectionId, String title, String mbeanName, String mbeanAttribute ) {

		this.meterType = JavaCollectionType.mbean ;
		this.collectionId = collectionId ;
		this.title = title ;
		this.mbeanName = mbeanName ;
		this.mbeanAttribute = mbeanAttribute ;

	}

	public ServiceMeter ( String collectionId, String title, String attribute ) {

		meterType = JavaCollectionType.http ;
		this.collectionId = collectionId ;
		this.title = title ;
		this.setHttpAttribute( attribute ) ;

	}

	public ServiceMeter ( String collectionId, ObjectNode csapCollectDefinition ) {

		this.collectionId = collectionId ;
		meterType = getJavaCollectionType( csapCollectDefinition ) ;

		if ( meterType.isMbean( ) ) {

			mbeanName = csapCollectDefinition.get( meterType.fieldName ).asText( ) ;
			mbeanAttribute = csapCollectDefinition.get( "attribute" ).asText( ) ;

		}

		if ( csapCollectDefinition.has( "title" ) ) {

			this.title = csapCollectDefinition.get( "title" ).asText( ) ;

		}

		if ( csapCollectDefinition.has( "multiplyBy" ) ) {

			this.multiplyBy = csapCollectDefinition.get( "multiplyBy" ).asLong( 100 ) ;

		}

		if ( csapCollectDefinition.has( "divideBy" ) ) {

			if ( csapCollectDefinition.get( "divideBy" ).asText( ).equals( "interval" ) ) {

				useDivideCollectionInterval = true ;

			}

			this.divideBy = csapCollectDefinition.get( "divideBy" ).asLong( 100 ) ;

		}

		if ( csapCollectDefinition.has( "delta" ) ) {

			// delta should be a boolean - but historically had delta
			this.delta = csapCollectDefinition.get( "delta" ).asBoolean( true ) ;

		}

		if ( csapCollectDefinition.has( "attribute" ) ) {

			this.setHttpAttribute( csapCollectDefinition.get( "attribute" ).asText( ) ) ;

		}

		if ( csapCollectDefinition.has( "decimals" ) ) {

			this.decimals = csapCollectDefinition.get( "decimals" ).asInt( 0 ) ;

		}

		if ( csapCollectDefinition.has( "ignoreErrors" ) ) {

			this.ignoreErrors = csapCollectDefinition.get( "ignoreErrors" ).asBoolean( false ) ;

		}

	}

	public enum JavaCollectionType {
		notFound("notFound"), mbean("mbean"), http("attribute"),
		simon("simon", true), simonCounter("simonCounter", true), simonMedianTime("simonMedianTime", true),
		simonMaxTime("simonMaxTime", true);

		private String fieldName ;
		private boolean isSimon = false ;

		private JavaCollectionType ( String fieldName ) {

			this.fieldName = fieldName ;

		}

		private JavaCollectionType ( String fieldName, boolean isSimon ) {

			this.fieldName = fieldName ;
			this.isSimon = isSimon ;

		}

		public boolean isMbean ( ) {

			if ( this == mbean )
				return true ;
			return false ;

		}

		public boolean isHttp ( ) {

			if ( this == http )
				return true ;
			return false ;

		}

		public String getFieldName ( ) {

			return fieldName ;

		}

		public void setFieldName ( String fieldName ) {

			this.fieldName = fieldName ;

		}
	}

	private JavaCollectionType getJavaCollectionType ( ObjectNode csapCollectDefinition ) {

		JavaCollectionType currentType = JavaCollectionType.notFound ;

		for ( JavaCollectionType collect : JavaCollectionType.values( ) ) {

			if ( csapCollectDefinition.has( collect.fieldName ) ) {

				currentType = collect ;
				break ;

			}

		}

		return currentType ;

	}

	@Override
	public String toString ( ) {

		// TODO Auto-generated method stub
		StringBuilder builder = new StringBuilder( ) ;
		builder.append( "\n collectionId: " + getCollectionId( ) + ", type: " + meterType.fieldName ) ;

		if ( meterType.isMbean( ) ) {

			builder.append( " - " + getMbeanName( ) + ", attribute: " + getMbeanAttribute( ) ) ;

		}

		if ( meterType.isHttp( ) ) {

			builder.append( " http - " + getHttpAttribute( ) ) ;

		}

		if ( isIgnoreErrors( ) ) {

			builder.append( " ** Collect failures ignored" ) ;

		}

		return builder.toString( ) ;

	}

	public String toSummary ( ) {

		String summary = getCollectionId( ) ;

		if ( meterType.isMbean( ) ) {

			summary = meterType.fieldName + ": " + getMbeanAttribute( ) + "," + getMbeanName( ) ;
			;

		}

		if ( meterType.isHttp( ) ) {

			summary = "http: " + getHttpAttribute( ) ;

		}

		return summary ;

	}

	public String getCollectionId ( ) {

		return collectionId ;

	}

	public void setCollectionId ( String collectionId ) {

		this.collectionId = collectionId ;

	}

	public JavaCollectionType getMeterType ( ) {

		return meterType ;

	}

	public void setMeterType ( JavaCollectionType meterType ) {

		this.meterType = meterType ;

	}

	public String getMbeanName ( ) {

		return mbeanName ;

	}

	public void setMbeanName ( String mbeanName ) {

		this.mbeanName = mbeanName ;

	}

	public String getMbeanAttribute ( ) {

		return mbeanAttribute ;

	}

	public void setMbeanAttribute ( String mbeanAttribute ) {

		this.mbeanAttribute = mbeanAttribute ;

	}

	public String getTitle ( ) {

		return title ;

	}

	public void setTitle ( String title ) {

		this.title = title ;

	}

	public long getMultiplyBy ( ) {

		return multiplyBy ;

	}

	public void setMultiplyBy ( long multiplyBy ) {

		this.multiplyBy = multiplyBy ;

	}

	public double getDivideBy ( int collectionInterval ) {

		if ( isUseDivideCollectionInterval( ) ) {

			return collectionInterval ;

		}

		return divideBy ;

	}

	public void setDivideBy ( long divideBy ) {

		this.divideBy = divideBy ;

	}

	public boolean isDelta ( ) {

		return delta ;

	}

	public void setDelta ( boolean isDelta ) {

		this.delta = isDelta ;

	}

	public String getHttpAttribute ( ) {

		return httpAttribute ;

	}

	public void setHttpAttribute ( String httpAttribute ) {

		this.httpAttribute = httpAttribute ;

	}

	public int getDecimals ( ) {

		return decimals ;

	}

	public void setDecimals ( int decimals ) {

		this.decimals = decimals ;

	}

	public boolean isIgnoreErrors ( ) {

		return ignoreErrors ;

	}

	public void setIgnoreErrors ( boolean ignoreMissing ) {

		this.ignoreErrors = ignoreMissing ;

	}

	public boolean isUseDivideCollectionInterval ( ) {

		return useDivideCollectionInterval ;

	}

	public void setUseDivideCollectionInterval ( boolean divideByUseInterval ) {

		this.useDivideCollectionInterval = divideByUseInterval ;

	}
}
