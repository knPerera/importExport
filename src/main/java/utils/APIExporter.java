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

import com.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
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
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.util.Scanner;

class APIExporter {

    private static final Log log = LogFactory.getLog(APIExporter.class);
    private static JSONParser parser = new JSONParser();
    private static ObjectMapper mapper = new ObjectMapper();


    /**
     * Handles the export of single API
     *
     * @param consumerCredentials encoded consumer key and consumer secret
     * @throws APIExportException
     */
    static void singleApiExport(String consumerCredentials) throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);
        if (StringUtils.isBlank(config.getApiName())) {
            System.out.print("Enter the name of the API been export : ");
            String name = scanner.next();
            if (StringUtils.isNotBlank(name)) {
                config.setApiName(name);
            }
        }
        if (StringUtils.isBlank(config.getApiProvider())) {
            System.out.print("Enter the provider of the API been export : ");
            String provider = scanner.next();
            if (StringUtils.isNotBlank(provider)) {
                config.setApiProvider(provider);
            }
        }
        if (StringUtils.isBlank(config.getApiVersion())) {
            System.out.print("Enter the version of the API been export : ");
            String version = scanner.next();
            if (StringUtils.isNotBlank(version)) {
                config.setApiVersion(version);
            }
        }
        System.out.println();
        //generating access token
        String token;
        try {
            token = ImportExportUtils.getAccessToken
                    (ImportExportConstants.EXPORT_SCOPE, consumerCredentials);
        } catch (UtilException e) {
            String errorMsg ="Error occurred while generating access token for "+config.getApiName();
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        //exporting the API
        String archivePath = getDestinationFolder(config);
        exportAPI(archivePath, config.getApiName(), config.getApiProvider(),
                config.getApiVersion(), token);

        //archiving the exporting directory
        createArchive(archivePath);
    }

    /**
     * Handling the bulk export
     *
     * @param credentials encoded consumer key and consumer secret
     */
    static void bulkApiExport(String credentials) throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String apiProvider, apiName, apiVersion;
        String token;
        try {
            token = ImportExportUtils.getAccessToken(ImportExportConstants.EXPORT_SCOPE,
                    credentials);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while generating access token for bulk export of APIs";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        String csvFile = config.getApiFilePath();
        CSVReader reader;
        String archivePath = getDestinationFolder(config);
        try {
            //reader = new CSVReader(new FileReader(csvFile));
            reader = new CSVReader(new InputStreamReader(new FileInputStream(csvFile),
                    ImportExportConstants.CHARSET));
            String[] line;
            while ((line = reader.readNext()) != null) {
                apiProvider = line[0];
                apiName = line[1];
                apiVersion = line[2];
                try {
                    //exporting the API
                    exportAPI(archivePath, apiName, apiProvider, apiVersion, token);
                } catch (APIExportException e) {
                    String errorMsg = "Error occurred while trying to export API " + apiName + "-" +
                            apiVersion;
                    log.warn(errorMsg, e);
                }
            }
            createArchive(archivePath);
        } catch (FileNotFoundException e) {
            String errorMsg = "Cannot find the source file file for bulk export";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while reading the source file for bulk export";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
    }

    /**
     * retrieve the information of API and create a exportable archive
     *
     * @param apiName     name of the API
     * @param provider    provider of the API
     * @param version     version of the API
     * @param accessToken valid access token
     */
    private static void exportAPI(String destinationLocation, String apiName, String provider,
                                  String version, String accessToken) throws APIExportException {
        String responseString;
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //building the API id
        String apiId = provider + "-" + apiName + "-" + version;
        CloseableHttpResponse response;
        try {
            //getting API meta- information
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            String metaInfoUrl = config.getPublisherUrl() + ImportExportConstants.APIS + apiId;
            HttpGet request = new HttpGet(metaInfoUrl);
            request.setHeader(HttpHeaders.AUTHORIZATION,
                    ImportExportConstants.CONSUMER_KEY_SEGMENT+ accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving details of the API " + apiId;
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closable http client for execution";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
            try {
                HttpEntity entity = response.getEntity();
                responseString = EntityUtils.toString(entity);
            } catch (IOException e) {
                String errorMsg = "Error occurred while parsing get api details response to string";
                log.error(errorMsg, e);
                throw new APIExportException(errorMsg, e);
            }
            //creating directory to store API information
            String APIFolderPath = destinationLocation.concat(File.separator + apiName + "-"
                    + version);
            //creating directory to store API's meta-information
            String metaInfoFolderPath = APIFolderPath.concat(File.separator +
                    ImportExportConstants.META_INFO);
            try {
                ImportExportUtils.createDirectory(APIFolderPath);
                ImportExportUtils.createDirectory(metaInfoFolderPath);

            } catch (UtilException e) {
                String errorMsg = "Error occurred while creating directory to hold API details";
                log.error(errorMsg, e);
                throw new APIExportException(errorMsg, e);
            }
            //set API status and scope before exporting
            if (!ImportExportUtils.readJsonValues(responseString, ImportExportConstants.STATUS_CONSTANT).
                    equalsIgnoreCase(ImportExportConstants.PROTOTYPED)) {
                responseString = setJsonValues(responseString, ImportExportConstants.STATUS_CONSTANT,
                        ImportExportConstants.CREATED);
            }
            responseString = setJsonValues(responseString, ImportExportConstants.SCOPE_CONSTANT, null);
            //get API uuid
            String uuid = ImportExportUtils.readJsonValues(responseString, ImportExportConstants.UUID);

            //optional
            //getMediations(uuid,accessToken);

            //writing API details to exporting folder
            writeFile(metaInfoFolderPath + File.separator + ImportExportConstants.API_JSON,
                    ImportExportUtils.formatJsonString(responseString));

            //get API swagger definition
            getSwagger(uuid, accessToken, metaInfoFolderPath);

            //export api thumbnail
            String thumbnailUri = ImportExportUtils.readJsonValues(responseString,
                    ImportExportConstants.THUMBNAIL);
            if (StringUtils.isNotBlank(thumbnailUri)) {
                exportAPIThumbnail(uuid, accessToken, APIFolderPath);
            }
            //export api documents
            String documentationSummary = getAPIDocumentation(accessToken, uuid);
            try {
                exportAPIDocumentation(uuid, documentationSummary, accessToken, APIFolderPath);
            } catch (UtilException e) {
                String errorMsg = "Error occurred while exporting documents of API " + apiId;
                log.error(errorMsg, e);
            }
        } else if (response.getStatusLine().getStatusCode() ==
                Response.Status.NOT_FOUND.getStatusCode()) {
            String message = "API " + apiId + " does not exist/ not found ";
            log.warn(message);
            throw new APIExportException(message);
        } else {
            String errorMsg = "Error occurred while retrieving the information of API " + apiId;
            log.error(errorMsg);
            throw new APIExportException(errorMsg);
        }


    }

    /**
     * This method get the API thumbnail and write in to the zip file
     *
     * @param uuid          API id of the API
     * @param accessToken   valid access token with exporting scopes
     * @param apiFolderPath archive base path
     */
    private static void exportAPIThumbnail(String uuid, String accessToken, String apiFolderPath) {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //REST API call to get API thumbnail
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.THUMBNAIL_SEG;
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            //assigning the response in to inputStream
            BufferedHttpEntity httpEntity = new BufferedHttpEntity(entity);
            InputStream imageStream = httpEntity.getContent();
            byte[] byteArray = IOUtils.toByteArray(imageStream);
            InputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(byteArray));
            //getting the mime type of the input Stream
            String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
            //getting file extension
            String extension = getThumbnailFileType(mimeType);
            OutputStream outputStream = null;
            if (extension != null) {
                //writing image in to the  archive
                try {
                    outputStream = new FileOutputStream(apiFolderPath + File.separator +
                            ImportExportConstants.IMG_NAME + "." + extension);
                    IOUtils.copy(httpEntity.getContent(), outputStream);
                } finally {
                    IOUtils.closeQuietly(outputStream);
                    IOUtils.closeQuietly(imageStream);
                    IOUtils.closeQuietly(inputStream);
                }
            }
        } catch (IOException e) {
            log.error("Error occurred while exporting the API thumbnail");
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient";
            log.error(errorMsg, e);
        }

    }


    /**
     * Retrieve content type of the thumbnail
     *
     * @param mediaType Media type of the thumbnail
     * @return File extension for the exporting image
     */
    private static String getThumbnailFileType(String mediaType) {
        if ((ImportExportConstants.PNG_IMG).equals(mediaType)) {
            return ImportExportConstants.PNG_TYPE;
        } else if (ImportExportConstants.JPG_IMG.equals(mediaType)) {
            return ImportExportConstants.JPG_TYPE;
        } else if (ImportExportConstants.JPEG_IMG.equals(mediaType)) {
            return ImportExportConstants.JPEG_TYPE;
        } else if ((ImportExportConstants.BMP_IMG).equals(mediaType)) {
            return ImportExportConstants.BMP_TYPE;
        } else if ((ImportExportConstants.GIF_IMG).equals(mediaType)) {
            return ImportExportConstants.GIF_TYPE;
        } else {
            log.error("can't predict the thumbnail file type.");
        }
        return null;
    }

    /**
     * retrieve the summary of API documentation
     *
     * @param accessToken access token with scope view
     * @param uuid        uuid of the API
     * @return String output of documentation summary
     */
    private static String getAPIDocumentation(String accessToken, String uuid) {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //REST API call on Get API Documents
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.DOCUMENT_SEG;
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            //HttpClients.createDefault();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity, ImportExportConstants.CHARSET);
        } catch (IOException e) {
            log.error("Error occurred while getting API documents");
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting closableHttpClient in exporting documents";
            log.error(errorMsg, e);
        }
        return null;
    }

    /**
     * write API documents in to the zip file
     *
     * @param uuid                 APIId
     * @param documentationSummary resultant string from the getAPIDocuments
     * @param accessToken          token with scope apim:api_view
     */
    private static void exportAPIDocumentation(String uuid, String documentationSummary,
                                               String accessToken, String archivePath)
            throws APIExportException, UtilException {
        OutputStream outputStream = null;
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //create directory to hold API documents
        String documentFolderPath = archivePath.concat(File.separator +
                ImportExportConstants.DOCUMENT_DIRECTORY);
        ImportExportUtils.createDirectory(documentFolderPath);

        //writing API documents to the zip folder
        try {
            Object json = mapper.readValue(documentationSummary, Object.class);
            String formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            writeFile(documentFolderPath + File.separator + ImportExportConstants.DOC_JSON,
                    formattedJson);
        } catch (IOException e) {
            String errorMsg = "Error occurred while formatting the response string";
            writeFile(documentFolderPath + File.separator + ImportExportConstants.DOC_JSON,
                    documentationSummary);
            log.warn(errorMsg, e);
        }
        //convert documentation summary to json object
        JSONObject jsonObj;
        try {
            jsonObj = (JSONObject) parser.parse(documentationSummary);
        } catch (ParseException e) {
            String errorMsg = "Error occurred while parsing json string,cannot export the API documents";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        if (jsonObj.containsKey(ImportExportConstants.DOC_LIST)) {
            org.json.simple.JSONArray arr = (org.json.simple.JSONArray)
                    jsonObj.get(ImportExportConstants.DOC_LIST);
            // traverse through each document
            for (Object anArr : arr) {
                JSONObject document = (JSONObject) anArr;
                //getting document source type (inline/url/file)
                String sourceType = (String) document.get(ImportExportConstants.SOURCE_TYPE);
                if (ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType) ||
                        ImportExportConstants.INLINE_DOC_TYPE.equalsIgnoreCase(sourceType)) {
                    //getting documentId
                    String documentId = (String) document.get(ImportExportConstants.DOC_ID);
                    String documentName = (String) document.get(ImportExportConstants.DOC_NAME);
                    //REST API call to get document contents
                    String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                            ImportExportConstants.DOCUMENT_SEG + ImportExportConstants.URL_SEPARATOR +
                            documentId + ImportExportConstants.CONTENT_SEG;
                    CloseableHttpClient client =
                            HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    HttpGet request = new HttpGet(url);
                    request.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT +accessToken);
                    HttpResponse response;
                    try {
                        response = client.execute(request);
                    } catch (IOException e) {
                        String errorMsg = "Error occurred while exporting document" + documentName;
                        log.error(errorMsg, e);
                        break;
                    }
                    HttpEntity entity = response.getEntity();

                    if (ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType)) {
                        //creating directory to hold FILE type content
                        String filetypeFolderPath = documentFolderPath.concat
                                (File.separator + ImportExportConstants.FILE_DOCUMENT_DIRECTORY);
                        ImportExportUtils.createDirectory(filetypeFolderPath);

                        //writing file type content in to the zip folder
                        String localFilePath = filetypeFolderPath + File.separator + document.get
                                (ImportExportConstants.DOC_NAME);
                        try {
                            outputStream = new FileOutputStream(localFilePath);
                            entity.writeTo(outputStream);
                        } catch (FileNotFoundException e) {
                            String errorMsg = "File to write the content of the document "
                                    + documentName + " not found";
                            log.error(errorMsg, e);
                        } catch (IOException e) {
                            String errorMsg = "Error occurred while exporting the content of"
                                    + documentName;
                            log.error(errorMsg, e);
                        } finally {
                            IOUtils.closeQuietly(outputStream);
                        }
                    } else {
                        //create directory to hold inline contents
                        String inlineFolderPath = documentFolderPath.concat(File.separator +
                                ImportExportConstants.INLINE_DOCUMENT_DIRECTORY);
                        ImportExportUtils.createDirectory(inlineFolderPath);

                        //writing inline content in to the zip folder
                        try {
                            String localFilePath = inlineFolderPath + File.separator +
                                    document.get(ImportExportConstants.DOC_NAME);
                            outputStream = new FileOutputStream(localFilePath);
                            entity.writeTo(outputStream);
                        } catch (IOException e) {
                            log.error("Error occurred while writing content of document" +
                                    documentName + " to the zip", e);
                        } finally {
                            IOUtils.closeQuietly(outputStream);
                        }
                    }

                }
            }
        }
    }

    /**
     * Method will retrieve the swagger definition of requested API
     *
     * @param uuid       api identifier
     * @param token      access token with exporting scopes
     * @param folderPath path to the exporting folder
     * @throws APIExportException
     */
    private static void getSwagger(String uuid, String token, String folderPath)
            throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid
                + ImportExportConstants.SWAGGER_SEG;
        HttpGet request = new HttpGet(url);
        CloseableHttpClient client;
        try {
            client = HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient for exporting swagger";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+token);
        CloseableHttpResponse response;
        try {
            response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String unformattedStr = EntityUtils.toString(entity);
            writeFile(folderPath + File.separator + ImportExportConstants.SWAGGER_JSON,
                    ImportExportUtils.formatJsonString(unformattedStr));
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving swagger definition";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
    }


    /**
     * write the given content to file
     *
     * @param path    Location of the file
     * @param content Content to be written
     */
    private static void writeFile(String path, String content) {

        FileOutputStream fileStream = null;
        OutputStreamWriter writer = null;
        try {
            fileStream = new FileOutputStream(new File(path));
            writer = new OutputStreamWriter(fileStream, ImportExportConstants.CHARSET);
            IOUtils.copy(new StringReader(content), writer);
        } catch (FileNotFoundException e) {
            String errorMsg = "File to be write not found";
            log.error(errorMsg, e);
        } catch (UnsupportedEncodingException e) {
            String errorMsg = "Error occurred while writing API details to the Zip";
            log.error(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while coping API details to the zip";
            log.error(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(fileStream);
        }
    }




    /**
     * Getting destination of exporting APIs
     *
     * @param config ApiImportExportConfiguration type object
     * @return destination to store the zip file
     */
    private static String getDestinationFolder(ApiImportExportConfiguration config) {
        String destinationFolderPath;
        if (StringUtils.isNotBlank(config.getDestinationPath())) {
            destinationFolderPath = config.getDestinationPath();
        } else {
            log.warn("zip folder of exporting API will be created at user's current directory ");
            destinationFolderPath = System.getProperty(ImportExportConstants.USER_DIR);
        }
        String folderName;
        if (StringUtils.isBlank(config.getDestinationFolderName())) {
            folderName = ImportExportConstants.DEFAULT_FOLDER_NAME;
            log.warn("zip folder will be created with default name ' " +
                    ImportExportConstants.DEFAULT_FOLDER_NAME + " ' ");
        } else folderName = config.getDestinationFolderName();

        return destinationFolderPath.concat(File.separator + folderName);

    }

    /**
     * creating archive of exported APIs
     *
     * @param folderPath file to archive
     */
    private static void createArchive(String folderPath) {
        File file = new File(folderPath);
        if (file.list().length > 0) {
            ArchiveGeneratorUtil.archiveDirectory(folderPath);
            if (new File(folderPath.concat(ImportExportConstants.ZIP_EXTENSION)).exists()) {
                System.out.println("API exported successfully");
            } else {
                System.out.println("API exporting unsuccessful");
            }
        }
    }

    /**
     * set the value been sent to the corresponding json element
     *
     * @param responseString response to the export rest call
     * @param key            key
     * @param value          value to be set
     */
    private static String setJsonValues(String responseString, String key, String value) {
        try {
            JSONObject json = (JSONObject) parser.parse(responseString);
            if (json.containsKey(key)) {
                json.put(key, value);
                return json.toString();
            }
        } catch (ParseException e) {
            String errorMsg = "error occurred while setting API status";
            log.error(errorMsg);
        }
        return responseString;
    }

    public static void getMediations(String uuid, String token){
        try {
            System.out.println("### inside get specific mediations");
            //String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            String url = "https://localhost:9443/api/am/publisher/v0.10/apis/"+uuid+"/policies/mediation";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+token);
            CloseableHttpResponse response = client.execute(request);
            System.out.println("#### entity "+EntityUtils.toString(response.getEntity(), ImportExportConstants.CHARSET));
            System.out.println("@@@@@@@@@2222  Responce code of mediation is "+response.getStatusLine().getStatusCode());
        } catch (UtilException e) {
            log.error("UTIL exception while generating access token ",e);
        } catch (ClientProtocolException e) {
            log.error("error while getting http client",e);
        } catch (IOException e) {
            log.error("Error while excecutig the request ",e);
        }

    }


    public static void putMediation(String uuid){
        try {
            String consumerCredentials="d3FMNWpnZE9OVGtXYlVCWGZXdmZiZ2NHSjNJYTpBU2JYVUZOOXlpNmlSblJqUVN6T1Z2NVpmeW9h";
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            System.out.println("Access token is "+token);
            String url ="https://localhost:9443/api/am/publisher/v0.10/apis/"+uuid+"/policies/mediation/890ee47e-be6d-4e5c-94af-a010e604007c";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpPut request = new HttpPut(url);

            JSONObject jsonObject = new JSONObject();

            jsonObject.put("name", "log_in_messageKavNom");
            jsonObject.put("type", "out");
            jsonObject.put("config", "<sequence xmlns=\"http://ws.apache.org/ns/synapse\" name=\"log_out_messageKav102\">\n" +
                    "    <log level=\"full\">\n" +
                    "        <property name=\"IN_MESSAGE\" value=\"Modified_out_message\"/>\n" +
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

    public static void postMediation(String uuid) {
        try {
            String consumerCredentials="d3FMNWpnZE9OVGtXYlVCWGZXdmZiZ2NHSjNJYTpBU2JYVUZOOXlpNmlSblJqUVN6T1Z2NVpmeW9h";
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            System.out.println("token isss" + token);
            String url = "https://localhost:9443/api/am/publisher/v0.10/apis/"+uuid+"/policies/mediation";
            CloseableHttpClient client = HttpClientGenerator.getHttpClient(false);
            HttpPost request = new HttpPost(url);

            JSONObject jsonObject = new JSONObject();

            jsonObject.put("name", "log_out_messageKav123456789.xml");
            jsonObject.put("type", "out");
            jsonObject.put("config", "<sequence xmlns=\"http://ws.apache.org/ns/synapse\" name=\"log_out_messageMALEE\">\n" +
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
    public static void deleteMediation(String uuid){
        try {
            String consumerCredentials="d3FMNWpnZE9OVGtXYlVCWGZXdmZiZ2NHSjNJYTpBU2JYVUZOOXlpNmlSblJqUVN6T1Z2NVpmeW9h";
            String token = ImportExportUtils.getAccessToken(ImportExportConstants.IMPORT_SCOPE, consumerCredentials);
            String url ="https://localhost:9443/api/am/publisher/v0.10/apis/"+uuid+"/policies/mediation/69ea3fa6-55c6-472e-896d-e449dd34a824";
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

}
