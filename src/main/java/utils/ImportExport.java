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
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter user name :");
        String username = scanner.next();

        System.out.print("Enter password : ");
        char password[]=scanner.next().toCharArray();

        //setting up default configurations
        ImportExportUtils.setDefaultConfigurations(config);

        //setting customized configurations
        setUserInputs(config);

        //configuring log4j properties
        PropertyConfigurator.configure(config.getLog4JFilePath());

        if(config.getCheckSSLCertificate()==false){
           //ImportExportUtils.disableCertificateValidation();
        }else {
            ImportExportUtils.setSSlCert();
        }
            //registering the client
            String consumerCredentials=null;
            try {
                consumerCredentials=ImportExportUtils.registerClient(username, String.valueOf(password));
                if (log.isDebugEnabled()) {
                    log.debug("client registered successfully");
                }
            } catch (APIExportException e) {
                log.error("Error occurred while registering the client", e);
                System.exit(1);
            }

        //handling single API export
        if(StringUtils.isBlank(config.getApiFilePath())){
            try {
                ExportAPI.singleApiExport(consumerCredentials);
            } catch (APIExportException e) {
                log.error("Error occurred while exporting the API ");
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
        boolean sslCheck= Boolean.parseBoolean(System.getProperty("certCheck"));
        String log4jFile= System.getProperty("log4j");
        String destinationPath = System.getProperty("zipFileLocation");
        String destinationFolderName = System.getProperty("filename");
        if(configFile!=null){
            ImportExportUtils.setUserConfigurations(configFile,config);
        }

        if(StringUtils.isNotBlank(name)){ config.setApiName(name); }
        if(StringUtils.isNotBlank(provider)){ config.setApiProvider(provider);}
        if(StringUtils.isNotBlank(version)){ config.setApiVersion(version);}
        if(StringUtils.isNotBlank(bulkFile)){ config.setApiFilePath(bulkFile);}
        if(sslCheck == true){ config.setCheckSSLCertificate(true);}
        if(StringUtils.isNotBlank(log4jFile)){ config.setLog4JFilePath(log4jFile);}
        if(StringUtils.isNotBlank(destinationPath)){ config.setDestinationPath(destinationPath);}
        if(StringUtils.isNotBlank(destinationFolderName)){config.setDestinationFolderName(destinationFolderName);}

        Scanner scanner=new Scanner(System.in);
        if(StringUtils.isBlank(config.getApiName())){
            System.out.print("Enter the name of the API been export : ");
            name = scanner.next();
            if(StringUtils.isNotBlank(name)){config.setApiName(name);}
        }
        if (StringUtils.isBlank(config.getApiProvider())){
            System.out.print("Enter the provider of the API been export : ");
            provider = scanner.next();
            if(StringUtils.isNotBlank(provider)){config.setApiProvider(provider);}
        }
        if(StringUtils.isBlank(config.getApiVersion())){
            System.out.print("Enter the version of the API been export : ");
            version = scanner.next();
            if(StringUtils.isNotBlank(version)){config.setApiVersion(version);}
        }

    }

}
