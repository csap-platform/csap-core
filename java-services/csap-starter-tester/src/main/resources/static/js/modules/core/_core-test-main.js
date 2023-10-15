
import "../libs/csap-modules.js";
import "../utils/table-test-page.js" ;

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";

_dom.onReady( function () {

	let appScope = new DemoManager( globalThis.settings );

	_dialogs.loading( "start up" );

	appScope.initialize();

} );


function DemoManager() {

	// note the public method
	this.initialize = function () {

		
        _dom.logSection( `initializing main ` );

		$( '#dbConnectionTest' ).click( function ( e ) {


			var message = "Testing Db Connection ";
			try {
				_dialogs.notify( message );

				// delay to display notification
				setTimeout( testDbConnection, 500 );
			} catch ( e ) {
				console.log( e )
			}
		} );

		$( '.showData' ).click( function ( e ) {



			var message = "Getting items from DB ";
			_dialogs.notify( message );

			setTimeout( getData, 500 );
		} );

		$( '.longTime' )
			.click( function ( e ) {

				e.preventDefault();
				alertify
					.alert( "Warning: this request might take a while. Once completed - the results will be display" );
				return true; // prevents link
			} );

		if ( $( "#inlineResults" ).text() != "" ) {
			alertify.csapInfo( '<pre style="font-size: 0.8em">'
				+ $( "#inlineResults" ).text() + "</pre>" );
		}

		setTimeout( _dialogs.loadingComplete, 500 );
		
	};

	function testDbConnection() {

		$( 'body' ).css( 'cursor', 'wait' );
		$.post( $( '#dbConnectionForm' ).attr( "action" ), $( '#dbConnectionForm' )
			.serialize(), function ( data ) {
				// alertify.alert(data) ;
				_dialogs.dismissAll();
				_dialogs.csapInfo( '<pre style="font-size: 0.8em">' + data
					+ "</pre>" )
				$( 'body' ).css( 'cursor', 'default' );
			}, 'text' // I expect a JSON response
		);
	}

	function getData() {


		_dialogs.loading( "Getting data" );

		$.getJSON( window.baseUrl + "api/showTestDataJson", {
			dummyParam: "dummy"

		} ).done( getDataSuccess )

			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "Getting Items in DB", errorThrown );
			} );
	}

	function getDataSuccess( dataJson ) {

		// _dialogs.notify( "Number of items in DB:" + dataJson.count );

		$( ".alertify-logs" ).css( "width", "800px" );

		var table = $( "#ajaxResults table" ).clone();

		for ( var i = 0; i < dataJson.data.length; i++ ) {

			var trContent = '<td style="padding: 2px;text-align: left">'
				+ dataJson.data[ i ].id
				+ '</td><td style="padding: 2px;text-align: left">'
				+ dataJson.data[ i ].description + '</td>';
			var tr = $( '<tr />', {
				'class': "peter",
				html: trContent
			} );
			table.append( tr );
		}

		var message = "Number of records displayed: " + dataJson.data.length
			+ ", of total in db: " + dataJson.count + "<br><br>";
		if ( dataJson.count == 0 ) {
			var trContent = '<td style="padding: 2px;text-align: left">-</td><td style="padding: 2px;text-align: left">No Data Found</td>';
			var tr = $( '<tr />', {
				'class': "peter",
				html: trContent
			} );
			table.append( tr );
		}
		_dialogs.dismissAll();
		_dialogs.csapInfo( message + table.clone().wrap( '<p>' ).parent().html() ); 

		setTimeout( _dialogs.loadingComplete, 500 );
	}

	function handleConnectionError( command, errorThrown ) {
		var message = "<pre>Failed connecting to server";
		message += "\n\n Server Message:" + errorThrown;
		message += "\n\n Click OK to reload page, or cancel to ignore.</pre>";

		alertify.csapWarning( message );
		$( 'body' ).css( 'cursor', 'default' );
	}

}
