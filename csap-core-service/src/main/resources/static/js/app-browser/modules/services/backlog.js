define( [ "browser/utils" ], function ( utils ) {

    console.log( "Module loaded" ) ;
    const apiUrl = API_URL + "/application/deployment/backlog" ;

    let $backlogTable, _backlogTimer ;
    let _latestQColumnWidth ;

    let $displayPanel = null ;

    let testCounter = 0 ;


    return {
        showBacklogInPanel: function ( $menuContent, forceHostRefresh, menuPath ) {

            $displayPanel = $menuContent ;
            $backlogTable = $( "#backlog-table-alertify" ) ;
            deployBacklogDialogResize() ;
            showBacklogInPanel( ) ;
            
            if ( forceHostRefresh ) {
                utils.refreshStatus( true ) ;
            }

        }
    } ;

    function showBacklogInPanel( ) {

        $( "#refresh-backlog-now" ).off().click( refreshBacklogNow ) ;
        //$( "#refresh-backlog-now" ).click() ;
        // clearTimeout( _backlogTimer ) ;
        refreshBacklogNow() ;

        $( "#filter-backlog-checkbox" ).off().change( function () {
            refreshBacklogNow() ;
        } ) ;

        $backlogTable.tablesorter( {
            sortList: [ [ 0, 0 ] ],
            theme: 'csapSummary'
        } ) ;

    }


    function refreshBacklogNow() {
        clearTimeout( _backlogTimer ) ;

        console.log( `refreshBacklogNow() $backlogTable: ${ $backlogTable.attr( "id" ) }` ) ;

        _backlogTimer = setTimeout( function () {
            let $backlogBody = $( "tbody", $backlogTable ) ;
            $backlogBody.empty() ;
            getBacklog( CSAP_HOST_NAME, apiUrl, true, true )
        }, 10 ) ;
    }

    function deployBacklogDialogResize( dialogWidth, dialogHeight ) {

        console.log( `deployBacklogDialogResize() : dialogWidth: ${dialogWidth} dialogHeight: ${ dialogHeight} ` ) ;

        return ;


        setTimeout( function () {

            let $backlogBody = $( "tbody", $backlogTable ) ;
            let $hostColumn = $( "td:first-child", $backlogBody ) ;
            let $queueColumn = $( "td:nth-child(2)", $backlogBody ) ;

            let $backlogHeader = $( "thead", $backlogTable ) ;
            let $hostColumnHeader = $( "th:nth-child(1)", $backlogHeader ) ;
            let $queueColumnHeader = $( "th:nth-child(2)", $backlogHeader ) ;
            $hostColumnHeader.css( "width", Math.round( $hostColumn.outerWidth( true ) + 50 ) ) ;



            console.log( `deployBacklogDialogResize() panel id: ${$displayPanel.parent().attr( "id" )}` ) ;
            dialogWidth = Math.round( $displayPanel.parent().outerWidth( true ) ) ;
            dialogHeight = Math.round( $displayPanel.parent().parent().outerHeight( true ) ) ;


            let tableWidth = Math.round( dialogWidth - 20 ) ;
            $backlogTable.css( "width", tableWidth ) ;

            let scrollBarWidth = 0 ;
            _latestQColumnWidth = tableWidth - scrollBarWidth - Math.round( $hostColumn.outerWidth( true ) ) ;

            $queueColumn.css( "width", _latestQColumnWidth ) ;
            $queueColumnHeader.css( "width", _latestQColumnWidth + 50 ) ;

            //console.log( `deployBacklogDialogResize() $dialogHeader: ${$dialogHeader.outerHeight( true )} $backlogHeader: ${$backlogHeader.outerHeight( true )} ` )

            let $dialogHeader = $( "#backlog-header" ) ;

            let maxHeight = dialogHeight
                    - Math.round( $dialogHeader.outerHeight( true ) )
                    - Math.round( $backlogHeader.outerHeight( true ) )
                    - 20 ;
            console.log( `deployBacklogDialogResize() maxHeight: ${ maxHeight } maxWidth: ${_latestQColumnWidth} ` )
            $backlogBody.css( "max-height", maxHeight ) ;

            if ( _backlogTimer == null ) {
                //$backlogBody.empty() ;

            }

//            let tdWidth = Math.round( $( "td:nth-child(2)", $backlogBody).outerWidth( true )) ;
//            $( "th:nth-child(2)", $backlogHeader).css("width", tdWidth ) ;

        }, 500 ) ;


    }

    function filterBacklog( $backlogTable ) {
        //console.log( "filterBacklog (), filter: ", $( "#filter-backlog-checkbox" ).is( ':checked' ) ) ;
        ;
        if ( $( "#filter-backlog-checkbox" ).is( ':checked' ) ) {
            //                alertify.csapWarning( "toggle filters." ) ;
            $( "tr", $backlogTable ).each( function () {
                let $row = $( this ) ;
                if ( $( "div.queue", $row ).length == 0 ) {
                    $row.hide() ;
                } else {
                    $row.show() ;
                }
            } ) ;
        } else {
            $( "tr", $backlogTable ).show() ;
        }
    }

    function showBusy( doBusy ) {

        if ( doBusy ) {
            $( "#backlog-refresh-image" ).hide() ;
            $( "#backlog-busy-image" ).show() ;
        } else {
            $( "#backlog-refresh-image" ).show() ;
            $( "#backlog-busy-image" ).hide() ;
        }
    }

    function getBacklog( apiHost, apiUrl, schedule, clear ) {

        if ( $displayPanel ) {
            if ( !$displayPanel.is( ":visible" ) ) {
                return ;
            }
        }

        showBusy( true ) ;

        console.log( `getBacklog() apiHost: ${ apiHost }, schedule: '${schedule}' $backlogTable: ${ $backlogTable.attr( "id" ) }` ) ;
        $.getJSON( apiUrl )

                .done( function ( responseJson ) {

                    if ( clear ) {
//                        $( "tbody", $backlogTable ).empty() ;
                    }

                    updateBacklog( apiUrl, $backlogTable, responseJson ) ;

                    let testDemo = {
                        "host": "desktop",
                        isPaused: false,
                        queue: [ { serviceName: "demo-service", type: "some-operation", userid: "someUser" } ]
                    }
                    if ( apiHost.includes( "localhost" ) && testCounter++ > 1 ) {
                        console.log(`adding test item`) ;
                        updateBacklog( apiUrl, $backlogTable, testDemo ) ;
                    }
                    filterBacklog( $backlogTable ) ;

                    if ( schedule ) {
                        clearTimeout( _backlogTimer ) ;
                        _backlogTimer = setTimeout( () => getBacklog( apiHost, apiUrl, true, true ), utils.getRefreshInterval() / 2 ) ;
                        deployBacklogDialogResize() ;

                    }

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Error: Retrieving backlog: " + apiUrl, errorThrown ) ;
                    let connectionFailure = {
                        "host": apiHost,
                        queue: [ ],
                        connectionFailure: true
                    }
                    updateBacklog( apiUrl, $backlogTable, connectionFailure ) ;
                    // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
                } )
    }

    function updateBacklog( apiUrl, $backLogTable, responseJson ) {

        let rowsData = new Array() ;

        if ( Array.isArray( responseJson ) ) {
            // admin
            rowsData = responseJson ;

            for ( const hostBacklog of rowsData ) {

                getBacklog( hostBacklog.host, hostBacklog["host-details"], false, false ) ;
            }

        } else {
            // agent
            let $hostRow = buildBacklogRow( responseJson, $backLogTable ) ;
            let $currentRow = $( "#" + responseJson.host, $backLogTable ) ;

            if ( $currentRow.length === 0 ) {
                $backLogTable.append( $hostRow ) ;
                $backlogTable.trigger( "update" ) ;
            } else {
                $currentRow.empty() ;
                $currentRow.append( $("td", $hostRow) ) ;
            }

            setTimeout( function () {
                showBusy( false ) ;
            }, 1000 ) ;

        }

    }

    function buildBacklogRow( hostResponse, $backLogTable ) {

        let $row = jQuery( '<tr/>', { id: hostResponse.host } ) ;

        let $hostColumn = $( "<td/>" ) ;
        $row.append( $hostColumn ) ;
        $hostColumn.append( utils.buildAgentLink( hostResponse.host, "host-dash" ) ) ;


        let  $itemColumn = $( "<td/>", { class: "deploy-backlog" } ) ;
        $itemColumn.css( "width", _latestQColumnWidth ) ;
        $row.append( $itemColumn ) ;

        let  $controlGrid = $( "<div/>", { class: "controls" } ) ;
        $itemColumn.append( $controlGrid ) ;

        let controlStatus = "status-green" ;
        if ( hostResponse.connectionFailure ) {
            controlStatus = "status-red" ;
        } else if ( hostResponse.isPaused ) {
            controlStatus = "status-yellow" ;
        }

        jQuery( '<span/>', {
            class: controlStatus
        } ).appendTo( $controlGrid ) ;

        if ( hostResponse.connectionFailure ) {
            //console.log(`connection failure: ${hostResponse.host}`) ;
            $controlGrid.append( $( `<span/>` ).html( "unable to connect to csap agent" ) ) ;
            return $row ;
        }
        
        $controlGrid.append( buildHostPauseButton( hostResponse.host, hostResponse.isPaused ) ) ;
        
        if ( hostResponse.queue.length === 0 ) {
            $controlGrid.append( $( `<span/>` ).html( "" ) ) ;

        } else {
            $controlGrid.append( buildHostClearButton( hostResponse.host ) ) ;

            let  $queueGrid = $( "<div/>", { class: "queue" } ) ;
            $itemColumn.append( $queueGrid ) ;

            let activeItem=true;
            for ( const rowItem of hostResponse.queue ) {
                // console.log( `rowItem: `, rowItem ) ;
                $item = buildLogLink( activeItem, hostResponse.host, rowItem.serviceName ) ;
                activeItem=false;
                $queueGrid.append( $item ) ;

                $( "<span/>", {
                    "text": rowItem.userid,
                    "class": "backlog-line"
                } ).appendTo( $queueGrid ) ;

                $( "<span/>", {
                    "text": rowItem.type,
                    "class": "backlog-line"
                } ).appendTo( $queueGrid ) ;

            }
        }
        return $row ;
    }

    function buildLogLink( activeItem, targetHost, targetService ) {
        let urlAction = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, targetHost ) ;
        urlAction += "/file/FileMonitor?u=1&isLogFile=true&serviceName=" + targetService + "&hostName=" + targetHost ;

        let styles = "csap-link-icon csap-indent backlog-line" ;
        if ( activeItem ) {
            styles = "csap-link-icon csap-loading backlog-line" ;
        }
        let $hostLogs = jQuery( '<a/>', {
            class: styles,
            title: "Open log viewer",
            target: "_blank",
            href: urlAction,
            text: targetService
        } ) ;

        return $hostLogs ;
    }

    function buildHostClearButton( targetHost ) {
        let jobsClearUrl = baseUrl + "service/deployments" ;
        let parameters = {
            hostName: targetHost
        }

        let $clearButton = jQuery( '<button/>', {
            title: "Clear remaining jobs (current job finishes)",
            class: "csap-icon job-action csap-trash"
        } ) ;


        $clearButton.click( function () {

            console.log( `clear clicked: sending request to ${jobsClearUrl}` ) ;
            showBusy( true ) ;
            utils.loading("Clearing Backlog") ;
            $( this ).css( "opacity", "0.5" ) ;
            $.delete( jobsClearUrl, parameters )
                    .done( function ( commandResults ) {
                        alertify.csapInfo( JSON.stringify( commandResults, "\n", "\t" ) ) ;
                        utils.loadingComplete() ;
                        refreshBacklogNow() ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
        } ) ;

        return $clearButton ;
    }

    function buildHostPauseButton( targetHost, isPaused ) {

//        let jobsUrl = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, targetHost )
//                + "/service/deployments/pause" ;
        let jobsPauseUrl = baseUrl + "service/deployments/pause" ;
        let parameters = {
            hostName: targetHost
        }

        let title = "Click to pause; operation in progress will complete" ;
        let controlStatus = "csap-pause" ;
        if ( isPaused ) {
            controlStatus = "csap-play" ;
            title = "Click to resume deployments" ;
        }


        let $pauseButton = jQuery( '<button/>', {
            title: title,
            class: `csap-icon job-action ${ controlStatus }` 
        } ) ;


        $pauseButton.click( function () {
            //$( "tbody", $backlogTable ).empty() ;
            console.log( `pause clicked: sending request to ${jobsPauseUrl}` ) ;
            showBusy( true ) ;
            utils.loading("Sending request to server...") ;
            $( this ).css( "opacity", "0.5" ) ;
            $.post( jobsPauseUrl, parameters )
                    .done( function ( commandResults ) {
                        //alertify.csapInfo( JSON.stringify( commandResults, "\n", "\t" ) ) ;
                        console.log( `buildHostPauseButton() `, commandResults ) ;
                        utils.loadingComplete() ;
                        refreshBacklogNow() ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
        } ) ;
        return $pauseButton ;
    }



    function buildAlertifyOptions( title ) {
        let options = {
            title: title,
            movable: false,
            maximizable: true,
            resizable: true,
            autoReset: false
        }

        return options ;
    }

} ) ;


jQuery.each( [ "put", "delete" ], function ( i, method ) {
    jQuery[ method ] = function ( url, data, callback, type ) {
        if ( jQuery.isFunction( data ) ) {
            type = type || callback ;
            callback = data ;
            data = undefined ;
        }

        return jQuery.ajax( {
            url: url,
            type: method,
            dataType: type,
            data: data,
            success: callback,
            xhrFields: {
                withCredentials: true // CORS Supports @CrossOrigin
            }
        } ) ;
    } ;
} ) ;