

import _dom from "../utils/dom-utils.js";
import jsonForms from "../editor/json-forms.js";


const utils = browser_utils();

export default utils

function browser_utils() {
    
    _dom.logHead( "Module loaded" );
    
    const $browser = $( "body#manager" );
    const $betweenHeaderAndFooter = $( ">section", $browser );
    const $uiTemplates = $( ">aside", $browser );
    const $navigation = $( "article.navigation", $betweenHeaderAndFooter );
    const $activeContent = $( "article.content", $betweenHeaderAndFooter );
    const $hiddenContent = $( "aside", $browser ).first();
    //    console.log( `$content size: ${ $content.length }` ) ;

    const $hostServiceNames = $( "#host-service-names", $navigation );
    let defaultService;
    let $loadingMessage = $( "#loading-project-message" );
    const UNREGISTERED = "unregistered";

    let pageParameters;

    let navigationChangeFunctions = new Array();
    const $preferences = $( "#preferences-tab-content" );
    const $disableLogFormat = $( "#disable-log-format", $preferences );
    const $useCsapIdForScm = $( "#use-csap-id-for-scm", $preferences );


    let refreshStatusFunction = null;
    let launchMenuFunction = null;
    let editServiceFunction = null;

    let activeProjectFunction = null;

    let browseServiceFunction = null;

    let logChoice = null;

    let _statusReport = "not-found";

    let _previousLocation;

    let _refreshInterval = 30 * 1000;

    let aceDefaults = {
        tabSize: 2,
        useSoftTabs: true,
        newLineMode: "unix",
        theme: "ace/theme/chrome", //kuroir  Xcode tomorrow, tomorrow_night, dracula, crimson_editor
        printMargin: false,
        fontSize: "11pt",
        //        enableLinking: true,
        wrap: true
    };


    let defaultLogParsers = [
        {
            match: "netmet-field8Colon",
            about: "checks 10 fields:  if field 8 is a colon",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 4, "message:", 10, "\n" ],
            newLineWords: []
        },

        {
            match: "netmet-field6Dash",
            about: "checks 10 fields: if field 6 is ---",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 7, 8, 9, "message:", 10, "\n" ],
            newLineWords: []
        },

        {
            match: "cyourapp-field6Colon",
            about: "checks 10 fields: if field 6 is a colon",
            columns: [ "   # level: ", 4, "time:", 1, 2, "source:", 3, 5, "message:", 7, 8, 9, 10, "\n" ],
            newLineWords: []
        },

        {
            match: "cyourapp-field5Dash",
            about: "checks 10 fields: if field 5 is ---",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 6, 7, "message:", 8, 9, 10, "\n" ],
            newLineWords: []
        },

        {
            match: "cyourapp-field9Tilde",
            about: "checks 10 fields: if field 9 is |~",
            columns: [ "   # level: ", 6, "time:", 1, 2, "source:", 8, "message:", 10, "\n\n" ],
            newLineWords: []
        },

        {
            match: "etcd",
            columns: [ "   # level: ", 3, "time:", 1, 2, "message:", 6, "\n" ],
            throttleWords: [ "health OK" ],
            newLineWords: []
        },
        {
            match: "nfs-client-provisioner",
            columns: [ "   # level: ", 1, "time:", 2, "source:", 4, "message:", 5, "\n" ],
            throttleWords: [ "health OK" ],
            newLineWords: []
        },
        {
            match: "kube-apiserver",
            columns: [ "   # level: ", 1, "time:", 2, "source:", 5, "message:", 6, "\n" ],
            throttleWords: [ "health OK" ],
            newLineWords: []
        },
        {
            match: "fluentd",
            columns: [ "   # level: ", 4, "time:", 1, " ", 2, "source:", 6, "message:", 7, "\n" ],
            throttleWords: [ "features are not enabled", "stats -" ],
            newLineWords: []
        },
        {
            match: "httpd",
            columns: [ "   # level: ", 6, "time:", 1, 2, 3, 4, 5, "message:", 10, "\n" ],
            newLineWords: []
        },
        {
            match: "calico",
            columns: [ "   # level: ", 3, "time:", 1, " ", 2, "message:", 6, "\n" ],
            newLineWords: []
        },
        {
            match: "docker$",
            columns: [ "   # ", 7, "date:", 1, 2, 3, "message:", 8, "\n" ],
            newLineWords: [ "error:" ]
        },
        {
            match: "yourapp-mgmt-mongo",
            columns: [ "   # level: ", 2, "time:", 1, "source:", 3, 4, "message:", 5, "\n" ],
            throttleWords: [ "end connection", "connection accepted", "Successfully authenticated", "received client metadata" ],
            newLineWords: [ "command:", "error:", "pipeline:" ]
        },
        {
            match: "netmet-mongo",
            columns: [ "   # level: ", 2, "time:", 1, "source:", 3, 4, "message:", 5, "\n" ],
            throttleWords: [ "end connection", "connection accepted", "Successfully authenticated", "received client metadata" ],
            newLineWords: [ "command:", "error:", "pipeline:" ]
        },
        {
            match: "keycloak",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 4, "message:", 6, "\n\n" ],
            trimAnsi: true,
            newLineWords: []
        },
        {
            match: "kafka",
            columns: [ "   # level: ", 3, "time:", 1, 2, "source:", 4, 5, "message:", 6, "\n" ],
            newLineWords: [ "(kafka", "(topicName", "(org.apache" ]
        },
        {
            match: "zookeeper",
            columns: [ "   # level: ", 3, "time:", 1, 2, "message:", 4, "\n" ],
            newLineWords: [ "LOOKING", "FOLLOWING", "(org.apache" ]
        },
        {
            match: "gitea",
            columns: [ "   #", "time:", 2, 3, "source:", 1, "message:", 4, "\n\n" ],
            trimAnsi: true
        },
        {
            match: "artifactory",
            columns: [ "   # level: ", 4, 5, "time:", 1, 2, "source:", 3, "message:", 8, "\n" ],
            newLineWords: [ "file:", "item:", "deployed:", "properties:", "repo:", "deleted:" ]
        }
    ];

    let applicationParsers = defaultLogParsers;

    let _statusFilterTimer;


    return {

        initialize: function ( refreshFunction, _launchMenuFunction, _activeProjectFunction ) {
            refreshStatusFunction = refreshFunction;
            launchMenuFunction = _launchMenuFunction;
            activeProjectFunction = _activeProjectFunction;
            jsonForms.setAceDefaults( aceDefaults );


            // add class=sorter-csap-sort-value to th, and then add data-sortvalue="xx" to sortcell
            $.tablesorter.addParser( {
                // set a unique id
                id: 'csap-sort-value',
                is: function ( s, table, cell, $cell ) {
                    // return false so this parser is not auto detected
                    return false;
                },
                format: function ( s, table, cell, cellIndex ) {
                    let $cell = $( cell );
                    let valueToSort = $cell.data( 'sortvalue' );
                    if ( valueToSort === undefined ) {
                        //console.log( ` missing sortvalue: ${valueToSort} in ${ $(table).attr("id")  }`);
                        valueToSort = "";
                    }

                    //                    if ( $cell.closest( `table`).attr(`id`) === `processTable`) {
                    //                        
                    //                        console.log( ` valueToSort: ${valueToSort}` ) ;
                    //                    }
                    // format your data for normalization
                    return valueToSort;
                },
                // set type, either numeric or text
                // text works in all cases - numbers or text - but a little slower
                type: 'text'
            } );

        },

        isLogAutoFormatDisabled: function () {
            console.log( ` $disableLogFormat: ${ $disableLogFormat.length }` );
            return $disableLogFormat.is( ":checked" );
        },

        stringSplitWithRemainder: function ( stringToBeSplit, separator, limit ) {
            // single space everything first
            stringToBeSplit = stringToBeSplit.trim().replace( /  +/g, ' ' );
            stringToBeSplit = stringToBeSplit.split( separator );

            if ( stringToBeSplit.length > limit ) {
                let stringPieces = stringToBeSplit.splice( 0, limit - 1 );
                stringPieces.push( stringToBeSplit.join( " " ) );

                return stringPieces;
            }

            return stringToBeSplit;
        },

        addTableFilter: function ( $filterInput, $tableContainer, alwaysShowFunction ) {
            return addTableFilter( $filterInput, $tableContainer, alwaysShowFunction );
        },

        agentServiceSelect: function () {
            return "default";
        },

        bytesFriendlyDisplay: function ( numBytes ) {
            return bytesFriendlyDisplay( numBytes );
        },

        buildMemoryCell: function ( memoryInBytes ) {

            let displayValue = "";
            if ( memoryInBytes !== 0
                && !isNaN( memoryInBytes ) ) {
                displayValue = bytesFriendlyDisplay( memoryInBytes );
            }
            let $cell = jQuery( '<td/>', {
                class: "numeric",
                //                "data-sortvalue": memoryInBytes,
                text: displayValue
                //                text:  memoryInBytes
            } );

            if ( displayValue !== "" ) {
                $cell.data( "sortvalue", memoryInBytes );
            }

            return $cell;
        },

        toDecimals: function ( original, decimals = 2 ) {
            let result = original;

            let number = Number( original );
            if ( isFloat( number ) ) {
                result = number.toFixed( decimals );
            }

            return result;
        },

        closeAllDialogs: function () {
            jsonForms.closeDialog();
            alertify.closeAll();
        },

        registerForNavChanges: function ( callbackFunction ) {
            console.log( `registerForNavChanges`, callbackFunction );
            navigationChangeFunctions.push( callbackFunction );

        },

        getNavChangeFunctions: function () {
            return navigationChangeFunctions;

        },

        getAceDefaults: function ( mode, readOnly ) {
            let settings = Object.assign( {}, aceDefaults );
            if ( mode ) {
                settings.mode = mode;
            }
            if ( readOnly ) {
                settings.readOnly = true;
            }

            return settings;
        },

        yamlSpaces: function ( yamlText, keyWords, spaceTopLevel ) {
            for ( let keyWord of keyWords ) {

                let re = new RegExp( `^[\s]*${ keyWord }:$`, "gm" );
                //console.log( `re: '${ re }'`) ;
                yamlText = yamlText.replace( re, `\n\n#\n#\n#\n$&` );
            }

            // 2nd level members gets spaced
            if ( spaceTopLevel ) {
                //alert("gotted")
                let serviceMatches = new RegExp( `^([a-z]|[A-Z]|-)*:.*$`, "gm" );
                yamlText = yamlText.replace( serviceMatches, `\n$&` );
            }
            //        
            let objectMatches = new RegExp( `^ ([a-z]|[A-Z]|-| )*:$`, "gm" );
            yamlText = yamlText.replace( objectMatches, `\n$&` );

            //        
            //            let nameValMatches = new RegExp( `^(?!.*http) ([a-z]|[A-Z]|-| )*:([a-z]|[A-Z]|-| |.|[0-9])*$`, "gm" ) ;
            let nameValMatches = new RegExp( `^([a-z]|[A-Z]|-| )*: ([a-z]|[A-Z]|-| |.|[0-9])*$`, "gm" );
            yamlText = yamlText.replace( nameValMatches, `\n$&` );

            //        
            //            let secondLevelMatches = new RegExp( `^  (.*):$`, "gm" ) ;
            //            yamlText = yamlText.replace( secondLevelMatches, `\n  $1:` ) ;
            //            let secondLevelMatches = new RegExp( `^  ([a-z]|[A-Z]|-)*:.*$`, "gm" ) ;
            //            yamlText = yamlText.replace( secondLevelMatches, `\n$&` ) ;

            return yamlText;
        },

        markAceYamlErrors: function ( aceSession, e ) {
            let lineNumber = jsonForms.getValue( "mark.line", e );
            if ( !lineNumber ) {
                lineNumber = 0;
            }
            console.debug( ` line: ${ lineNumber }, message: ${ e.message }` );
            aceSession.setAnnotations( [ {
                row: lineNumber,
                column: 0,
                text: e.message, // Or the Json reply from the parser 
                type: "error" // also "warning" and "information"
            } ] );
        },

        generalErrorHandler: function ( jqXHR, textStatus, errorThrown ) {

            // $mainLoading.hide();
            console.log( `Failed command:  ${ textStatus }`, jqXHR, `\n errorThrown: `, errorThrown );

            let messageTitle = `Failed Operation ${ jqXHR.status }: ${ jqXHR.statusText }`;
            let messageContent = "Contact your administrator";
            if ( jqXHR.status === 403 ) {
                messageContent += ` - permissions failure`;
            } else if ( jqXHR.status === 0 ) {
                // only known instance: saveing file to large to be stored
                messageContent += ` - failed to submit - request may be too large, or server may be down for maintenance.`;
            }

            let fullMessage = `<label class=csap>${ messageTitle }</label>` + `<br/><br/>` + messageContent;

            if ( jqXHR && jqXHR.responseText ) {
                fullMessage += `<br/><br/>Details:<br/> <div class=extra-info>` + jqXHR.responseText + `</div>`;
            }
            alertify.csapWarning( fullMessage );

            _lastError = jqXHR;
        },

        updateAceDefaults: function ( wrap, theme, fontSize ) {
            aceDefaults.wrap = wrap;
            aceDefaults.theme = theme;
            aceDefaults.fontSize = fontSize;

            jsonForms.setAceDefaults( aceDefaults );
        },

        setRefreshInterval: function ( seconds ) {
            _refreshInterval = seconds * 1000;
        },

        getRefreshInterval: function () {
            return _refreshInterval;
        },

        setLastLocation( previousLocation ) {
            _previousLocation = previousLocation;
        },

        getLastLocation() {
            return _previousLocation;
        },

        jqplotLegendOffset: function () {
            // css: jqplot legend width + 30
            return 180;
        },

        openAgentWindow: function ( host, path, parameterMap ) {
            let targetUrl = agentUrl( host, path );
            window.open( buildGetUrl( targetUrl, parameterMap ), '_blank' );
        },

        buildGetUrl: function ( url, parameterMap ) {
            return buildGetUrl( url, parameterMap );
        },

        agentUrl: function ( targetHost, command ) {
            return agentUrl( targetHost, command );
        },

        buildAgentLink: function ( targetHost, command = "/app-browser", linkText, parameters ) {

            if ( typeof linkText === 'undefined' ) {
                linkText = getHostShortName( targetHost );
            }
            return buildAgentLink( targetHost, command, linkText, parameters );
        },

        getHostShortName: function ( targetHost ) {
            return getHostShortName( targetHost );
        },

        isUnregistered: function ( serviceName ) {
            return UNREGISTERED === serviceName;
        },

        isAgent: function () {
            return AGENT_MODE;
        },

        getErrorIndicator() {
            return "__ERROR:";
        },

        getWarningIndicator() {
            return "__WARN:";
        },

        setPageParameters: function ( parameters ) {
            if ( parameters ) {
                pageParameters = new URLSearchParams( parameters );
            } else {
                console.debug( `setting page params from: '${ document.location.href }'` )
                pageParameters = ( new URL( document.location ) ).searchParams;
            }
            if ( pageParameters.has( "defaultService" ) ) {
                defaultService = pageParameters.get( "defaultService" );
                pageParameters.delete( "defaultService" );
                $hostServiceNames.empty();
                jQuery( '<option/>', {
                    text: defaultService
                } ).appendTo( $hostServiceNames );
                $( "span", $hostServiceNames.parent() ).text( defaultService );
            }
        },
        getDefaultService: function () {
            return defaultService;
        },

        getServiceSelector: function () {
            return $hostServiceNames;
        },

        getSelectedService: function () {

            if ( $hostServiceNames.length > 0 ) {
                return $hostServiceNames.val();
            }
            return null;
        },

        getSelectedServiceIdName: function () {

            if ( $hostServiceNames.length > 0 ) {
                console.log( `$hostServiceNames.length: ${ $hostServiceNames.length }`, $hostServiceNames.text() );
                let $selectedOption = $hostServiceNames.find( ':selected' );
                if ( $selectedOption.data( "service" ) ) {

                    console.log( "using service mapping " );
                    return $selectedOption.data( "service" );

                }
            }
            return null;
        },

        /**
         * 
         * @returns { URLSearchParams }
         */
        getPageParameters: function () {
            if ( !pageParameters ) {
                console.log( `\n\n ***** loading page parameters ******\n\n` );
                pageParameters = ( new URL( document.location ) ).searchParams;
            }
            //https://developer.mozilla.org/en-US/docs/Web/API/URLSearchParams
            return pageParameters;
        },

        setStatusReport: function ( statusReport ) {
            _statusReport = statusReport;
        },

        getMasterHost: function () {
            return _statusReport[ "kubernetes-master" ];
        },

        getEnvironmentHostCount: function () {
            return _statusReport[ "hosts-all-projects" ];
        },

        //        setMasterHost: function ( hostName ) {
        //            masterHost = hostName ;
        //        },

        getCsapUser: function () {
            return CSAP_USER;
        },

        getScmUser: function () {
            let user = CSAP_SCM_USER;

            if ( $useCsapIdForScm.is( ":checked" ) ) {
                user = CSAP_USER;
            }
            return user;
        },

        getAppId: function () {
            return APP_ID;
        },

        getHostName: function () {
            return CSAP_HOST_NAME;
        },

        getHostFqdn: function () {
            let host = CSAP_HOST_NAME;

            try {
                let url = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, CSAP_HOST_NAME );
                let thost = url.substring( url.indexOf( "//" ) + 2 );
                thost = thost.split( ":" )[ 0 ];
                host = thost;
            } catch ( e ) {
                console.log( "Failed to build host", e );
            }

            return host;
        },

        getEnvironment: function () {
            return HOST_ENVIRONMENT_NAME;
        },

        // legacy
        getActiveEnvironment: function () {
            return HOST_ENVIRONMENT_NAME;
            //return  $environmentSelect.val() ;
        },

        getExplorerUrl: function () {
            return EXPLORER_URL;
        },

        getCsapBrowserUrl: function () {
            return APP_BROWSER_URL;
        },

        getOsExplorerUrl: function () {
            return OS_EXPLORER_URL;
        },

        getMetricsUrl: function () {
            return METRICS_URL;
        },

        getAnalyticsUrl: function () {
            return ANALYTICS_URL;
        },

        getTrendUrl: function () {
            return TREND_URL;
        },

        getFileUrl: function () {
            return FILE_URL;
        },

        getOsUrl: function () {
            return OS_URL;
        },

        confirmDialog( title, okFunction, okLabel = "ok", message = "proceed or cancel", cancelFunction, cancelLabel = "cancel" ) {

            if ( !cancelFunction ) {
                cancelFunction = function () {
                    console.log( `${ title } cancelled` );
                }
            }

            let confirmDialog = alertify.confirm(
                title,
                `<div class="quote">${ message }</div>`,
                okFunction,
                cancelFunction
            );


            if ( confirmDialog ) {
                confirmDialog.setting( {
                    'labels': {
                        ok: okLabel,
                        cancel: cancelLabel
                    }
                } );
            }
        },

        getParameterByName: function ( name ) {
            return getParameterByName( name );
        },

        adminHost: function () {
            return $( "#admin-host" ).text();
        },

        findContent: function ( selector ) {

            let $resolved = $( selector, $hiddenContent );
            if ( $resolved.length == 0 ) {
                // handle deferred loading cases
                $resolved = $( selector, $activeContent );
            }

            //console.log( `findContent $content size: ${ $content.length }, selector: ${ selector }, size: ${ $resolved.length}` ) ;
            return $resolved;
        },

        findNavigation: function ( selector ) {
            return findNavigation( selector );
        },

        navigationCount: function ( navSelector, count, alertCount, suffix = "" ) {

            let $itemSource = findNavigation( navSelector );
            //console.log( "navigationCount", $itemSource) ;
            let currentVal = $itemSource.text();


            $itemSource.text( count + suffix );
            $itemSource.removeClass( "up down" )


            if ( currentVal === "disabled" ) {
                flash( $itemSource, true );
                return $itemSource;
            }

            if ( currentVal != 0 ) {
                if ( count > currentVal ) {
                    $itemSource.addClass( "up" );
                } else if ( count < currentVal ) {
                    $itemSource.addClass( "down" );
                }
            }
            let active = false;
            if ( count > alertCount ) {
                active = true;
            }
            flash( $itemSource, active );

            return $itemSource;
        },

        launchService: function ( serviceName, servicePath ) {
            $.getJSON( `${ APP_BROWSER_URL }/launch/${ serviceName }`, null )
                .done( function ( launchReport ) {
                    console.log( `launchReport`, launchReport )
                    if ( launchReport.location ) {

                        let targetUrl = launchReport.location;

                        if ( serviceName == "csap-analytics" ) {
                            targetUrl += "&project=" + activeProjectFunction( false ) + "&appId=" + APP_ID;
                        }
                        if ( servicePath ) {
                            if ( targetUrl.endsWith( "/" ) && servicePath.startsWith( "/" ) ) {

                                if ( servicePath.length == 1 ) {
                                    servicePath = "";
                                } else {
                                    servicePath = servicePath.substring( 1 );
                                }
                            }
                            targetUrl += servicePath;
                        }
                        launch( targetUrl );
                    } else {
                        alertify.csapInfo( `Unable to launch:<div class=csap-red> ${ launchReport.reason }</div>` );
                    }
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Error: Retrieving meter definitions ", errorThrown )
                    // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
                } );
        },

        launchFiles: function ( parameters ) {
            console.log( `Updating: `, parameters );
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }
            }

            launchMenuFunction( "agent-tab,file-browser" );
        },

        launchLogs: function ( parameters ) {
            console.log( `Updating: `, parameters );
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }
            }

            launchMenuFunction( "agent-tab,logs" );
        },

        launchScript: function ( parameters ) {

            console.log( `launchScript: `, parameters );
            if ( parameters ) {
                for ( let name in parameters ) {
                    pageParameters.delete( name );
                    pageParameters.append( name, parameters[ name ] );
                }

                pageParameters.append( "scriptRefresh", "true" );
            }

            launchMenuFunction( "agent-tab,script" );

            //findNavigation(".command-runner") ;
            let $menuMatch = menuMatch( "script", "agent-tab" );
            $menuMatch.effect( "pulsate", { times: 2 }, 2000 );


        },

        menuMatch: function ( path, tab ) {
            return menuMatch( path, tab );
        },

        launchMenu: function ( tabCommaMenu ) {
            launchMenuFunction( tabCommaMenu );
        },

        setServiceEditorFunction: function ( theFunction ) {
            editServiceFunction = theFunction;
        },

        launchServiceLogs: function ( logFileName ) {
            logChoice = null;
            if ( !$( "#disable-auto-show-logs" ).is( ":checked" ) ) {
                logChoice = logFileName;
                launchMenuFunction( "services-tab,logs" );
            }
        },

        isLaunchServiceLogs: function () {
            return !$( "#disable-auto-show-logs" ).is( ":checked" );
        },

        setLogChoice: function ( logFileName ) {
            logChoice = logFileName;
        },

        getLogChoice: function () {
            return logChoice;
        },

        getLogParsers: function () {


            // lazy loading of parser definitions. Initial load will return defaults;
            // subsequent load will use application defined - if specified

            $.getJSON( `${ APP_BROWSER_URL }/logParsers`, null ).done( function ( customParsers ) {

                if ( Array.isArray( customParsers )
                    && customParsers.length > 0 ) {
                    applicationParsers = customParsers;
                }

            } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Error: Retrieving meter definitions ", errorThrown )
                    // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
                } );

            console.log( `applicationParsers: `, applicationParsers );
            return applicationParsers;

        },

        resetLogChoice: function () {
            logChoice = null;
        },

        launchServiceEditor: function ( serviceName ) {
            launchMenuFunction( "projects-tab,environment" );
            editService( serviceName );
        },

        launchServiceResources: function ( serviceName ) {
            launchMenuFunction( "projects-tab,files" );
            browseServiceFiles( serviceName );
        },

        setBrowseServiceFunction( _theFunction ) {
            browseServiceFunction = _theFunction;
        },

        launchServiceHistory: function ( serviceName ) {

            $( "#event-category" ).val( `/csap/ui/service/${ serviceName }*` );
            launchMenuFunction( "performance-tab,events" );

        },

        refreshStatus: function ( isBlocking ) {
            return refreshStatusFunction( isBlocking );
        },

        json: function ( dotPath, theObject ) {
            return jsonForms.getValue( dotPath, theObject );
        },

        loading: function ( message ) {
            if ( $loadingMessage.length == 0 ) {
                $loadingMessage = jQuery( '<article/>', {
                    id: "loading-project-message"
                } );

                jQuery( '<div/>', {
                    class: "loading-message-large",
                    text: "loading application"
                } ).appendTo( $loadingMessage );
                $( "body" ).append( $loadingMessage );
            }

            if ( message ) {
                $( "div", $loadingMessage ).html( message );
            }
            $loadingMessage.show();
        },

        loadingComplete: function ( source ) {

            if ( $loadingMessage.is( ":visible" ) ) {
                console.log( `loadingComplete - hiding message: ${ source } ` );
            }
            $loadingMessage.hide();
        },

        getActiveProject: function ( isAllSupport = true ) {
            return activeProjectFunction( isAllSupport );
        },

        flash: function ( $item, flashOn = true, count ) {
            flash( $item, flashOn, count );
        },

        isObject: function ( theReference ) {
            return isObject( theReference );
        },

        // sample using object attribute: let sortedInstances = ( instanceReport.instances ).sort( ( a, b ) => ( a.host > b.host ) ? 1 : -1 ) ;
        keysSortedCaseIgnored: function ( theObject ) {

            let theArray = Object.keys( theObject );
            theArray.sort( function ( a, b ) {
                return a.toLowerCase().localeCompare( b.toLowerCase() );
            } );
            return theArray;
        },

        launch: function ( theUrl, frameTarget ) {
            launch( theUrl, frameTarget )
        },

        buildHostsParameter: function ( $rows ) {
            return buildHostsParameter( $rows );
        },

        instanceRows: function () {
            return instanceRows();
        },

        selectedInstanceRows: function () {
            return selectedInstanceRows();
        },

        isSelectedKubernetes: function () {
            let $row = instanceRows().first();
            let clusterType = $row.data( "clusterType" );
            let isKubernetes = ( clusterType === "kubernetes" );
            return isKubernetes;
        },

        selectedKubernetesPod: function () {

            let $row = selectedInstanceRows().first();
            if ( $row.length === 0 ) {
                $row = instanceRows().first();
            }
            let name = $row.data( "service" );
            let containerIndex = $row.data( "container-index" );

            let pod = `${ name }-${ containerIndex + 1 }`;
            console.log( `selectedKubernetesPod() ${ pod } ` );
            return pod;
        },

        disableButtons: function ( ...$items ) {
            $items.forEach(
                $item => {
                    $item.prop( 'disabled', true );
                    if ( $item.parent().is( "label" ) ) {
                        $item.parent().css( "opacity", "0.25" );
                    } else {
                        $item.css( "opacity", "0.25" )
                    }
                }
            );

        },

        enableButtons: function ( ...$items ) {

            $items.forEach(
                $item => {
                    $item.prop( 'disabled', false );
                    if ( $item.parent().is( "label" ) ) {
                        $item.parent().css( "opacity", "1.0" );
                    } else {
                        $item.css( "opacity", "1.0" );
                    }
                }
            );
            //            $items.forEach(
            //                    $button => $button.prop( 'disabled', false ).css( "opacity", "1.0" )
            //            ) ;
        },

        buildValidDomId: function ( inputName ) {

            let regexPeriod = new RegExp( "\\.", "g" );
            let regexComma = new RegExp( "\\,", "g" );
            let regexLeftParen = new RegExp( "\\(", "g" );
            let regexRightParen = new RegExp( "\\)", "g" );
            let regexUnderscore = new RegExp( "_", "g" );
            let regexColon = new RegExp( ":", "g" );
            let regexSpace = new RegExp( " ", "g" );

            let updatedName = inputName.replace( regexPeriod, "-" );
            updatedName = updatedName.replace( regexComma, "-" );
            updatedName = updatedName.replace( regexLeftParen, "" );
            updatedName = updatedName.replace( regexRightParen, "" );
            updatedName = updatedName.replace( regexUnderscore, "-" );
            updatedName = updatedName.replace( regexColon, "-" );
            updatedName = updatedName.replace( regexSpace, "" );
            return updatedName;
        },

        getClusterImage: function ( clusterName, firstServiceType ) {
            return getClusterImage( clusterName, firstServiceType );
        },

        splitWithTail: function ( str, delim, count ) {
            let parts = str.split( delim );
            let tail = parts.slice( count ).join( delim );
            let result = parts.slice( 0, count );
            result.push( tail );
            return result;
        }



    };

    function findNavigation( selector ) {

        //console.log(`looking for ${ selector } in ${ $navigation.attr("class") }`, selector) ;
        if ( selector )
            return $( selector, $navigation );

        return $navigation;
    }

    function getParameterByName( name ) {
        name = name.replace( /[\[]/, "\\\[" ).replace( /[\]]/, "\\\]" );
        let regexS = "[\\?&]" + name + "=([^&#]*)",
            regex = new RegExp( regexS ),
            results = regex.exec( window.location.href );
        if ( results == null ) {
            return "";
        } else {
            return decodeURIComponent( results[ 1 ].replace( /\+/g, " " ) );
        }
    }

    function browseServiceFiles( serviceName ) {

        setTimeout( function () {

            // lazy initialized
            if ( !browseServiceFunction ) {
                console.log( "waiting for browser" );
                browseServiceFiles( serviceName );
                return;
            }
            browseServiceFunction( serviceName );
        }, 1000 );
    }

    function editService( serviceName ) {
        setTimeout( function () {

            // lazy initialized
            if ( !editServiceFunction ) {
                editService( serviceName );
                return;
            }
            editServiceFunction( serviceName );
        }, 300 );
    }

    function isObject( theReference ) {

        if ( theReference == null )
            return false;
        if ( isArray( theReference ) )
            return false;
        return typeof theReference === "object";
    }

    function isArray( theReference ) {
        return Array.isArray( theReference );
    }

    function instanceRows() {
        return $( "#instance-details table tbody tr" );
    }

    function selectedInstanceRows() {
        return $( "#instance-details table tbody tr.selected" );
    }

    function buildHostsParameter( $rows ) {

        if ( !$rows ) {
            $rows = selectedInstanceRows();
        }
        let hostsParam = "";

        $rows.each( function () {

            let $row = $( this );

            let hostName = $row.data( "host" );
            if ( hostsParam !== "" ) {
                hostsParam += ",";
            }
            hostsParam += hostName;
        } );

        return hostsParam;

    }

    function agentUrl( targetHost, command = "host-dash" ) {

        switch ( command ) {

            case "files":
                //                command = "/file/FileManager" ;
                command = "/app-browser#agent-tab,file-browser";
                break;

            case "host-dash":
                //                command = "/app-browser" ;
                command = "/app-browser#agent-tab,explorer";
                break;

            case "scripts":
                //                command = "/app-browser" ;
                command = "/app-browser#agent-tab,script";
                break;

            case "logs":
                command = "/file/FileMonitor";
                break;

            default:
                break;
        }

        let theUrl;

        if ( targetHost === getHostShortName( targetHost )
            && !isIpAddress( targetHost ) ) {
            //console.log("simple host") ;
            theUrl = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, targetHost )
                + command;
        } else {
            // if multiple parts: host is either ip or fqdn
            theUrl = `http://${ targetHost }${ AGENT_ENDPOINT }${ command }`;
        }

        //console.debug( `targetHost: '${ targetHost }', pattern: '${AGENT_URL_PATTERN}', \n theUrl: ${theUrl}`)

        return theUrl;
    }


    function isIpAddress( ipaddress ) {
        if ( /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/.test( ipaddress ) ) {
            return true;
        }
        return false;
    }


    function getHostShortName( targetHost ) {
        if ( isIpAddress( targetHost ) ) {
            return targetHost;
        }
        return targetHost.split( "." )[ 0 ];
    }

    function buildAgentLink( targetHost, command, linkText, parameters ) {

        let hostUrl = agentUrl( targetHost, command );
        if ( parameters ) {
            hostUrl = buildGetUrl( hostUrl, parameters );
        }

        let commandClass = "csap-link";
        if ( command.includes( "scripts" ) ) {
            commandClass = `${ commandClass } host-command`;
        } else if ( command.includes( "FileManager" ) || command.includes( "files" ) ) {
            commandClass = `${ commandClass } host-files`;
        } else if ( command.includes( "host-dash" ) ) {
            commandClass = `${ commandClass } host-infra`;
        }

        let $hostPortalLink = jQuery( '<a/>', {
            title: `open ${ targetHost } portal in new window`,
            target: "_blank",
            class: commandClass,
            href: hostUrl,
            text: linkText
        } );


        return $hostPortalLink;
    }


    function flash( $item, doFlash, count ) {

        if ( count ) {
            for ( let i = 0; i < count; i++ ) {
                let delay = 200;
                let ms = i * delay;
                setTimeout( function () {
                    flash( $item, true );
                }, ms + 1 );
                setTimeout( function () {
                    flash( $item, false );
                }, Math.round( ms + ( delay / 2 ) ) );
            }
            return;
        }

        if ( !doFlash ) {
            $item.css( "background-color", "" );
            $item.css( "color", "" );

        } else {

            $item.css( "background-color", "#aa0000" );
            $item.css( "color", "#fff" );

        }
    }

    function buildGetUrl( url, parameterMap ) {

        if ( !url.includes( "http" ) ) {
            url = agentUrl( CSAP_HOST_NAME, url );
        }

        const myUrlWithParams = new URL( url );

        for ( let paramName in parameterMap ) {
            let paramValue = parameterMap[ paramName ];
            if ( paramValue !== null ) {
                myUrlWithParams.searchParams.append( paramName, paramValue );
            }
        }

        console.debug( `buildGetUrl: ${ myUrlWithParams.href }, source: ${ url }, `, parameterMap );

        return myUrlWithParams.href;
    }

    function getClusterImage( clusterName, firstServiceType ) {

        console.debug( `firstServiceType: ${ firstServiceType }` );

        if ( clusterName.startsWith( "csap-m" ) ) {
            clusterName = "manager";

        } else if ( clusterName.includes( "csap-event" )
            || clusterName.includes( "postgres" )
            || clusterName.includes( "mongo" ) ) {
            clusterName = "db";

        } else if ( clusterName.includes( "autoplays" )
            || clusterName.includes( "kube-system" )
            || clusterName.includes( "kubernetes-dashboard" ) ) {
            clusterName = "autoplays";

        } else if ( clusterName.startsWith( "yourapp-" ) ) {
            clusterName = "rni";

        } else if ( clusterName.includes( "monitor" )
            || clusterName.includes( "metrics" ) ) {
            clusterName = "monitor";
        } else if ( clusterName.includes( "log" )
            || clusterName.includes( "elastic" ) ) {
            clusterName = "logs";
        } else if ( clusterName.includes( "auth" ) ) {
            clusterName = "auth";
        } else if ( clusterName.includes( "ingest" )
            || clusterName.includes( "kafka" )
            || clusterName.includes( "activemq" )
            || clusterName.includes( "nginx" ) ) {
            clusterName = "messaging";
        } else if ( clusterName.includes( "cron" ) ) {
            clusterName = "cron";

        } else if ( clusterName.startsWith( "kubernetes-" ) ) {
            clusterName = "kubernetes";
        }

        //console.log(`ALL_SERVICES: ${ALL_SERVICES}`) ;

        let nameMatch = {
            "autoplays": '32x32/tools.png',
            "cron": '32x32/appointment-new.png',
            "base-os": '32x32/server.svg',
            "All Services": '32x32/applications-internet.png',
            "all-namespaces": '32x32/applications-internet.png',
            "Containers Discovered": '32x32/discovery.png',
            "rni": "32x32/network-wireless.png",
            "manager": '32x32/network-workgroup.png',
            "kubernetes": 'kubernetes.svg',
            "db": 'database.png',
            "monitor": '32x32/utilities-system-monitor.png',
            "logs": '32x32/logs.png',
            "auth": '32x32/system-users.png',
            "messaging": '32x32/message.png',

        }

        let clusterImage = nameMatch[ clusterName ];

        if ( !clusterImage ) {
            //clusterImage = '32x32/application.png' ;
            clusterImage = '32x32/network-workgroup.png';

        }

        return clusterImage;
    }



    function menuMatch( path, tab ) {

        let $menuMatch = null;
        $( "div.tab-menu >span", $( `#${ tab }` ) ).each( function () {
            let $menu = $( this );
            if ( $menu.data( "path" ) == path ) {
                $menuMatch = $menu;
            }
        } );

        if ( $menuMatch == null ) {
            console.error( `failed to locate path ${ path }: ${ $menuMatch }, tab: ${ tab }` );
        } else {
            console.log( ` Path:  ${ path }, tab: ${ tab }, matched: ${ $menuMatch.text() }` );
        }

        return $menuMatch;
    }

    function addTableFilter( $filterInput, $tableContainer, alwaysShowFunction ) {

        $filterInput.parent().attr( "title", "filter output; comma separated items will be or'ed together. Optional: !csap will exclude csap" )

        let $clearButton = jQuery( '<button/>', { class: "csap-icon csap-remove" } )
            .appendTo( $filterInput.parent() )
            .click( function () {
                $filterInput.val( "" );
                $filterInput.trigger( "keyup" )
            } );

        $clearButton.hide();


        jQuery.expr[ ':' ].ignoreCaseForHiding = function ( a, i, m ) {
            return jQuery( a ).text().toUpperCase()
                .indexOf( m[ 3 ].toUpperCase() ) >= 0;
        };
        jQuery.expr[ ':' ].ignoreCaseForNotHiding = function ( a, i, m ) {
            let searchField = m[ 3 ];

            let tableCellText = jQuery( a ).children().text();

            let found = tableCellText.toUpperCase()
                .indexOf( searchField.toUpperCase() ) == -1;

            //console.debug( `found: ${ found } searchField: ${ searchField } cell: ${ tableCellText }` ) ;
            return found;
        };

        let applyFunction = function () {

            let $tableRows = $( 'tbody tr', $tableContainer );

            let includeFilter = $filterInput.val();

            console.debug( ` includeFilter: ${ includeFilter } ` );



            if ( includeFilter.length > 0 ) {
                $filterInput.addClass( "modified" );
                $clearButton.show();
                $tableRows.hide();
                let filterEntries = includeFilter.split( "," );

                for ( let filterItem of filterEntries ) {
                    // console.debug( `filterItem: ${ filterItem }` ) ;
                    if ( filterItem.startsWith( "!" ) && filterItem.length > 1 ) {
                        $( `tr:ignoreCaseForNotHiding("${ filterItem.substring( 1 ) }")`, $tableRows.parent() ).show();
                    } else {
                        $( `td:ignoreCaseForHiding("${ filterItem }")`, $tableRows ).parent().show();
                    }
                }

            } else {
                $tableRows.show();
                $filterInput.removeClass( "modified" );
                $clearButton.hide();
            }

            if ( alwaysShowFunction ) {
                alwaysShowFunction();
            }

        }

        $filterInput.off().keyup( function () {
            //console.log( "Applying template filter" ) ;
            clearTimeout( _statusFilterTimer );
            _statusFilterTimer = setTimeout( function () {
                applyFunction();
            }, 500 );
        } );

        return applyFunction;
    }

    function isFloat( n ) {
        return Number( n ) === n && n % 1 !== 0;
    }

    function bytesFriendlyDisplay( numBytes ) {

        if ( numBytes === 0 ) {
            return 0;
        }

        if ( !numBytes
            || typeof ( numBytes ) == "undefined"
            || numBytes === undefined ) {
            return "-";
        }
        let resultNum = numBytes;
        let resultString = `${ resultNum }`;
        let resultUnits = `-`;

        if ( Number.isInteger( numBytes ) ) {
            resultUnits = `b`;
            if ( resultNum > 1024 ) {
                resultNum = resultNum / 1024;
                resultUnits = `kb`;
            }
            if ( resultNum > 1024 ) {
                resultNum = resultNum / 1024;
                resultUnits = `mb`;
            }

            if ( resultNum > 1024 ) {
                resultNum = resultNum / 1024;
                resultUnits = `gb`;
            }

            resultString = resultNum.toFixed( 1 );
            if ( resultString.endsWith( "\.0" ) ) {
                resultString = resultNum.toFixed( 0 );
            }
        }

        return `${ resultString } ${ resultUnits }`;
    }

    function launch( theUrl, frameTarget = "_blank" ) {
        openWindowSafely( theUrl, frameTarget );
    }

} 