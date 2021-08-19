define( [ "browser/utils", "agent/explorer-operations", "agent/host-operations" ], function ( utils, explorerOperations, hostOperations ) {


    const $containerDialog = $( "#createContainerDialog" ) ;
    const $envSettings = $("#createContainerEnvVariables", $containerDialog) ;
    const $autoResizeContainers = $("textarea.create-auto-size", $containerDialog) ;
    
    let _lastImage = "" ;
    let _jsonParseTimer ;
    let _targetImage ;
    
    console.log( "Module loaded" ) ;

    let _initComplete = false ;

    let _reloadImageFunction  ;

    return {

        initialize: function ( reloadImageFunction ) {
            
            _reloadImageFunction = reloadImageFunction ;
            initialize() ;
        }


    }

    function initialize() {
        _initComplete = true ;
        console.log( "Initializing" ) ;

        $( "#containerCreate" ).click( function () {
            _reloadImageFunction() ;
            setTimeout( showDialog, 10 ) ;
        } ) ;

        $( "#reloadFromImage", $containerDialog ).click( function () {
            populateContainerDefaults() ;
            return false ;
        } ) ;

        $( ".my-container-radio", $containerDialog ).change( function () {
            populateContainerDefaults() ;
            if ( isDockerTarget() ) {
                $( ".docker-only" ).show() ;
                $( ".k8-only" ).hide() ;
            } else {
                $( ".docker-only" ).hide() ;
                $( ".k8-only" ).show() ;

            }
        } ) ;
        
        $( "#container-radio-docker", $containerDialog ).click() ;
        
        
        $( "button", $containerDialog ).click( function () {
            let $button = $( this ) ;
            let type = $button.data( "type" ) ;
            console.log( "Updating model", type, $( this ) ) ;

            let $targetText = $( "textarea", $button.parent() ) ;

            if ( type == "ignore" ) {
                // do nothing
            } else if ( type == "variable" ) {
                $targetText = $envSettings ;
                let envs = JSON.parse( $targetText.val( ) ) ;
                envs.push( "your_name=your_value" ) ;
                console.log( "updated", envs ) ;
                $targetText.val( JSON.stringify( envs, "\n", "\t" ) ) ;

            } else if ( type == "port" ) {
                alertify.csapInfo( getSample( type ) ) ;
//                let ports = JSON.parse( $targetText.val( ) ) ;
//                let p = JSON.parse( getSample( type ) )
//                ports.push( p[0] ) ;
//                console.log( "updated", ports ) ;
//                $targetText.val( JSON.stringify( ports, "\n", "\t" ) ) ;

            } else if ( type == "volume" ) {
                alertify.csapInfo( getSample( type ) ) ;
//                let volumes = JSON.parse( $targetText.val( ) ) ;
//                let p = JSON.parse( getSample( type ) )
//                volumes.push( p[0] ) ;
//                console.log( "updated", volumes ) ;
//                $targetText.val( JSON.stringify( volumes, "\n", "\t" ) ) ;

            } else {
                $targetText.val( getSample( type ) ) ;
            }
        } )

    }

    function isDockerTarget() {

        let containerTarget = $( 'input.my-container-radio:checked' ).val() ;
        console.log( "isDockerTarget () containerTarget: ", containerTarget ) ;

        if ( containerTarget == "docker" ) {
            return true ;
        }

        return false ;
    }

    function getSample( type ) {
        let sample = {
            "variable": [
                "JAVA_HOME=/opt/java",
                "WORKING_DIR=/working"
            ],
            "network": {
                "name": "my-network",
                "createPersistent": {
                    "enabled": true,
                    "driver": "bridge"
                }
            },
            "volume": [ {
                    "about": "Sample: kubernetes host volume",
                    "name": "junit-demo-host-path",
                    "mountPath": "/mnt/host-path",
                    "hostPath": {
                        "type": "DirectoryOrCreate",
                        "path": "/opt/csap/demo-k8s"
                    }
                }, {
                    "about": "Sample: kubernetes empty volume",
                    "name": "$$service-name-empty-dir",
                    "mountPath": "/mnt/empty-dir",
                    "emptyDir": {
                        "sizeLimit": "1Gi"
                    }
                }, {
                    "about": "Sample: kubernetes persistent volume",
                    "name": "$$service-name-volume",
                    "mountPath": "/mnt/nfs-volume",
                    "persistentVolumeClaim": {
                        "claimName": "$$service-name-claim",
                        "storageClass": "csap-nfs-storage-1",
                        "accessModes": [
                            "ReadWriteOnce"
                        ],
                        "storage": "1Gi",
                        "createIfNotPresent": true
                    }
                }, {
                    "about": "Sample: docker temporary volume",
                    "containerMount": "/my-demo-local-mount",
                    "readOnly": false,
                    "sharedUser": false,
                    "hostPath": "$$service-name-volume",
                    "createPersistent": {
                        "enabled": true,
                        "driver": "local"
                    }
                }, {
                    "about": "Sample: docker host volume",
                    "containerMount": "/my-demo-host-mount",
                    "readOnly": true,
                    "sharedUser": true,
                    "hostPath": "/opt/csap/my-demo-host-fs",
                    "createPersistent": {
                        "enabled": true,
                        "driver": "host"
                    }
                } ],
            "port": [
                {
                    "about": "Sample: kubernetes",
                    "containerPort": "$$service-primary-port"
                },
                {
                    "about": "Sample: kubernetes with a service port and name",
                    "containerPort": "$$service-primary-port",
                    "servicePort": "$$service-primary-port"
                },

                {
                    "about": "Sample: kubernetes optional params",
                    "containerPort": "$$service-primary-port",
                    "hostPort": "9091",
                    "protocol": "TCP|UDP",
                    "servicePort": "$$service-primary-port",
                    "name-note": "name must be 14 or fewer characters, and needed when multiple ports are exported",
                    "name": "http-$$service-primary-port"
                },
                {
                    "about": "Sample: docker container(Private) and host(Public) port ",
                    "PrivatePort": "$$service-primary-port",
                    "PublicPort": "$$service-primary-port"
                } ],
            "limit": {
                "cpuCoresMax": 2,
                "cpuCoresAssigned": "0-7",
                "memoryInMb": 512,
                "logs": {
                    "type": "json-file",
                    "max-size": "10m",
                    "max-file": "2"
                },
                "skipValidation": false,
                "ulimits": [
                    {
                        "name": "nofile",
                        "soft": 500,
                        "hard": 500
                    },
                    {
                        "name": "nproc",
                        "soft": 200,
                        "hard": 200
                    }
                ]
            }
        }

        return JSON.stringify( sample[ type ], "\n", "\t" ) ;
    }

    function showDialog(  ) {
        // Lazy create
        _targetImage = explorerOperations.getCurrentImage() ;

        $( ".warning", $containerDialog ).hide() ;
        if ( !alertify.createContainer ) {

            let containerFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( $containerDialog.show()[0] ) ;
                        this.setting( {
                            'onok': function () {
                                createContainer(  ) ;
                            },
                            'oncancel': function () {
                                alertify.warning( "Cancelled Request" ) ;
                            }
                        } ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Create", className: alertify.defaults.theme.ok },
                                { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                            ],
                            options: {
                                title: "Create Container:",
                                resizable: true,
                                autoReset: false,
                                movable: true,
                                maximizable: true
                            }
                        } ;
                    }

                } ;
            } ;
            alertify.dialog( 'createContainer', containerFactory, false, 'confirm' ) ;
        }

        let instance = alertify.createContainer().show() ;

        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;
        instance.resizeTo( targetWidth, targetHeight )

        instance.setting( {
            title: "Create Container using: " + _targetImage.imageName
        } ) ;



        // Populate the fields
        if ( _targetImage.imageName != _lastImage ) {
            populateContainerDefaults() ;
            _lastImage = _targetImage.imageName ;
        }

        $( "#kubernetes-ingress-host" ).val( window.location.hostname ) ;


        $( ".jsonCompile" ).off().keydown( verifyJsonChanges ) ;
        
        
        $autoResizeContainers.off().focusin( function() {
            console.log( `focus createContainerEnvVariables`) ;
            $(this).height( $(this)[0].scrollHeight );
        }) 
        $autoResizeContainers.focusout( function() {
            console.log( `focus out createContainerEnvVariables`) ;
            $(this).height( "" );
        }) 
        
        
    }

    function populateContainerDefaults() {
        if ( _targetImage == undefined ) {
            console.log( "Skipping init - image not selected" ) ;
            return ;
        }
        console.log( "_lastImageSelected", _targetImage ) ;
        $( "#createWorkingDirectory", $containerDialog  ).val( _targetImage.attributes.Config.WorkingDir ) ;

        let containerCommand = getDockerCommandString( _targetImage.attributes.Config.Cmd ) ;
//        if ( containerCommand == "" ) {
//            containerCommand = getDockerCommandString( _targetImage.attributes.ContainerConfig.Cmd ) ;
//        }
        $( "#createContainerCommand", $containerDialog  ).val( containerCommand ) ;


        let containerEntry = getDockerCommandString( _targetImage.attributes.ContainerConfig.Entrypoint ) ;
        if ( containerEntry == "" ) {
            containerEntry = getDockerCommandString( _targetImage.attributes.Config.Entrypoint ) ;
        }
        $( "#createContainerEntry", $containerDialog ).val( containerEntry ) ;


        // pre-populate from image data
        let portDefaults = new Array() ;
        let portInfo = _targetImage.attributes.ContainerConfig.ExposedPorts ;
        if ( portInfo ) {
            for ( exposedItem in portInfo ) {
                // "80/tcp":
                let port = exposedItem.split( "\/" )[0] ;
                let binding = {
                    "PrivatePort": port,
                    "PublicPort": port
                }
                if ( !isDockerTarget() ) {
                    binding = {
                        "containerPort": port,
                        "servicePort": port
                    }
                }
                portDefaults.push( binding ) ;
            }
        }
        $( "#createContainerPorts" ).val( JSON.stringify( portDefaults, "\n", "\t" ) ) ;


        let volumes = new Array() ;
        let volumeInfo = _targetImage.attributes.ContainerConfig.Volumes ;
        if ( volumeInfo ) {
            let volCount = 0 ;
            for ( exposedItem in volumeInfo ) {
                let volumeDef = {
                    "containerMount": exposedItem,
                    "hostPath": "",
                    "readOnly": false,
                    "sharedUser": true
                }

                if ( !isDockerTarget() ) {
                    volumeDef = {
                        "name": "volume-" + ( ++volCount ),
                        "mountPath": exposedItem,
                        "emptyDir": {
                            "sizeLimit": "10Mi"
                        } }
                }

                volumes.push( volumeDef ) ;
            }
        }
        $( "#createContainerVolumes" ).val( JSON.stringify( volumes, "\n", "\t" ) ) ;

        let envVariables = new Array() ;
        if ( _targetImage.attributes.ContainerConfig.Env ) {
            envVariables = _targetImage.attributes.ContainerConfig.Env ;
        }
        $envSettings.val( JSON.stringify( envVariables, "\n", "\t" ) ) ;

        $( "#createContainerNetwork" ).val( getSample( "network" ) ) ;

        // $( "#createContainerLimits" ).val( getSample("limit") );
        $( "#createContainerLimits" ).val( "" ) ;
        
    }

    function isArray( o ) {
        return Object.prototype.toString.call( o ) == '[object Array]' ;
    }

    function getDockerCommandString( commandArray ) {

        let displayItem = "" ;
        if ( isArray( commandArray ) ) {
            commandString = JSON.stringify( commandArray, null, "  " ) ;
            displayItem = commandString.replace( /\n/g, '' ) ;
            
            if ( displayItem.length > 40 ) {
                displayItem = JSON.stringify( commandArray, "\n", "\t" ) ;
            }
//            let anyParamsWithSpaces = false;
//            attribute.forEach( function ( item ) {
//                if (item.contains( " " )) {
//                    anyParamsWithSpaces = true;
//                }
//            } );
//
//            if (anyParamsWithSpaces) {
//                displayItem = JSON.stringify( attribute, "", "  " );
//            } else {
//                displayItem = attribute.join( " " );
//            }
        }
        return displayItem
    }

    function verifyJsonChanges( e ) {
        let $jsonTextArea = $( this ) ;

        // support tab in textarea
        if ( e.keyCode === 9 ) {

            // get caret position/selection
            let start = this.selectionStart ;
            end = this.selectionEnd ;

            let $this = $( this ) ;

            // set textarea value to: text before caret + tab + text after caret
            $this.val( $this.val().substring( 0, start )
                    + "\t"
                    + $this.val().substring( end ) ) ;

            // put caret at right position again
            this.selectionStart = this.selectionEnd = start + 1 ;

            // prevent the focus lose
            return false ;
        } else {
            clearTimeout( _jsonParseTimer ) ;
            _jsonParseTimer = setTimeout( function () {
                validateJsonTextArea( $jsonTextArea ) ;
            }, 2000 )
        }
    }

    function validateJsonTextArea( $jsonTextArea ) {

        try {

            let parsedJson = JSON.parse( $jsonTextArea.val() ) ;
            $jsonTextArea.css( "background-color", "#D5F7DE" ) ;

        } catch ( e ) {
            $jsonTextArea.css( "background-color", "#F2D3D3" ) ;
        }
    }


    function buildJsonArray( entry ) {
        let json = entry ;
        if ( entry.length > 0 && !entry.trim().startsWith( "[" ) ) {
            json = JSON.stringify( entry.split( " " ), "", "" ) ;
        }

        return json ;
    }

    function createContainer(  ) {

        utils.loading( "Loading image information" ) ;

        let commandUrl = explorerUrl + "/docker/container/create" ;

        let namespace = null ;
        let serviceType = null ;
        let ingressPath = null ;
        let ingressPort = null ;
        let ingressHost = null ;
        if ( !isDockerTarget() ) {
            commandUrl = explorerUrl + "/" + hostOperations.categories().kubernetesDeployments ;
            namespace = $( "#k8-create-namespace-select" ).val() ;
            serviceType = $( "#kubernetes-service-type-select" ).val() ;
            ingressPath = $( "#kubernetes-ingress-path" ).val() ;
            ingressPort = $( "#kubernetes-ingress-port" ).val() ;
            ingressHost = $( "#kubernetes-ingress-host" ).val() ;
        }

        let dockerCommand = $( "#createContainerCommand" ).val() ;
        let dockerEntry = $( "#createContainerEntry" ).val() ;
        let paramObject = {

            // k8s params
            "namespace": namespace,
            "serviceType": serviceType,
            "ingressPath": ingressPath,
            "ingressPort": ingressPort,
            "ingressHost": ingressHost,
            "addCsapTools": $( "#kubernetes-create-csaptools" ).is( ':checked' ),

            // common docker params
            "start": $( "#create-start-checkbox" ).is( ':checked' ),
            "name": $( "#createContainerName" ).val().toLowerCase(),
            "image": _targetImage.imageName,
            "replicas": $( "#k8-replica-count" ).val(),
            "workingDirectory": $( "#createWorkingDirectory" ).val(),
            "network": $( "#createContainerNetwork" ).val(),
            "restartPolicy": $( "#restartPolicy" ).val(),
            "runUser": $( "#runUser" ).val(),
            "command": dockerCommand, // buildJsonArray( dockerCommand ),
            "entry": dockerEntry, // buildJsonArray( dockerEntry ),
            "ports": $( "#createContainerPorts" ).val(),
            "volumes": $( "#createContainerVolumes" ).val(),
            "environmentVariables": $envSettings.val(),
            "limits": $( "#createContainerLimits" ).val()
        } ;

        console.log( "createContainer() url:", commandUrl, paramObject ) ;

        $.post( commandUrl, paramObject )
                .done( function ( commandResults ) {
                    //explorerOperations.showResultsDialog( "/container/create", commandResults, commandUrl ) ;
                    hostOperations.showResultsDialog( commandUrl, commandResults ) ;

                    let folder = hostOperations.categories().dockerContainers ;
                    if ( !isDockerTarget() ) {
                        folder = hostOperations.categories().kubernetesDeployments ;
                    }
                    explorerOperations.refreshFolder(
                            folder,
                            true,
                            hostOperations.categories().dockerImages ) ;

                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    utils.loadingComplete() ;
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                } ) ;

    }


} ) ;
