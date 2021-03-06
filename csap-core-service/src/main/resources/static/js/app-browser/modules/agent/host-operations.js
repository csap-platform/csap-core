define( [ "browser/utils", "ace/ace" ], function ( utils, aceEditor ) {

    console.log( "Module loaded" ) ;

    var _initComplete = false ;
    var _resultsEditor ;
    var $aceWrapCheckbox ;


    var $processKillDialog ;

    var $resultsText, $resultsDialog ;

    // folderTypes
    var CATEGORIES = {
        jsonContainer: "json-container",
        jsonValue: "json-value",
        jsonEditor: "json-editor",
        folder: "folder",

        host: "host",
        api: "api",
        security: "security",
        roles: "roles",
        events: "events",

        vsphere: "vsphere",
        vsphereDatastores: "vsphere/datastores",
        vsphereVms: "vsphere/vms",

        csapServices: "os/csap/services",
        csapDefinition: "os/csap/definition",

        linuxServices: "os/services/linux",
        linuxProcesses: "os/processes",
        linuxPackages: "os/packages/linux",
        linuxNetworkDevices: "os/network/devices",
        cpu: "os/cpu",
        memory: "os/memory",
        disk: "os/disk",
        socketConnections: "os/socket/connections",
        socketListeners: "os/socket/listeners",

        dockerConfig: "docker/configuration",
        dockerContainers: "docker/containers",
        dockerImages: "docker/images",
        dockerVolumes: "docker/volumes",
        dockerNetworks: "docker/networks",

        kubernetesConfig: "kubernetes/configuration",
        kubernetesPods: "kubernetes/pods",
        kubernetesPodContainer: "kubernetes/pod/container",
        kubernetesServices: "kubernetes/services",
        kubernetesJobs: "kubernetes/jobs",
        kubernetesCronJob: "kubernetes/cronJobs",
        kubernetesDeployments: "kubernetes/deployments",
        kubernetesEvents: "kubernetes/events",
        kubernetesConfigMaps: "kubernetes/configMaps",
        kubernetesReplicaSets: "kubernetes/replicaSets",
        kubernetesEndpoints: "kubernetes/endpoints",
        kubernetesStatefulSets: "kubernetes/statefulSets",
        kubernetesDaemonSets: "kubernetes/daemonSets",
        kubernetesVolumeClaims: "kubernetes/persistent-volume-claims"
    }


    var NODE_TYPES = {
        "folder": { icon: "ft-folder", iconTooltip: "dynamic folder" },

        "host": { icon: "ft-host", iconTooltip: "" },
        "api": { icon: "ft-api", iconTooltip: "" },
        "security": { icon: "ft-security", iconTooltip: "" },
        "roles": { icon: "ft-roles", iconTooltip: "" },
        "events": { icon: "ft-events", iconTooltip: "" },
        "json-container": { icon: "ft-json-container", iconTooltip: "JSON attributes" },
        "json-value": { icon: "ft-json-value", iconTooltip: "" },
        "json-editor": { icon: "ft-json-editor", iconTooltip: "" },

        "vsphere": { icon: "ft-vm", iconTooltip: "vsphere" },
        "vsphere/datastores": { icon: "ft-docker-volume", iconTooltip: "Disk" },
        "vsphere/vms": { icon: "ft-host", iconTooltip: "Virtual Machines" },

        "os/csap/services": { icon: "ft-csap-process", iconTooltip: "CSAP Services" },
        "os/csap/definition": { icon: "ft-csap-definition", iconTooltip: "CSAP Definition" },

        "os/services/linux": { icon: "ft-os-process", iconTooltip: "Linux Services" },
        "os/processes": { icon: "ft-os-process", iconTooltip: "Processes" },
        "os/packages/linux": { icon: "ft-os-device", iconTooltip: "Packages" },
        "os/cpu": { icon: "ft-os-device", iconTooltip: "CPU Cores" },
        "os/network/devices": { icon: "ft-os-device", iconTooltip: "Network Devices" },
        "os/memory": { icon: "ft-os-device", iconTooltip: "Memory" },
        "os/disk": { icon: "ft-os-device", iconTooltip: "Disk" },
        "os/socket/connections": { icon: "ft-k8-service", iconTooltip: "Socket Connections" },
        "os/socket/listeners": { icon: "ft-k8-service", iconTooltip: "Socket Listeners" },

        "docker/configuration": { icon: "ft-configuration", iconTooltip: "Docker Settings" },
        "docker/containers": { icon: "ft-docker-container", iconTooltip: "Docker Container" },
        "docker/images": { icon: "ft-docker-image", iconTooltip: "Docker Image" },
        "docker/volumes": { icon: "ft-docker-volume", iconTooltip: "Docker Volume" },
        "docker/networks": { icon: "ft-docker-network", iconTooltip: "Docker Network" },

        "kubernetes/configuration": { icon: "ft-configuration", iconTooltip: "Kubernetes Settings" },
        "kubernetes/configMaps": { icon: "ft-k8-volume-claim", iconTooltip: "Kubernetes Config Maps: settings and customization" },
        "kubernetes/pods": { icon: "ft-k8-pod", iconTooltip: "Kubernetes Pods" },
        "kubernetes/pod/container": { icon: "ft-k8-pod-container", iconTooltip: "Kubernetes Pods" },
        "kubernetes/services": { icon: "ft-k8-service", iconTooltip: "kubernetes services" },
        "kubernetes/jobs": { icon: "ft-k8-job", iconTooltip: "kubernetes jobs" },
        "kubernetes/cronJobs": { icon: "ft-k8-cron-job", iconTooltip: "kubernetes cron job" },
        "kubernetes/deployments": { icon: "ft-k8-deploy", iconTooltip: "Kubernetes Deployments" },
        "kubernetes/events": { icon: "ft-events", iconTooltip: "Kubernetes Events" },
        "kubernetes/endpoints": { icon: "ft-k8-replica", iconTooltip: "Kubernetes End Points" },
        "kubernetes/replicaSets": { icon: "ft-k8-replica", iconTooltip: "Kubernetes ReplicaSets" },
        "kubernetes/statefulSets": { icon: "ft-k8-replica", iconTooltip: "Kubernetes Stateful Sets" },
        "kubernetes/daemonSets": { icon: "ft-k8-replica", iconTooltip: "Kubernetes Daemon Sets" },
        "kubernetes/persistent-volume-claims": { icon: "ft-k8-volume-claim", iconTooltip: "Kubernetes Persistent Volume Claims" }
    }

    return {

        initialize: function () {
            initialize() ;
        },

        showKillPidDialog: function ( $button, postKillFunction ) {
            showKillPidDialog( $button, postKillFunction ) ;
        },

        show_k8s_deploy_delete: function ( commandUrl, postKillFunction ) {
            show_k8s_deploy_delete( commandUrl, postKillFunction ) ;
        },

        show_k8s_delete_confirmation: function ( commandUrl, postKillFunction ) {
            show_k8s_delete_confirmation( commandUrl, postKillFunction ) ;
        },

        categories: function () {
            return CATEGORIES ;
        },

        categoryUI: function () {
            return NODE_TYPES ;
        },

        commandRunner: function ( $htmlLink ) {
            commandRunner( $htmlLink ) ;
        },

        httpGet: function ( url, resultTitle ) {
            httpGet( url, resultTitle ) ;
        },

        isKubernetesEnabled: function () {
            return  $( "#container-radio-kubernetes" ).length != 0 ;
        },

        showResultsDialog: function ( operation, commandResults ) {
            show_command_results_dialog( operation, commandResults ) ;
        },

    }

    function initialize() {
        _initComplete = true ;
        console.log( "Initializing" ) ;

        $processKillDialog = $( "#process-kill-dialog" ) ;

        $resultsDialog = $( "#os-results-dialog" ) ;
        $resultsText = $( "#os-results-text" ) ;

        console.log( "initialize() Building ace  editor" ) ;
        _resultsEditor = aceEditor.edit( $resultsText.attr( "id" ) ) ;
        //editor.setTheme("ace/theme/twilight");
        //editor.session.setMode("ace/mode/yaml");

        $aceWrapCheckbox = $( '#ace-wrap-checkbox', $( "#os-results-head" ) ) ;
        _resultsEditor.setOptions( utils.getAceDefaults( "ace/mode/sh", true ) ) ;


        $aceWrapCheckbox.change( function () {
            if ( isAceWrapChecked() ) {
                _resultsEditor.session.setUseWrapMode( true ) ;

            } else {
                _resultsEditor.session.setUseWrapMode( false ) ;
            }
        } ) ;

        var $osFoldCheckbox = $( "#ace-fold-checkbox", $( "#os-results-head" ) ) ;
        $osFoldCheckbox.change( function () {
            if ( $( this ).is( ':checked' ) ) {
                _resultsEditor.getSession().foldAll( 1 ) ;
            } else {
                //_yamlEditor.getSession().unfoldAll( 2 ) ;
                _resultsEditor.getSession().unfold( ) ;
            }
        } ) ;

        $( "#ace-theme-select", $( "#os-results-head" ) ).change( function () {
            _resultsEditor.setTheme( $( this ).val() ) ;
        } ) ;



    }

    function isAceWrapChecked() {
        if ( $aceWrapCheckbox.prop( 'checked' ) ) {
            return true ;
        } else {
            return false ;
        }

    }


    function commandRunner( command ) {

        let targetUrl = command.data( "targeturl" ) ;
        let parameters = command.data( "parameters" ) ;
        console.log( `commandRunner() jquery link parameter,url: ${ targetUrl } parameters: ${parameters} id: ${command.attr( "id" )}` ) ;

        if ( !_initComplete ) {
            initialize() ;
        }

        if ( parameters ) {
            console.log( `prompting for parameters` ) ;
            let paramWords = parameters.split( "=" ) ;
            if ( paramWords.length == 2 ) {
				let $promptDiv =  `<div id="command-runner-prompt"> ${paramWords[0]}</div>`;
				
				if ( paramWords[0].includes( "calicoctl") ) {
					let helpLink = `<a class='csap-link-icon csap-window' style='margin-left: 5em' target=_blank href='https://docs.projectcalico.org/reference/calicoctl/get'>calicoctl reference guide</a>` ;
					$promptDiv =  `<div id="command-runner-prompt"> ${paramWords[0]} ${ helpLink }</div>`;
				}
                alertify.prompt( $promptDiv, ""
                        , function ( evt, promptParameters ) {
                            utils.loading( `Running ${ targetUrl }` ) ;
                            
                            let paramUrl = utils.buildGetUrl( targetUrl , {
                                parameters: promptParameters
                            }) ;

                            $.get( paramUrl )
                                    .done( function ( commandResults ) {
                                     show_command_results_dialog( "Command Output", commandResults ) ;
                                    } )
                                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                                    } ) ;
                        }
                , function () {
                    alertify.error( 'Canceled' )
                } ) ;

                setTimeout( function () {
                    $( "input", $( "#command-runner-prompt" ).parent().parent() ).attr( "placeholder", paramWords[1] )
                }, 500 ) ;
            } else {
                alertify.csapWarning( "Invalid parameters specs" ) ;
            }

        } else {

           utils.loading( `Running ${ targetUrl }` ) ;

            $.get( targetUrl )
                    .done( function ( commandResults ) {
                        show_command_results_dialog( "Command Output", commandResults ) ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
        }
    }

    function httpGet( url, resultTitle ) {

        console.log( "url: ", url, " $htmlLink:", resultTitle ) ;
        if ( !_initComplete )
            initialize() ;

        utils.loading( `Getting ${ url }` ) ;

        $.get( url )
                .done( function ( commandResults ) {
                    show_command_results_dialog( resultTitle, commandResults ) ;
                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                } ) ;
    }

    function show_k8s_delete_confirmation( commandUrl, onCompleteFunction ) {

        if ( !_initComplete )
            initialize() ;

        var commandTitle = "Deployment Deletion" ;
        //
        // confirm dialog re-use: works because $processKillDialog is module variable
        //

        var okFunction = function () {
            utils.loading( "Deleting " ) ;

            $.delete( commandUrl )
                    .done( function ( commandResults ) {
                        show_command_results_dialog( commandTitle, commandResults ) ;

                        if ( onCompleteFunction )
                            onCompleteFunction() ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        console.log( jqXHR, textStatus, " errorThrown", errorThrown ) ;
                        alertify.alert( "Failed Operation: Response code: " + jqXHR.status,
                                JSON.stringify( jqXHR.responseJSON, "\n", "\t" ) ) ;
                        utils.loadingComplete()
                    } ) ;
        }


        //var $description = jQuery( '<div/>', { "id:", ""} );
        alertify.confirm(
                commandTitle + " Confirmation",
                '<div id="k8DeleteConfirmation"></div>',
                okFunction,
                no_op_cancel_function
                ) ;


        var $confirmationDialog = jQuery( '<div/>', {
            class: "info",
            text: "Resource: " + commandUrl.substring( commandUrl.indexOf( "kubernetes" ) + 11 )
        } ).css( "font-size", "12pt" ) ;
        $( "#k8DeleteConfirmation" ).empty() ;
        $( "#k8DeleteConfirmation" ).append( $confirmationDialog ) ;

    }

    function show_k8s_deploy_delete( commandUrl, onCompleteFunction ) {

        if ( !_initComplete )
            initialize() ;

        var commandTitle = "Deployment Deletion" ;
        //
        // confirm dialog re-use: works because $processKillDialog is module variable
        //

        var okFunction = function () {
            utils.loading( "Deleteing deploy" )

            $.delete( commandUrl )
                    .done( function ( commandResults ) {
                        show_command_results_dialog( commandTitle, commandResults ) ;

                        if ( onCompleteFunction )
                            onCompleteFunction() ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
        }


        //var $description = jQuery( '<div/>', { "id:", ""} );
        alertify.confirm(
                commandTitle + " Confirmation",
                '<div id="deployContent"></div>',
                okFunction,
                no_op_cancel_function
                ) ;



        $( "#deployContent" )
                .html( $( "#k8-deploy-delete" ).html() + commandUrl ) ;

    }




    function showKillPidDialog( $button, postKillFunction ) {

        if ( !_initComplete )
            initialize() ;

        //
        // confirm dialog re-use: works because $processKillDialog is module variable
        //

        var okFunction = function () {
            utils.loading( ` killing pid` )

            var commandUrl = utils.getOsExplorerUrl()
                    + "/" + CATEGORIES.linuxProcesses
                    + "/" + $button.data( "pid" )
                    + "/" + $( "#kill-signal" ).val() ;

            $.delete( commandUrl )
                    .done( function ( commandResults ) {
                        show_command_results_dialog( "killing process", commandResults ) ;

                        if ( postKillFunction )
                            postKillFunction() ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
        }


        //var $description = jQuery( '<div/>', { "id:", ""} );
        alertify.confirm(
                "Kill OS Process",
                '<div id="process-kill-prompt"></div>',
                okFunction,
                no_op_cancel_function
                ) ;

        $processKillDialog.appendTo( $( '#process-kill-prompt' ) ) ;

    }

    function no_op_cancel_function() {

    }


    function show_command_results_dialog( operation, commandResults ) {

        if ( !_initComplete )
            initialize() ;

        utils.loadingComplete() ;

        getResultsDialog() ;

        $( "#os-results-title" ).text( operation ) ;

        var targetMode = "sh" ;
        if ( commandResults["response-sh"] ) {
            _resultsEditor.setValue( commandResults["response-sh"], -1 ) ;

        } else if ( commandResults["response-yaml"] ) {
            _resultsEditor.setValue( commandResults["response-yaml"], -1 ) ;
            targetMode = "yaml" ;

        } else if ( commandResults["response-json"] ) {
            _resultsEditor.setValue( commandResults["response-json"], -1 ) ;
            targetMode = "json" ;

        } else if ( commandResults["plainText"] ) {
            _resultsEditor.setValue( commandResults["plainText"], -1 ) ;

        } else {
            if ( commandResults.error && commandResults.reason ) {

                _resultsEditor.setValue(
                        "Error: " + commandResults.error
                        + "\n\n reason: \n" + commandResults.reason, -1 ) ;

            } else {
                _resultsEditor.setValue( JSON.stringify( commandResults, "\n", "\t" ), -1 ) ;
                targetMode = "json" ;

            }
        }

        console.log( "showResultsDialog() Mode set", targetMode ) ;
        _resultsEditor.getSession().setMode( "ace/mode/" + targetMode ) ;

    }

    function getResultsDialog(  ) {

        // Lazy create
        if ( !alertify.hostResults ) {

            let csapDialogFactory = CsapCommon.dialog_factory_builder( {
                content: $resultsDialog.show()[0],
                onresize: resultsViewerResize
            } ) ;

            alertify.dialog( 'hostResults', csapDialogFactory, false, 'alert' ) ;
        }
        var instance = alertify.hostResults().show() ;

        return instance ;
    }

    function resultsViewerResize( dialogWidth, dialogHeight ) {

        setTimeout( function () {

            let $header = $( ":first-child", $resultsText.parent() ) ;

            let maxWidth = dialogWidth - 10 ;
            $resultsText.css( "width", maxWidth ) ;

            let maxHeight = dialogHeight
                    - Math.round( $header.outerHeight( true ) )
                    - 10 ;

            $resultsText.css( "height", maxHeight ) ;

            console.log( `kubernetes_yaml_dialog() launched/resizing yaml editor` ) ;
            _resultsEditor.resize() ;

        }, 500 ) ;

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
            success: callback
        } ) ;
    } ;
} ) ;