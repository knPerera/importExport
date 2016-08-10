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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import java.io.*;
import java.util.Properties;
import java.util.Scanner;

//todo discuss: don't we need to get trustStore and password.


class ImportExport{

    private static final Log log = LogFactory.getLog(ImportExport.class);

    protected static Properties prop;

    public static void main(String[] args) {
        //configuring log4j properties
        //PropertyConfigurator.configure(System.getProperty("user.dir") + File.separator + "log4j.properties"); //// TODO: 8/9/16  seperator
        PropertyConfigurator.configure("log4j.properties");
        log.info("good morming   ");
        // create a scanner to read the command-line input
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter user name :");
        String username = scanner.next();

        System.out.print("Enter password : ");
        char password[]=scanner.next().toCharArray();

        //getting inputs from CLI
        String name = System.getProperty(ImportExportConstants.API_NAME);
        String provider = System.getProperty(ImportExportConstants.API_PROVIDER);
        String version = System.getProperty(ImportExportConstants.API_VERSION);
        String configFile = System.getProperty(ImportExportConstants.CONFIG_FILE);
        //input file holds the list of APIs tobe exported in bulk export
        String inputFile = System.getProperty(ImportExportConstants.API_LIST);

        setDefaultConfigurations();
        if( configFile!= null){
            readProperties("config.properties");

            //setting SSL Cert
            String trustStore = prop.getProperty(ImportExportConstants.TRUST_STORE_URL_PROPERTY);
            String trustStorePassword = prop.getProperty(ImportExportConstants.TRUST_STORE_PASSWORD_PROPERTY);
            if (trustStore != null && trustStorePassword != null) {
                System.setProperty(ImportExportConstants.SSL_TRUSTSTORE, trustStore);
                System.setProperty(ImportExportConstants.SSL_PASSWORD, trustStorePassword);
            }
           // ImportExportUtils.disableCertificateValidation();


            //registering the client
            UserInfo userInfo = new UserInfo();
            try {
                ImportExportUtils.registerClient(username, String.valueOf(password), userInfo);
            } catch (APIExportException e) {
                log.info("Error occurred while registering the client", e);
                System.exit(1);
            }
            //handling single API export
            if(name != null && provider != null && version != null && inputFile == null){
                //getting access token for export
                String token;
                try {
                    token = ImportExportUtils.getAccessToken(ImportExportConstants.SCOPE_VIEW,userInfo);
                    if(token != null) {
                        ExportAPI.exportAPI(name, provider, version,token);
                    }else {
                        log.info("Cannot export the API without a valid token");
                    }
                } catch (UnsupportedEncodingException e) {
                    log.error("cannot get a access token ", e);
                } catch (IOException e) {
                    log.error("");
                }

            }else {
                log.info("Error : Usage : -Dname=<API_name> -Dprovider=<provider> -Dversion=<version> "+
                        "-Dconfig=<config_file> -jar <name of jar>.jar");
            }
        }else{
            log.info("No configuration file detected \n Usage : -Dname=<API_name> -Dprovider=<provider>"+
                    " -Dversion=<version> -Dconfig=<config_file> -jar <name of jar>.jar");
        }
    }

    private static void setDefaultConfigurations() {
        readProperties("config.properties");
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        config.setDestinationPath(System.getProperty(ImportExportConstants.USER_DIR));
        config.setLog4JFilePath("log4j.properties");
        config.setCheckSSLCertificate(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.SSL_VALIDATION)));
        config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
        config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
        config.setGatewayUrl((String) prop.get(ImportExportConstants.GATEWAY_URL));
    }

    /**
     * reading the configuration file
     */
    private static void readProperties(String config) {
        prop= new Properties();
        InputStream input = null;
        File configurations = new File (config);
        try {
            input = new FileInputStream(configurations);

            // load a properties file
            prop.load(input);
        } catch (FileNotFoundException e) {
           log.error("Unable to find the file, please give the full path");
        } catch (IOException e) {
            log.error("unable to load the properties file");
        }finally {
            try {
                if(input != null){
                    input.close();
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }

    }
}
