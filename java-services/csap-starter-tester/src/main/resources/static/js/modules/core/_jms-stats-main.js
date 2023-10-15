
import "../libs/csap-modules.js";
import "../utils/table-test-page.js" ;

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";

_dom.onReady( function () {

	_utils.prefixColumnEntryWithNumbers( $( "table" ) );

	let appScope = new Jms_Stats();

	_dialogs.loading( "start up" );


	appScope.initialize();
	
	setTimeout( _dialogs.loadingComplete, 500 );
		

} );


function Jms_Stats() {

	var $hostPattern = $( "#hostPattern" );
	var $hostCount = $( "#hostCount" );
	var $loading = $( ".loadingBody" );
	var $hostReports = $( "#hostReports" );
	var $hostReportsBody = $( "#hostReportBody" );
	var SECOND_MS = 1000;

	this.initialize = function () {

		$( "#queryButton" ).click( getStats );

		$.tablesorter.addParser( {
			// set a unique id
			id: 'raw',
			is: function ( s, table, cell, $cell ) {
				// return false so this parser is not auto detected
				return false;
			},
			format: function ( s, table, cell, cellIndex ) {
				var $cell = $( cell );
				// console.log("timestamp parser", $cell.data('timestamp'));
				// format your data for normalization
				return $cell.data( 'raw' );
			},
			// set type, either numeric or text
			type: 'numeric'
		} );

		$hostReports.tablesorter( {
			sortList: [ [ 1, 1 ] ],
			theme: 'csap'
		} );



	}

	function getStats() {

		$( "#results" ).show();
		$loading.show();
		$( "#hungDiv" ).hide();

		var params = {
			backlogQ: $( "#backlogQ" ).val(),
			processedQ: $( "#processedQ" ).val(),
			hostPattern: $( "#hostPattern" ).val(),
			hostCount: $( "#hostCount" ).val(),
			sampleCount: $( "#sampleCount" ).val(),
			expression: $( "#expression" ).val(),
		};

		$.getJSON(
			window.baseUrl + "/../hungReport", params )
			.done( buildReport )

			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "getting alerts", errorThrown );
			} );

	}


	function buildReport( reportResponse ) {

		$loading.hide();
		console.log( "reportResponse", reportResponse );

		$hostReportsBody.empty();



		$( "#hungDiv" ).show();
		$( "#hungHosts" ).empty();
		for ( var i = 0; i < reportResponse.hungNodes.length; i++ ) {
			jQuery( '<label/>', {
				class: "host",
				text: reportResponse.hungNodes[ i ]
			} ).appendTo( $( "#hungHosts" ) );
		}


		for ( var i = 0; i < reportResponse.hungReports.length; i++ ) {
			var hostReport = reportResponse.hungReports[ i ];
			var $row = jQuery( '<tr/>', {} );

			$row.appendTo( $hostReportsBody );

			$row.append( jQuery( '<td/>', {
				text: hostReport.host
			} ) )

			var $statusImage = jQuery( '<img/>', {
				src: window.imagesBase + "/16x16/green.png",
				class: "loadMetric"
			} );
			if ( hostReport.isHung == undefined || hostReport.isHung ) {
				$statusImage = jQuery( '<img/>', {
					src: window.imagesBase + "/16x16/red.png",
					class: "loadMetric"
				} );
			}
			var $statusCol = jQuery( '<td/>', {
				"data-raw": hostReport.isHung
			} )
			$statusCol.append( $statusImage );
			$row.append( $statusCol );



			$notesCol = jQuery( '<td/>', {} );
			$row.append( $notesCol )
			var $notes = jQuery( '<div/>', {} );
			$notesCol.append( $notes );

			if ( hostReport.error ) {
				jQuery( '<pre/>', { class: "error", text: hostReport.error } ).appendTo( $notes )
				jQuery( '<br/>', {} ).appendTo( $notes )

			}

			if ( hostReport.deviceBacklog ) {
				jQuery( '<div/>', { class: "qStatsLabel", text: "Backlog:" } ).appendTo( $notes )
				jQuery( '<div/>', { class: "qStats", text: hostReport.deviceBacklog } ).appendTo( $notes )
				jQuery( '<br/>', {} ).appendTo( $notes )
				jQuery( '<div/>', { class: "qStatsLabel", text: "Dispatched:" } ).appendTo( $notes )
				jQuery( '<div/>', { class: "qStats", text: hostReport.deviceDispatched } ).appendTo( $notes )
			}
		}


		$hostReports.trigger( "update" );


	}



}