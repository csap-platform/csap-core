package org.csap.docs ;

import java.util.ArrayList ;

public class ApiResult {

	private ArrayList<ApiDocs> apiNavList ;

	private String Description = null ;

	private ArrayList<String> hostList ;

	private String results = null ;

	public ArrayList<ApiDocs> getApiNavList ( ) {

		return apiNavList ;

	}

	public String getDescription ( ) {

		return Description ;

	}

	public ArrayList<String> getHostList ( ) {

		return hostList ;

	}

	public String getResults ( ) {

		return results ;

	}

	public void setApiNavList ( ArrayList<ApiDocs> apiNavList ) {

		this.apiNavList = apiNavList ;

	}

	public void setDescription ( String description ) {

		Description = description ;

	}

	public void setHostList ( ArrayList<String> hostList ) {

		this.hostList = hostList ;

	}

	public void setResults ( String results ) {

		results = results.replaceAll( ",", ",\n\t" ) ;
		results = results.replaceAll( "\\{", "\n\\{" ) ;
		this.results = results ;

	}

}
