define( [ "browser/utils" ], function ( utils ) {

    console.log( "Module loaded" ) ;
    let $contentLoaded = null ;

    const $performanceTab = $( "#performance-tab-content" ) ;

    const $eventDetailsMenu = $( ".event-details-selected", "#performance-tab" ) ;

    const $eventsContent = $( "#performance-tab-events", $performanceTab ) ;
    const $eventsPanel = $( "#events-panel", $eventsContent ) ;
    const $eventsTableBody = $( "tbody", $eventsContent ) ;

    const $eventsFilter = $( "#event-filter", $eventsContent ) ;
    
    const $dateControls = $("#date-controls", $eventsContent) ;
    const $dateFrom = $("#from", $dateControls) ;
    const $dateTo = $("#to", $dateControls) ;
    const $category = $( "#event-category", $eventsContent ) ;
    const $count = $( "#csap-event-count", $eventsContent ) ;
    
    let _eventFilterTimer ;
    let lastSelected ;

    let applyFilterFunction ;


    return {

        initialize: function () {
            initialize() ;
        },

        show: function ( $displayContainer, forceHostRefresh ) {
            $contentLoaded = new $.Deferred() ;
            
            if ( forceHostRefresh ) {
                $dateFrom.val("") ;
                $dateTo.val("") ;
                $category.val( "/csap/ui/*" ) ;
            }
            getEvents() ;


            return $contentLoaded ;
        },

        selected: function () {
            return lastSelected ;
        },

        closeDetails: function () {
            utils.launchMenu( "performance-tab,events" ) ;
            $eventDetailsMenu.hide() ;
        }
    } ;



    function initialize() {


        $( '#user-events', $eventsContent ).click( function () {
            console.log( "contents", $( '#activityCount' ).text() ) ;
            if ( $( '#activityCount' ).text().contains( "disabled" ) ) {
                alertify.csapWarning( "csap-events-service is currently disabled in your Application.json definition" ) ;
                return false ;
            }

            let targetUrl = HISTORY_URL ;
            let curPackage = utils.getActiveProject() ;
            if ( curPackage != "All Packages" ) {
                targetUrl += "&project=" + curPackage ;
            }
            openWindowSafely( targetUrl, "_blank" ) ;
            return false ;
        } ) ;

        let alwaysShowFunction = function() {
             $( `td.event-date-label`, $eventsTableBody ).parent().show() ;
        }
        applyFilterFunction = utils.addTableFilter( $eventsFilter, $eventsTableBody.parent(), alwaysShowFunction ) ;


        $( "#event-limit", $eventsContent ).change( getEvents ) ;
        $( "#event-refresh", $eventsContent ).click( getEvents ) ;
        $dateFrom.change( getEvents ) ;
        $dateTo.change( getEvents ) ;
        $category.change( getEvents ) ;


        $( "#event-category-combo", $eventsContent ).change( function () {
           $category.val( $( this ).val() ) ;
            getEvents() ;
        } ) ;
        
        $dateFrom.datepicker( {
            defaultDate: "+0w",
            changeMonth: true,
            numberOfMonths: 1,
            onClose: function ( selectedDate ) {
                var toDateVal = $dateTo.val() ;
                if ( toDateVal === '' ) {
                    $dateTo.val( selectedDate ) ;
                }
                $dateTo.datepicker( "option", "minDate", selectedDate ) ;
                searchSetup() ;
            }
        } ) ;
        $dateTo.datepicker( {
            defaultDate: "+0w",
            changeMonth: true,
            numberOfMonths: 1,
            onClose: function ( selectedDate ) {
                var fromDateVal = $dateFrom.val() ;
                if ( fromDateVal === '' ) {
                    $dateFrom.val( selectedDate ) ;
                }
                $dateFrom.datepicker( "option", "maxDate", selectedDate ) ;
                searchSetup() ;
            }
        } ) ;

    }

    function getEvents() {

        utils.loading( `Loading Events` ) ;


        let parameters = {
            project: utils.getActiveProject(),
            count: $( "#event-limit", $eventsContent ).val(),
            category: $category.val(),
            from: $dateFrom.val(),
            to: $dateTo.val(),
        }

        $.getJSON(
                APP_BROWSER_URL + "/events/csap",
                parameters )

                .done( function ( eventsReport ) {

                    $contentLoaded.resolve() ;
                    utils.loadingComplete() ;
                    if ( !eventsReport.events ) {
                        $eventsTableBody.empty() ;
                        alertify.csapInfo( "No Events found" ) ;
                        return ;
                    }
                    console.log( `getEvents() : eventsReport: ${eventsReport.events.length}` ) ;

                    showEvents( eventsReport.events ) ;

                    applyFilterFunction( ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( `retrieving alerts '${textStatus}'`, errorThrown ) ;
                } ) ;
    }

    function showEvents( events ) {

        $eventsTableBody.empty() ;

        let lastDate = null ;
        
        let eventCount=0;

        for ( let event of events ) {
            eventCount ++ ;

            let eventDate = event.date ;
            if ( eventDate != lastDate ) {
                let $labelRow = jQuery( '<tr/>', { } )
                        .appendTo( $eventsTableBody ) ;

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
                    .appendTo( $eventsTableBody ) ;
            let $dateColumn = jQuery( '<td/>', { } ).appendTo( $row ) ;

            let $dateTime = jQuery( '<div/>', { class: "date-time" } )
                    .appendTo( $dateColumn ) ;

            let $date = jQuery( '<div/>', {
                class: "event-date",
                text: `${ eventDate }`
            } ) ;

            var $timeButton = jQuery( '<button/>', {
                "data-id": event.id,
                "data-self": event.selfLink,
                title: "View Event details",
                class: "csap-button event-time",
                text: event.time
            } ) ;


            $dateTime.append( $timeButton ) ;
            $dateTime.append( $date ) ;


            jQuery( '<td/>', {
                text: `${ event.host }`
            } ).appendTo( $row ) ;

            let $eventDetailsColumn = jQuery( '<td/>', {
            } ).appendTo( $row ) ;

            jQuery( '<div/>', {
                class: "event-summary",
                text: `${ event.summary }`
            } ).appendTo( $eventDetailsColumn ) ;


            let $details = jQuery( '<div/>', {
                class: "event-details"
            } ).appendTo( $eventDetailsColumn ) ;


            jQuery( '<span/>', {
                text: event.category
            } ).appendTo( $details ) ;

            let user = event.user ;
            if ( !user ) {
                user = "" ;
            }
            jQuery( '<span/>', {
                text: user
            } ).appendTo( $details ) ;


//            let fields = Object.keys( event ) ;
//            for ( let field of fields ) {
//                jQuery( '<span/>', {
//                    text: event[ field]
//                } ).appendTo( $eventsPanel ) ;
//            }

        }
        
        $count.text(`events: ${ eventCount }`) ;

        $( "button", $eventsTableBody ).off().click( showEvent ) ;


    }

    function showEvent() {

        lastSelected = {
            self: $( this ).data( "self" ),
            id: $( this ).data( "id" )
        } ;

        $eventDetailsMenu.show() ;

        utils.launchMenu( "performance-tab,event-details" ) ;


    }

} ) ;

