// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
    waitSeconds: 30,
    paths: {
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        // CRITICAL: use relative paths to leverage spring boot VersionResourceResolver
        deployment: "../../deployment/modules",
        editor: "../../editor/modules",
        file: "../../file/modules",
        browser: "../../app-browser/modules",
        csapflot: "../../csapLibs/modules/csap-flot-helpers"
    },
    shim: {
        csapflot: {
            exports: 'csapflot',
            init: function () {
                return {
                    getUsCentralTime: getUsCentralTime,
                    calculateUsCentralDays: calculateUsCentralDays
                } ;
            }
        }
    },
    packages: [ ]
} ) ;
//require( [ "preferences", "projects/project", "services/services", "performance/performance", "hosts", "browser/utils", "deployment/app-cluster-browser", "deployment/deployment-backlog" ], function ( preferences, projects, services, performance, hosts, utils, clusterBrowser, deploymentBacklog ) {
require( [ ], function () {

    // Shared variables need to be visible prior to scope

    $( document ).ready( function () {
        console.log( "Document ready: loading modules xxx ...." ) ;

        let $loadingPanel = $( "#loading-project-message" ) ;
        $( "div", $loadingPanel ).text( "Loading Application" ) ;
        $loadingPanel.css( "visibility", "visible" ) ;
        deferredLoadingToEnableJquerySelectors() ;
    } ) ;

    let mainModules = [ "browser/utils", "preferences", "projects/project", "services/services", "performance/performance", "hosts", "agent/agent-loader" ] ;
    function deferredLoadingToEnableJquerySelectors() {
        require( mainModules, function ( utils, preferences, projects, services, performance, hosts, agent ) {

            console.log( "\n\n modules loading complete \n\n" ) ;

            $( document ).ready( function () {
                initialize() ;
            } )

            let historyNavigationClicked = false ;

            let autoRefreshTimer = 0 ;
            let windowResizeTimer = null ;


            const $browser = $( "body#manager" ) ;
            const $betweenHeaderAndFooter = $( ">section", $browser ) ;
            const $uiTemplates = $( ">aside", $browser ) ;
            const $content = $( "article.content", $betweenHeaderAndFooter ) ;

            const $tabContainers = utils.findNavigation( "#tabs" ) ;
            const $navigatorTabs = $( "div.tab-primary", $tabContainers ) ;
            const $navigatorMenus = $( "div.tab-menu >span", $tabContainers ) ;

            const $inactiveContent = $( "#manager-inactive-content", $uiTemplates ) ;
            const layout = utils.getParameterByName( "layout" ) ;


            let historySuffix = "" ;

            let  navigationOperations = {
                "agent-tab": agent.show,
                "performance-tab": performance.show,
                "projects-tab": projects.show,
                "services-tab": services.show,
                "hosts-tab": hosts.show,
                "preferences-tab": preferences.show
            }
            ;

            let $defaultTab = $( "#performance-tab" ) ;


            let lastNavigationClickedLabel = null ;
            function initialize() {
                console.log( "\n\n\n --------  Starting module initialization  -------- \n\n\n" ) ;


                //
                //  Before anything: store request parameters for use during cross launchs
                //
                utils.setPageParameters( ) ;

                let pageParams = new Object() ;
                for ( const [ key, value ] of utils.getPageParameters().entries() ) {
                    pageParams[ key ] = value ;
                }

                console.log( `pageParams: `, pageParams ) ;

                CsapCommon.configureCsapAlertify() ;


                if ( !layout ) {
                    preferences.initialize() ;
                    projects.initialize() ;
                    services.initialize() ;
                    hosts.initialize() ;
                    performance.initialize() ;
                }


                register_navigation() ;





                $( "#bar-alerts" ).click( function () {
                    $( "#nav-performance-alert" ).trigger( "click" ) ;
                } ) ;

                $( "#bar-backlog" ).click( function () {
                    $( "#nav-performance-backlog" ).trigger( "click" ) ;
                } ) ;


                utils.initialize(
                        autoRefresh,
                        gotoMenu,
                        projects.activeProjectFunction() ) ;

                let goDefault = true ;
//                if ( !IS_FIRST_ACCESS ) {


                console.log( ` service: '${ utils.getPageParameters().get( "service" ) }'` ) ;

                if ( window.location.hash ) {

                    let locator = window.location.hash.substring( 1 ) ;
                    gotoMenu( locator ) ;
                    goDefault = false ;
                } else {
                    console.log( `default location will be used` ) ;
                }

//                }
                if ( AGENT_MODE ) {
                    $defaultTab = utils.findNavigation( "#agent-tab" ) ;
                }
                ;
                if ( goDefault ) {
                    // trigger refresh process and display initial tab
                    $( ".tab-primary ", $defaultTab ).click() ;
                }

                $( window ).resize( function () {
                    clearTimeout( windowResizeTimer ) ;
                    windowResizeTimer = setTimeout( windowResize, 200 ) ;
                } ) ;


                $( window ).on( 'popstate', function ( event ) {
                    console.log( "history: popstate event - backing nav" ) ;

                    if ( event && event.originalEvent && event.originalEvent.state ) {

                        historyNavigationClicked = true ;
                        let tab_menu = event.originalEvent.state ;
                        gotoMenu( tab_menu ) ;

                    }
                } ) ;


            }

            function gotoMenu( tab_menu ) {
                let menuNavItems = tab_menu.split( "," ) ;

                let tab = menuNavItems[0] ;
                let menu = menuNavItems[1] ;

                console.log( `gotoMenu: ${tab}, ${menu} `, menuNavItems ) ;

                utils.closeAllDialogs() ;

                let $tabMenuToRestore = $( "div.tab-primary",
                        $( `#${ tab }` ) ) ;

                if ( menu && menu != "none" ) {

                    let $menuMatch = utils.menuMatch( menu, tab ) ;

                    if ( menuNavItems.includes( "instances" )
                            && menuNavItems.length >= 3 ) {
                        let serviceName = menuNavItems[2] ;
                        services.selectService( serviceName ) ;
                        if ( menuNavItems.length === 4 ) {
                            historySuffix = `,${serviceName},${ menuNavItems[3] }`
                            setTimeout( function () {

                                console.log( `state launch - waiting for instances to load: ${menuNavItems[3]}` ) ;
                                tabMenuSelected( utils.menuMatch( menuNavItems[3], tab ) ) ;

                            }, 1000 ) ;
                        }
                    } else if ( menuNavItems.includes( "kpod-instances" )
                            && menuNavItems.length >= 4 ) {
                        let namespace = menuNavItems[2] ;
                        let owner = menuNavItems[3] ;
                        services.selectPodOwner( namespace, owner ) ;
                        if ( menuNavItems.length === 5 ) {
                            let podOperation = menuNavItems[4] ;
                            console.log( `podOperation: ${ podOperation} ` ) ;
                            historySuffix = `,${ namespace },${ owner },${ podOperation }`
                            setTimeout( function () {

                                tabMenuSelected( utils.menuMatch( podOperation, tab ) ) ;

                            }, 500 ) ;
                        }
                    } else {
                        tabMenuSelected( $menuMatch ) ;
                    }

                } else {
                    tabSelected( $tabMenuToRestore ) ;
                }
            }

            function windowResize() {

                services.reDraw() ;
                performance.reDraw() ;
            }

            function autoRefresh( forceHostRefresh = false ) {
                clearTimeout( autoRefreshTimer ) ;

                let $completedDefer = performance.refreshStatus( forceHostRefresh ) ;
                $.when( $completedDefer ).done( function () {
                    autoRefreshTimer = setTimeout( autoRefresh, utils.getRefreshInterval() ) ;
                } ) ;

                return $completedDefer ;
            }

            function register_navigation() {

                $navigatorMenus.click( function ( ) {
                    tabMenuSelected( $( this ) ) ;
                } ) ;

                $navigatorTabs.click( function ( ) {
                    tabSelected( $( this ) ) ;
                } ) ;

            }

            function navItemClicked( menuId ) {
                utils.loading( `Loading ${menuId}` ) ;
                preferences.snapNavClosed() ;

            }

            function tabMenuSelected( $menuClicked ) {

                let $tabMenu = $menuClicked.parent() ;
                let $tabSelected = $tabMenu.parent() ;
                let menuId = $menuClicked.data( "path" ) ;

//        navItemClicked( menuId ) ;
                console.log( `Menu selected: ${ menuId }` ) ;

                $( ">span", $tabMenu ).removeClass( "active" ) ;
                $menuClicked.addClass( "active" ) ;

                $( "div.tab-primary", $tabSelected ).trigger( "click" ) ;

            }

            function tabSelected( $tabButton ) {

                //
                //  Save stateful tab positions: limited number
                //

                console.debug( `lastNavigationClickedLabel: ${lastNavigationClickedLabel}` ) ;
                for ( let navListener of utils.getNavChangeFunctions() ) {
                    console.debug( `navListener: `, navListener ) ;
                    navListener( lastNavigationClickedLabel ) ;
                }

                let $currentTab = $( ">.active", $tabContainers ) ;
                $currentTab.removeClass( "active" ) ;

                clearTimeout( autoRefreshTimer ) ;
                $( ">div", $content ).appendTo( $inactiveContent ) ;

                //
                //  Primary Tab
                //
                let $tabSelected = $tabButton.parent() ;
                $tabSelected.addClass( "active" ) ;

                let tabLabel = $( ">span", $tabButton ).text() ;
                let tabId = $tabSelected.attr( "id" ) ;

                let activeItem = tabId ;

                navItemClicked( tabId ) ;


                let contentId = `${ tabId }-content` ;
                let $activatedContent = $( "#" + contentId ) ;


                //
                //  Optional tab menu
                //
                let $menuContent = null ;
                let $tabMenu = $( ".tab-menu", $tabSelected ) ;

                if ( $currentTab.attr( "id" ) !== $tabSelected.attr( "id" ) ) {
                    $tabMenu.hide() ;
                    $tabMenu.show( 500 ) ;
                }

                let activeMenuItem = null ;
                let menuPath = "none" ;
                if ( $tabMenu.length > 0 ) {

                    $( ">div", $activatedContent ).hide() ;
                    menuPath = $( ".active", $tabMenu ).data( "path" ) ;
                    activeMenuItem = tabId + "-" + menuPath ;
                    console.log( `activeMenuItem: ${activeMenuItem}` ) ;

                    $menuContent = $( `#${ activeMenuItem }`, $activatedContent ) ;
                    $menuContent.show() ;

                    activeItem = activeMenuItem ;

                }
                if ( !historyNavigationClicked ) {
                    let location = `${ tabId },${ menuPath }${ historySuffix }` ;

                    if ( menuPath == "instances" ) {
                        location += `,${ services.getSelectedService() }` ;
                    } else if ( menuPath == "kpod-instances" ) {
                        location += `,${ services.namespaceSelected() },${ services.selectPodOwner() }` ;
                    } else if ( menuPath == "pod-logs" ) {
                        location = `${ tabId },kpod-instances,${ services.namespaceSelected() },${ services.selectPodOwner() },pod-logs` ;
                    } else if ( menuPath == "pod-describe" ) {
                        location = `${ tabId },kpod-instances,${ services.namespaceSelected() },${ services.selectPodOwner() },pod-describe` ;
                    }

                    console.log( `history: pushing: ${ location} ` ) ;

                    history.pushState( location, null, `#${location}` ) ;
                }
                historyNavigationClicked = false ;


                console.log( `register_navigation() ${ tabLabel } id: ${ activeItem } selected, lastNavItemSelected: ${lastNavigationClickedLabel} ` ) ;
                utils.setLastLocation( lastNavigationClickedLabel ) ;
                let forceHostRefresh = false ;
                if ( lastNavigationClickedLabel === activeItem ) {
                    forceHostRefresh = true ;
                }
                lastNavigationClickedLabel = activeItem ;

                //
                //  Update selected view, and perform registered actions
                //
                $activatedContent.appendTo( $content ) ;

                let $contentLoaded = null ;

                let operation = navigationOperations[ tabId ] ;
                if ( operation ) {
                    $contentLoaded = operation( $menuContent, forceHostRefresh, menuPath ) ;
                } else {
                    console.warn( ` no operation for ${ tabId }` ) ;
                }


                $.when( $contentLoaded ).done( function () {
                    utils.loadingComplete() ;
                    if ( lastNavigationClickedLabel == "performance-tab" ) {
                        // just displayed - so schedule
                        autoRefreshTimer = setTimeout( autoRefresh, utils.getRefreshInterval() ) ;
                    } else {
                        autoRefresh() ;
                    }

                } )


            }




        } ) ;

    }

} ) ;