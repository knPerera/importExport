

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


import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
import java.io.FileReader;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class APIImporter {
    static String errorMsg;
    private static final Log log = LogFactory.getLog(APIImporter.class);
    static ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

    /**
     * handle importing of APIs
     * @param zipFileLocation path for imported zip file
     * @param consumerCredentials encoded consumerkey and consumerSecret
     * @throws APIImportException
     */
    public static void importAPIs(String zipFileLocation,String consumerCredentials)throws
            APIImportException{
        //Temporary directory is used to create the required folders
        String currentDirectory = System.getProperty(ImportExportConstants.USER_DIR);
        String tempDirectoryName = RandomStringUtils.randomAlphanumeric
                (ImportExportConstants.TEMP_FILENAME_LENGTH);
        String temporaryDirectory= currentDirectory+File.separator+tempDirectoryName;
        ImportExportUtils.createDirectory(temporaryDirectory);
        try {
            //unzipping the imported zip folder
            unzipFolder(zipFileLocation, temporaryDirectory);
            //obtaining access tokens
            String  token = ImportExportUtils.getAccessToken
                    (ImportExportConstants.IMPORT_SCOPE,consumerCredentials);
            if(StringUtils.isBlank(token)){
                String errorMsg = "error occurred while generating the access token for API Import";
                log.error(errorMsg);
                throw new APIImportException(errorMsg);
            }
            File[] files = new File(temporaryDirectory).listFiles();
            for (File file : files) {
                String pathToApiDirectory = temporaryDirectory + File.separator + file.getName();
                //publishing each api
                publishAPI(pathToApiDirectory, token);
            }
        } catch (APIImportException e) {
            String errorMsg = "Unable to import the API/ APIs ";
            log.error(errorMsg, e);e.printStackTrace();
        }
    }

    /**
     * function to unzip the imported folder
     * @param zipFile zip file path
     * @param outputFolder folder to copy the zip content
     * @throws APIImportException
     */
    private static void unzipFolder(String zipFile, String outputFolder) throws APIImportException {
        byte[] buffer = new byte[1024];

        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();
            while(ze!=null){
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                //create all non exists folders
                //else it hit FileNotFoundException for compressed folder
                boolean directoryStatus = new File(newFile.getParent()).mkdirs();
                while (directoryStatus){
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (FileNotFoundException e) {
            String errorMsg = "imported zip file not found ";
            log.error(errorMsg, e);
            throw new APIImportException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg ="Cannot extract the zip entries ";
            log.error(errorMsg, e);
            throw  new APIImportException(errorMsg,e);
        }
    }

    /**
     * Publishing each imported APIs
     * @param apiFolder path to the imported folder
     * @param token access token
     * @throws APIImportException
     */
    private static void publishAPI(String apiFolder, String token) throws APIImportException {
        String apiId = null;
        try {
            //getting api.json of imported API
            String pathToJSONFile = apiFolder + ImportExportConstants.JSON_FILE_LOCATION;
            String jsonContent =  FileUtils.readFileToString(new File(pathToJSONFile));
            //building API id
            apiId = ImportExportUtils.readJsonValues(jsonContent,ImportExportConstants.API_PROVIDER)+
                    "-" +ImportExportUtils.readJsonValues(jsonContent,ImportExportConstants.API_NAME)+
                    "-"+ ImportExportUtils.readJsonValues(jsonContent,ImportExportConstants.API_VERSION);

            String url = config.getPublisherUrl()+"apis";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            HttpPost request = new HttpPost(url);
            request.setEntity(new StringEntity(jsonContent, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+token);
            request.setHeader(HttpHeaders.CONTENT_TYPE,ImportExportConstants.CONTENT_JSON);
            CloseableHttpResponse response = client.execute(request);

            if(response.getStatusLine().getStatusCode()== Response.Status.CONFLICT.getStatusCode()){
                if(config.getUpdateApi()){
                    updateApi(jsonContent, apiId,token,apiFolder);
                }else {
                    errorMsg = "API "+apiId+" already exists. ";
                    System.out.println(errorMsg);
                }
            }else if(response.getStatusLine().getStatusCode()== Response.Status.CREATED.getStatusCode()){
                System.out.println("creating API "+apiId);
                //getting created API's uuid
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, ImportExportConstants.CHARSET);
                String uuid = ImportExportUtils.readJsonValues(responseString,
                        ImportExportConstants.UUID);
                if(StringUtils.isNotBlank(ImportExportUtils.readJsonValues(jsonContent,
                        ImportExportConstants.THUMBNAIL))){
                    addAPIImage(apiFolder,token,uuid);
                }
                //Adding API documentations
                addAPIDocuments(apiFolder,token,uuid);


                //addAPISequences(pathToArchive, importedApi);
                // addAPISpecificSequences(pathToArchive, importedApi);
                // addAPIWsdl(pathToArchive, importedApi);
                System.out.println("Importing API "+apiId+" was successful");

            }else {
                errorMsg = response.getStatusLine().toString();
                log.error(errorMsg);
                throw new APIImportException(errorMsg);
            }
        } catch (IOException e) {
            String errorMsg = "cannot find details of "+apiId+" for import";
            log.error(errorMsg, e);
            throw new APIImportException(errorMsg, e);
        }
    }

    /**
     * Updated an existing API
     * @param payload payload to update the API
     * @param apiId API id of the updating API (provider-name-version)
     * @param token access token
     * @param folderPath folder path to the imported API folder
     */
    private static void updateApi(String payload, String apiId,String token,String folderPath)
            throws APIImportException {
        String url = config.getPublisherUrl()+"apis/"+apiId;
        CloseableHttpClient client =
                HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        HttpPut request = new HttpPut(url);
        request.setEntity(new StringEntity(payload, ImportExportConstants.CHARSET));
        request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.CONSUMER_KEY_SEGMENT+" "
                +token);
        request.setHeader(HttpHeaders.CONTENT_TYPE,ImportExportConstants.CONTENT_JSON);
        try {
            CloseableHttpResponse response = client.execute(request);
            if(response.getStatusLine().getStatusCode()==Response.Status.OK.getStatusCode()){
                //getting API uuid
                String responseString = EntityUtils.toString(response.getEntity());
                String uuid = ImportExportUtils.readJsonValues(responseString,
                        ImportExportConstants.UUID);
                updateAPIDocumentation(uuid, apiId,token,folderPath);
                addAPIImage(folderPath,token,uuid);
                System.out.println("API "+apiId+" updated successfully");
            }else {
                String status = response.getStatusLine().toString();
                log.error(status);
                System.out.println(apiId+" update unsuccessful");
            }
        } catch (IOException e) {
            errorMsg = "Error occurred while updating, API "+apiId;
            log.error(errorMsg,e);
            throw new APIImportException(errorMsg,e);
        }
    }

    private static void addAPIImage(String folderPath, String accessToken,String uuid){
        File apiFolder = new File(folderPath);
        File[] fileArray = apiFolder.listFiles();
        if (fileArray != null) {
            for(File file: fileArray){
                String fileName= file.getName();
                String imageName = fileName.substring(0,fileName.indexOf("."));
                if(imageName.equalsIgnoreCase(ImportExportConstants.IMG_NAME)){
                    File imageFile = new File(folderPath+ImportExportConstants.ZIP_FILE_SEPARATOR+fileName);
                    MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                    multipartEntityBuilder.addBinaryBody(ImportExportConstants.MULTIPART_FILE,
                            imageFile);
                    HttpEntity entity = multipartEntityBuilder.build();
                    String url = config.getPublisherUrl()+"apis/"+uuid+"/thumbnail";
                    CloseableHttpClient client=
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpPost request = new HttpPost(url);
                    request.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+accessToken);
                    request.setEntity(entity);
                    try {
                        client.execute(request);
                    } catch (IOException e) {
                        errorMsg = "Error occurred while publishing the API thumbnail";
                        log.error(errorMsg,e);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Adding the API documents to the published API
     * @param folderPath folder path imported API folder
     * @param accessToken access token
     * @param uuid uuid of the created API
     */
    private static void addAPIDocuments(String folderPath, String accessToken,String uuid){
        String docSummaryLocation = folderPath + ImportExportConstants.DOCUMENT_FILE_LOCATION;
        try {
            //getting the list of documents
            String jsonContent = FileUtils.readFileToString(new File(docSummaryLocation));
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonContent);
            JSONArray array = (JSONArray) jsonObject.get(ImportExportConstants.DOCUMENT_LIST);
            if(array.size()==0){
                String message = "Imported API doesn't have any documents to be publish";
                log.warn(message);
            }else {
                for (Object anArray : array) {
                    JSONObject obj = (JSONObject) anArray;
                    //publishing each document
                    String url = config.getPublisherUrl() + "apis/" + uuid + "/documents";
                    CloseableHttpClient client =
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpPost request = new HttpPost(url);
                    request.setEntity(new StringEntity(obj.toString(),ImportExportConstants.CHARSET));
                    request.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT + " " + accessToken);
                    request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                    CloseableHttpResponse response = client.execute(request);
                    if(response.getStatusLine().getStatusCode()==Response.Status.CREATED.getStatusCode()){
                        String responseString = EntityUtils.toString(response.getEntity(),
                                ImportExportConstants.CHARSET);
                        String sourceType = ImportExportUtils.readJsonValues(responseString,
                                ImportExportConstants.SOURCE_TYPE);
                        if (sourceType.equalsIgnoreCase(ImportExportConstants.FILE_DOC_TYPE) ||
                                sourceType.equalsIgnoreCase(ImportExportConstants.INLINE_DOC_TYPE)){
                            addDocumentContent(folderPath, uuid, responseString, accessToken);
                        }
                    }else {
                        String message = "Error occurred while publishing the API document "+
                                obj.get(ImportExportConstants.DOC_NAME)+" "+response.getStatusLine();
                        log.warn(message);
                    }
                }
            }
        } catch (IOException e) {
            errorMsg = "error occurred while adding the API documents";
            log.error(errorMsg,e);
        } catch (ParseException e) {
            errorMsg = "error occurred importing the API documents";
            log.error(errorMsg, e);
        }
    }

    /**
     * update the content of a document of the API been sent
     * @param folderPath folder path to the imported API
     * @param uuid uuid of the API
     * @param response payload for the publishing document
     * @param accessToken access token
     */
    private static void addDocumentContent(String folderPath,String uuid, String response,
                                           String accessToken){
        String documentId = ImportExportUtils.readJsonValues(response,ImportExportConstants.DOC_ID);
        String sourceType = ImportExportUtils.readJsonValues(response,
                ImportExportConstants.SOURCE_TYPE);
        String documentName = ImportExportUtils.readJsonValues(response,
                ImportExportConstants.DOC_NAME);
        String directoryName;
        //setting directory name depending on the document source type
        if(sourceType.equals(ImportExportConstants.INLINE_DOC_TYPE)){
            directoryName=ImportExportConstants.INLINE_DOCUMENT_DIRECTORY;
        }else {
            directoryName= ImportExportConstants.FILE_DOCUMENT_DIRECTORY;
        }
        //getting document content
        String documentContentPath = folderPath + ImportExportConstants.DIRECTORY_SEPARATOR +
                ImportExportConstants.DOCUMENT_DIRECTORY + ImportExportConstants.DIRECTORY_SEPARATOR +
                directoryName+ ImportExportConstants.DIRECTORY_SEPARATOR + documentName;
        File content = new File(documentContentPath);
        HttpEntity entity = null;
        if(sourceType.equals(ImportExportConstants.FILE_DOC_TYPE) ){
            //adding file type content
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addBinaryBody(ImportExportConstants.MULTIPART_FILE,content);
            entity = multipartEntityBuilder.build();
        }else {
            BufferedReader br;
            try {
                br = new BufferedReader(new FileReader(content));
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append("\n");
                    line = br.readLine();
                }
                String inlineContent = sb.toString();
                MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                multipartEntityBuilder.addTextBody(ImportExportConstants.MULTIPART_FILE,////TODO changed 'inlineContent' to file
                        inlineContent,ContentType.APPLICATION_OCTET_STREAM);
                entity = multipartEntityBuilder.build();
            } catch (IOException e) {
                errorMsg = "error occurred while retrieving content of document "+
                        ImportExportUtils.readJsonValues(response,ImportExportConstants.DOC_NAME);
                log.error(errorMsg, e);
            }
        }
        String url = config.getPublisherUrl()+"apis/"+uuid+"/documents/"+documentId+"/content";
        CloseableHttpClient client =
                HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        HttpPost request = new HttpPost(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+
                " "+accessToken);
        request.setEntity(entity);
        try {
            client.execute(request);
        } catch (IOException e) {
            errorMsg = "error occurred while uploading the content of document "+
                    ImportExportUtils.readJsonValues(response,ImportExportConstants.DOC_NAME);
            log.error(errorMsg, e);
        }
    }

    /**
     * Update the documents of a existing API
     * @param uuid uudi of the API
     * @param apiId api id of the API(provider-name-version)
     * @param token access token
     * @param folderPath folder path to the imported folder
     */
    public static void updateAPIDocumentation(String uuid, String apiId,String token,
                                              String folderPath){
        //getting the document list of existing API
        String url = config.getPublisherUrl() +"apis/"+uuid+"/documents";
        CloseableHttpClient client =
                HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+
                token);
        try {
            HttpResponse response = client.execute(request);
            if(response.getStatusLine().getStatusCode()==Response.Status.OK.getStatusCode()){
                String responseString  =EntityUtils.toString(response.getEntity());
                JSONParser parser = new JSONParser();
                JSONObject jsonObj= (JSONObject) parser.parse(responseString);
                JSONArray array = (JSONArray) jsonObj.get(ImportExportConstants.DOC_LIST);
                for(int i = 0;i<array.size();i++){
                    JSONObject obj = (JSONObject) array.get(i);
                    String documentId = (String) obj.get(ImportExportConstants.DOC_ID);
                    //deleting existing documents
                    String deleteUrl = config.getPublisherUrl()+"apis/"+uuid+"/documents/"+documentId;
                    CloseableHttpClient httpclient =
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpDelete deleteRequest = new HttpDelete(deleteUrl);
                    deleteRequest.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+token);
                    httpclient.execute(deleteRequest);
                }
                addAPIDocuments(folderPath,token,uuid);
            }else {
                String status = response.getStatusLine().toString();
                log.error(status);
                System.out.println("error occurred while updating the documents of "+apiId); ////TODO-error msg
            }
        } catch (IOException e) {
            errorMsg = "Error occurred while updating the documents of API "+apiId;
            log.error(errorMsg, e);
        } catch (ParseException e) {
            errorMsg = "Error occurred while updating the documents of API "+apiId;
            log.error(errorMsg,e);
        }
    }

}