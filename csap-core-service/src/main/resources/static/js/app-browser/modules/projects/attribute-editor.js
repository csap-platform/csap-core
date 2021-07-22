define( [ "browser/utils", "editor/json-forms" ], function ( utils, jsonForms ) {

    console.log( "Module loaded" ) ;
    var $attributesContent = $( "#projects-tab-attribute" ) ;
    var $attributePanel = $( "#attribute-content", $attributesContent ) ;
    var $batchTable = $( "table", $attributePanel ) ;
    var $releaseTableBody = $( "tbody", $batchTable ) ;

    var _lastLife = null, _lastRelase = null ;


    var _refreshFunction = function () {
        getSummaryView( _lastLife, _lastRelase ) ;
    } ;

    var $definition = null ;
    var _refreshTimer = null ;
    var _serviceConfigurations = null ;

    let _initComplete = false ;
    
    
    let onChangeFunction ;



    return {
        //
        show: function ( $definitionTextArea ) {
            initialize() ;
            $definition = $definitionTextArea ;
            getServiceConfigurations() ;
        },
        
        addOnChangeFunction: function ( theFunction ) {
            onChangeFunction = theFunction ;
        },
    }
    
    function initialize() {
        if ( _initComplete ) {
            return
        }

        $( "#include-csap-templates-checkbox" ).change( function () {
            console.log(` refreshing services`) ;
            getServiceConfigurations() ;
        } ) ;

        _initComplete = true ;
        console.log( "initialize() Registering Events" ) ;
        $( "#releaseFilter" ).keyup( function () {
            // 
            clearTimeout( _refreshTimer ) ;
            _refreshTimer = setTimeout( function () {
                getServiceConfigurations() ;
            }, 500 ) ;


            return false ;
        } ) ;

        $( "#batchAttributeSelect" ).change( function () {
            // showReleaseTab();
            console.log( "selected: " + $( this ).val() ) ;
            getServiceConfigurations() ;
        } ) ;

        $batchTable.tablesorter( {
            sortList: [ [ 1, 0 ] ],
            theme: 'csapSummary',
            textExtraction: {
                1: function ( node, table, cellIndex ) {
                    let batchContent = $( node ).find( "input" ).val() ;
                    return batchContent ;
                }
            }
            //		headers: { 1: { sorter: 'input' } }
        } ) ;

    }

    function buildAttributeTable( serviceConfigurations ) {

        $releaseTableBody.empty() ;

//        let serviceConfigurations = getServiceConfigurations();
        let serviceNames = Object.keys( serviceConfigurations ) ;
        serviceNames.sort( function ( a, b ) {
            return a.toLowerCase().localeCompare( b.toLowerCase() ) ;
        } ) ;


        for ( let serviceName of serviceNames ) {

            let serviceJson = serviceConfigurations[ serviceName ] ;
            let servicePath = `service-templates.${serviceName}` ;

            let selectedAttribute = $( "#batchAttributeSelect" ).val() ;
            let jsonPath = servicePath + "." + selectedAttribute ;

            let attributeValue = jsonForms.getValue( selectedAttribute, serviceJson ) ;

            let definitionSource = jsonForms.getValue( "definitionSource", serviceJson ) ;
            if ( definitionSource &&
                    ( ( definitionSource == "csap-templates" )
                            || ( definitionSource == "copySource" ) ) ) {

                if ( attributeValue == undefined )
                    continue ;

                if ( definitionSource == "csap-templates" ) {
                    let templatePath = jsonForms.getValue( "path-to-template", serviceJson ) ;
                    if ( templatePath ) {
                        definitionSource = templatePath ;
                    }
                } else {
                    let copySource = jsonForms.getValue( "copySource", serviceJson ) ;
                    if ( copySource ) {
                        definitionSource = "cloned from " + copySource ;
                    }
                }

                attributeValue = `${ attributeValue } <div>source: ${ definitionSource}<input style="display: none" value="${attributeValue}"></div>` ;
                jsonPath = null ;
            }

            var row = buildRow( serviceName,
                    attributeValue,
                    "(default)",
                    jsonPath )

            if ( row != null ) {
                $releaseTableBody.append( row ) ;
            }

            // Add rows for environments
            let envSettings = serviceJson["environment-overload"] ;
            for ( let envName in  envSettings ) {

                //console.log( `${serviceName} settings: `, envName) ;
                let envValue = jsonForms.getValue( selectedAttribute, envSettings[envName] ) ;

                if ( envValue != undefined ) {
                    console.log( `environment-overload: ${envValue} ` ) ;
                    var lifePath = servicePath + ".environment-overload." + envName
                            + "." + selectedAttribute ;

                    var row = buildRow( serviceName,
                            envValue,
                            "(" + envName + ")",
                            lifePath )

                    if ( row != null ) {
                        $releaseTableBody.append( row ) ;
                    }
                }
            }

        }

        if ( ( $( "#batchAttributeSelect" ).val() === "maven.dependency" )
                || ( $( "#batchAttributeSelect" ).val() === "docker.image" ) ) {
            checkForReleaseFile(  ) ;
        }

        $batchTable.trigger( "update" ) ;

    }

    function isObject( o ) {
        return Object.prototype.toString.call( o ) == '[object Object]' ;
    }
    function isArray( o ) {
        return Object.prototype.toString.call( o ) == '[object Array]' ;
    }
    function buildRow( serviceName, attributeValue, lifeVersion, jsonPath ) {

        var serviceFilter = $( "#releaseFilter" ).val().trim() ;
        if ( serviceFilter.length > 0 &&
                !serviceName.toLowerCase().contains( serviceFilter.toLowerCase() ) &&
                !lifeVersion.toLowerCase().contains( serviceFilter.toLowerCase() ) ) {

            return null ;
        }

        var serviceIdentifier = serviceName + '<span class="releaseVersion">' + lifeVersion + '</span>' ;

        var $userInput ;
        if ( jsonPath == null ) {
            $userInput = jQuery( '<div/>', {
                class: "releaseDiv",
                title: "Auto generated using tools. To ignore, use service editor to disable release file",
                html: attributeValue
            } ) ;
        } else {

            var resolvedValue = attributeValue ;
            var isJson = false ;
            console.log( "attributeValue", attributeValue ) ;
            if ( isObject( attributeValue ) || isArray( attributeValue ) ) {
                try {
                    resolvedValue = JSON.stringify( resolvedValue, null, "  " ) ;
                    isJson = true ;
                } catch ( e ) {
                    var message = "Failed to parse attributeValue: " + attributeValue ;
                    console.log( message, e ) ;
                }
            }

            $userInput = jQuery( '<input/>', {
                class: "releaseInput",
                "data-path": jsonPath,
                "data-json": isJson,
                value: resolvedValue
            } ).change( function () {

                updateDefinition( $( this ) ) ;
            } ) ;
        }

        var $row = $( "<tr></tr>" ) ;
        $row.append( $( "<td/>" ).html( serviceIdentifier ) ) ;
        $row.append( $( "<td/>" ).append( $userInput ) ) ;

        return $row ;

    }

    function updateDefinition( $inputThatChanged ) {

        var jsonPath = $( $inputThatChanged ).data( "path" ) ;
        console.log( "modified: " + jsonPath ) ;

        var definitionJson = JSON.parse( $definition.val() ) ;

        jsonForms.updateDefinition( definitionJson, $inputThatChanged ) ;
        $definition.val( JSON.stringify( definitionJson, null, "\t" ) ) ;
        onChangeFunction() ;
    }

    function getServiceConfigurations() {

        let defUrl = DEFINITION_URL + "/service-definitions" ;
        console.log( `defUrl: ${defUrl}` ) ;

        let includeTemplates = $( "#include-csap-templates-checkbox" ).is( ":checked" ) ;

        if ( includeTemplates ) {

            if ( _serviceConfigurations != null ) {
                return buildAttributeTable( _serviceConfigurations ) ;
            }

            $.getJSON( defUrl, {
                project: utils.getActiveProject()
            } )

                    .done( function ( serviceConfigurations ) {
                        _serviceConfigurations = serviceConfigurations ;
                        buildAttributeTable( serviceConfigurations ) ;

                    } )

                    .fail(
                            function ( jqXHR, textStatus, errorThrown ) {

                                handleConnectionError( "Retrieving definitionGetSuccess " , errorThrown ) ;
                            } ) ;

        } else {

            let serviceConfigurations = { } ;
            let applicationDefinition = JSON.parse( $definition.val() ) ;
            $.extend( serviceConfigurations, applicationDefinition[ "service-templates" ] ) ;
            buildAttributeTable( serviceConfigurations ) ;

        }


    }

    function checkForReleaseFile(  ) {
        $.getJSON( DEFINITION_URL + "/external-release", {
            project: utils.getActiveProject()
        } )

                .done( function ( externalReleaseInfo ) {

                    if ( externalReleaseInfo.error ) {
                        console.log( "No further processing due to:", externalReleaseInfo.error ) ;
                    } else {
                        addReleaseFileVersions( externalReleaseInfo ) ;
                    }

                } )

                .fail(
                        function ( jqXHR, textStatus, errorThrown ) {

                            handleConnectionError( "Retrieving definitionGetSuccess " , errorThrown ) ;
                        } ) ;
    }

    function addReleaseFileVersions( externalReleaseInfo ) {
        console.log( "adding release rows" ) ;
        let serviceFilter = $( "#releaseFilter" ).val().trim() ;

        let services = externalReleaseInfo[ "service-templates" ] ;

        for ( let serviceName in services ) {

            let versionItem = services[serviceName] ;

            for ( var lifecycle in versionItem ) {

                let releaseColumn = `${versionItem[lifecycle]} <div>source: ${ externalReleaseInfo.source}<input style="display: none" value="${versionItem[lifecycle]}"></div>` ;
                let row = buildRow( serviceName,
                        releaseColumn,
                        `( ${ lifecycle } )`,
                        null ) ;

                if ( row != null ) {
                    $releaseTableBody.append( row ) ;
                }
            }

        }
        $batchTable.trigger( "update" ) ;


    }



} ) ;