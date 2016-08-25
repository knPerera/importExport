

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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class APIImporter {
    static String errorMsg;
    private static final Log log = LogFactory.getLog(APIImporter.class);
    static ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

    public static int importAPIs(String zipFileLocation,String consumerCredentials)throws APIImportException{

        InputStream stream;
        try {
            stream = new ByteArrayInputStream(zipFileLocation.getBytes(ImportExportConstants.CHARSET));
        } catch (UnsupportedEncodingException e) {
            String errorMsg = "Error occur while converting zip file into a inpul stream";
            log.error(errorMsg, e);
            return Response.Status.BAD_REQUEST.getStatusCode();
        }

        //Temporary directory is used to create the required folders
        String currentDirectory = System.getProperty(ImportExportConstants.USER_DIR);
        String tempDirectoryName = RandomStringUtils.randomAlphanumeric
                (ImportExportConstants.TEMP_FILENAME_LENGTH);
        String temporaryDirectory= currentDirectory+File.separator+tempDirectoryName;
        ImportExportUtils.createDirectory(temporaryDirectory);
        try {
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
            for(int i=0;i<files.length;i++ ){
                System.out.println(files[i].getName());
                String pathToApiDirectory = temporaryDirectory+File.separator+files[i].getName();
                //publishing each api
                publishAPI(pathToApiDirectory, token);

            }
        } catch (APIImportException e) {
            String errorMsg = "Unable to import the API/ APIs ";
            log.error(errorMsg, e);e.printStackTrace();
            return Response.Status.BAD_REQUEST.getStatusCode();
        }

        return Response.Status.OK.getStatusCode();
    }

    public static void unzipFolder(String zipFile, String outputFolder) throws APIImportException {
        byte[] buffer = new byte[1024];

        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();
            while(ze!=null){
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
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

    public static void publishAPI(String apiFolder, String token) throws APIImportException {
        String pathToJSONFile = apiFolder + ImportExportConstants.JSON_FILE_LOCATION;
        try {
            String jsonContent =  FileUtils.readFileToString(new File(pathToJSONFile));

            //building API id
            String apiId = ImportExportUtils.readJsonValues(jsonContent,"provider")+"-"
                    +ImportExportUtils.readJsonValues(jsonContent,"name")+"-"+
                    ImportExportUtils.readJsonValues(jsonContent,"version");

            String url = config.getPublisherUrl()+"apis";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            HttpPost request = new HttpPost(url);
            request.setEntity(new StringEntity(jsonContent, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+token);
            request.setHeader(HttpHeaders.CONTENT_TYPE,ImportExportConstants.CONTENT_JSON);
            CloseableHttpResponse response = client.execute(request);

            if(response.getStatusLine().getStatusCode()== Response.Status.CONFLICT.getStatusCode()){
                System.out.println(" ************ CONFLICT **************");
            }else {
                //getting created API's uuid
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, ImportExportConstants.CHARSET);
                String uuid = ImportExportUtils.readJsonValues(responseString, "id");

                if(StringUtils.isNotBlank(ImportExportUtils.readJsonValues(jsonContent,"thumbnailUri"))){
                    addAPIImage(apiFolder,token,uuid);
                }
                //Adding API documentations
                addAPIDocuments(apiFolder,token,uuid);


                //addAPISequences(pathToArchive, importedApi);
//        addAPISpecificSequences(pathToArchive, importedApi);
//        addAPIWsdl(pathToArchive, importedApi);

            }
        } catch (IOException e) {
            String errorMsg = "Unable to read API details "; ////TODO need to improve
            log.error(errorMsg, e);
            throw new APIImportException(errorMsg, e);
            }

    }

    public static void addAPIImage(String folderPath, String accessToken,String uuid){
        File apiFolder = new File(folderPath);
        File[] fileArray = apiFolder.listFiles();
        for(File file: fileArray){
            String fileName= file.getName();
            String imageName = fileName.substring(0,fileName.indexOf("."));
            if(imageName.equalsIgnoreCase(ImportExportConstants.IMG_NAME)){

                File imageFile = new File(folderPath+ImportExportConstants.ZIP_FILE_SEPARATOR+fileName);
                MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                multipartEntityBuilder.addBinaryBody("file",imageFile);
                HttpEntity entity = multipartEntityBuilder.build();

                //adding thumbnail to the API
                String url = config.getPublisherUrl()+"apis/"+uuid+"/thumbnail";
                CloseableHttpClient client=
                        HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                HttpPost request = new HttpPost(url);
                request.setHeader(HttpHeaders.AUTHORIZATION,
                        ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+accessToken);
                request.setEntity(entity);
                try {
                    CloseableHttpResponse response = client.execute(request);
                } catch (IOException e) {
                    errorMsg = "cannot publish the API thumbnail"; ////TODO improve
                }
                break;
            }
        }
    }

    public static void addAPIDocuments(String folderPath, String accessToken,String uuid){
        String docSummaryLocation = folderPath + ImportExportConstants.DOCUMENT_FILE_LOCATION;
        JSONParser parser = new JSONParser();
        try {
            String jsonContent = FileUtils.readFileToString(new File(docSummaryLocation));
                //getting the list of documents
                JSONObject jsonObject = (JSONObject) parser.parse(jsonContent);
                JSONArray array = (JSONArray) jsonObject.get("list");
            if(array.size()==0){
                String message = "No api documents to be publish";
                log.warn(message);
                // // TODO: 8/24/16 handle the return
            }else {

                for(int i = 0;i<array.size();i++){
                    JSONObject obj = (JSONObject) array.get(i);
                    //publishing each document
                    String url = config.getPublisherUrl()+"apis/"+uuid+"/documents";
                    CloseableHttpClient client =
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpPost request = new HttpPost(url);
                    request.setEntity(new StringEntity(obj.toString(),ImportExportConstants.CHARSET));
                    request.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+accessToken);
                    request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                    CloseableHttpResponse response = client.execute(request);
                    String responseString  = EntityUtils.toString(response.getEntity(),
                            ImportExportConstants.CHARSET);
                    String sourceType = ImportExportUtils.readJsonValues(responseString,"sourceType");
                    if(sourceType.equalsIgnoreCase(ImportExportConstants.FILE_DOC_TYPE)||
                            sourceType.equalsIgnoreCase(ImportExportConstants.INLINE_DOC_TYPE)){
                        addDocumentContent(folderPath,uuid,responseString, accessToken);
                    }
                 }

            }
        } catch (IOException e) {
            errorMsg = "error occurred while reading the imported document contents"; //TODO improve
            log.error("can not import the API Documents ");
        } catch (ParseException e) {
            errorMsg = "error occurred while reading the imported document contents";
            log.error(errorMsg, e);
        }
    }

    public static void addDocumentContent(String folderPath,String uuid, String response, String accessToken){
        String documentId = ImportExportUtils.readJsonValues(response, "documentId");
        String sourceType = ImportExportUtils.readJsonValues(response, "sourceType");
        String documentName = ImportExportUtils.readJsonValues(response, "name");
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
            multipartEntityBuilder.addBinaryBody("file",content);
            entity = multipartEntityBuilder.build();
        }else {
            BufferedReader br = null;
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
                multipartEntityBuilder.addTextBody("inlineContent", inlineContent, ContentType.APPLICATION_OCTET_STREAM);
                entity = multipartEntityBuilder.build();
            } catch (FileNotFoundException e) {
                errorMsg = "error occured while publishing the inline documentation content";
                log.error(errorMsg, e);
            } catch (IOException e) {
                errorMsg = "error occured while publishing the inline documentation content";
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
            CloseableHttpResponse postResponse = client.execute(request); ////TODO  change this response 201
        } catch (IOException e) {
            errorMsg = "error occurred while uploading the document content ";
            log.error(errorMsg, e);
        }
    }

}