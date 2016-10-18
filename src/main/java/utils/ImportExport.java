/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import java.io.Console;
import java.util.Scanner;

//todo discuss: don't we need to get trustStore and password.


class ImportExport{

    private static final Log log = LogFactory.getLog(ImportExport.class);
    static Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);

    public static void main(String[] args) {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //getting CLI inputs
        System.out.print("Enter user name :");
        config.setUsername(scanner.next());

        Console console = System.console();
        config.setPassword(console.readPassword("Enter password : "));

        System.out.println();

        //setting up default configurations
        try {
            ImportExportUtils.setDefaultConfigurations(config);
        } catch (UtilException e) {
            String error = "Error occurred while loading the default configurations";
            log.warn(error,e);
        }
        //setting user customized configurations
        setUserInputs(config);

        //enabling SSL certCheck,if certificate validation enabled
        if (config.getCheckSSLCertificate()) {
            ImportExportUtils.setSSlCert();
        }

        //configuring log4j properties
        System.out.println("@@@@@@@@@@@@@@@@2 log4j file "+config.getLog4JFilePath());
        PropertyConfigurator.configure(config.getLog4JFilePath());

        //registering the client
        String consumerCredentials = "";
        try {
            consumerCredentials = ImportExportUtils.registerClient(config.getUsername(),
                    String.valueOf(config.getPassword()));
        } catch (UtilException e) {
            System.out.println("Error occurred while registering the client");
            System.exit(1);
        }
        //if the zip file path is not null continue API import
        if (StringUtils.isNotBlank(config.getZipFile())) {
            try {
                //handling API import
                APIImporter.importAPIs(config.getZipFile(), consumerCredentials);
            } catch (APIImportException e) {
                String errorMsg = "Unable to Import the APIs";
                log.error(errorMsg, e);
                System.exit(1);
            } catch (UtilException e) {
                String errorMsg ="Error occurred while importing the API/APIs";
                log.error(errorMsg,e);
                System.exit(1);
            }
        } else {
            if (StringUtils.isNotBlank(config.getApiFilePath()) &&
                    StringUtils.isBlank(config.getApiName())) {
                //if a cvs file provided and details about a specific API not provided as cmd
                //arguments,bulkExport will be prioritize over single API export
                try {
                    APIExporter.bulkApiExport(consumerCredentials);
                } catch (APIExportException e) {
                    String errorMsg = "Error occurred while attempting to bulk export ";
                    log.error(errorMsg,e);
                    System.exit(1);
                }
            } else {
                try {
                    //handling single API export
                    if (StringUtils.isNotBlank(config.getApiName()) &&
                            StringUtils.isNotBlank(config.getApiFilePath())) {
                        String message = "Only API " + config.getApiName() +
                                " will be exported and api list ignored";
                        log.warn(message);
                    }
                    APIExporter.singleApiExport(consumerCredentials);
                } catch (APIExportException e) {
                    log.error("Error occurred while exporting the API "+config.getApiName());
                    System.exit(1);
                }
            }
        }

    }

    /**
     * This method will get all the CLI inputs and map the properties in to a
     * ApiImportExportConfiguration ojject
     *
     * @param config ApiImportExportConfiguration type object
     */
    private static void setUserInputs(ApiImportExportConfiguration config) {
        //getting inputs from CLI
        String name = System.getProperty(ImportExportConstants.API_NAME);
        String provider = System.getProperty(ImportExportConstants.API_PROVIDER);
        String version = System.getProperty(ImportExportConstants.API_VERSION);
        String configFile = System.getProperty(ImportExportConstants.CONFIG_FILE);
        String bulkFile = System.getProperty(ImportExportConstants.API_LIST);
        String sslCheckString = System.getProperty(ImportExportConstants.SSL_CERT);
        String log4jFile = System.getProperty(ImportExportConstants.LOG4J_PROP);
        String destinationPath = System.getProperty(ImportExportConstants.DESTINATION);
        String destinationFolderName = System.getProperty(ImportExportConstants.ZIP_NAME);
        String trustStoreUrl = System.getProperty(ImportExportConstants.TRUST_STORE_URL);
        String dcrUrlSegment = System.getProperty(ImportExportConstants.DCR_URL_SEG);
        String publisherUrlSegment = System.getProperty(ImportExportConstants.PUBLISHER_URL_SEG);
        String gatewayUrlSegment = System.getProperty(ImportExportConstants.GATEWAY_URL_SEG);
        String zipFile = System.getProperty(ImportExportConstants.ZIP_FILE_PROP);
        String updateApi=System.getProperty(ImportExportConstants.UPDATE_API_PROP);

        //setting configurations on the user input config file
        if (StringUtils.isNotBlank(configFile)) {
            ImportExportUtils.setUserConfigurations(configFile, config);
        }

        //setting the configurations taken from CLI
        if (StringUtils.isNotBlank(name)) {
            config.setApiName(name);
        }
        if (StringUtils.isNotBlank(provider)) {
            config.setApiProvider(provider);
        }
        if (StringUtils.isNotBlank(version)) {
            config.setApiVersion(version);
        }
        if (StringUtils.isNotBlank(bulkFile)) {
            config.setApiFilePath(bulkFile);
        }
        if (StringUtils.isNotBlank(sslCheckString)) {
            config.setCheckSSLCertificate(Boolean.parseBoolean(sslCheckString));
        }
        if (StringUtils.isNotBlank(log4jFile)) {
            config.setLog4JFilePath(log4jFile);
        }
        if (StringUtils.isNotBlank(destinationPath)) {
            config.setDestinationPath(destinationPath);
        }
        if (StringUtils.isNotBlank(destinationFolderName)) {
            config.setDestinationFolderName(destinationFolderName);
        }
        if (StringUtils.isNotBlank(trustStoreUrl)) {
            config.setTrustStoreUrl(trustStoreUrl);
        }
        if (StringUtils.isNotBlank(dcrUrlSegment)) {
            config.setDcrUrl(dcrUrlSegment);
        }
        if (StringUtils.isNotBlank(publisherUrlSegment)) {
            config.setPublisherUrl(publisherUrlSegment);
        }
        if (StringUtils.isNotBlank(gatewayUrlSegment)) {
            config.setGatewayUrl(gatewayUrlSegment);
        }
        if (StringUtils.isNotBlank(zipFile)) {
            config.setZipFile(zipFile);
        }
        if (StringUtils.isNotBlank(updateApi)) {
            config.setUpdateApi(Boolean.parseBoolean(updateApi));
        }
        if(!config.getCheckSSLCertificate()){
            System.out.println("3333333333333333  sert state "+config.getCheckSSLCertificate());
            log.info("SSL certificate validation disabled");
        }
        //validating publisher url
        boolean value = ImportExportUtils.checkPublisherUrl(config.getPublisherUrl());

        while (!value){
            //if incorrect prompting user to re- enter
            System.out.print("Entered publisher url incorrect, check and re-enter : ");
            String url=scanner.next();
            config.setPublisherUrl(url);
            value=ImportExportUtils.checkPublisherUrl(config.getPublisherUrl());
        }

    }

}
