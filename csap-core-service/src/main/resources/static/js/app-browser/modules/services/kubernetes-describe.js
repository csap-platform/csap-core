define( [ "browser/utils", "services/kubernetes", "ace/ace", "ace/ext-modelist" ], function ( utils, kubernetes, aceEditor, aceModeListLoader ) {

    console.log( "Module loaded" ) ;

    let lastPodName ;

    let _editors = new Object() ;

    return {

        show: function ( $container, forceHostRefresh, menuPath ) {

            console.log( `details for ${ $container.attr( "id" ) }` ) ;

            initialize( $container ) ;

            return loadAttributeDetails( forceHostRefresh, $container ) ;

        }

    } ;

    function initialize( $container ) {

        let containerId = $container.attr( "id" ) ;

        if ( !_editors [ containerId ] ) {

            let isVolume = ( containerId === "services-tab-volume-describe" ) ;
            let isPod = ( containerId === "services-tab-pod-describe" ) ;

            let editorId = containerId + "-editor" ;
            let $editorContainer = $( ".describe-editor", $container ) ;
            $editorContainer.attr( "id", editorId ) ;

            console.log( ` Building: describe editor: ${ editorId } ` ) ;
            _editors [ containerId ] = aceEditor.edit( editorId ) ;

            _editors [ containerId ].setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) ) ;

            $( "#close-details", $container ).click( function () {

                let navMenu = ".node-selected" ;
                let launchTarget = "services-tab,knodes" ;
                if ( isVolume ) {
                    navMenu = ".volume-selected" ;
                    launchTarget = "services-tab,kvolumes" ;
                } else if ( isPod ) {
                    navMenu = ".pod-selected.level4" ;
                    launchTarget = "services-tab,kpod-instances" ;
                }
                utils.findNavigation( navMenu ).hide() ;
                utils.launchMenu( launchTarget ) ;
            } ) ;


            $( ".view-definition", $container ).change( function () {
                loadAttributeDetails( true, $container ) ;
            } ) ;

            $( ".code-fold", $container ).change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    console.log( "Initiating fold" ) ;
                    setTimeout( function () {
                        _editors [ containerId ].getSession().foldAll( 2 ) ;
                    }, 100 )

                } else {
                    //_yamlEditor.getSession().unfoldAll( 2 ) ;
                    _editors [ containerId ].getSession().unfold( ) ;
                }
            } ) ;
        }

    }



    function loadAttributeDetails( forceHostRefresh, $container ) {

        let containerId = $container.attr( "id" ) ;
        let $selectedAttribute = kubernetes.selectedPod() ;
        if ( containerId.includes( "node" ) ) {
            $selectedAttribute = kubernetes.selectedNode() ;
        }
        if ( containerId.includes( "volume" ) ) {
            $selectedAttribute = kubernetes.selectedVolume() ;
        }


        let selectedId = $selectedAttribute.attr( "id" ) ;
        if ( !selectedId && containerId.includes( "node" ) ) {
            utils.launchMenu( "services-tab,knodes" ) ;
            return ;
        }
        if ( !selectedId && containerId.includes( "volume" ) ) {
            utils.launchMenu( "services-tab,kvolumes" ) ;
            return ;
        }


        let selectedPodName = $selectedAttribute.data( "name" ) ;
        let apiPath = $selectedAttribute.data( "self" ) ;

        if ( selectedPodName === "not-initialized-yet" ) {
            utils.launchMenu( "services-tab,kpods" ) ;
            return ;
        }

        let selectedHost = $selectedAttribute.data( "host" ) ;
        //selectedHost = `csap-dev05.***REMOVED***` ;
        console.log( `loadAttributeDetails() host: ${ selectedHost }, selectedService ${ selectedPodName }, selectedId: ${ selectedId }` ) ;


        forceHostRefresh = true ;
//        if ( selectedService != lastServiceName ) {
//            forceHostRefresh = true ;
//        }
        lastPodName = selectedPodName ;

        let content = "describe" ;
        if ( $( ".view-definition", $container ).is( ':checked' ) ) {
            content = "specification" ;
        }

        let inspectUrl = `${ EXPLORER_URL }/kubernetes/cli/info/${ content }` ;


        //listingUrl = utils.agentUrl(selectedHost, `${ FILE_URL }/remote/listing` ) ;



        let parameters = {
            resourcePath: apiPath,
            project: utils.getActiveProject()
        } ;


        console.log( `loading: ${inspectUrl}`, parameters ) ;

        utils.loading( `loading pod: ${ selectedHost } ` )

        let $contentLoaded = new $.Deferred() ;

        $.getJSON( inspectUrl, parameters )

                .done( function ( podOrNodeOrVolumeReport ) {
                    // console.log( ` content: `, lifeDialogHtml ) ;

                    utils.loadingComplete() ;
                    $contentLoaded.resolve() ;
                    addItemDetails( podOrNodeOrVolumeReport, $container ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    utils.loadingComplete() ;
                    handleConnectionError( `Getting pod details ${ inspectUrl }`, errorThrown ) ;
                } ) ;


        return $contentLoaded ;


    }


    function addItemDetails( podDetails, $container ) {

        let details = podDetails["response-yaml"] ;

        let containerEditor = _editors[ $container.attr( "id" ) ] ;
        containerEditor.getSession().setValue( details ) ;
        containerEditor.gotoLine( 0 ) ;

        let containerId = $container.attr( "id" ) ;
        console.log( `containerId: ${ containerId } ` ) ;
        if ( containerId.includes( "pod" ) ) {
            let highlightWord = "Status:" ;
            let $selectedContainer = kubernetes.selectedContainer() ;
            console.log( `$selectedContainer`, $selectedContainer )
            if ( $selectedContainer.length > 0 ) {
                highlightWord = `${$selectedContainer.data( "name" )}:` ;
            } else {
                $( ".code-fold", $container ).trigger( "change" ) ;
            }
            console.log( `highlightWord: ${ highlightWord } ` ) ;
            
            setTimeout( function () {

                let range = containerEditor.find( highlightWord, {
                    backwards: false,
                    wrap: false,
                    caseSensitive: false,
                    wholeWord: false,
                    regExp: false
                } ) ;
                console.log( `range:`, range ) ;
//                if ( $selectedContainer.length > 0 ) {
//                     
//                    containerEditor.getSession().foldAll( 2 ) ;
//                    setTimeout( function () {
//                        containerEditor.getSession().unfold(range.start.row, false) ;
//                        containerEditor.getSession().$toggleFoldWidget(range.start.row, {})
//                    }, 300) ;
//                    //
//                }

            }, 100 ) ;
        } else {
            $( ".code-fold", $container ).trigger( "change" ) ;
        }


    }


} ) ;

