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


import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.apache.commons.io.IOUtils.closeQuietly;

class APIImporter {
    private static final Log log = LogFactory.getLog(APIImporter.class);
    private static ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

    /**
     * handle importing of APIs
     *
     * @param zipFileLocation     path to the imported zip file
     * @param consumerCredentials encoded consumerKey and consumerSecret
     * @throws APIImportException
     */
    static void importAPIs(String zipFileLocation, String consumerCredentials) throws
            APIImportException, UtilException {
        //obtaining access tokens
        String token = ImportExportUtils.getAccessToken
                (ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
       // addMediation(consumerCredentials);
        //create temporary directory to extract the content from imported folder
        String currentDirectory = System.getProperty(ImportExportConstants.USER_DIR);
        String tempDirectoryName = RandomStringUtils.randomAlphanumeric
                (ImportExportConstants.TEMP_FILENAME_LENGTH);
        String temporaryDirectory = currentDirectory + File.separator + tempDirectoryName;
        ImportExportUtils.createDirectory(temporaryDirectory);
        //unzipping the imported zip folder
        unzipFolder(zipFileLocation, temporaryDirectory);
        try {
            File[] files = new File(temporaryDirectory).listFiles();
            if (files != null) {
                //publishing each api in imported folder
                for (File file : files) {
                    String pathToApiDirectory = temporaryDirectory + File.separator + file.getName();
                    createAPI(pathToApiDirectory, token);
                }
            }
            FileUtils.deleteDirectory(new File(temporaryDirectory));
        } catch (IOException e) {
            String errorMsg = "Error occurred while deleting temporary directory";
            log.warn(errorMsg, e);
        }
    }

    /**
     * this method unzip the imported folder
     *
     * @param zipFile      zip file path
     * @param outputFolder folder to copy the zip content
     * @throws APIImportException
     */
    private static void unzipFolder(String zipFile, String outputFolder) throws APIImportException {

        byte[] buffer = new byte[ImportExportConstants.defaultBufferSize];

        //create output directory is not exists
        File folder = new File(outputFolder);
        if(!folder.exists()){
            folder.mkdir();
        }
        //get the zip file content
        ZipInputStream zis;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
        }  catch (FileNotFoundException e) {
            String errorMsg="cannot find the file to unzip";
            log.error(errorMsg,e);
            throw new APIImportException(errorMsg,e);
        }
        //get the zipped file list entry
        ZipEntry ze = null;
        try {
            ze = zis.getNextEntry();
        } catch (IOException e) {
            String errorMsg="error occurred while getting the zip file entries";
            log.error(errorMsg,e);
            throw new APIImportException(errorMsg,e);
        }
            while(ze!=null){
                //remedy to handle user modified zip files
                if(ze.isDirectory()){
                    try {
                        ze=zis.getNextEntry();
                    } catch (IOException e) {
                       String errorMsg = "error occurred while getting the zip entries";
                        log.error(errorMsg,e);
                        throw new APIImportException(errorMsg,e);
                    }
                    continue;
                }
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = null;
                try{
                        fos = new FileOutputStream(newFile);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                int len;
                try {
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                closeQuietly(fos);
                try{
                    ze = zis.getNextEntry();
                }catch (IOException e){
                    String errorMsg="Error occurred while continuing the while loop";
                    log.error(errorMsg,e);
                    throw new APIImportException(errorMsg,e);
                }
            }

            try {
                zis.closeEntry();
            } catch (IOException e) {
                String errorMsg="Error occurdded while closing the entry ";
                log.error(errorMsg,e);
                throw new APIImportException(errorMsg,e);
            }
            closeQuietly(zis);
       // }

//        try {
//            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
//            //get the zipped file list entry
//            ZipEntry ze = zis.getNextEntry();
//            FileOutputStream fos = null;
//            while (ze != null) {
//                String fileName = ze.getName();
//                File newFile = new File(outputFolder + File.separator + fileName);
//
//                //create all non exists folders
//                //else it will hit FileNotFoundException for compressed folder
//                new File(newFile.getParent()).mkdirs();
//                fos = new FileOutputStream(newFile);
//                int len;
//                while ((len = zis.read(buffer)) > 0) {
//                    fos.write(buffer, 0, len);
//                }
//                ze = zis.getNextEntry();
//            }
//            IOUtils.closeQuietly(zis);
//            IOUtils.closeQuietly(fos);
//            IOUtils.closeQuietly(zis);
//            IOUtils.closeQuietly(fos);
//
//        } catch (IOException e) {
//            FileUtils.deleteQuietly(new File(outputFolder));
//            String errorMsg = "error occurred while unzipping the imported folder ";
//            log.error(errorMsg, e);
//            throw new APIImportException(errorMsg, e);
//        }
    }

    /**
     * creating each imported API
     *
     * @param apiFolder path to the API folder withing imported folder
     * @param token     access token
     */
    private static void createAPI(String apiFolder, String token) {
        try {
            //getting api.json of the imported API
            String pathToJSONFile = apiFolder + ImportExportConstants.JSON_FILE_LOCATION;
            String jsonContent;

            jsonContent = FileUtils.readFileToString(new File(pathToJSONFile));
            String apiName = ImportExportUtils.readJsonValues(jsonContent,
                    ImportExportConstants.API_NAME);
            //creating the API
            String url = config.getPublisherUrl() + ImportExportConstants.APIS_URL;
            CloseableHttpClient client;
            try {
                client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            } catch (UtilException e) {
                String errorMsg = "Error occurred while getting a closableHttpClient for creating" +
                        " the API";
                log.error(errorMsg, e);
                return;
            }
            HttpPost request = new HttpPost(url);
            request.setEntity(new StringEntity(jsonContent, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + token);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            CloseableHttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == Response.Status.CONFLICT.getStatusCode()) {
                //if API already exists perform update, if enabled
                if (config.getUpdateApi()) {
                    updateApi(jsonContent, token, apiFolder);
                } else {
                    String errorMsg = "API " + apiName + " already exists. ";
                    log.info(errorMsg);
                }
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.CREATED.getStatusCode()) {
                System.out.println("creating API " + apiName);
                //getting uuid of created API
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, ImportExportConstants.CHARSET);

                String uuid = ImportExportUtils.readJsonValues(responseString,
                        ImportExportConstants.UUID);
                if (StringUtils.isNotBlank(uuid))
                    //importing API thumbnail
                    if (StringUtils.isNotBlank(ImportExportUtils.readJsonValues(jsonContent,
                            ImportExportConstants.THUMBNAIL))) {
                        addAPIImage(apiFolder, token, uuid);
                    }
                //Adding API documentations
                addAPIDocuments(apiFolder, token, uuid);


                //addAPISequences(pathToArchive, importedApi);
                // addAPISpecificSequences(pathToArchive, importedApi);
                // addAPIWsdl(pathToArchive, importedApi);
                System.out.println("API " + apiName + " imported successfully");

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.FORBIDDEN.getStatusCode()) {
                String errorMsg = "cannot create different APIs with duplicate context";
                log.error(errorMsg);
            }else if(response.getStatusLine().getStatusCode()==
                    Response.Status.BAD_REQUEST.getStatusCode()){
                String error=EntityUtils.toString(response.getEntity());
                log.error(error);
            } else {
                System.out.println("error code  "+response.getStatusLine().getStatusCode());
                String errorMsg = "Error occurred while creating the API " + apiName;
                log.error(errorMsg);
            }
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving details to create API ";
            log.error(errorMsg, e);
        }
    }

    /**
     * Updated an existing API
     *
     * @param payload    payload to update the API
     * @param token      access token
     * @param folderPath folder path to the imported API folder
     */
    private static void updateApi(String payload, String token,
                                  String folderPath) {
        String apiName = ImportExportUtils.readJsonValues(payload, ImportExportConstants.API_NAME);
        String version = ImportExportUtils.readJsonValues(payload, ImportExportConstants.API_VERSION);
        String identifier = apiName + "-" + version;

        //getting uuid of the existing API
        String uuid = null;
        String httpUrl = config.getPublisherUrl() + ImportExportConstants.APIS_URL + "?query=name:" +
                apiName;
        CloseableHttpClient httpClient;
        try {
            httpClient = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient on getting uuid " +
                    "of existing API";
            log.error(errorMsg, e);
            return;
        }
        HttpGet httpRequest = new HttpGet(httpUrl);
        httpRequest.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                + token);
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpRequest);
            if (httpResponse.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                String responseString = EntityUtils.toString(httpResponse.getEntity());
                JSONParser parser = new JSONParser();
                JSONObject jsonObj = (JSONObject) parser.parse(responseString);
                JSONArray array = (JSONArray) jsonObj.get(ImportExportConstants.DOC_LIST);
                for (Object api : array) {
                    JSONObject obj = (JSONObject) api;
                    if (obj.get(ImportExportConstants.API_VERSION).equals(version)) {
                        uuid = (String) obj.get(ImportExportConstants.UUID);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("error in getting all the apis");
        } catch (ParseException e) {
            System.out.println("error in simplifying response string");
        }

        //updating API
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid;
        CloseableHttpClient client;
        try {
            client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient for updating " +
                    "the existing API ";
            log.error(errorMsg, e);
            return;
        }
        HttpPut request = new HttpPut(url);
        request.setEntity(new StringEntity(payload, ImportExportConstants.CHARSET));
        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                token);
        request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
        try {
            CloseableHttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                //updating API documents
                updateAPIDocumentation(uuid, identifier, token, folderPath);
                //adding API thumbnail
                addAPIImage(folderPath, token, uuid);
                System.out.println("API " + identifier + " updated successfully");
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String status = "API " + identifier + " not found/ does not exists";
                log.error(status);
                return;
            } else {
                String status = "Updating API " + identifier + " unsuccessful";
                log.error(status);
                return;
            }
        } catch (IOException e) {
            String errorMsg = "Error occurred while updating, API " + apiName;
            log.error(errorMsg, e);
        }
    }

    /**
     * Posting API thumbnail
     *
     * @param folderPath  imported folder location
     * @param accessToken valid access token
     * @param uuid        API uuid
     */
    private static void addAPIImage(String folderPath, String accessToken, String uuid) {
        File apiFolder = new File(folderPath);
        File[] fileArray = apiFolder.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                //finding the file with name 'icon'
                String fileName = file.getName();
                //String imageName = fileName.substring(0, fileName.indexOf("."));

                if (fileName.contains(ImportExportConstants.IMG_NAME)) {
                    //getting image file,convert it to multipart entity and attaching to the http post
                    File imageFile = new File(folderPath + ImportExportConstants.ZIP_FILE_SEPARATOR +
                            fileName);
                    MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                    multipartEntityBuilder.addBinaryBody(ImportExportConstants.MULTIPART_FILE,
                            imageFile);
                    HttpEntity entity = multipartEntityBuilder.build();
                    String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                            ImportExportConstants.THUMBNAIL_SEG;
                    try {
                        CloseableHttpClient client =
                                HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                        HttpPost request = new HttpPost(url);
                        request.setHeader(HttpHeaders.AUTHORIZATION,
                                ImportExportConstants.CONSUMER_KEY_SEGMENT + accessToken);
                        request.setEntity(entity);
                        client.execute(request);
                    } catch (UtilException e) {
                        String errorMsg = "Error occurred while getting ClosableHttpClient for " +
                                "importing API thumbnail";
                        log.warn(errorMsg, e);
                    } catch (IOException e) {
                        String errorMsg = "Error occurred while posting the API thumbnail";
                        log.error(errorMsg, e);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Adding API documents to the created API
     *
     * @param folderPath  folder path for imported API folder
     * @param accessToken access token
     * @param uuid        uuid of the created API
     */
    private static void addAPIDocuments(String folderPath, String accessToken, String uuid) {
        String docSummaryLocation = folderPath + ImportExportConstants.DOCUMENT_FILE_LOCATION;
        try {
            //getting the list of documents from imported folder
            String jsonContent = FileUtils.readFileToString(new File(docSummaryLocation));
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonContent);
            JSONArray array = (JSONArray) jsonObject.get(ImportExportConstants.DOC_LIST);
            if (array.size() == 0) {
                String message = "Imported API doesn't have any documents to be publish";
                log.warn(message);
            } else {
                for (Object anArray : array) {
                    JSONObject obj = (JSONObject) anArray;
                    //publishing each document
                    String url = config.getPublisherUrl() + ImportExportConstants.APIS+ uuid +
                            ImportExportConstants.DOCUMENT_SEG;
                    CloseableHttpClient client =
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpPost request = new HttpPost(url);
                    request.setEntity(new StringEntity(obj.toString(),ImportExportConstants.CHARSET));
                    request.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT + accessToken);
                    request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                    CloseableHttpResponse response = client.execute(request);
                    if (response.getStatusLine().getStatusCode() ==
                            Response.Status.CREATED.getStatusCode()) {
                        String responseString = EntityUtils.toString(response.getEntity(),
                                ImportExportConstants.CHARSET);
                        String sourceType = obj.get(ImportExportConstants.SOURCE_TYPE).toString();
                        if (sourceType.equalsIgnoreCase(ImportExportConstants.FILE_DOC_TYPE) ||
                                sourceType.equalsIgnoreCase(ImportExportConstants.INLINE_DOC_TYPE)) {
                            //adding content of the inline and file type documents
                            addDocumentContent(folderPath, uuid, responseString, accessToken);
                        }
                    } else {
                        String message = "Error occurred while importing the API document " +
                                obj.get(ImportExportConstants.DOC_NAME);
                        log.warn(message);
                    }
                }
            }
        } catch (IOException e) {
            String errorMsg = "error occurred while importing the API documents";
            log.error(errorMsg, e);
        } catch (ParseException e) {
            String errorMsg = "error occurred getting the document list from the imported folder";
            log.error(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting ClosableHttpClient on " +
                    "importing API DOcuments";
            log.warn(errorMsg,e);
        }
    }

    /**
     * update the content of a document
     *
     * @param folderPath  folder path to the imported API
     * @param uuid        uuid of the API
     * @param response    payload for the publishing document
     * @param accessToken access token
     */
    private static void addDocumentContent(String folderPath, String uuid, String response,
                                           String accessToken) {
        String documentId = ImportExportUtils.readJsonValues(response, ImportExportConstants.DOC_ID);
        String sourceType = ImportExportUtils.readJsonValues(response,
                ImportExportConstants.SOURCE_TYPE);
        String documentName = ImportExportUtils.readJsonValues(response,
                ImportExportConstants.DOC_NAME);
        String directoryName;
        //setting directory name depending on the document source type
        if (sourceType.equals(ImportExportConstants.INLINE_DOC_TYPE)) {
            directoryName = ImportExportConstants.INLINE_DOCUMENT_DIRECTORY;
        } else {
            directoryName = ImportExportConstants.FILE_DOCUMENT_DIRECTORY;
        }
        //getting document content from the imported folder
        String documentContentPath = folderPath + ImportExportConstants.DIRECTORY_SEPARATOR +
                ImportExportConstants.DOCUMENT_DIRECTORY + ImportExportConstants.DIRECTORY_SEPARATOR
                + directoryName + ImportExportConstants.DIRECTORY_SEPARATOR + documentName;
        File content = new File(documentContentPath);
        HttpEntity entity = null;
        if (sourceType.equals(ImportExportConstants.FILE_DOC_TYPE)) {
            //setting the  file type content to http entity
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addBinaryBody(ImportExportConstants.MULTIPART_FILE, content);
            entity = multipartEntityBuilder.build();
        } else {
            //setting inline content to http entity
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(content),
                        ImportExportConstants.CHARSET));
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append("\n");
                    line = br.readLine();
                }
                String inlineContent = sb.toString();
                MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                multipartEntityBuilder.addTextBody(ImportExportConstants.MULTIPART_Inline,
                        inlineContent, ContentType.APPLICATION_OCTET_STREAM);
                entity = multipartEntityBuilder.build();
            } catch (IOException e) {
                String errorMsg = "error occurred while writing inline content to a String " +
                        "builder on document " + documentName;
                log.error(errorMsg, e);
            } finally {
                closeQuietly(br);
            }
        }

        //updating the document content
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                ImportExportConstants.DOCUMENT_SEG + ImportExportConstants.ZIP_FILE_SEPARATOR +
                documentId + ImportExportConstants.CONTENT_SEG;
        try {
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            HttpPost request = new HttpPost(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            request.setEntity(entity);
            client.execute(request);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting ClosableHttpClient on " +
                    "importing document content";
            log.warn(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "error occurred while uploading the content of document " +
                    ImportExportUtils.readJsonValues(response, ImportExportConstants.DOC_NAME);
            log.error(errorMsg, e);
        }
    }

    /**
     * Update the documents of a existing API
     *
     * @param uuid       uudi of the API
     * @param apiId      api id of the API(provider-name-version)
     * @param token      access token
     * @param folderPath folder path to the imported folder
     */
    private static void updateAPIDocumentation(String uuid, String apiId, String token,
                                               String folderPath){
        //getting the document list of existing API
        String url = config.getPublisherUrl() +ImportExportConstants.APIS+ uuid +
                ImportExportConstants.DOCUMENT_SEG;
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        } catch (UtilException e) {
            String errorMsg="Error while getting ClosableHttpClient for updating API documents";
            log.warn(errorMsg,e);
        }
        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                token);
        try {
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                String responseString = EntityUtils.toString(response.getEntity());
                //parsing strig array of documents in to a jsonArray
                JSONParser parser = new JSONParser();
                JSONObject jsonObj = (JSONObject) parser.parse(responseString);
                JSONArray array = (JSONArray) jsonObj.get(ImportExportConstants.DOC_LIST);
                //accessing each document in the document array
                for (Object anArray : array) {
                    JSONObject obj = (JSONObject) anArray;
                    //getting document id and delete each existing document
                    String documentId = (String) obj.get(ImportExportConstants.DOC_ID);
                    String deleteUrl = config.getPublisherUrl() +ImportExportConstants.APIS + uuid +
                            ImportExportConstants.DOCUMENT_SEG+
                            ImportExportConstants.ZIP_FILE_SEPARATOR+documentId;
                    CloseableHttpClient httpclient =
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpDelete deleteRequest = new HttpDelete(deleteUrl);
                    deleteRequest.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT+ token);
                    httpclient.execute(deleteRequest);
                }
                //adding new documentation
                addAPIDocuments(folderPath, token, uuid);
            } else {
                String errorMsg="Error occurred while getting the document list of API "+apiId;
                log.warn(errorMsg);
            }
        } catch (IOException | ParseException e) {
            String errorMsg = "Error occurred while updating the documents of API " + apiId;
            log.warn(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg="Error occurred while getting a ClosableHttpClient on updating" +
                    " the API documents";
            log.warn(errorMsg,e);
        }
    }

    public static void getMediation(String consumerCredentials){
        try {
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            System.out.println("token isss"+token);
            String url ="https://localhost:9443/api/am/publisher/v0.10/policies/mediation/1a46c3eb-0c60-49ce-b124-9c99ce76f89c";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+token);
            CloseableHttpResponse response = client.execute(request);
            System.out.println("@@@@@@@@@2222  Responce code of mediation is "+response.getStatusLine().getStatusCode());
            System.out.println();
            System.out.println(" !!!!!!  entity of mediation "+EntityUtils.toString(response.getEntity(), ImportExportConstants.CHARSET));
        } catch (UtilException e) {
            String error = "error occurred while getting a client to get mediations ";
            log.error(error,e);
        } catch (ClientProtocolException e) {
            String error = "error occurred whle getting mediation policies";
            log.error(error,e);
        } catch (IOException e) {
            String error = "IO error occurred whle getting mediation policies";
            log.error(error,e);
        }
    }
    public static void deleteMediation(String consumerCredentials){
        try {
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            System.out.println("token isss"+token);
            String url ="https://localhost:9443/api/am/publisher/v0.10/policies/mediation/0d59854f-01f1-404a-b668-e00292ed81e3";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpDelete request = new HttpDelete(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+token);
            CloseableHttpResponse response = client.execute(request);
            System.out.println("@@@@@@@@@2222  Responce code of mediation is "+response.getStatusLine().getStatusCode());
            System.out.println();
            System.out.println(" !!!!!!  entity of mediation "+EntityUtils.toString(response.getEntity(), ImportExportConstants.CHARSET));
        } catch (UtilException e) {
            String error = "error occurred while getting a client to get mediations ";
            log.error(error,e);
        } catch (ClientProtocolException e) {
            String error = "error occurred whle getting mediation policies";
            log.error(error,e);
        } catch (IOException e) {
            String error = "IO error occurred whle getting mediation policies";
            log.error(error,e);
        }
    }

    public static void addMediation(String uuid) {
        try {
            String consumerCredentials="d3FMNWpnZE9OVGtXYlVCWGZXdmZiZ2NHSjNJYTpBU2JYVUZOOXlpNmlSblJqUVN6T1Z2NVpmeW9h";
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            System.out.println("token isss" + token);
            String url = "https://localhost:9443/api/am/publisher/v0.10/apis/"+uuid+"/policies/mediation";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpPost request = new HttpPost(url);

            JSONObject jsonObject = new JSONObject();

            jsonObject.put("name", "log_in_messageKav1234678.xml");
            jsonObject.put("type", "in");
            jsonObject.put("config", "<sequence xmlns=\"http://ws.apache.org/ns/synapse\" name=\"log_in_messageMAL\">\n" +
                    "    <log level=\"full\">\n" +
                    "        <property name=\"IN_MESSAGE\" value=\"IN_MESSAGE\"/>\n" +
                    "    </log>\n" +
                    "</sequence>");
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            request.setEntity(new StringEntity(jsonObject.toJSONString(), ImportExportConstants.CHARSET));
            CloseableHttpResponse response = client.execute(request);
            System.out.println("@@@@@@@@@2222  Responce code of mediation is " + response.getStatusLine().getStatusCode());
            System.out.println();
            System.out.println(" !!!!!!  entity of mediation " + EntityUtils.toString(response.getEntity(), ImportExportConstants.CHARSET));
        } catch (UtilException e) {
            String error = "error occurred while getting a client to get mediations ";
            log.error(error, e);
        } catch (ClientProtocolException e) {
            String error = "error occurred whle getting mediation policies";
            log.error(error, e);
        } catch (IOException e) {
            String error = "IO error occurred whle getting mediation policies";
            log.error(error, e);
        }
    }

    public static void putMediation(String consumerCredentials){
        try {
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            System.out.println("token isss"+token);
            String url ="https://localhost:9443/api/am/publisher/v0.10/policies/mediation/0d59854f-01f1-404a-b668-e00292ed81e3";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpPut request = new HttpPut(url);

            JSONObject jsonObject = new JSONObject();

            jsonObject.put("name", "log_in_messageKav.xml");
            jsonObject.put("type", "in");
            jsonObject.put("config", "<sequence xmlns=\"http://ws.apache.org/ns/synapse\" name=\"log_in_messageKav1234567\">\n" +
                    "    <log level=\"full\">\n" +
                    "        <property name=\"IN_MESSAGE\" value=\"IN_MESSAGE1234\"/>\n" +
                    "    </log>\n" +
                    "</sequence>");
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+token);
            request.setHeader(HttpHeaders.CONTENT_TYPE,ImportExportConstants.CONTENT_JSON);
            request.setEntity(new StringEntity(jsonObject.toJSONString(),ImportExportConstants.CHARSET));
            CloseableHttpResponse response = client.execute(request);
            System.out.println("@@@@@@@@@2222  Responce code of mediation is "+response.getStatusLine().getStatusCode());
            System.out.println();
            System.out.println(" !!!!!!  entity of mediation "+EntityUtils.toString(response.getEntity(), ImportExportConstants.CHARSET));
        } catch (UtilException e) {
            String error = "error occurred while getting a client to get mediations ";
            log.error(error,e);
        } catch (ClientProtocolException e) {
            String error = "error occurred whle getting mediation policies";
            log.error(error,e);
        } catch (IOException e) {
            String error = "IO error occurred whle getting mediation policies";
            log.error(error,e);
        }
    }

}