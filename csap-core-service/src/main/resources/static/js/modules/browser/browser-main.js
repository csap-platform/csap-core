

console.log( `loading imports` );

import "../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";



import _browserUtils from "./utils.js"
import _agent from "./agent/agent-loader.js"
import _preferences from "./preferences.js"

import performance from "./performance/performance.js"


import projects from "./projects/project.js"

import services from "./services/services.js"

import hosts from "./hosts.js"

_dom.onReady( function () {

    let appScope = new browser_application( globalThis.settings );

    // _dialogs.loading( "start up" );

    appScope.initialize();


} );

/**
 *  closure for initializing
 */
function browser_application() {


    let historyNavigationClicked = false;

    let autoRefreshTimer = 0;
    let windowResizeTimer = null;


    const $browser = $( "body#manager" );
    const $betweenHeaderAndFooter = $( ">section", $browser );
    const $uiTemplates = $( ">aside", $browser );
    const $content = $( "article.content", $betweenHeaderAndFooter );

    const $tabContainers = _browserUtils.findNavigation( "#tabs" );
    const $navigatorTabs = $( "div.tab-primary", $tabContainers );
    const $navigatorMenus = $( "div.tab-menu >span", $tabContainers );

    const $inactiveContent = $( "#manager-inactive-content", $uiTemplates );
    const layout = _browserUtils.getParameterByName( "layout" );


    let historySuffix = "";

    let navigationOperations = {
        "agent-tab": _agent.show,
        "performance-tab": performance.show,
        "projects-tab": projects.show,
        "services-tab": services.show,
        "hosts-tab": hosts.show,
        "preferences-tab": _preferences.show
    }
        ;

    let $defaultTab = $( "#performance-tab" );


    let lastNavigationClickedLabel = null;
    this.initialize = function () {

        _dom.logSection( `initializing browser_application` );


        //
        //  Before anything: store request parameters for use during cross launchs
        //
        _browserUtils.setPageParameters();

        let pageParams = new Object();
        for ( const [ key, value ] of _browserUtils.getPageParameters().entries() ) {
            pageParams[ key ] = value;
        }

        console.log( `pageParams: `, pageParams );

        // _csapCommon.configureCsapAlertify();


        if ( !layout ) {
            _preferences.initialize();
            projects.initialize();
            services.initialize();
            hosts.initialize();
            performance.initialize();
        }


        register_navigation();





        $( "#bar-alerts" ).click( function () {
            $( "#nav-performance-alert" ).trigger( "click" );
        } );

        $( "#bar-backlog" ).click( function () {
            $( "#nav-performance-backlog" ).trigger( "click" );
        } );


        _browserUtils.initialize(
            autoRefresh,
            gotoMenu,
            projects.activeProjectFunction() );

        let goDefault = true;
        //                if ( !IS_FIRST_ACCESS ) {


        console.log( ` service: '${ _browserUtils.getPageParameters().get( "service" ) }'` );

        if ( window.location.hash ) {

            let locator = window.location.hash.substring( 1 );
            gotoMenu( locator );
            goDefault = false;
        } else {
            console.log( `default location will be used` );
        }

        //                }
        if ( AGENT_MODE ) {
            $defaultTab = _browserUtils.findNavigation( "#agent-tab" );
        }
        ;
        if ( goDefault ) {
            // trigger refresh process and display initial tab
            $( ".tab-primary ", $defaultTab ).click();
        }

        $( window ).resize( function () {
            clearTimeout( windowResizeTimer );
            windowResizeTimer = setTimeout( windowResize, 200 );
        } );


        $( window ).on( 'popstate', function ( event ) {
            console.log( "history: popstate event - backing nav" );

            if ( event && event.originalEvent && event.originalEvent.state ) {

                historyNavigationClicked = true;
                let tab_menu = event.originalEvent.state;
                gotoMenu( tab_menu );

            }
        } );


    }

    function gotoMenu( tab_menu ) {
        let menuNavItems = tab_menu.split( "," );

        let tab = menuNavItems[ 0 ];
        let menu = menuNavItems[ 1 ];

        console.log( `gotoMenu: ${ tab }, ${ menu } `, menuNavItems );

        _browserUtils.closeAllDialogs();

        let $tabMenuToRestore = $( "div.tab-primary",
            $( `#${ tab }` ) );

        if ( menu && menu != "none" ) {

            let $menuMatch = _browserUtils.menuMatch( menu, tab );

            if ( menuNavItems.includes( "instances" )
                && menuNavItems.length >= 3 ) {
                let serviceName = menuNavItems[ 2 ];
                services.selectService( serviceName );
                if ( menuNavItems.length === 4 ) {
                    historySuffix = `,${ serviceName },${ menuNavItems[ 3 ] }`
                    setTimeout( function () {

                        console.log( `state launch - waiting for instances to load: ${ menuNavItems[ 3 ] }` );
                        tabMenuSelected( _browserUtils.menuMatch( menuNavItems[ 3 ], tab ) );

                    }, 1000 );
                }
            } else if ( menuNavItems.includes( "kpod-instances" )
                && menuNavItems.length >= 4 ) {
                let namespace = menuNavItems[ 2 ];
                let owner = menuNavItems[ 3 ];
                services.selectPodOwner( namespace, owner );
                if ( menuNavItems.length === 5 ) {
                    let podOperation = menuNavItems[ 4 ];
                    console.log( `podOperation: ${ podOperation } ` );
                    historySuffix = `,${ namespace },${ owner },${ podOperation }`
                    setTimeout( function () {

                        tabMenuSelected( _browserUtils.menuMatch( podOperation, tab ) );

                    }, 500 );
                }
            } else {
                tabMenuSelected( $menuMatch );
            }

        } else {
            tabSelected( $tabMenuToRestore );
        }
    }

    function windowResize() {

        services.reDraw();
        performance.reDraw();
    }

    function autoRefresh( forceHostRefresh = false ) {
        clearTimeout( autoRefreshTimer );

        let $completedDefer = performance.refreshStatus( forceHostRefresh );
        $.when( $completedDefer ).done( function () {
            autoRefreshTimer = setTimeout( autoRefresh, _browserUtils.getRefreshInterval() );
        } );

        return $completedDefer;
    }

    function register_navigation() {

        $navigatorMenus.click( function () {
            tabMenuSelected( $( this ) );
        } );

        $navigatorTabs.click( function () {
            tabSelected( $( this ) );
        } );

    }

    function navItemClicked( menuId ) {
        _browserUtils.loading( `Loading ${ menuId }` );
        _preferences.snapNavClosed();

    }

    function tabMenuSelected( $menuClicked ) {

        let $tabMenu = $menuClicked.parent();
        let $tabSelected = $tabMenu.parent();
        let menuId = $menuClicked.data( "path" );

        //        navItemClicked( menuId ) ;
        console.log( `Menu selected: ${ menuId }` );

        $( ">span", $tabMenu ).removeClass( "active" );
        $menuClicked.addClass( "active" );

        $( "div.tab-primary", $tabSelected ).trigger( "click" );

    }

    function tabSelected( $tabButton ) {

        //
        //  Save stateful tab positions: limited number
        //

        console.debug( `lastNavigationClickedLabel: ${ lastNavigationClickedLabel }` );
        for ( let navListener of _browserUtils.getNavChangeFunctions() ) {
            console.debug( `navListener: `, navListener );
            navListener( lastNavigationClickedLabel );
        }

        let $currentTab = $( ">.active", $tabContainers );
        $currentTab.removeClass( "active" );

        clearTimeout( autoRefreshTimer );
        $( ">div", $content ).appendTo( $inactiveContent );

        //
        //  Primary Tab
        //
        let $tabSelected = $tabButton.parent();
        $tabSelected.addClass( "active" );

        let tabLabel = $( ">span", $tabButton ).text();
        let tabId = $tabSelected.attr( "id" );

        let activeItem = tabId;

        navItemClicked( tabId );


        let contentId = `${ tabId }-content`;
        let $activatedContent = $( "#" + contentId );


        //
        //  Optional tab menu
        //
        let $menuContent = null;
        let $tabMenu = $( ".tab-menu", $tabSelected );

        if ( $currentTab.attr( "id" ) !== $tabSelected.attr( "id" ) ) {
            $tabMenu.hide();
            $tabMenu.show( 500 );
        }

        let activeMenuItem = null;
        let menuPath = "none";
        if ( $tabMenu.length > 0 ) {

            $( ">div", $activatedContent ).hide();
            menuPath = $( ".active", $tabMenu ).data( "path" );
            activeMenuItem = tabId + "-" + menuPath;
            console.log( `activeMenuItem: ${ activeMenuItem }` );

            $menuContent = $( `#${ activeMenuItem }`, $activatedContent );
            $menuContent.show();

            activeItem = activeMenuItem;

        }
        if ( !historyNavigationClicked ) {
            let location = `${ tabId },${ menuPath }${ historySuffix }`;

            if ( menuPath == "instances" ) {
                location += `,${ services.getSelectedService() }`;
            } else if ( menuPath == "kpod-instances" ) {
                location += `,${ services.namespaceSelected() },${ services.selectPodOwner() }`;
            } else if ( menuPath == "pod-logs" ) {
                location = `${ tabId },kpod-instances,${ services.namespaceSelected() },${ services.selectPodOwner() },pod-logs`;
            } else if ( menuPath == "pod-describe" ) {
                location = `${ tabId },kpod-instances,${ services.namespaceSelected() },${ services.selectPodOwner() },pod-describe`;
            }

            console.log( `history: pushing: ${ location } ` );

            history.pushState( location, null, `#${ location }` );
        }
        historyNavigationClicked = false;


        console.log( `register_navigation() ${ tabLabel } id: ${ activeItem } selected, lastNavItemSelected: ${ lastNavigationClickedLabel } ` );
        _browserUtils.setLastLocation( lastNavigationClickedLabel );
        let forceHostRefresh = false;
        if ( lastNavigationClickedLabel === activeItem ) {
            forceHostRefresh = true;
        }
        lastNavigationClickedLabel = activeItem;

        //
        //  Update selected view, and perform registered actions
        //
        $activatedContent.appendTo( $content );

        let viewLoadPromise = null;

        let operation = navigationOperations[ tabId ];
        if ( operation ) {
            viewLoadPromise = operation( $menuContent, forceHostRefresh, menuPath );
        }

        // handle null promises;
        if ( viewLoadPromise == null ) {
            console.info( ` no promise found for ${ tabId }` );
            viewLoadPromise = new Promise( ( resolve, reject ) => {
                resolve( "null promise" )
            } );
        }

        console.debug( `viewLoadPromise`, viewLoadPromise );

        const viewLoaded = function ( resolvedValue ) {

            console.debug( `resolved promises`, resolvedValue );
            _browserUtils.loadingComplete();
            if ( lastNavigationClickedLabel == "performance-tab" ) {
                // just displayed - so schedule
                autoRefreshTimer = setTimeout( autoRefresh, _browserUtils.getRefreshInterval() );
            } else {
                autoRefresh();
            }

        } ;

        viewLoadPromise
            .then( viewLoaded )
            .catch( ( e ) => {
                _browserUtils.loadingComplete();
                _dom.logHead( `Failed loading view`);
                console.warn(`source`,e) ;
            } );


    }




}
