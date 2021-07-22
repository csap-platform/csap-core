define( [ "browser/utils" ], function ( utils ) {

    console.log( "Module loaded" ) ;
    let $contentLoaded = null ;

    const $performanceTab = $( "#services-tab-content" ) ;

    const $eventDetailsMenu = $( ".kevent-details-selected", "#services-tab" ) ;

    const $eventsContent = $( "#services-tab-kevents", $performanceTab ) ;
    const $eventsPanel = $( "#events-panel", $eventsContent ) ;
    const $eventsTable = $( "tbody", $eventsContent ) ;

    const $eventsFilter = $( "#event-filter", $eventsContent ) ;
    const $eventQueryLimit = $( "#event-limit", $eventsContent ) ;
    const $eventsTypeFilter = $( "#kevent-type", $eventsContent ) ;
    const $eventsKindFilter = $( "#kevent-kind", $eventsContent ) ;
    const $eventsNamespaceFilter = $( "#kevent-namespace", $eventsContent ) ;

    const $eventCounts = $( "#kevent-counts", $eventsContent ) ;



    let lastTypeFilter, lastKindFilter, lastNamespaceFilter ;

    let _eventFilterTimer ;

    let lastSelected ;

    let initialized ;




    return {

        show: function ( $displayContainer, forceHostRefresh ) {

            if ( !initialized ) {
                initialize() ;
                initialized = true ;
            }
            $contentLoaded = new $.Deferred() ;
            getEvents() ;
            return $contentLoaded ;
        },

        selected: function () {
            return lastSelected ;
        },

        closeDetails: function () {
            utils.launchMenu( "services-tab,kevents" ) ;
            $eventDetailsMenu.hide() ;
        }
    } ;



    function initialize() {



        $eventsFilter.off().keyup( function () {
            console.log( "Applying template filter" ) ;
            clearTimeout( _eventFilterTimer ) ;
            _eventFilterTimer = setTimeout( function () {
                applyFilter( ) ;
            }, 500 ) ;
        } ) ;

        $eventsTypeFilter.off().change( getEvents )
        $eventsKindFilter.off().change( getEvents )
        $eventsNamespaceFilter.off().change( getEvents ) ;

        $eventQueryLimit.change( getEvents ) ;

        $( "#event-refresh", $eventsContent ).click( getEvents ) ;


        $( "#event-category-combo", $eventsContent ).change( function () {
            $( "#event-category", $eventsContent )
                    .val( $( this ).val() ) ;
            getEvents() ;
        } ) ;


    }




    function applyFilter( ) {

        let $eventRows = $( 'tr', $eventsTable ) ;

        let  includeFilter = $eventsFilter.val() ;

        console.log( ` includeFilter: ${includeFilter}` ) ;


        if ( includeFilter.length > 0 ) {
            $eventRows.hide() ;
            let filters = includeFilter.split( "," ) ;
            for ( let filter of filters ) {
                $( `td:icontains("${ filter }")`, $eventRows ).parent().show() ;
            }
            $( `td.event-date-label`, $eventRows ).parent().show() ;
        } else {
            $eventRows.show() ;
        }

    }


    function getEvents() {

        utils.loading( `Loading Events` ) ;



        let parameters = {
            project: utils.getActiveProject(),
            kubernetes: true,
            count: $eventQueryLimit.val(),
            category: $( "#event-category", $eventsContent ).val()
        }

        $.getJSON(
                APP_BROWSER_URL + "/events/kubernetes",
                parameters )

                .done( function ( eventsReport ) {

                    lastTypeFilter = $eventsTypeFilter.val() ;
                    $eventsTypeFilter.empty() ;

                    lastKindFilter = $eventsKindFilter.val() ;
                    $eventsKindFilter.empty() ;

                    lastNamespaceFilter = $eventsNamespaceFilter.val() ;
                    $eventsNamespaceFilter.empty() ;

                    $contentLoaded.resolve() ;
                    utils.loadingComplete() ;
                    if ( !eventsReport.events ) {
                        $eventsTable.empty() ;
                        alertify.csapInfo( "No Events found" ) ;
                        return ;
                    }
                    console.log( `getEvents() : eventsReport: ${eventsReport.events.length}` ) ;

                    showEvents( eventsReport.events ) ;

                    applyFilter( ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( `retrieving alerts '${textStatus}'`, errorThrown ) ;
                } ) ;
    }

    function showEvents( events ) {

        $eventsTable.empty() ;

        let lastDate = null ;

        let types = new Array() ;
        jQuery( '<option/>', {
            text: "all"
        } ).appendTo( $eventsTypeFilter ) ;

        let kinds = new Array() ;
        jQuery( '<option/>', {
            text: "all"
        } ).appendTo( $eventsKindFilter ) ;

        let namespaces = new Object() ;

        let countShown = 0 ;
        let countTotal = 0 ;

        for ( let event of events ) {

            countTotal += event.count ;
            if ( !types.includes( event.type ) ) {
                types.push( event.type ) ;
                jQuery( '<option/>', {
                    text: event.type
                } ).appendTo( $eventsTypeFilter ) ;
            }

            if ( !kinds.includes( event.kind ) ) {
                kinds.push( event.kind ) ;
                jQuery( '<option/>', {
                    text: event.kind
                } ).appendTo( $eventsKindFilter ) ;
            }

            if ( !namespaces[event.namespace ] ) {
                // namespaces.push( event.namespace ) ;
                namespaces[event.namespace ] = event.count ;
//                jQuery( '<option/>', {
//                    text: event.namespace
//                } ).appendTo( $eventsNamespaceFilter ) ;
            } else {
                namespaces[event.namespace ] = namespaces[event.namespace ] + event.count ;
            }

            if ( event.reason !== "more-events-warning" ) {

                if ( lastTypeFilter !== "all"
                        && lastTypeFilter !== event.type ) {
                    continue ;
                }

                if ( lastKindFilter !== "all"
                        && lastKindFilter !== event.kind ) {
                    continue ;
                }

                if ( lastNamespaceFilter !== "all"
                        && lastNamespaceFilter !== event.namespace ) {
                    continue ;
                }
            }
             countShown += event.count ;

            let eventDate = event.date ;
            if ( eventDate != lastDate ) {
                let $labelRow = jQuery( '<tr/>', { } )
                        .appendTo( $eventsTable ) ;

                let label = eventDate ;
                if ( label == "" ) {
                    label = "Today"
                }

                jQuery( '<td/>', {
                    text: label,
                    class: "event-date-label",
                    colspan: 3
                } ).appendTo( $labelRow ) ;

            }
            lastDate = eventDate ;


            let $row = jQuery( '<tr/>', { } )
                    .appendTo( $eventsTable ) ;
            let $dateColumn = jQuery( '<td/>', { } ).appendTo( $row ) ;

            let $dateTime = jQuery( '<span/>', { class: "date-time" } )
                    .appendTo( $dateColumn ) ;

            let $date = jQuery( '<span/>', {
                class: "event-date",
                text: `${ eventDate }`
            } ) ;

            let $timeButton = jQuery( '<button/>', {
                "data-id": event.id,
                "data-api": event.apiPath,
                title: "View Event details",
                class: "csap-button event-time",
                text: event.time
            } ) ;

            if ( event.type !== "Normal" ) {
                jQuery( '<span/>', {
                    class: "status-red"
                } ).appendTo( $timeButton ) ;
            }

            $dateTime.append( $timeButton ) ;
            $dateTime.append( $date ) ;


            let $sourceColummn = jQuery( '<td/>', {
                class: "source"
            } ).appendTo( $row ) ;
            
            let $sourceHeader = jQuery( '<div/>', {
                class: "header"
            } ).appendTo( $sourceColummn ) ;


            if ( event.reason ) {
                jQuery( '<span/>', {
                    class: "reason",
                    text: event.reason
                } ).appendTo( $sourceHeader ) ;
            }

            if ( event.host ) {
                jQuery( '<span/>', {
                    class: "flex-right-info host",
                    text: event.host
                } ).appendTo( $sourceHeader ) ;
            }


            if ( event.component ) {
                let trimmed = event.component ;
                if ( trimmed.length > 40 ) {
                    trimmed = trimmed.substr(0,29) + "..." ;
                }
                jQuery( '<span/>', {
                    class: "component",
                    title: event.component,
                    text: trimmed
                } ).appendTo( $sourceColummn ) ;
            }

            let $eventDetailsColumn = jQuery( '<td/>', {
            } ).appendTo( $row ) ;

            jQuery( '<div/>', {
                class: "event-summary",
                text: `${ event.summary }`
            } ).appendTo( $eventDetailsColumn ) ;


            let $details = jQuery( '<div/>', {
                class: "event-details"
            } ).appendTo( $eventDetailsColumn ) ;

            if ( event.kind ) {
                jQuery( '<span/>', {
                    text: event.kind + ": "
                } ).appendTo( $details ) ;
            }

            if ( event.simpleName ) {
                jQuery( '<span/>', {
                    text: event.simpleName
                } ).appendTo( $details ) ;
            }

            if ( event.namespace ) {
                jQuery( '<span/>', {
                    text: `(${ event.namespace })`
                } ).appendTo( $details ) ;
            }


            if ( event.count > 1 ) {
                jQuery( '<span/>', {
                    class: "multiple-instances",
                    text: `${event.count} instances`
                } ).appendTo( $details ) ;
            }

        }


        jQuery( '<option/>', {
            text: `all (${countTotal})`,
            value: "all"
        } ).appendTo( $eventsNamespaceFilter ) ;
        for ( let namespace of utils.keysSortedCaseIgnored( namespaces ) ) {
            let text = `${namespace} ( ${ namespaces[namespace] } )` ;
            jQuery( '<option/>', {
                text: text,
                value: namespace
            } ).appendTo( $eventsNamespaceFilter ) ;
        }

        let countMessage = `${ countShown } Events` ;
        if ( countShown !== countTotal ) {
            countMessage = `${ countShown } of ${ countTotal } Events` ;
        }
        $eventCounts.text( countMessage ) ;

        $( "button", $eventsTable ).off().click( showEvent ) ;

        $eventsTypeFilter.val( lastTypeFilter ) ;
        $eventsKindFilter.val( lastKindFilter ) ;
        $eventsNamespaceFilter.val( lastNamespaceFilter ) ;


    }

    function showEvent() {

        lastSelected = {
            api: $( this ).data( "api" ),
            id: $( this ).data( "id" )
        } ;

        $eventDetailsMenu.show() ;

        utils.launchMenu( "services-tab,kevent-details" ) ;


    }

} ) ;

