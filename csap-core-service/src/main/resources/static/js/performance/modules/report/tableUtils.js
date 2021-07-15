define( [ "model", "./dataService" ], function ( model, dataService ) {

    console.log( "Module loaded: reports/tableUtils" ) ;

    let TOTAL = "_TOTAL" ;

    return {
        // this will skip the divide by, useful for fields where totals are more appropriate
        getFieldSummaryAppendix: function ( fieldName ) {
            let fieldWithFlagAppended = fieldName + TOTAL ;
            //console.log("fieldWithFlagAppended: " + fieldWithFlagAppended) ;
            return fieldWithFlagAppended ;
        },
        //
        buildHostLinkColumn: function ( host ) {
            return buildHostLinkColumn( host ) ;
        },
        //
        addCell: function ( tableRow, rowJson, field,
                decimalPlaces, specialDivideBy,
                content, maxValue ) {
            addTableCell( tableRow, rowJson, field, decimalPlaces, specialDivideBy,
                    content, maxValue ) ;
        },
        //
        showHighestInColumn: function ( tableId ) {
            showHighestInColumn( tableId ) ;
        },
        //
    }



    function buildHostLinkColumn( host ) {
        let life = $( "#lifeSelect" ).val() ;
        if ( life == null )
            life = uiSettings.lifeParam ;

        let curAppid = $( "#appIdFilterSelect" ).val() ;
        if ( curAppid == null ) {
            curAppid = uiSettings.appIdParam ;
        }

        let hostUrl = uiSettings.analyticsUiUrl + "?host=" + host + "&life=" + life
                + "&appId=" + curAppid + "&project=" + model.getSelectedProject() ;
        // console.log("hostUrl: " + hostUrl) ;
        let hostLink = jQuery( '<a/>', {
            class: "simple",
            target: "_blank",
            href: hostUrl,
            text: host
        } ) ;

        let col1 = jQuery( '<td/>', {
            class: "col1"
        } ) ;
        col1.html( hostLink ) ;
        return col1 ;
    }


    function addTableCell( tableRow, rowJson, field, decimalPlaces, specialDivideBy,
            content, maxValue ) {

        content = typeof content !== 'undefined' ? content : "" ;
        maxValue = typeof content !== 'undefined' ? maxValue : -1 ;

        let currentValue = -1 ;
        let testField = field ;
        if ( field.contains( TOTAL ) ) {
            testField = field.substring( 0, field.length - TOTAL.length ) ;
        }

        if ( field == "combinedCpu" || rowJson[testField] != undefined ) {
            currentValue = getReportFieldValue( rowJson, field, decimalPlaces,
                    specialDivideBy ) ;
        }

        //console.log(field, "currentValue: ", currentValue) ;

        if ( currentValue == -1 ) {
            // console.log("Warning: using default value for ", field) ;
        }

        tableRow.append( jQuery( '<td/>', {
            class: "numeric alt",
            html: numberWithCommas( currentValue ) + content
        } ) ) ;
    }



    function showHighestInColumn( tableId ) {
        let tdCount = $( tableId + ' tbody tr:eq(1) td' ).length,
                trCount = $( tableId + ' tbody tr' ).length ;

        for ( let colIndex = 0 ; colIndex < tdCount ; colIndex++ ) {
            let highest = 0,
                    lowest = 9e99 ;

            //  if ( !$td.hasClass("num") ) continue; // skip past non numerics

            let columnElements = $( "" ) ;
            for ( let rowIndex = 0 ; rowIndex < trCount ; rowIndex++ ) {
                columnElements = columnElements.add( tableId + ' tbody tr:eq(' + rowIndex + ') td:eq(' + colIndex + ')' ) ;
            }

            columnElements.each( function ( i, el ) {
                let $el = $( el ) ;
                if ( i >= 0 ) {
                    let val = parseFloat( $el.text().replace( /[&\/\\#,+()$~%'":*?<>{}]/g, '' ) ) ;

//	            if ( colIndex == 7 )
//	            	console.log("Column: " + colIndex + " value: " + val) 

                    if ( val > highest ) {
                        highest = val ;
                        columnElements.removeClass( 'resourceHighest' ) ;
                        $el.addClass( 'resourceHighest' ) ;
                    }
                    if ( val < lowest ) {
                        lowest = val ;
                        columnElements.removeClass( 'low' ) ;
                        $el.addClass( 'low' ) ;
                    }
                }
            } ) ;
        }

        $( ".resourceHighest" ).each( function ( i, el ) {
            $( this ).html( "<span>" + $( this ).html() + "</span>" ) ;

        } )
    }


    let isDeltaMessage = true ;
    function getReportFieldValue( rowJson, field, decimalPlaces, specialDivideBy ) {

        decimalPlaces = typeof decimalPlaces !== 'undefined' ? decimalPlaces
                : 1 ;
        specialDivideBy = typeof specialDivideBy !== 'undefined' ? specialDivideBy
                : 1 ;

        let warnings = "" ;
        let deltaDivideBy = 1 ;
        let deltaRow = null ;
        if ( dataService.isCompareSelected() ) {

            if ( rowJson.host != undefined ) {
                deltaRow = dataService.findCompareMatch( "host", rowJson.host ) ;
            } else if ( rowJson.hostName != undefined ) {
                deltaRow = dataService.findCompareMatch( "hostName", rowJson.hostName ) ;
            } else {
                deltaRow = dataService.findCompareMatch( "serviceName", rowJson.serviceName ) ;
            }
            //

            if ( !$( "#isUseTotal" ).is( ':checked' ) && field.indexOf( "Avg" ) == -1 ) {
                if ( deltaRow == null ) {
                    if ( isDeltaMessage ) {
                        isDeltaMessage = false ; // we would display many messages
                        alertify.alert( "Warning - got null results on comparison" ) ;
                    }
                    return -1 ;
                }
                if ( !field.contains( TOTAL ) )
                    deltaDivideBy = deltaRow.numberOfSamples ;
            }

            // console.log( "Match" + JSON.stringify(responseJson, null,"\t") )
        }

        let divideBy = 1 ;

        if ( !$( "#isUseTotal" ).is( ':checked' ) && field.indexOf( "Avg" ) == -1 ) {
            divideBy = rowJson.numberOfSamples ;
        }

        //console.log("field : ", rowJson[field], "divideBy ", divideBy) ;

        // support manual overrides for certain fields
        if ( field.contains( TOTAL ) ) {
            field = field.substring( 0, field.length - TOTAL.length ) ;
            divideBy = 1 ;
        }

        let fieldValue = rowJson[field] / divideBy ;

        if ( field === "combinedCpu" ) {
            fieldValue = ( rowJson["totalUsrCpu"] + rowJson["totalSysCpu"] ) / divideBy ;
        }
        
        if ( deltaRow !== null ) {
            let origValue = fieldValue ;
            if ( field !== "combinedCpu" ) {
                fieldValue = ( deltaRow[field] / deltaDivideBy ) - origValue ;
            } else {
                // Special hook for combining usr and sys into more meaningul vm cpu
                fieldValue = ( ( deltaRow["totalUsrCpu"] + deltaRow["totalSysCpu"] ) / deltaDivideBy )
                        - origValue ;
                // console.log(fieldValue + " Combined: delta deltaDivideBy: " +
                // deltaDivideBy + " totalUsrCpu: " + deltaRow[ "totalUsrCpu" ] + "
                // totalSysCpu:" + deltaRow[ "totalSysCpu" ]) ;
                // console.log(" Combined: delta " + fieldValue ) ;
            }

            if ( Math.abs( origValue ) > $( "#compareMinimum" ).val() ) {
                let percentDiff = Math.abs( fieldValue / origValue * 100 ) ;
                // console.log("field: " + field + " fieldValue: " + fieldValue + "
                // percentDiff:" + percentDiff) ;
                if ( percentDiff > $( "#compareThreshold" ).val() ) {
                    // ../images/16x16/go-up.png
                    if ( fieldValue > 0 ) {
                        //warnings = '<span class="diffHigh" src="../images/redArrow.png">' ;
                        warnings = '<span class="diffHigh csap-icon csap-up-arrow" ></span>' ;
                    } else {
                        warnings = '<span class="diffHigh csap-icon csap-down-arrow" ></span>' ;
                    }
                }
            }
        }

        if ( field === "numberOfSamples" ) {
            fieldValue = rowJson[field] ;
            if ( deltaRow !== null ) {
                fieldValue = fieldValue - deltaRow[field] - fieldValue ;
            }
        }
        if ( !$.isNumeric( fieldValue ) )
            return fieldValue ;

        return ( fieldValue / specialDivideBy ).toFixed( decimalPlaces ) + warnings ;

    }

} ) ;