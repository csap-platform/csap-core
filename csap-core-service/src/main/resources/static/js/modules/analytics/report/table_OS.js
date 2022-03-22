//define( [ "model", "./dataService", "./tableUtils", "./utils" ], function ( model, dataService, tableUtils, utils ) {



import _dom from "../../utils/dom-utils.js";

import model from "../model.js";

import tableUtils from "./tableUtils.js";

import utils from "./utils.js";


const osTable = table_os();

export default osTable


function table_os() {

    _dom.logHead( "Module loaded" );



    return {
        //
        buildSummaryRow: function ( rowJson, tableRow, rowData ) {
            buildSummaryRow( rowJson, tableRow, rowData );
        },
        //
        buildServiceDetailRow: function ( rowJson, tableRow, rowData ) {
            buildServiceDetailRow( rowJson, tableRow, rowData );
        },
        //
    }

    function getProjectInstancesLength() {

        if ( model.getPackages() == null )
            return 0; // hack for start

        return model.getPackageDetails( model.getSelectedProject() ).instances.instanceCount.length;
    }

    function buildSummaryRow( rowJson, tableRow, rowData ) {

        // console.log( JSON.stringify(rowJson, null,"\t") )

        tableRow.append( jQuery( '<td/>', {
            class: "projectColumn",
            text: rowData.project + " - " + rowData.lifecycle
        } ) );

        $( "#serviceLabel" ).html( "OS  Resource Report" );
        let $serviceDetailsLink = jQuery( '<a/>', {
            class: "simple",
            target: "_blank",
            href: uiSettings.analyticsUiUrl + "?service=" + rowData.serviceName
                + "&life=" + $( "#lifeSelect" ).val() + "&project="
                + model.getSelectedProject(),
            text: rowData.serviceName
        } )

        $serviceDetailsLink.click( function () {
            utils.launchServiceOsReport( rowData.serviceName );
            return false;
        } );

        let $firstColumn = jQuery( '<td/>', {
            class: "col1"
        } );
        $firstColumn.html( $serviceDetailsLink );
        tableRow.append( $firstColumn );

        //        let numberOfHosts = "-" ;
        //        for ( let i = 0 ; i < getProjectInstancesLength() ; i++ ) {
        //            let instanceCount = model.getPackageDetails( model.getSelectedProject() ).instances.instanceCount[i] ;
        //            // console.log( JSON.stringify(rowJson, null,"\t") )
        //            if ( instanceCount.serviceName == rowData.serviceName ) {
        //                numberOfHosts = instanceCount.count ;
        //                break ;
        //            }
        //        }

        //        tableRow.append( jQuery( '<td/>', {
        //            class: "numeric alt",
        //            text: numberOfHosts
        //        } ) ) ;

        let divideBy = 1;
        if ( rowData.countCsapMean ) {
            //21.02 addition
            let numDecimals = 0;
            let numContainers = rowData.countCsapMean;
            if ( numContainers != Math.round( numContainers ) ) {
                numDecimals = 1;
            }
            tableUtils.addCell( tableRow, rowData, tableUtils.getFieldSummaryAppendix( "countCsapMean" ), numDecimals );
            if ( $( "#show-instance-summary" ).is( ":checked" )
                && numContainers >= 1 ) {
                divideBy = 1 / numContainers;
            }
            // console.log(`Using numContainers ${numContainers}`) ; 

        } else {
            // legacy estimate of containers

            let numberOfSamplesIn24Hours = 24 * 60 * 2;

            // instances column, contrived because container count not included. 
            tableUtils.addCell( tableRow, rowData, "numberOfSamples", 1, numberOfSamplesIn24Hours );

            let numberOfInstances = rowData.numberOfSamples / numberOfSamplesIn24Hours;
            if ( $( "#show-instance-summary" ).is( ":checked" )
                && numberOfInstances >= 1 ) {
                divideBy = 1 / numberOfInstances;
            }

            // console.log(`Using numberOfInstances ${numberOfInstances}`) ; 
        }

        console.debug( `divideBy ${ divideBy }` );

        tableUtils.addCell( tableRow, rowData, "numberOfSamples", 0 );


        tableUtils.addCell( tableRow, rowData, "topCpu", 1, divideBy, "%", 40 );

        tableUtils.addCell( tableRow, rowData, "threadCount", 0, divideBy, "", 100 );

        tableUtils.addCell( tableRow, rowData, "rssMemory", 0, divideBy, "", 1000 );

        tableUtils.addCell( tableRow, rowData, "diskUtil", 0, divideBy, "", 350 );

        tableUtils.addCell( tableRow, rowData, "diskWriteKb", 0, divideBy, "", 350 );
        tableUtils.addCell( tableRow, rowData, "diskReadKb", 0, divideBy, "", 350 );

        tableUtils.addCell( tableRow, rowData, "fileCount", 0, divideBy, "", 500 );

        tableUtils.addCell( tableRow, rowData, "socketCount", 0, divideBy, "", 30 );

    }



    // Service
    function buildServiceDetailRow( rowJson, tableRow, rowData ) {

        // console.log( JSON.stringify(rowJson, null,"\t") )

        $( "#serviceLabel" ).html( rowData.serviceName + " OS Resources " );

        tableRow.append( jQuery( '<td/>', {
            class: "projectColumn",
            text: rowData.project + " - " + rowData.lifecycle
        } ) );

        tableRow.append( tableUtils.buildHostLinkColumn( rowData.host ) );



        let divideBy = 1;
        if ( rowData.countCsapMean ) {
            //21.02 addition
            tableUtils.addCell( tableRow, rowData, tableUtils.getFieldSummaryAppendix( "countCsapMean" ), 0 );
            let numContainers = rowData.countCsapMean;
            if ( $( "#show-instance-summary" ).is( ":checked" )
                && numContainers >= 1 ) {
                divideBy = 1 / numContainers;
            }
            console.log( `Using numContainers ${ numContainers }` );
        } else {

            let numberOfSamplesIn24Hours = 24 * 60 * 2;
            tableUtils.addCell( tableRow, rowData, "numberOfSamples", 1, numberOfSamplesIn24Hours );

            let numberOfInstances = rowData.numberOfSamples / numberOfSamplesIn24Hours;
            if ( $( "#show-instance-summary" ).is( ":checked" )
                && numberOfInstances >= 1 ) {
                divideBy = 1 / numberOfInstances;
            }

        }

        tableUtils.addCell( tableRow, rowData, "numberOfSamples", 0 );

        tableUtils.addCell( tableRow, rowData, "topCpu", 1, divideBy, "%", 40 );

        tableUtils.addCell( tableRow, rowData, "threadCount", 0, divideBy, "", 100 );

        tableUtils.addCell( tableRow, rowData, "rssMemory", 0, divideBy, "", 1000 );

        tableUtils.addCell( tableRow, rowData, "diskUtil", 0, divideBy, "", 350 );

        tableUtils.addCell( tableRow, rowData, "diskWriteKb", 0, divideBy, "", 350 );
        tableUtils.addCell( tableRow, rowData, "diskReadKb", 0, divideBy, "", 350 );

        tableUtils.addCell( tableRow, rowData, "fileCount", 0, divideBy, "", 500 );

        tableUtils.addCell( tableRow, rowData, "socketCount", 0, divideBy, "", 30 );

    }


}