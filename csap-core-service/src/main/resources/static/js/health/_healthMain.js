
// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
// needed by graphs
// paths: {
// mathjs: "../../csapLibs/mathjs/modules/math.min"
// },
// packages: [
// { name: 'graphPackage',
// location: '../../graphs/modules/graphPackage', // default 'packagename'
// main: 'ResourceGraph' // default 'main'
// }
// ]
} );

require( [], function () {
	console.log( "\n\n ************** module loaded *** \n\n" );

	var $filterCounts = $( "#filterCounts" );
	var $loading = $( ".loadingBody" );
	var $alertsBody = $( "#alertsBody" );
	var $defBody = $( "#defBody" );
	var $healthTable = $( "#health" );
	var $numberOfHours = $( "#numberHoursSelect" );
	var $metricTable = $( "#metricTable" );
	var $metricBody = $( "#metricBody" );
	var _alertsCountMap = new Object();

	var SECOND_MS = 1000;
	var MINUTE_MS = 60 * SECOND_MS;
	var HOUR_MS = 60 * MINUTE_MS;
	var _refreshTimer = null;

	$( document ).ready( function () {
		CsapCommon.configureCsapAlertify();
		initialize();

	} );

	function initialize() {
		//$( "#tabs" ).tabs() ;
		$( "#tabs" ).tabs( {
			activate: function ( event, ui ) {

				console.log( "Loading: " + ui.newTab.text() );

			}
		} );

		$numberOfHours.change( getAlerts );

		$( "#refreshAlerts" ).click( function () {
			getAlerts()
		} );


		$( "#metricFilter" ).keyup( function () {
			// 
			clearTimeout( _refreshTimer );
			_refreshTimer = setTimeout( function () {
				filterMetrics();
			}, 500 );


			return false;
		} );


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

		$healthTable.tablesorter( {
			sortList: [[0, 1]],
			theme: 'csap'
		} );

		getAlerts();

		$( "tr", $defBody ).each( function ( index ) {
			var $defRow = $( this );
			var defId = $( ":nth-child(1)", $defRow ).text().trim();
			_alertsCountMap[defId] = 0;
		} );


	}

	function filterMetrics() {
		var simonFilter = $( "#metricFilter" ).val().trim().toLowerCase();

//		if ( simonFilter.length == 0) {
//			console.log("No filters") ;
//			return;
//		}
		console.log( "applying filter: ", simonFilter );
		$( "tr td:first-child", $metricBody ).each( function ( index ) {
			var simonName = $( this ).text().toLowerCase();
			var $row = $( this ).parent();
			if ( simonFilter.length > 0 && simonName.indexOf( simonFilter ) == -1 ) {
				$row.hide();
			} else {
				$row.show();
			}
		} );

	}

	function getAlerts() {

		$loading.show();

		var paramObject = {
			hours: $numberOfHours.val()
		};

		if ( testCountParam ) {
			$.extend( paramObject, {
				testCount: testCountParam
			} );
		}

		$.getJSON(
				healthReportUrl, paramObject )
				.done(
						function ( alertResponse ) {
							console.log( "alertResponse", alertResponse );


							$alertsBody.empty();
							var alerts = alertResponse.triggered;
							if ( alerts.length == 0 ) {
								var $row = jQuery( '<tr/>', { } );

								$row.appendTo( $alertsBody );

								$row.append( jQuery( '<td/>', {
									colspan: 99,
									text: "No alerts found. Adjust filters as needed."
								} ) )
								$loading.hide();
							} else {
								for ( var id in _alertsCountMap ) {
									_alertsCountMap[id] = 0;
								}
								console.time( 'updatingAlertsTable' );
								addAlerts( alerts, 0 );
								console.timeEnd( 'updatingAlertsTable' );
							}


							$( "span:nth-child(1)", $filterCounts ).text( alertResponse.filterTotal );
							$( "span:nth-child(2)", $filterCounts ).text( alertResponse.storeTotal );

						} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "getting alerts", errorThrown );
				} );

	}

	// add 100 at a time to improve ui responsiveness
	function addAlerts( alerts, offset ) {

		setTimeout( function () {
			var isComplete = true;
			for ( var i = offset; i < alerts.length; i++ ) {
				//$alertsBody.append( newRows[i] );
				$alertsBody.append( buildRow( alerts[i] ) );
				if ( (i - offset) > 200 ) {
					isComplete = false;
					addAlerts( alerts, i + 1 );
					break;
				}
			}
			//console.log("All done: " + newRows.length ) ;
			if ( isComplete ) {
				 $loading.hide();

					console.log("triggering update") ;
					$healthTable.trigger( "update" );
			}
		}, 10 );


		//$alertsBody.html( $updatedAlertsBody.html() ) ;

		if ( alerts.length > 0 ) {
			_alertsCountMap["csap.health.report.fail"] = alerts.length;
		} else {
			_alertsCountMap["csap.health.report.fail"] = 0;
		}
	}

	function buildRow( alert ) {
		var $row = jQuery( '<tr/>', { } );


		jQuery( '<td/>', {
			text: alert.time,
			"data-raw": alert.ts
		} ).appendTo( $row );



		var $serviceCell = jQuery( '<td/>', { } );
		$serviceCell.appendTo( $row );

		var $serviceLink = jQuery( '<a/>', {
			target: "_blank",
			title: "Open Health Portal for Service",
			class: "simple",
			href: alert.healthUrl,
			text: alert.service
		} ).appendTo( $serviceCell );




		var $hostCell = jQuery( '<td/>', { } );
		$hostCell.appendTo( $row );

		var hostUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, alert.host )
				+ "/app-browser";

		var $hostPortalLink = jQuery( '<a/>', {
			target: "_blank",
			title: "Open Host Portal",
			class: "simple",
			href: hostUrl,
			text: alert.host
		} ).appendTo( $hostCell );

		var alertId = alert.id;
		alertId = alertId.replace( /\./g, ".<WBR>" )
		jQuery( '<td/>', {
			html: alertId
		} ).appendTo( $row );

		_alertsCountMap[alert.id] = _alertsCountMap[alert.id] + 1;

		jQuery( '<td/>', {
			text: alert.type
		} ).appendTo( $row );

		var desc = alert.description;
		if ( alert.count > 1 ) {
			desc = desc + "<br/><div>Alerts Throttled: <span>" + alert.count + "</span></div>";
		}
		jQuery( '<td/>', {
			html: desc
		} ).appendTo( $row );

		return $row;
	}

} );