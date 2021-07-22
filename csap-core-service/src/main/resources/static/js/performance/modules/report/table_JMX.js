define( ["model", "./dataService", "./tableUtils", "./utils"], function ( model, dataService, tableUtils, utils ) {

	console.log( "Module loaded: reports/table_JMX" );


	var _jmxCustomNeedsInit = true;
	var tableMathFormat = function ( $cell, wo, result, value, arry ) {
		var txt = '<span class="align-decimal">' + result + '</span>';
		if ( $cell.attr( 'data-prefix' ) != null ) {
			txt = $cell.attr( 'data-prefix' ) + txt;
		}
		if ( $cell.attr( 'data-suffix' ) != null ) {
			txt += $cell.attr( 'data-suffix' );
		}
		return txt;
	}

	return {
		//
		buildSummaryRow: function ( rowJson, tableRow, rowJson ) {
			buildSummaryRow( rowJson, tableRow, rowJson );
		},
		//
		buildDetailRow: function ( rowJson, tableRow, rowJson ) {
			buildDetailRow( rowJson, tableRow, rowJson );
		},
		//
		buildCustomRow: function ( rowJson, tableRow, rowJson ) {
			buildCustomRow( rowJson, tableRow, rowJson );
		},
		//
	}


	function buildSummaryRow( rowJson, tableRow, rowJson ) {

		// console.log( JSON.stringify(rowJson, null,"\t") )

		tableRow.append( jQuery( '<td/>', {
			class: "projectColumn",
			text: rowJson.project + " - " + rowJson.lifecycle
		} ) );

		$( "#serviceLabel" ).html( " JMX  Report " );
		var serviceLink = jQuery(
				'<a/>',
				{
					class: "simple",
					target: "_blank",
					href: uiSettings.analyticsUiUrl + "?service=" + rowJson.serviceName
							+ "&life=" + $( "#lifeSelect" ).val() + "&project="
							+ model.getSelectedProject(),
					text: rowJson.serviceName
				} ).click( function () {
			utils.launchServiceJmxReport( rowJson.serviceName );
			return false;
		} );

		var col1 = jQuery( '<td/>', {
			class: "col1"
		} );
		col1.html( serviceLink );
		tableRow.append( col1 );

		var numInstances = 0;
		for ( var i = 0; i < model.getPackageDetails( model.getSelectedProject() ).instances.instanceCount.length; i++ ) {
			var instanceCount = model.getPackageDetails( model.getSelectedProject() ).instances.instanceCount[i];
			// console.log( JSON.stringify(rowJson, null,"\t") )
			if ( instanceCount.serviceName == rowJson.serviceName ) {
				numInstances = instanceCount.count;
				break;
			}
		}

		tableRow.append( jQuery( '<td/>', {
			class: "num ",
			text: numInstances
		} ) );

		tableUtils.addCell( tableRow, rowJson, "numberOfSamples", 0 );

		tableUtils.addCell( tableRow, rowJson, "cpuPercent", 1, 1, "%", 40 );

		tableUtils.addCell( tableRow, rowJson, "tomcatConnections", 0, 1, "", 10 );

		tableUtils.addCell( tableRow, rowJson, tableUtils.getFieldSummaryAppendix( "sessionsCount" ), 0 );
		tableUtils.addCell( tableRow, rowJson, "sessionsActive", 0 );

		tableUtils.addCell( tableRow, rowJson, "httpRequestCount", 0 );
		tableUtils.addCell( tableRow, rowJson, "httpProcessingTime", 0 );
		tableUtils.addCell( tableRow, rowJson, "httpKbytesReceived", 0 );
		tableUtils.addCell( tableRow, rowJson, "httpKbytesSent", 0 );


		tableUtils.addCell( tableRow, rowJson, "openFiles", 0, 1, "", 300 );
		tableUtils.addCell( tableRow, rowJson, "jvmThreadCount", 0, 1, "", 100 );

		tableUtils.addCell( tableRow, rowJson, "tomcatThreadsBusy", 0, 1, "", 10 );
		tableUtils.addCell( tableRow, rowJson, "tomcatThreadCount", 0, 1, "", 10 );

		tableUtils.addCell( tableRow, rowJson, "heapUsed", 0 );
		tableUtils.addCell( tableRow, rowJson, "heapMax", 0 );

		tableUtils.addCell( tableRow, rowJson, "minorGcInMs", 0 );
		tableUtils.addCell( tableRow, rowJson, "majorGcInMs", 0 );


	}


	function buildCustomRow( rowJson, tableRow, rowJson ) {

		// console.log( JSON.stringify(rowJson, null,"\t") ) ;

		var tableId = "#jmxCustomTable"
		var lastChecked = new Array();

		if ( _jmxCustomNeedsInit ) {
			buildCustomerHeaderAndFooter( rowJson, tableId );
		}

		tableRow.append( jQuery( '<td/>', {
			class: "projectColumn",
			text: rowJson.project + " - " + rowJson.lifecycle
		} ) );

		tableRow.append( tableUtils.buildHostLinkColumn( rowJson.host ) );

		for ( var key in rowJson ) {

			if ( isSkipCustomColumn( key ) )
				continue;
			tableUtils.addCell( tableRow, rowJson, key, 1 );
			// tableRow.append(jQuery('<td/>', {
			// class : "num",
			// text : rowJson[key]
			// }) );

		}
		if ( _jmxCustomNeedsInit ) {
			_jmxCustomNeedsInit = false;

			// previous selections?

			var colsView = new Array();
			colsView[0] = "disable";
			colsView[1] = "disable";

			if ( lastChecked.length > 0 ) {
				// console.log("buildJmxCustomRow() Using last column selected") ;
				colsView = colsView.concat( lastChecked );
			} else {
				// console.log("buildJmxCustomRow: Using default column selected") ;

				colsView[2] = false;
				var index = 0;
				for ( var key in rowJson ) {
					if ( index < 2 )
						continue;
					index++;
					if ( index < 9 )
						colsView[index] = true;
					else
						colsView[index] = false;

				}
			}
// console.log( "custom col view: " + JSON.stringify(colsView,
// null,"\t") ) ;
			$( "#jmxCustomTable" ).tablesorter( {
				sortList: [[1, 0]],
				theme: 'csapSummary',
				widgets: ['math', 'columnSelector'],
				widgetOptions: {
					columnSelector_container: $( '#applicationdetailColumnSelector' ),
					columnSelector_columns: colsView,
					columnSelector_saveColumns: false,
					columnSelector_mediaquery: false,
					math_mask: '#,###,##0.',
					math_data: 'math',
					columns_tfoot: false,
					math_complete: tableMathFormat
				}
			} );
// $("#jmxCustomTable").trigger("refreshColumnSelector") ;
		}
	}

	function buildCustomerHeaderAndFooter( rowJson, tableId ) {

		// console.log( "Building headers" );

		if ( $( '#applicationdetailColumnSelector input' ).length > 0 ) {
			$( '#applicationdetailColumnSelector input' ).each( function () {

				if ( $( this ).is( ':checked' ) ) {
					// console.log ("Showing: " + $(this).data("column") )
					lastChecked.push( true );
				} else {
					// console.log ("Hiding: " + $(this).data("column") )
					lastChecked.push( false );
				}
			} );
		}


// Tablesorted cleanup needs this
		$( "#jmxCustomTable" ).trigger( "destroy" );

		$( tableId + ' thead tr' ).empty();
		$( tableId + ' tfoot tr' ).empty();

		$( tableId + ' thead tr' ).append( jQuery( '<th/>', {
			class: "projectColumn",
			text: "Project"
		} ) );

		$( tableId + ' tfoot tr' ).append( jQuery( '<td/>', {
			class: "projectColumn",
		} ) );

		$( tableId + ' thead tr' ).append( jQuery( '<th/>', {
			class: "col1",
			text: " Host"
		} ) );

		$( "#serviceLabel" ).html(
				rowJson.serviceName + " Application Report " );

		$( tableId + ' tfoot tr.totalRow' ).append( jQuery( '<td/>', {
			class: "num",
			"data-math": "col-count",
			"data-prefix": "Totals:  "
		} ) );
		$( tableId + ' tfoot tr.averageRow' ).append( jQuery( '<td/>', {
			class: "num",
			text: "Average:  "
		} ) );
		$( tableId + ' tfoot tr.medianRow' ).append( jQuery( '<td/>', {
			class: "num",
			text: "Median:  "
		} ) );


		for ( var key in rowJson ) {

			if ( isSkipCustomColumn( key ) ) {
				continue;
			}

			var label = model.getServiceLabels( rowJson.serviceName, key );

			$( tableId + ' thead tr' ).append( jQuery( '<th/>', {
				class: "num",
				text: label
			} ) );
			$( tableId + ' tfoot tr.totalRow' ).append( jQuery( '<td/>', {
				class: "num",
				"data-math": "col-sum",
				text: "-"
			} ) );
			$( tableId + ' tfoot tr.averageRow' ).append( jQuery( '<td/>', {
				class: "num",
				"data-math": "col-mean",
				text: "-"
			} ) );
			$( tableId + ' tfoot tr.medianRow' ).append( jQuery( '<td/>', {
				class: "num",
				"data-math": "col-median",
				text: "-"
			} ) );
		}
// console.log( "Complete  headers" );

	}

	function isSkipCustomColumn( key ) {
		if ( key == "lifecycle" || key == "project" || key == "appId"
				|| key == "serviceName" || key == "host" )
			return true;
		return false;
	}

	function buildDetailRow( rowJson, tableRow, rowJson ) {

		// console.log( JSON.stringify(rowJson, null,"\t") )

		$( "#serviceLabel" ).html( rowJson.serviceName + " Java Report " );

		tableRow.append( jQuery( '<td/>', {
			class: "projectColumn",
			text: rowJson.project + " - " + rowJson.lifecycle
		} ) );

		tableRow.append( tableUtils.buildHostLinkColumn( rowJson.host ) );

		tableUtils.addCell( tableRow, rowJson, "numberOfSamples", 0 );

		tableUtils.addCell( tableRow, rowJson, "cpuPercent", 1, 1, "%", 40 );
		tableUtils.addCell( tableRow, rowJson, "tomcatConnections", 0, 1, "", 10 );

		tableUtils.addCell( tableRow, rowJson, tableUtils.getFieldSummaryAppendix( "sessionsCount" ), 0 );
		tableUtils.addCell( tableRow, rowJson, "sessionsActive", 0 );

		tableUtils.addCell( tableRow, rowJson, "httpRequestCount", 0 );
		tableUtils.addCell( tableRow, rowJson, "httpProcessingTime", 0 );
		tableUtils.addCell( tableRow, rowJson, "httpKbytesReceived", 0 );
		tableUtils.addCell( tableRow, rowJson, "httpKbytesSent", 0 );


		tableUtils.addCell( tableRow, rowJson, "openFiles", 0, 1, "", 400 );

		tableUtils.addCell( tableRow, rowJson, "jvmThreadCount", 0, 1, "", 100 );

		tableUtils.addCell( tableRow, rowJson, "tomcatThreadsBusy", 0, 1, "", 10 );
		tableUtils.addCell( tableRow, rowJson, "tomcatThreadCount", 0, 1, "", 30 );

		tableUtils.addCell( tableRow, rowJson, "heapUsed", 0 );
		tableUtils.addCell( tableRow, rowJson, "heapMax", 0 );

		tableUtils.addCell( tableRow, rowJson, "minorGcInMs", 0 );
		tableUtils.addCell( tableRow, rowJson, "majorGcInMs", 0 );


	}
} );