
let agentModules = [ "browser/utils", "agent/explorer-pod-logs", "services/about-host", "agent/agent-logs", "agent/agent-file-browser", "agent/host-operations", "agent/command-runner" ] ;
define( agentModules, function ( utils, podLogs, aboutHost, agentLogs, fileBrowser, hostOperations, commandRunner ) {

    console.log( "Module loaded agent1" ) ;

    let memoryRefreshTimer = 0 ;

    let initialized ;

    const $agentContent = utils.findContent( "#agent-tab-content" ) ;
    const $processContent = $( "#agent-tab-processes" ) ;

    const $loading = $( "#loadRow", $processContent ) ;
    const $processRefreshSelection = $( "#cpuIntervalId", $processContent ) ;
    const $processFilter = $( "#processFilter", $processContent ) ;
    const $processTable = $( "#processTable", $processContent ) ;
    const $processBody = $( "tbody.content", $processTable ) ;

    const $wrapProcessTableCheckbox = $( '#wrap-process-table', $processContent ) ;


    let currentPriorityForProcess = 22 ;
    let processRefreshTimer ;
    let _processFilterFunction ;
    let _shown_process_once ;

    let refreshPromise ;

    const $diskExcludeFilter = $( "#disk-exclude-pattern" ) ;
    let _diskFilterTimer, refreshDfTimer ;



    const navigationOperations = {
        "memory": refreshMem,
        "files": refreshDf,
        "processes": refreshProcessTab,
        "cpus": refreshProcessTab,
        "script": commandRunner.show,
        "command-output": () => {
            console.log( `showing command output` ) ;
        },
        "file-browser": fileBrowser.show,
        "logs": agentLogs.show,
        "explorer-pod-logs": podLogs.show,
        // "live": serviceLive.show,
        "system": null,
        "service": null,
        "java": null,
        "application": null,
        "explorer": null,
    } ;


    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            console.log( ` menu selected: ${ menuPath }` ) ;

            if ( !initialized ) {
                initialize() ;
                initialized = true ;
            }

            let $menuLoaded ;

            let operation = navigationOperations[ menuPath ] ;

            if ( operation ) {
                $menuLoaded = operation( $menuContent, forceHostRefresh, menuPath ) ;

            } else if ( menuPath === "explorer" ) {

                utils.loading( `loading explorer module` ) ;
                $menuLoaded = new $.Deferred() ;
                require( [ "agent/host-explorer" ], function ( XhostExplorer ) {
                    navigationOperations.explorer = XhostExplorer.show ;
                    navigationOperations.explorer( $menuContent, forceHostRefresh, menuPath ) ;
                    $menuLoaded.resolve() ;
                } ) ;

            } else if ( menuPath === "live" ) {

                utils.loading( `loading service live module` ) ;
                $menuLoaded = new $.Deferred() ;

                require( [ "agent/service-live" ], function ( XserviceLive ) {
                    navigationOperations.live = XserviceLive.show ;
                    navigationOperations.live( $menuContent, forceHostRefresh, menuPath ) ;
                    $menuLoaded.resolve() ;
                } ) ;

            } else if ( ( menuPath === "system" )
                    || ( menuPath === "service" )
                    || ( menuPath === "java" )
                    || ( menuPath === "application" ) ) {


                utils.loading( `loading graphs module (${ menuPath } )` ) ;
                console.log( `loading graph package: ${ menuPath }` ) ;
                $menuLoaded = new $.Deferred() ;

                require( [ "agent/performance-graphs" ], function ( Xgraphs ) {
                    navigationOperations.system = Xgraphs.show ;
                    navigationOperations.service = Xgraphs.show ;
                    navigationOperations.java = Xgraphs.show ;
                    navigationOperations.application = Xgraphs.show ;

                    Xgraphs.show( $menuContent, forceHostRefresh, menuPath ) ;
                    $menuLoaded.resolve() ;

                } ) ;

            } else {
                console.warn( `Command not bound for menu: ${ menuPath }` ) ;
            }

            return $menuLoaded ;


        }

    }

    function initialize() {

        $( "a.commandRunner", $agentContent ).click( function () {
            hostOperations.commandRunner( $( this ) ) ;
            return false ;
        } )
        

        $( "a.script-runner", $agentContent ).click( function () {
            utils.launchScript( {
                template: $( this ).data( "template" )
            } ) ;
            return false ;
        } )


        $processRefreshSelection.change( function () {
            refreshProcessTab() ;
        } ) ;

        utils.getServiceSelector().change( function () {

            refreshProcessTab() ;

        } ) ;

        $wrapProcessTableCheckbox.change( refreshProcessTab ) ;

        $diskExcludeFilter.off().keyup( function () {
            console.log( "Applying template filter" ) ;
            clearTimeout( _diskFilterTimer ) ;
            _diskFilterTimer = setTimeout( function () {
                applyDiskFilter() ;
            }, 500 ) ;
        } ) ;

        $diskIncludeFilter = $( "#disk-include-pattern" ) ;
        $diskIncludeFilter.off().keyup( function () {
            console.log( "Applying template filter" ) ;
            clearTimeout( _diskFilterTimer ) ;
            _diskFilterTimer = setTimeout( function () {
                applyDiskFilter() ;
            }, 500 ) ;
        } ) ;

        // column counts begin with 0
        $processTable.tablesorter( {
            emptyTo: 'bottom',
            sortList: [ [ 3, 1 ] ],
            theme: 'csapSummary'
//            headers: {
//                // disable sorting of the first column (we start counting at zero)
//                1: {
//                    // disable it by setting the property sorter to false
//                    sorter: false
//                }
//            }
        } ) ;

        _processFilterFunction = utils.addTableFilter( $processFilter, $processTable ) ;
        $processFilter.val( "!ns-" ) ;


        $( "#memTableDiv table" ).tablesorter( {
            sortList: [ [ 1, 1 ] ],
            theme: 'csapSummary'
        } ) ;
        $( "#mpTable" ).tablesorter( {
            sortList: [ [ 0, 0 ] ],
            theme: 'csapSummary'
        } ) ;
        $( "#swapTableDiv table" ).tablesorter( {
            sortList: [ [ 2, 1 ] ],
            theme: 'csapSummary'
        } ) ;
        $( "#dfTableDiv table" ).tablesorter( {
            sortList: [ [ 2, 1 ] ],
            theme: 'csapSummary'
        } ) ;


        $( '#filterCsap', $processContent ).change( function () {
            console.log( `Switching view` ) ;
            $processFilter.val( "" ) ;
            refreshProcessTab() ;
        } ) ;

        $( '#sysButton' ).click( function () {

            $( "section" ).hide() ;
            // alert("toggling") ;
            $( "#systemGraphs" ).show() ;

            return false ; // prevents link
        } ) ;

        $( '#hostInfo' ).click( function () {
            console.log( "getting summary for: ", uiSettings ) ;
            aboutHost.show( utils.getHostName() ) ;
            return false ; // prevents link
        } ) ;

        $( '#resourceButton' ).click( function () {

            $( "section" ).hide() ;
            // alert("toggling") ;
            $( "#systemGraphs" ).show() ;

            return false ; // prevents link
        } ) ;

        $( '#fileButton' ).click( function () {

            $( "section" ).hide() ;
            // alert("toggling") ;
            $( "#fileSystems" ).show() ;

            return false ; // prevents link
        } ) ;

        $( '#memoryButton' ).click( function () {

            $( "section" ).hide() ;
            // alert("toggling") ;
            $( "#memoryStats" ).show() ;

            return false ; // prevents link
        } ) ;

        $( '#swapButton' ).click( function () {

            $( "section" ).hide() ;
            // alert("toggling") ;
            $( "#swapStats" ).show() ;

            return false ; // prevents link
        } ) ;

        $( '#processButton' ).click( function () {

            $( "section" ).hide() ;
            // alert("toggling") ;
            $( "#processStats" ).show() ;

            return false ; // prevents link
        } ) ;

        $( '#allButton' ).click( function () {

            $( "section" ).show() ;

            return false ; // prevents link
        } ) ;

        if ( serviceFilterParam != null ) {
            initializeGraphs() ;
        }

    }



    function applyDiskFilter() {

        let $body = $( "#dfTableDiv table tbody" ) ;
        let $rows = $( 'tr', $body ) ;

        let  excludeFilter = $diskExcludeFilter.val() ;
        let  includeFilter = $diskIncludeFilter.val() ;

        console.log( `excludeFilter: ${ excludeFilter }, includeFilter: ${includeFilter}` ) ;
        $rows.show() ;

        if ( excludeFilter.length > 0 ) {

            let filterArray = excludeFilter.split( "," ) ;
            for ( let i = 0 ; i < filterArray.length ; i++ ) {
                $( 'td:icontains("' + filterArray[i] + '")', $rows ).parent().hide() ;
            }
        }

        if ( includeFilter.length > 0 ) {
            $rows.hide() ;
            let filterArray = includeFilter.split( "," ) ;
            for ( let i = 0 ; i < filterArray.length ; i++ ) {
                $( 'td:icontains("' + filterArray[i] + '")', $rows ).parent().show() ;
            }
        }

    }
    function refreshDf() {

        clearTimeout( refreshDfTimer ) ;

        let dfTable = $( "#dfTableDiv table tbody" ) ;

        $.getJSON( utils.getOsUrl( ) + "/getDf", {
            q: "test"
        }, function ( dfJson ) {

            console.log( `loading df table` ) ;

            dfTable.empty() ;

            for ( let key in dfJson ) {

                console.debug( "DF adding: " + key ) ;


                let tableRow = jQuery( '<tr/>', { } ) ;
                dfTable.append( tableRow ) ;

                let $mountCol = jQuery( '<td/>', { } ).appendTo( tableRow ) ;

                let fileDescription = dfJson[key].mount ;
                let filePath = dfJson[key].mount ;
                let warningMessage = "" ;
                if ( filePath.indexOf( "[" ) !== -1 ) {
                    filePath = filePath.substring( 0, filePath.indexOf( "[" ) ) ;
                    warningMessage = "<div class='quote'> Warning: path contains escaped characters</div>" ;
                }

//                let $browseLink = utils.buildAgentLink(
//                        utils.getHostName(),
//                        "files", filePath,
//                        { fromFolder: filePath } ) ;
                let $browseLink = jQuery( '<a/>', {
                    class: `csap-link`,
                    href: '#open-browse-panel',
                    title: `Run du report`,
                    html: filePath
                } ) ;
                $browseLink.off().click( function () {
                    utils.launchFiles( {
                        locationAndClear: `__root__${ filePath}`
                    } ) ;
                    return false ;
                } ) ;


                $mountCol.append( $browseLink ) ;
                $mountCol.append( warningMessage ) ;

                let $commandsCol = jQuery( '<td/>', { } ).appendTo( tableRow ) ;

                let $percentUsedColumn = jQuery( '<td/>', {
                    class: "numeric"
                } ).appendTo( tableRow ) ;


                let $duReport = jQuery( '<a/>', {
                    class: `csap-link`,
                    href: '#open-command-panel',
                    title: `Run du report`,
                    html: dfJson[key].usedp
                } ) ;
                $duReport.off().click( function () {
                    utils.launchScript( {
                        fromFolder: `__root__${ filePath}`,
                        template: `disk-du.sh`
                    } ) ;
                    return false ;
                } ) ;


                $percentUsedColumn.append( $duReport ) ;


                jQuery( '<td/>', {
                    class: "numeric",
                    text: dfJson[key].used + ' / ' + dfJson[key].avail
                } ).appendTo( tableRow ) ;

                let deviceColumn = jQuery( '<td/>', {
                    class: " ",
                    text: dfJson[key].dev
                } ).appendTo( tableRow ) ;

                if ( dfJson[key].dev == "shmfs" || dfJson[key].dev == "tmpfs" ) {
                    deviceColumn.css( "color", "red" ).css( "font-weight", "bold" ) ;
                }

                let $fsReport = jQuery( '<a/>', {
                    class: `csap-link`,
                    href: '#open-command-panel',
                    title: `Run filesystem report against specified filesystem. inode counts, etc`,
                    html: "Report"
                } ) ;
                $fsReport.off().click( function () {
                    utils.launchScript( {
                        fromFolder: `__root__${ filePath}`,
                        template: `disk-df.sh`
                    } ) ;
                    return false ;
                } ) ;
                $commandsCol.append( $fsReport ) ;


                let $testlink = jQuery( '<a/>', {
                    class: `csap-link`,
                    href: '#open-command-panel',
                    title: `Test throughput`,
                    html: "Test"
                } ) ;
                $testlink.off().click( function () {
                    utils.launchScript( {
                        fromFolder: `__root__${ filePath}`,
                        template: `host-performance-disk.sh`
                    } ) ;
                    return false ;
                } ) ;
                $commandsCol.append( $testlink ) ;


            }

            dfTable.trigger( "update", "resort" ) ;
            $diskExcludeFilter.keyup() ;
        } )

        refreshDfTimer =
                setTimeout( function () {
                    refreshDf()
                }, 60000 ) ;
    }



    function refreshMem() {

        console.log( "Updating memory stats: " + memoryRefreshTimer ) ;

        clearTimeout( memoryRefreshTimer ) ;


        $.getJSON( utils.getOsUrl( ) + "/getMem", {
            q: "test"
        } )
                .done( renderMemoryUi ) ;


        memoryRefreshTimer =
                setTimeout( function () {
                    refreshMem()
                }, utils.getRefreshInterval() ) ;
    }

    function renderMemoryUi( memoryMetrics ) {

        // update Swap table
        let swapTableBody = $( "#swapTableDiv table tbody" ) ;
        swapTableBody.empty() ;

        for ( let key in memoryMetrics ) {

            if ( ( key.indexOf( "swapon" ) == -1 )
                    || ( key == "timestamp" ) ) {
                continue ;
            }

            let tableRow = jQuery( '<tr/>', { } ) ;

            swapTableBody.append( tableRow ) ;

            let swapArray = memoryMetrics[key] ;
            for ( let i = 0 ; i < swapArray.length ; i++ ) {

                jQuery( '<td/>', {
                    class: "numeric",
                    text: swapArray[i]
                } ).appendTo( tableRow ) ;
            }


        }


        // Update memory table
        let memTableBody = $( "#memTableDiv table tbody" ) ;
        memTableBody.empty() ;

        // newer kernels combine buffer data into first line, so skip
        if ( !memoryMetrics.isFreeAvailable ) {
            // Used hashed values
            let bufferArray = memoryMetrics["buffer"] ;
            let memRow = jQuery( '<tr/>', { } ) ;
            memRow.appendTo( memTableBody ) ;
            jQuery( '<td/>', {
                class: "",
                text: bufferArray[0] + " " + bufferArray[1]
            } ).appendTo( memRow ) ;

            jQuery( '<td/>', { class: "numeric" } ).appendTo( memRow ) ;

            jQuery( '<td/>', { class: "numeric", text: "Used: " + bufferArray[2] } ).appendTo( memRow ) ;


            jQuery( '<td/>', { class: "numeric", text: "Free: " + bufferArray[3] } ).css( "color", "red" ).appendTo( memRow ) ;
        }



        // Used hashed values
        let ramArray = memoryMetrics["ram"] ;

        let usage = Math.round( parseInt( ramArray[2] )
                / parseInt(ramArray[1]) * 100);

        memRow = jQuery( '<tr/>', { } ) ;
        memRow.appendTo( memTableBody ) ;

        jQuery( '<td/>', { text: ramArray[0] } ).appendTo( memRow ) ;

        jQuery( '<td/>', { class: "numeric", text: usage + "%" } ).appendTo( memRow ) ;

        jQuery( '<td/>', { class: "numeric", text: ramArray[2] + " / " + ramArray[1] } ).appendTo( memRow ) ;

        let $bufCacheAvailable = jQuery( '<span/>', { text: ramArray[4] + " / " + ramArray[5] } ) ;
        if ( memoryMetrics.isFreeAvailable ) {
            let available = jQuery( '<span/>', { text: ramArray[6] } ).css( "color", "red" ) ;
            $bufCacheAvailable.append( "  available: " ) ;
            $bufCacheAvailable.append( available ) ;
        } else {
            $bufCacheAvailable.append( " / " + ramArray[6] ) ;
        }
        let $memoryCol = jQuery( '<td/>', { class: "numeric" } ) ;
        $memoryCol.append( $bufCacheAvailable ) ;
        memRow.append( $memoryCol ) ;




        // Used hashed values
        let swapArray = memoryMetrics["swap"] ;

        usage = 0 ;
        if ( swapArray[1] != 0 ) {

            usage = Math.round( parseInt( swapArray[2] )
                    / parseInt(swapArray[1]) * 100);
        }


        memRow = jQuery( '<tr/>', { } ) ;
        memRow.appendTo( memTableBody ) ;

        jQuery( '<td/>', { text: swapArray[0] } ).appendTo( memRow ) ;

        jQuery( '<td/>', { class: "numeric", text: usage + "%" } ).appendTo( memRow ) ;

        jQuery( '<td/>', { class: "numeric", text: swapArray[2] + " / " + swapArray[1] } ).appendTo( memRow ) ;


        jQuery( '<td/>', { class: "numeric", text: "" } ).appendTo( memRow ) ;



        memTableBody.trigger( "update", "resort" ) ;

        swapTableBody.trigger( "update", "resort" ) ;

        $( "#memReloadTime" ).html( memoryMetrics["timestamp"] ) ;


    }




    function refreshProcessTab() {
//        $( "tbody.content", $processTable ).hide() ;
//        $( "#loadRow" ).show() ;

        utils.loading( "loading processes" ) ;
        clearTimeout( processRefreshTimer ) ;

//        let defaultService = utils.getSelectedService() ;
//        if ( defaultService !== utils.agentServiceSelect() ) {
//            let matches = defaultService.match( /-\d+$/ ) ;
//            if ( matches ) {
//                defaultService = defaultService.substr( 0, defaultService.length - matches[0].length ) ;
//            }
//
//            $processFilter.val( defaultService ) ;
//        } else {
//            $processFilter.val( "" ) ;
//        }

        if ( !refreshPromise ) {
            refreshPromise = new Promise( ( resolve, reject ) => {
                setTimeout( function () {
                    resolve( "init" ) ;
                }, 10 ) ;
            } ) ;
        }

        setTimeout( function () {
            // empty can be very slow of large tables. run after a ui delay
            // and do a detach

            refreshPromise.then( function ( value ) {

                $processBody.children().detach().remove() ;
                $processBody.empty() ;
                refreshProcessData() ;

            } ) ;
        }, 10 ) ;
    }


    function refreshProcessData() {


        clearTimeout( processRefreshTimer ) ;

        // alert("refreshProcessTable") ;
        refreshPromise = new Promise( ( resolve, reject ) => {

            let destUrl = "processes/all" ;
            // console.log("clearing" + processRefreshTime) ; 
            if ( $( '#filterCsap', $processContent ).is( ':checked' ) ) {
                destUrl = "processes/csap" ;
            }

//            console.log(`scheduled process refresh`) ;

//            setTimeout( function() {

            // this comes back extremly quickly with a lot of data- so add a lag to allow for UI update
            $.getJSON( utils.getOsUrl( ) + "/" + destUrl, function ( processInfoJson ) {
                hostProcessResponseHandler( processInfoJson ) ;
                utils.loadingComplete() ;


                resolve( "completed" ) ;

            } ) ;

//            },5000)

        } ) ;


    }

    function hostProcessResponseHandler( hostProcessReport ) {

        // use the report to derive the display; handles in process updates
        // where another report will shortly overrid this one
        updateHostProcessTable( hostProcessReport.csapFiltered, hostProcessReport.ps ) ;

        updateHostCpuTable( hostProcessReport.mp ) ;

        $( "#processReloadTime" ).html( hostProcessReport.timestamp ) ;

        let intervalInSeconds = $processRefreshSelection.val() ;
        // alert("interval" + interval) ;
        processRefreshTimer = setTimeout( function () {
            refreshProcessData() ;
        }, intervalInSeconds * 1000 ) ;

    }

    function isArray( o ) {
        return Object.prototype.toString.call( o ) == '[object Array]' ;
    }

    function updateHostProcessTable( isCsapFilterChecked, processReport ) {

        //console.log( "updateHostProcessTable()", processJson ) ;
        $loading.hide() ;
        $processBody.show() ;

        let totalMem = 0 ;


        if ( isCsapFilterChecked ) {
            $( "#diskAndArgsHeader" ).text( "Disk Space" ) ;
        } else {
            $( "#diskAndArgsHeader" ).text( "Process Commandline" ) ;
        }

        $processBody.empty() ;

        for ( let processName in processReport ) {

            let processCollection = processReport[processName] ;

            for ( let containerStats of  processCollection.containers ) {

                let processMemory = addProcessRow(
                        containerStats,
                        isCsapFilterChecked,
                        processName,
                        processCollection ) ;
                if ( Number.isInteger( processMemory ) ) {
                    totalMem += parseInt( processMemory ) ;
                }
            }
        }

        $( ".notFound" ).remove() ; // get rid of any rows not found

        // hide process controls unless in full mode
        if ( isCsapFilterChecked ) {
            $( ".controls" ).hide() ;
            $( ".csap-only" ).show() ;
        } else {
            $( ".controls" ).show() ;
            $( ".csap-only" ).hide() ;

        }

        $( ".kill-pid-button", $processTable ).off().click( function () {
            hostOperations.showKillPidDialog( $( this ), refreshProcessData ) ;
        } )


        $( ".tree-pid-button", $processTable ).off().click( function () {
            //hostOperations.showKillPidDialog( $( this ), refreshProcessData ) ;
            let pid = $( this ).data( "pid" )
            let commandUrl = utils.getOsUrl( ) + "/process/report/" + pid ;
            let params = { }

            $.getJSON( commandUrl, params )
                    .done( function ( commandResults ) {
                        hostOperations.showResultsDialog( "OS Process Tree", commandResults ) ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
        } )


        $( ".show-process-details", $processTable ).off().click( function () {
            hostOperations.commandRunner( $( this ), refreshProcessData ) ;
            return false ;
        } )


        _processFilterFunction() ;
        $processTable.trigger( "update", "resort" ) ;
        if ( !isCsapFilterChecked && !_shown_process_once ) {
            $processTable.trigger( "sorton", [ [ [ 2, 1 ] ] ] ) ;
            _shown_process_once = true ;
        }

        $( "#totalMem" ).html( utils.bytesFriendlyDisplay( totalMem * 1024 ) ) ;

    }

    function getRandomInt( max ) {
        return Math.floor( Math.random() * max ) ;
    }

    function addProcessRow(
            containerStats,
            isCsapFilterChecked,
            processName,
            processCollection ) {


        let containerPids = containerStats.pid ;
        let primaryPid = containerStats.pid ;
        if ( isArray( primaryPid ) ) {
            primaryPid = primaryPid[0] ; // need to launch children
        }
        let isProcessRunning = true ;
        if ( primaryPid == "-" ) {
            isProcessRunning = false ;
        }

        let theRowId = "os-" + processName ;
        let $processRow = jQuery( '<tr/>', {
            id: theRowId,
            title: "OS pid(s): " + containerPids
        } ) ;


        let processLabel = processCollection.serviceName ;

        if ( primaryPid == "host" ) {
            console.log( "Skipping row as pid=host. Used for?" ) ;
            return ;
        }

        let nameColumn = `<span class="ptab-name code-block"> ${processLabel } </span> ` ;

        if ( containerStats.podNamespace ) {
            // add container name
            nameColumn += `<span class=ptab-container> ${containerStats.podNamespace } </span> ` ;
        }
        if ( containerStats.containerLabel
                && !containerStats.containerLabel.includes( processLabel ) ) {
            // add container name
            nameColumn += `<span class=ptab-container> ${containerStats.containerLabel } </span> ` ;
        }

        if ( Array.isArray( containerStats.pid ) ) {
            let numProcesses = containerStats.pid.length ;
            if ( numProcesses > 1 ) {
                nameColumn += `<div class=ptab-container> ${ numProcesses} processes</div> ` ;
            }
        }

        if ( isProcessRunning ) { // && ! processLabel.serverType
            nameColumn += buildTreeButton( primaryPid ) ;
            nameColumn += buildKillButton( primaryPid ) ;
        }

        jQuery( '<td/>', {
            html: nameColumn
        } ).appendTo( $processRow ) ;






        //
        // os process socket count
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.socketCount,
                        function () {
                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `network-socket-pid.sh`,
                                pid: containerPids
                            } ) ;
                        },
                        false ).addClass( "csap-only" )
                ) ;




        //
        // os process ps output
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        utils.toDecimals( containerStats.cpuUtil, 1 ),
                        function () {
                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `process-pidstat.sh`,
                                pid: containerPids
                            } ) ;
                        },
                        false )
                ) ;


        //
        // os top
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        utils.toDecimals( containerStats.topCpu, 1 ),
                        function () {
                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `process-top.sh`,
                                pid: containerPids
                            } ) ;
                        },
                        false ).addClass( "csap-only" )
                ) ;




        //
        // os process priority
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.currentProcessPriority,
                        function () {
                            showProcessPriorityDialog( primaryPid, containerStats.currentProcessPriority ) ;
                        },
                        false )
                ) ;


        //
        // threads
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.threadCount,
                        function () {
                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `process-threads.sh`,
                                pid: containerPids
                            } ) ;
                        },
                        false )
                ) ;




        //
        // rss is provided as kb
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.rssMemory * 1024,
                        function () {
                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `process-memory.sh`,
                                pid: primaryPid
                            } ) ;
                        } )
                ) ;


        //
        // virtualMemory is provided as kb
        //
        $processRow.append(
                buildNumberCell( isProcessRunning, containerStats.virtualMemory * 1024 ) ) ;


        //
        // file counts
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.fileCount,
                        function () {
                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `linux-open-file-by-pid.sh`,
                                pid: containerPids
                            } ) ;
                        },
                        false ).addClass( "csap-only" )
                ) ;



        //
        // process parameters
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.runHeap,
                        function () {
                            let $command = jQuery( '<a/>', {
                                href: "#run-command",
                                "data-targeturl": `${ utils.getOsUrl() }/process/report/${ primaryPid }`
                            } )

                            hostOperations.commandRunner( $command ) ;
                        },
                        false ).addClass( "csap-only" ).addClass( "ptab-params code-block" ).removeClass( "numeric" )
                ) ;




        if ( !isCsapFilterChecked ) {
            let commaRegex = new RegExp( ',', 'g' ) ;
            // error check
            let diskColumn = containerStats.diskUtil.replace( commaRegex, ', ' ) ;

            let wrapClass = "numeric" ;
            if ( $wrapProcessTableCheckbox.is( ":checked" ) ) {
                wrapClass = "" ;
            }

            jQuery( '<td/>', {
                class: `${ wrapClass } processArgs`,
                html: diskColumn
            } ).appendTo( $processRow ) ;

        } else {

            //
            // disk usage is provided as mb
            //
            $processRow.append(
                    buildNumberCell(
                            isProcessRunning,
                            containerStats.diskUtil * 1024 * 1024,
                            function () {
                                utils.launchFiles( {
                                    serviceAndClear: processName
                                } ) ;
                            } )
                    ) ;

        }




        //
        // disk reads is provided as kb
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.diskReadKb * 1024,
//                        getRandomInt( 10 ) * getRandomInt( 1024 ) * 1024,
                        function () {

                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `process-pidstat.sh`,
                                pid: primaryPid
                            } ) ;

                        } ).addClass( "csap-only" )
                ) ;


        //
        // disk writes is provided as kb
        //
        $processRow.append(
                buildNumberCell(
                        isProcessRunning,
                        containerStats.diskWriteKb * 1024,
                        function () {

                            utils.launchScript( {
                                serviceName: processLabel,
                                template: `process-pidstat.sh`,
                                pid: primaryPid
                            } ) ;

                        } ).addClass( "csap-only" )
                ) ;


        $processBody.append( $processRow ) ;

        return containerStats.rssMemory ;
    }

    function buildNumberCell( isProcessRunning, number, clickFunction, isBytes = true ) {

        let $cell ;
        if ( !isProcessRunning
                || ( number !== undefined
                        && ( number === 0 || number === "0" ) ) ) {
            $cell = jQuery( '<td/>', { } ) ;

        } else if ( isBytes ) {
            $cell = utils.buildMemoryCell( number ) ;

        } else {
            $cell = jQuery( '<td/>', { class: "numeric", text: number } ) ;
        }

        if ( clickFunction && isProcessRunning ) {
            jQuery( '<button/>', {
                class: `csap-icon csap-go`,
                title: "Launch report"
            } )
                    .click( clickFunction )
                    .appendTo( $cell ) ;
        }

        return $cell ;

    }

    function buildTreeButton( pid ) {

        let $description = jQuery( '<div/>', { } ) ;

        let id = "processkill-" + pid ;
        let $button = jQuery( '<button/>', {
            "id": id,
            "class": "csap-icon csap-crop tree-pid-button",
            "title": "Show process tree",
            "data-pid": pid
        } ) ;

        $button.appendTo( $description ) ;

        return $description.html() ;
    }

    function buildKillButton( pid ) {

        let $description = jQuery( '<div/>', { } ) ;

        let id = "processkill-" + pid ;
        let $button = jQuery( '<button/>', {
            "id": id,
            "class": "csap-icon csap-remove kill-pid-button",
            "title": "Kill pid",
            "data-pid": pid
        } ) ;

        $button.appendTo( $description ) ;

        return $description.html() ;
    }

    function updateHostCpuTable( mpStatCommandOutput ) {
        let mpTable = $( "#mpTable tbody" ) ;
        mpTable.empty() ;
        for ( let key in mpStatCommandOutput ) {
            // if ( ! isInt(threadCount[key].pid) ) continue ;

            let mpRow = jQuery( '<tr></tr>', { } ).appendTo( mpTable ) ;

            mpRow.append( jQuery( '<td></td>', {
                text: mpStatCommandOutput[key].cpu
            } ) ) ;

            mpRow.append( jQuery( '<td></td>', {
                class: "num",
                text: mpStatCommandOutput[key].puser
            } ) ) ;

            mpRow.append( jQuery( '<td></td>', {
                class: "num",
                text: mpStatCommandOutput[key].psys
            } ) ) ;

            mpRow.append( jQuery( '<td></td>', {
                class: "num",
                text: mpStatCommandOutput[key].pio
            } ) ) ;

            mpRow.append( jQuery( '<td></td>', {
                class: "num",
                text: mpStatCommandOutput[key].pidle
            } ) ) ;

            mpRow.append( jQuery( '<td></td>', {
                class: "num",
                text: mpStatCommandOutput[key].intr
            } ) ) ;


            mpTable.append( mpRow ) ;
        }
        mpTable.trigger( "update" ) ; // update table sorter
    }

    function showProcessPriorityDialog( pid, currentPriorityForProcess ) {

        $( ".ajs-dialog .priorityDesc" ).val( currentPriorityForProcess ) ;


        let newItemDialog = alertify.confirm( $( "#priorityPrompt" ).html() ) ;

        newItemDialog.setting( {
            title: "Caution: Modify linux process priority",
            resizable: false,
            'labels': {
                ok: 'Temporarily Modify',
                cancel: 'Cancel Request'
            },
            'onok': function () {
                let updatedPriority = $( ".ajs-dialog .priorityDesc" ).val() ;

                if ( updatedPriority == "999" )
                    updatedPriority = currentPriorityForProcess ;

                alertify.notify( "Sending request to renice pid: " + pid + " to priority: " + updatedPriority ) ;
                setTimeout( function () {
                    updatePriority( pid, updatedPriority ) ;
                }, 500 ) ;

            },
            'oncancel': function () {
                alertify.warning( "Canceled Request" ) ;
            }

        } ) ;
    }



} ) ;