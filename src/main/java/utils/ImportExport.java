/*
 *
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import java.util.Scanner;

//todo discuss: don't we need to get trustStore and password.


class ImportExport{

    private static final Log log = LogFactory.getLog(ImportExport.class);

    public static void main(String[] args) {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        // create a scanner to read the command-line input
        Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);

        System.out.print("Enter user name :");
        config.setUsername(scanner.next());

        System.out.print("Enter password : ");
        config.setPassword(scanner.next().toCharArray());

        //setting up default configurations
        try {
            ImportExportUtils.setDefaultConfigurations(config);
        } catch (APIExportException e) {
            log.warn("Unable to set default configurations");
        }
        //setting customized configurations
        setUserInputs(config);

        //configuring log4j properties
        PropertyConfigurator.configure("log4j.properties");///TODO should changed

            if (config.getCheckSSLCertificate()) {
                ImportExportUtils.setSSlCert();
            }
            //registering the client
            String consumerCredentials = "";
            try {
                consumerCredentials = ImportExportUtils.registerClient(config.getUsername(),
                        String.valueOf(config.getPassword()));
                System.out.println("Consumer credential   "+consumerCredentials);
            } catch (APIExportException e) {
                System.out.println("Error occurred while registering the client");
                System.exit(1);
            }
            //handling single API export
            if (StringUtils.isBlank(config.getApiFilePath()) && StringUtils.isBlank(config.getZipFile())) {
                try {
                    APIExporter.singleApiExport(consumerCredentials);
                } catch (APIExportException e) {
                    log.error("Error occurred while exporting the API, API cannot be exported without valid tokens ");
                    System.exit(1);
                }
            }else if(StringUtils.isNotBlank(config.getApiFilePath())&& StringUtils.isBlank(config.getZipFile())){
                APIExporter.bulkApiExport(consumerCredentials);
            }
        //handling API import
        if(StringUtils.isNotBlank(config.getZipFile())){
            try {
                APIImporter.importAPIs(config.getZipFile(),consumerCredentials);
            } catch (APIImportException e) {
                String errorMsg = "Unable to Import the APIs";
                log.error(errorMsg, e);
                System.exit(1);
            }
        }
        }

    private static void setUserInputs(ApiImportExportConfiguration config) {
        //getting inputs from CLI
        String name = System.getProperty(ImportExportConstants.API_NAME);
        String provider = System.getProperty(ImportExportConstants.API_PROVIDER);
        String version = System.getProperty(ImportExportConstants.API_VERSION);
        String configFile = System.getProperty(ImportExportConstants.CONFIG_FILE);
        String bulkFile = System.getProperty(ImportExportConstants.API_LIST);
        boolean sslCheck= Boolean.parseBoolean(System.getProperty(ImportExportConstants.SSL_CERT));
        String log4jFile= System.getProperty(ImportExportConstants.LOG4J_PROP);
        String destinationPath = System.getProperty(ImportExportConstants.DESTINATION);
        String destinationFolderName = System.getProperty(ImportExportConstants.ZIP_NAME);
        String trustStoreUrl= System.getProperty(ImportExportConstants.TRUST_STORE_URL);
        String dcrUrlSegment = System.getProperty(ImportExportConstants.DCR_URL_SEG);
        String publisherUrlSegment = System.getProperty(ImportExportConstants.PUBLISHER_URL_SEG);
        String gatewayUrlSegment = System.getProperty(ImportExportConstants.GATEWAY_URL_SEG);
        String zipFile = System.getProperty(ImportExportConstants.ZIP_FILE_PROP);
        boolean updateApi = Boolean.parseBoolean(System.getProperty
                (ImportExportConstants.UPDATE_API_PROP));
        //setting configurations on the config file
        if(configFile!=null){
                ImportExportUtils.setUserConfigurations(configFile,config);
        }
        //setting the configurations taken by CLI
        if(StringUtils.isNotBlank(name)){ config.setApiName(name); }
        if(StringUtils.isNotBlank(provider)){ config.setApiProvider(provider);}
        if(StringUtils.isNotBlank(version)){ config.setApiVersion(version);}
        if(StringUtils.isNotBlank(bulkFile)){ config.setApiFilePath(bulkFile);}
        if(sslCheck){ config.setCheckSSLCertificate(true);}
        if(StringUtils.isNotBlank(log4jFile)){ config.setLog4JFilePath(log4jFile);}
        if(StringUtils.isNotBlank(destinationPath)){ config.setDestinationPath(destinationPath);}
        if(StringUtils.isNotBlank(destinationFolderName))
        {config.setDestinationFolderName(destinationFolderName);}
        if(StringUtils.isNotBlank(trustStoreUrl)){config.setTrustStoreUrl(trustStoreUrl);}
        if(StringUtils.isNotBlank(dcrUrlSegment)){config.setDcrUrl(dcrUrlSegment);}
        if(StringUtils.isNotBlank(publisherUrlSegment)){config.setPublisherUrl(publisherUrlSegment);}
        if(StringUtils.isNotBlank(gatewayUrlSegment)){config.setGatewayUrl(gatewayUrlSegment);}
        if(StringUtils.isNotBlank(zipFile)){config.setZipFile(zipFile);}
        if(updateApi){config.setUpdateApi(updateApi);}
        }

}
