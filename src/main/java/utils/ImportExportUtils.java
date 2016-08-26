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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ImportExportUtils {
    private static final Log log = LogFactory.getLog(ImportExportUtils.class);
    static String errorMsg;
    /**
     * Registering the clientApplication
     * @param username user name of the client
     * @param password password of the client
     * @return UserInfo type object
     */
    static String registerClient(String username, String password) throws APIExportException {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        String concatUsernamePassword=username+":"+password;
        byte[] encodedBytes = Base64.encodeBase64(concatUsernamePassword.getBytes
                (Charset.forName(ImportExportConstants.CHARSET)));
        String encodedCredentials;
        try {
            encodedCredentials = new String(encodedBytes, ImportExportConstants.CHARSET);
        } catch (UnsupportedEncodingException e) {
            String error = "Error occurred while encoding the user credentials";
            log.error(error,e);
            throw new APIExportException(error,e);
        }
        //payload for registering
        JSONObject jsonObject =  new JSONObject();
        jsonObject.put(ImportExportConstants.CLIENT_NAME,config.getClientName() );
        jsonObject.put(ImportExportConstants.OWNER,username);
        jsonObject.put(ImportExportConstants.GRANT_TYPE, ImportExportConstants.DEFAULT_GRANT_TYPE);
        jsonObject.put(ImportExportConstants.SAAS_APP, true);

        //REST API call for registering
        CloseableHttpResponse response;
        String encodedConsumerDetails;
        CloseableHttpClient client = null;
        try {
            String url=config.getDcrUrl();
            client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            HttpPost request =  new HttpPost(url);
            request.setEntity(new StringEntity(jsonObject.toJSONString(),
                    ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,
                    ImportExportConstants.AUTHORIZATION_KEY_SEGMENT+" "
                    +encodedCredentials);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

            if(response.getStatusLine().getStatusCode() == 401){
                String errormsg ="invalid username or password entered ";
                log.error(errormsg);
                throw new APIExportException(errormsg);
            }
            String jsonString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(jsonString);

            //storing encoded Consumer credentials
            String consumerCredentials = jsonObj.get(ImportExportConstants.CLIENT_ID) + ":" +
                    jsonObj.get(ImportExportConstants.CLIENT_SECRET);
            byte[] bytes = Base64.encodeBase64(consumerCredentials.getBytes
                    (Charset.defaultCharset()));
            encodedConsumerDetails = new String(bytes, ImportExportConstants.CHARSET);
        } catch (ParseException e) {
            String msg =
                    "error occurred while getting consumer key and consumer secret from the response";
            log.error(msg,e);
            throw new APIExportException(msg, e);
        }catch (IOException e) {
            String msg = "Error occurred while reistering the client";
            log.error(msg,e);
            throw new APIExportException(msg,e);
        }  finally {
            IOUtils.closeQuietly(client);
        }
        return encodedConsumerDetails;
    }

    /**
     * retrieve a access token with requested scope
     *
     * @param scope required token scope
     */

    static String getAccessToken(String scope,String consumerCredentials){

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String url = config.getGatewayUrl();
        String responseString;

        //mapping payload to a List
        List<NameValuePair> params = new ArrayList<>(4);
        params.add(new BasicNameValuePair(ImportExportConstants.TOKEN_GRANT_TYPE,
                ImportExportConstants.DEFAULT_GRANT_TYPE));
        params.add(new BasicNameValuePair(ImportExportConstants.USERNAME,config.getUsername()));
        params.add(new BasicNameValuePair(ImportExportConstants.DEFAULT_GRANT_TYPE,
                String.valueOf(config.getPassword())));
        params.add(new BasicNameValuePair(ImportExportConstants.SCOPE_CONSTANT, scope));

        //REST API call for get tokens
        CloseableHttpClient client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        try {
            HttpPost request = new HttpPost(url);
            request.setEntity(new UrlEncodedFormEntity(params, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,
                    ImportExportConstants.AUTHORIZATION_KEY_SEGMENT+" "+
                    consumerCredentials);
            CloseableHttpResponse response;
            response = client.execute(request);
            responseString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(responseString);
            return ((String)jsonObj.get(ImportExportConstants.ACCESS_TOKEN));
        } catch (ParseException e) {
           log.error("error occurred while getting the access token");
        } catch (UnsupportedEncodingException | ClientProtocolException e) {
            String errormsg = "error occurred while passing the payload for token generation";
            log.error(errormsg,e);
            return null;
        } catch (IOException e) {
            String errormsg = "error occurred while generating tokens";
            log.error(errormsg, e);
            return null;
        } finally {
            IOUtils.closeQuietly(client);
        }
        return null;
    }
    /**
     * reading the configuration file
     */
    private static Properties readProperties(String config) throws APIExportException {
        Properties prop;
        prop= new Properties();
        InputStream input = null;
        try {
            if(config.equalsIgnoreCase(ImportExportConstants.DEFAULT_CONFIG_FILE)){
                input =ImportExportUtils.class.getResourceAsStream(config);
            }else {
                File configurations = new File (config);
                input=new FileInputStream(configurations);
            }
            // load the properties file
            prop.load(input);
        } catch (FileNotFoundException e) {
            String errorMsg = "Cannot find the configuration file path";
            log.error(errorMsg,e);
            throw new APIExportException(errorMsg,e);
        } catch (IOException e) {
            String errorMsg= "unable to load the properties file";
            log.error(errorMsg,e);
            throw new APIExportException(errorMsg,e);
        }finally {
            try {
                if(input != null){
                    input.close();
                }
            } catch (IOException e) {
                log.warn("Error occur while closing the input stream");
            }
        }
        return prop;
    }

     static  void setDefaultConfigurations(ApiImportExportConfiguration config) throws
            APIExportException {
        Properties prop;
        prop=readProperties(ImportExportConstants.DEFAULT_CONFIG_FILE);
        config.setDestinationPath(System.getProperty(ImportExportConstants.USER_DIR));
        config.setDestinationFolderName(prop.getProperty(ImportExportConstants.DESTINATION_FOLDER));
        config.setCheckSSLCertificate(Boolean.parseBoolean(prop.getProperty
                (ImportExportConstants.SSL_VALIDATION)));
         config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
         config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
         config.setGatewayUrl(prop.getProperty(ImportExportConstants.GATEWAY_URL));
         config.setClientName(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
         config.setSaasApp(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.IS_SAAS)));
         config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
         config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
         config.setApiFilePath(prop.getProperty(ImportExportConstants.API_LIST_FILE));
         config.setTrustStoreUrl(prop.getProperty(ImportExportConstants.TRUST_STORE_URL_PROPERTY));
         config.setUpdateApi(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.UPDATE_API)));
    }

    static void setUserConfigurations(String configFile, ApiImportExportConfiguration config) {
        Properties prop = null;
        try {
            prop = readProperties(configFile);
        } catch (APIExportException e) {
            log.warn("Unable to find the user configuration file");
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CONFIG_API))){
            config.setApiName(prop.getProperty(ImportExportConstants.CONFIG_API));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CONFIG_VERSION))){
            config.setApiVersion(prop.getProperty(ImportExportConstants.CONFIG_VERSION));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CONFIG_PROVIDER))){
            config.setApiProvider(prop.getProperty(ImportExportConstants.CONFIG_PROVIDER));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.ZIP_DESTINATION))){
            config.setDestinationPath(prop.getProperty(ImportExportConstants.ZIP_DESTINATION));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.DESTINATION_FOLDER))){
            config.setDestinationFolderName(prop.getProperty
                    (ImportExportConstants.DESTINATION_FOLDER));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.SSL_VALIDATION))){
            config.setCheckSSLCertificate(Boolean.parseBoolean(prop.getProperty
                    (ImportExportConstants.SSL_VALIDATION)));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.REGISTRATION_URL))){
            config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.PUBLISHER_URL))){
            config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.GATEWAY_URL))){
            config.setGatewayUrl(prop.getProperty(ImportExportConstants.GATEWAY_URL));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY))){
            config.setClientName(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.IS_SAAS))){
            config.setSaasApp(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.IS_SAAS)));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.LOG4J_FILE))){
            config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.API_LIST_FILE))){
            config.setApiFilePath(prop.getProperty(ImportExportConstants.API_LIST_FILE));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.TRUST_STORE_URL_PROPERTY))){
            config.setTrustStoreUrl(prop.getProperty(ImportExportConstants.TRUST_STORE_URL_PROPERTY));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.ZIP_FILE))){
            config.setZipFile(prop.getProperty(ImportExportConstants.ZIP_FILE));
        }
        if(StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.UPDATE_API))){
            config.setUpdateApi(Boolean.parseBoolean(prop.getProperty
                    (ImportExportConstants.UPDATE_API)));
        }
    }

    /**
     * setting SSL Cert
     */
    static void setSSlCert(){
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String trustStore;
        Scanner sc = new Scanner(System.in, ImportExportConstants.CHARSET);
        if(StringUtils.isBlank(config.getTrustStoreUrl())){
            System.out.print("Enter the trust store url : ");
            trustStore= sc.next();
        }else {
            trustStore=config.getTrustStoreUrl();
        }
        System.out.print("Enter trust store password : ");
        char[] trustStorePassword = sc.next().toCharArray();

            if (StringUtils.isNotBlank(trustStore) &&
                    StringUtils.isNotBlank(String.valueOf(trustStorePassword))) {
                System.setProperty(ImportExportConstants.SSL_TRUSTSTORE, trustStore);
                System.setProperty(ImportExportConstants.SSL_PASSWORD,
                        String.valueOf(trustStorePassword));
            }

    }

    /**
     * This method uploads a given file to specified location
     *
     * @param uploadedInputStream input stream of the file
     * @param newFileName         name of the file to be created
     * @param storageLocation     destination of the new file
     */
    public static void transferFile(InputStream uploadedInputStream, String newFileName, String storageLocation)
            {
        FileOutputStream outFileStream = null;

        try {
            outFileStream = new FileOutputStream(new File(storageLocation, newFileName));
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = uploadedInputStream.read(bytes)) != -1) {
                outFileStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            String errorMessage = "Error in transferring files.";
            log.error(errorMessage, e);
        } finally {
            IOUtils.closeQuietly(outFileStream);
        }
    }

    /**
     * This method decompresses API the archive
     *
     * @param sourceFile  The archive containing the API
     * @param destination location of the archive to be extracted
     * @return Name of the extracted directory
     */
    public static String extractArchive(File sourceFile, String destination) throws APIImportException {

        BufferedInputStream inputStream = null;
        InputStream zipInputStream = null;
        FileOutputStream outputStream = null;
        ZipFile zip = null;
        String archiveName = null;

        try {
            zip = new ZipFile(sourceFile);
            Enumeration zipFileEntries = zip.entries();
            int index = 0;

            // Process each entry
            while (zipFileEntries.hasMoreElements()) {

                // grab a zip file entry
                ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
                String currentEntry = entry.getName();

                //This index variable is used to get the extracted folder name; that is root directory
                if (index == 0) {
                    archiveName = currentEntry.substring(0, currentEntry.indexOf(
                            ImportExportConstants.ZIP_FILE_SEPARATOR));
                    --index;
                }

                File destinationFile = new File(destination, currentEntry);
                File destinationParent = destinationFile.getParentFile();

                // create the parent directory structure
                if (destinationParent.mkdirs()) {
                    log.info("Creation of folder is successful. Directory Name : " + destinationParent.getName());
                }

                if (!entry.isDirectory()) {
                    zipInputStream = zip.getInputStream(entry);
                    inputStream = new BufferedInputStream(zipInputStream);

                    // write the current file to the destination
                    outputStream = new FileOutputStream(destinationFile);
                    IOUtils.copy(inputStream, outputStream);
                }
            }
            return archiveName;
        } catch (IOException e) {
            String errorMessage = "Failed to extract archive file ";
            log.error(errorMessage, e);
            throw new APIImportException(errorMessage,  e);
        } finally {
            IOUtils.closeQuietly(zipInputStream);
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
        }
    }

    /**
     * Create directory at the given path
     *
     * @param path Path of the directory
     */
    static void createDirectory(String path) {
        if (path != null) {
            File file = new File(path);
            if (!file.exists() && !file.mkdirs()) {
                String errorMessage = "Error while creating directory : " + path;
                log.error(errorMessage);
                System.exit(1);
            }
        }
    }

    /**
     * retrieve the corresponding value of the requesting key from the response json string
     *
     * @param responseString json string retrived from rest call
     * @param key            key of the element of required value
     * @return value correspond to the key
     */
    public static String readJsonValues(String responseString, String key) {
        String value = null;
        JSONParser parser = new JSONParser();
        try {
            JSONObject json = (JSONObject) parser.parse(responseString);
            if(json.containsKey(key)) {
                value = (String) json.get(key);
            }
        } catch (ParseException e) {
            errorMsg = "Error occurred while parsing the json string to Json object";
            log.error(errorMsg);
        }
        return value;
    }
}
