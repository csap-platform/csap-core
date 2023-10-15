
import _dom from "../../utils/dom-utils.js";

import utils from "../utils.js"
import fileBrowser from "./file-browser.js"



const projects = projects_loader();

export default projects


function projects_loader() {

    _dom.logHead( "Module loaded" );


    const $projectsEnvironmentTab = $( "#projects-tab-listing" ) ;
    const $projectButtons = $( "a.project", $projectsEnvironmentTab ) ;
    const $projectTabNav = utils.findNavigation( "#projects-tab" ) ;
    const $activeProject = utils.findNavigation( "#active-project" ) ;
    const $environmentSelect = $( "header #environment-select" ) ;
    const $helpSelect = $( "header #portal-help-select" ) ;
    const $environmentTable = $( "#environment-discovery table tbody", $projectsEnvironmentTab ) ;

    let $contentLoaded ;

    let editor = null;
    let latestPackageMap ;

    const navigationOperations = {
        "listing": runEnvironmentReport,
       "code": null,
       "attribute": null,
       "environment": null,
       "editor": null,
        "summary": getSummary,
        "files": fileBrowser.show
    } ;

    return {

        initialize: function () {
            initialize() ;
        },

        activeProjectFunction: function () {
            return getActiveProject ;
        },

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            let menuId = $menuContent.attr( "id" ) ;

            console.log( ` menu selected: ${ menuPath }` ) ;

            let viewRenderedPromise ;

            let operation = navigationOperations[ menuPath ] ;

            if ( operation) {
                viewRenderedPromise = operation( $menuContent, forceHostRefresh, menuPath ) ;

            } else {
                //console.warn( ` no operation for ${ menuPath }` ) ;
                if (!  editor  ) {


                    async function loadModules( resolve, reject ) {
                        const editorLoadedPromise = import( './editor.js' );
    
                        editorLoadedPromise.then( ( module ) => {
                            editor = module.editorPanel;
                        } );
    
                        await editorLoadedPromise ;
                        console.log( `panelsLoaded:`, editor );

                        editor.initialize() ;
                        editor.updatePackageMap( latestPackageMap ) ;

                        await editor.show( $menuContent, forceHostRefresh, menuPath );
    
                        resolve( "panels loaded" );
    
                    }
    
                    viewRenderedPromise = new Promise( loadModules );

                } else {
                    viewRenderedPromise = editor.show( $menuContent, forceHostRefresh, menuPath ) ;
                }
            }

            return viewRenderedPromise ;

        }

    } ;

    function initialize( ) {

        
        _dom.logHead( "Initializing project views" );

        runEnvironmentReport() ;

        if ( $( "option", $activeProject ).length > $( "#minimum-project-count" ).val() ) {
            // All and project name - no point in displaying
            $projectTabNav.addClass( "show-selector" ) ;
        }

        let helpSelected = function () {
            let $selectedOption = $( "option:selected", $helpSelect ) ;
            let targetUrl = $( this ).val() ;
            if ( targetUrl == "Help" ) {
                return ;  // do nothing
            }
            if ( targetUrl === "local login" ) {
                targetUrl = utils.agentUrl( utils.getHostName(), "/login?local=true" ) ;
            }
            utils.launch( targetUrl, $selectedOption.data( "target" ) ) ;
            $helpSelect.val( "Help" ) ;
            $helpSelect.selectmenu("refresh") ;
        }
        //$helpSelect.change( helpSelected ) ;

        let myRenderer = function ( ul, item ) {
            //console.log( `custom render: `, item ) ;
            
            let $helpItem = $( "<li>" ) ;
            
            let customItem = $( "<div>" )
                    .appendTo( $helpItem ) ;

            let itemClasses = item.element.attr( "class" ) ;
            if ( ! itemClasses ) {
                itemClasses = "csap-logs" ;
            }
            if ( itemClasses.includes("hidden") ) {
                $helpItem.addClass( itemClasses ) ;
            }
            let regexHyphen = new RegExp( "-", "g" ) ;
            let label = item.label.replace(regexHyphen, " ") ;
            function toTitleCase( str ) {
               return str.split(/\s+/).map( s => s.charAt( 0 ).toUpperCase() + s.substring(1).toLowerCase() ).join( " " );
            }
            $( "<span>", {
                "class": "csap-link-icon " + itemClasses,
                text: toTitleCase( label )
            } )
                    .prependTo( customItem ) ;

            return $helpItem.appendTo( ul ) ;
        }

		let selMenuWidth = $helpSelect.width + 100 ;
        $helpSelect.selectmenu( {
            //width: "5.4em", autowidth based on detected version
            // position: { my: "right+35 top+12", at: "bottom right" },
            position: { my: "right top", at: "right bottom", of: '#portal-help' },
            change: helpSelected
        } ).data( "ui-selectmenu" )._renderItem = myRenderer ;

        $activeProject.change( function () {
            utils.refreshStatus( true ) ;
            utils.launchMenu( "services-tab,status" ) ;
            //utils.launchMenu( "projects-tab,status" ) ;
            $( "span", $activeProject.parent() ).text( $( this ).val() ) ;
        } ) ;


        $projectButtons.first().addClass( "active" ) ;
        $projectButtons.click( function () {

            $projectButtons.removeClass( "active" ) ;

            let $projectButton = $( this ) ;
            $projectButton.addClass( "active" ) ;

            let projectName = $projectButton.data( "name" ) ;
            console.log( `selected: ${ projectName }` ) ;

            $activeProject
                    .val( projectName )
                    .trigger( "change" ) ;
            return false ;
        } ) ;


        $( '#launch-adoption' ).click( function () {

            let targetUrl = utils.getMetricsUrl() + "../../analytics" ;
            openWindowSafely( targetUrl, "_blank" ) ;
            return false ;
        } ) ;

    }

    function getActiveProject( isAllSupport ) {
        let project = $activeProject.val() ;
        //if ( project == "All Packages") project = $('.releasePackage option').eq(0).val() ;

        if ( !isAllSupport && ( project == "All Packages" ) ) {
            project = $( 'option', $activeProject ).eq( 0 ).val() ;
        }

//        if ( project != "All Packages" && desktopTestProject != "" ) {
//            project = desktopTestProject ;
//        }

        return project ;
    }

    function getSummary( $menuContent, forceHostRefresh, menuPath ) {

        if ( !forceHostRefresh && ( $( "#definition-summary" ).text() != "" ) ) {
            return ;
        }
        $contentLoaded = new $.Deferred() ;

        let parameters = {
            project: getActiveProject( false )
        } ;


        $( "#definition-summary" ).hide() ;
        utils.loading( `Loading summary for ${  getActiveProject( false ) }` ) ;
        //targetUrl + "&callback=?",  
        let summaryUrl = `${ EDIT_URL }/summary` ;
        console.log( `loading: ${summaryUrl}` ) ;


        $.get( summaryUrl,
                parameters,
                function ( lifeDialogHtml ) {
                    let $summaryContent = $( "<html/>" ).html( lifeDialogHtml ).find( '#content-for-browser' ) ;

//                    console.log( ` content: `, $summaryContent[0] ) ;
                    $( "#definition-summary" ).html( $summaryContent ) ;

                    //utils.loadingComplete() ;
                    $( "#definition-summary" ).show() ;

                    $contentLoaded.resolve() ;
                },
                'text' ) ;


        return $contentLoaded ;


    }


    function runEnvironmentReport() {
        $contentLoaded = new $.Deferred() ;

        let requestParms = { } ;

        let curPackage = getActiveProject() ;
        if ( curPackage != "All Packages" ) {
            $.extend( requestParms, {
                project: curPackage
            } ) ;
        }

        //targetUrl + "&callback=?",  
        $.getJSON(
                ACTIVITY_URL,
                requestParms )

                .done( function ( environmentReport ) {
                    buildEnvironmentViews( environmentReport ) ;
                    $contentLoaded.resolve() ;

                    latestPackageMap = environmentReport.packageMap ;
                    if ( editor ) {
                        editor.updatePackageMap( latestPackageMap ) ;
                    }
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "getActivityCount " + errorThrown + " :" + JSON.stringify( jqXHR ) ) ;
                    //handleConnectionError( "Retrieving changes for file " +  $("#logFileSelect").val() , errorThrown ) ;
                } ) ;

        //$('#activityCount').html( loadJson.activityCount + " Events" ) ;


    }

    function buildEnvironmentViews( environmentReport ) {

        // 	console.log( JSON.stringify( countJson ) ) ;

        $( '#event-count' ).html( environmentReport.eventsIn24hours ) ;
        utils.navigationCount( "#event-count", environmentReport.eventsIn24hours, 150, "" ) ;
        console.log( `buildEnvironmentListing(): ${environmentReport.eventsIn24hours} 24 hour events` ) ;


        $environmentTable.empty() ;


        let discovery = environmentReport.discovery ;
        let discoveredCount = 0 ;
        let showAll = $( "#include-all-appids", utils.findContent( "#preferences-tab-content" ) ).is( ":checked" ) ;
        let testAppId = "netMetAppId" ;
        testAppId = null ;
        if ( utils.getAppId() === "yourcompanyCsap" ) {
            showAll = true ;
        }
        if ( discovery ) {

            // remove previous discovery opt groups
            $( ".env-discovered", $environmentSelect ).remove() ;

            let appIds = Object.keys( discovery ) ;
            discoveredCount = appIds.length ;

            if ( discoveredCount >= 1 ) {


                appIds = appIds.sort( function ( a, b ) {
                    return a.toLowerCase().localeCompare( b.toLowerCase() ) ;
                } ) ;
                $environmentSelect.css( "display", "inline" ) ;

                for ( let appId of appIds ) {

                    let addDiscovered = appId === utils.getAppId()
                            || appId === testAppId
                            || showAll ;
                    let discoveryGroupLabel = "Discovered:" ;
                    if ( showAll ) {
                        discoveryGroupLabel = `${ appId }:` ;
                    }
                    let $discoveryGroup ;
                    if ( addDiscovered ) {
                        // separator
                        if ( showAll ) {
                            jQuery( '<optgroup/>', {
                                class: "env-discovered env-spacer",
                                label: "-------------------------------"
                            } ).appendTo( $environmentSelect ) ;

                            $discoveryGroup = jQuery( '<optgroup/>', {
                                class: "env-discovered ",
                                label: discoveryGroupLabel
                            } ).appendTo( $environmentSelect ) ;
                        } else {
                            $discoveryGroup = $( "#discovery-group", $environmentSelect ) ;
                        }
                    }
                    let appIdEnvs = discovery[appId] ;
//                    let $appGroup = jQuery( '<optgroup/>', {
//                        class: "env-discovered env-appid",
//                        label: appId
//                    } ).appendTo( $environmentSelect ) ;

                    let $row = jQuery( '<tr/>', { } ).appendTo( $environmentTable ) ;

                    jQuery( '<td/>', {
                        text: appId
                    } ).appendTo( $row ) ;

                    let $environmentColummn = jQuery( '<td/>', { } ).appendTo( $row ) ;

                    let $envPanel = jQuery( '<div/>', {
                    } ).appendTo( $environmentColummn ) ;


                    //let environments = Object.keys( appIdEnvs ) ;
                    let sortedEnvironments = utils.keysSortedCaseIgnored( appIdEnvs ) ;
                    for ( let environmentName of sortedEnvironments ) {

                        let envUrl = appIdEnvs[environmentName] ;
                        let title = "click to switch enviroments"
                        let label = environmentName ;
                        if ( !envUrl.startsWith( "http" ) ) {
                            title = `environment needs to be updated to latest CSAP release` ;
                            envUrl = utils.agentUrl( envUrl, "/services" ) ;
                            label = `${ environmentName }*` ;
                        } else if ( envUrl.includes( "needToAddYourLbToClusterFile" ) ) {
                            label = `** ${ environmentName } **` ;
                            title = `configured environment loadbalancer is not set` ;
                        }

                        jQuery( '<a/>', {
                            class: "csap-link launch-window",
                            target: "_blank",
                            href: envUrl,
                            title: title,
                            text: label
                        } ).appendTo( $envPanel ) ;

                        if ( addDiscovered ) {
                            jQuery( '<option/>', {
                                value: envUrl,
                                title: title,
                                text: label
                            } ).appendTo( $discoveryGroup ) ;
                        }


                    }
                }
            }
        }

        let lbUrlMap = environmentReport["environment-urls"] ;
        if ( lbUrlMap ) {
            let lifeCycles = utils.keysSortedCaseIgnored( lbUrlMap ) ;

            let $currentEnvironments = $( "#definition-group", $environmentSelect ) ;
            $currentEnvironments.empty() ;
            for ( let environmentName of lifeCycles ) {

                let choice = lbUrlMap[environmentName] ;
                if ( environmentName == HOST_ENVIRONMENT_NAME ) {
                    choice = environmentName ;
                }

                jQuery( '<option/>', {
                    value: choice,
                    text: environmentName
                } ).appendTo( $currentEnvironments ) ;

            }

            if ( ( lifeCycles.length ) > 1 || ( discoveredCount >= 1 ) ) {
                $( "#environment-name" ).css( "opacity", "1.0" ) ;
            }
        } else {
            console.log( `buildEnvironmentListing - no environments found` ) ;
        }
        $environmentSelect
                .off()
                .change( applicationViewChange )
                .val( utils.getEnvironment() ) ;


//        }

    }

    function applicationViewChange() {
        // document.location.href = $( this ).val() ;
        utils.launch( $( this ).val() ) ;

        // reset back to current env
        $environmentSelect.val( utils.getEnvironment() ) ;
    }

} 