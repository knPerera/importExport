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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import java.io.*;
import java.net.URLConnection;

class ExportAPI {

    private static final Log log = LogFactory.getLog(ExportAPI.class);

    private static String zipFileLocation = null;
    //System.getProperty(ImportExportConstants.USER_DIR);
    private static JSONParser parser = new JSONParser();
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * generate a archive of exporting API
     * @param APIName name of the API
     * @param provider provider of the API
     * @param version version of the API
     * @param accessToken valid access token
     * @throws IOException
     */
    static void exportAPI(String APIName, String provider, String version, String accessToken)
            throws IOException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        //creating a directory to hold API details
        String APIFolderPath = zipFileLocation.concat(File.separator + APIName + "-" + version);
        createDirectory(APIFolderPath);

        //building the API id
        String APIId = provider + "-" + APIName + "-" + version;

        //getting API meta- information
        CloseableHttpClient client = SSL.getHttpClient(config.getCheckSSLCertificate());
                //HttpClients.createDefault();
        String metaInfoUrl = config.getPublisherUrl() + "apis/" + APIId;
        HttpGet request = new HttpGet(metaInfoUrl);
        request.setHeader(javax.ws.rs.core.HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + " " +
                accessToken);
        CloseableHttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, ImportExportConstants.CHARSET);

        //creating directory to hold meta-information
        String metaInfoFolderPath = APIFolderPath.concat(File.separator + "meta-information");
        createDirectory(metaInfoFolderPath);

        //set API status and scope before exporting
        setJsonValues(responseString, ImportExportConstants.STATUS_CONSTANT, ImportExportConstants.CREATED);
        setJsonValues(responseString,ImportExportConstants.SCOPE_CONSTANT,null);

        //writing meta-information
        Object json = mapper.readValue(responseString, Object.class);
        String formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        writeFile(metaInfoFolderPath + File.separator + "api.json", formattedJson);

        JSONObject jsonObj = null;
        try {
            jsonObj = (JSONObject) parser.parse(responseString);
            String swagger = (String) jsonObj.get(ImportExportConstants.SWAGGER);
            writeFile(metaInfoFolderPath + File.separator + "swagger.json", swagger);
        } catch (ParseException e) {
            log.error("error occurred while getting swagger definision");
        }
        //get API uuid
        String uuid = null;
        if ( jsonObj != null) {
            uuid = (String) jsonObj.get(ImportExportConstants.UUID);
        }

        //export api thumbnail
        String  thumbnailUrl= null;
        if ( jsonObj != null ) {
            thumbnailUrl = (String) jsonObj.get(ImportExportConstants.THUMBNAIL);
        }
        if(thumbnailUrl!= null) {
            exportAPIThumbnail(uuid,accessToken,APIFolderPath);
        }

        //export api documents
        String documentationSummary = getAPIDocumentation(accessToken, uuid);
        exportAPIDocumentation(uuid,documentationSummary,accessToken,APIFolderPath);

        ArchiveGeneratorUtil.archiveDirectory(zipFileLocation);

    }

    /**
     * This method get the API thumbnail and write in to the zip file
     * @param uuid API id of the API
     * @param accessToken valide access token with view scope
     * @param APIFolderpath archive base path
     */
    private static void exportAPIThumbnail(String uuid, String accessToken,String APIFolderpath) {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //REST API call to get API thumbnail
            String url= config.getPublisherUrl()+"apis/"+uuid+"/thumbnail";
                    //ImportExport.prop.getProperty(ImportExportConstants.PUBLISHER_URL)+"apis/"+uuid+"/thumbnail";
            CloseableHttpClient client = SSL.getHttpClient(config.getCheckSSLCertificate());
                    //HttpClients.createDefault();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+" "+accessToken);
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
            String extension =  getThumbnailFileType(mimeType);

            if (extension != null) {
                //writing image in to the  archive
                OutputStream outputStream = null;
                try{
                    outputStream = new FileOutputStream(APIFolderpath + File.separator +"icon." + extension);
                    IOUtils.copy(httpEntity.getContent(), outputStream);
                }finally {
                    imageStream.close();
                    inputStream.close();
                    outputStream.close();
                }

            }
        }
        catch (IOException e) {
            log.error("Error occurred while exporting the API thumbnail");
        }

    }


    /**
     * Retrieve content type of the thumbnail image for setting the exporting file extension
     *
     * @param mediaType Media type of the thumbnail
     * @return File extension for the exporting image
     */
    private static String getThumbnailFileType(String mediaType) {
        if (("image/png").equals(mediaType)) {
            return "png";
        } else if (("image/jpg").equals(mediaType)) {
            return "jpg";
        } else if ("image/jpeg".equals(mediaType)) {
            return "jpeg";
        } else if (("image/bmp").equals(mediaType)) {
            return "bmp";
        } else if (("image/gif").equals(mediaType)) {
            return "gif";
        } else {
            log.error("can't predict the thumbnail file type.");
        }

        return null;
    }

    /**
     * This method will get the summary of API documentation
     * @param accessToken access token with scope view
     * @param uuid uuid of the API
     * @return String output of documentation summary
     */
    public static String getAPIDocumentation(String accessToken,String uuid ){
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //REST API call on Get API Documents
            String url = config.getPublisherUrl()+"apis/"+uuid+"/documents";
                    //ImportExport.prop.getProperty(ImportExportConstants.PUBLISHER_URL)+"apis/"+uuid+"/documents";
            CloseableHttpClient client = SSL.getHttpClient(config.getCheckSSLCertificate());
                    //HttpClients.createDefault();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+" " + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            return  EntityUtils.toString(entity, ImportExportConstants.CHARSET);
        } catch (IOException e) {
           log.error("Error occurred while getting API documents");
        }
        return null;
    }

    /**
     *write API documents in to the zip file
     * @param uuid APIId
     * @param documentaionSummary resultant string from the getAPIDocuments
     *@param accessToken token with scope apim:api_view
     */
    public static void exportAPIDocumentation(String uuid,String documentaionSummary,String accessToken,
                                              String archivePath){
        OutputStream outputStream = null;
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //create directory to hold API documents
        String documentFolderPath = archivePath.concat(File.separator+"docs");
        createDirectory(documentFolderPath);

        try {
            //writing API documents to the zip folder
            Object json = mapper.readValue(documentaionSummary, Object.class);
            String formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            writeFile(documentFolderPath + File.separator + "docs.json", formattedJson);

            //convert documentation summary to json object
            JSONObject jsonObj = (JSONObject) parser.parse(documentaionSummary);
            if(jsonObj.containsKey("list")){
                org.json.simple.JSONArray arr = (org.json.simple.JSONArray) jsonObj.get(ImportExportConstants.DOC_LIST);
                // traverse through each document
                for (int i=0;i<arr.size();i++) {
                    JSONObject document = (JSONObject) arr.get(i);
                    //getting document source type (inline/url/file)
                    String sourceType = (String) document.get(ImportExportConstants.SOURCE_TYPE);
                    if (ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType) ||
                            ImportExportConstants.INLINE_DOC_TYPE.equalsIgnoreCase(sourceType)) {
                        //getting documentId
                        String documentId = (String) document.get(ImportExportConstants.DOC_ID);
                        //REST API call to get document contents
                        String url =config.getPublisherUrl()+"apis/"+uuid+"/documents/"+ documentId+"/content";
                                //ImportExport.prop.getProperty(ImportExportConstants.PUBLISHER_URL)+"apis/"+uuid+"/documents/"+ documentId+"/content";
                        CloseableHttpClient client = SSL.getHttpClient(config.getCheckSSLCertificate());
                                //HttpClients.createDefault();
                        HttpGet request = new HttpGet(url);
                        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT+" " +
                                accessToken);
                        HttpResponse response = client.execute(request);
                        HttpEntity entity = response.getEntity();

                        if(ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType)){
                            //creating directory to hold FILE type content
                            String filetypeFolderPath = documentFolderPath.concat
                                    (File.separator+ImportExportConstants.FILE_DOCUMENT_DIRECTORY);
                            createDirectory(filetypeFolderPath);

                            //writing file type content in to the zip folder
                            String documentContent = EntityUtils.toString(entity, ImportExportConstants.CHARSET);
                            String localFilePath = filetypeFolderPath + File.separator + document.get
                                    (ImportExportConstants.DOC_NAME);
                            try{
                                outputStream = new FileOutputStream(localFilePath);
                                outputStream.write(documentContent.getBytes(ImportExportConstants.CHARSET));
                            }finally {
                                outputStream.close();
                            }
                        }else {
                            //create directory to hold inline contents
                            String inlineFolderPath = documentFolderPath.concat(File.separator+
                                    ImportExportConstants.INLINE_DOCUMENT_DIRECTORY);
                            createDirectory(inlineFolderPath);

                            //writing inline content in to the zip folder
                            InputStream inlineStream=null;
                            try{
                                inlineStream = entity.getContent();
                                String localFilePath = inlineFolderPath + File.separator + document.get
                                        (ImportExportConstants.DOC_NAME);
                                outputStream = new FileOutputStream(localFilePath);
                                IOUtils.copy(inlineStream, outputStream);
                            }finally {
                                inlineStream.close();
                                outputStream.close();
                            }
                        }

                    }
                }
            }
        } catch (ParseException e) {
            log.error("Error occurred while getting API documents");
        } catch (JsonParseException e) {
            log.error("Error occured while writing getting API documents");
        } catch (JsonGenerationException e) {
            log.error("Error occured while writing getting API documents");
        } catch (JsonMappingException e) {
            log.error("Error occured while writing getting API documents");
        } catch (IOException e) {
            log.error("Error occured while writing getting API documents");
        }finally {

        }


    }

    /**
     * Create directory at the given path
     *
     * @param path Path of the directory
     */
    public static void createDirectory(String path) {
        if (path != null) {
            File file = new File(path);
            if (!file.exists() && !file.mkdirs()) {
                String errorMessage = "Error while creating directory : " + path;
               log.error(errorMessage);
            }
        }
    }

    /**
     * Write content to file
     *
     * @param path    Location of the file
     * @param content Content to be written
     */
    private static void writeFile(String path, String content) {
        FileWriter writer = null;

        try {
            writer = new FileWriter(path);
            IOUtils.copy(new StringReader(content), writer);
        } catch (IOException e) {
            String errorMessage = "I/O error while writing to file";
            log.error(errorMessage);
        } finally {
            IOUtils.closeQuietly( writer);
        }

    }

    /**
     * set the value been sent to the corresponding key
     * @param responseString json string recieve inresponse to the export rest call
     * @param key key
     * @param value value to be set
     */
    private static void setJsonValues(String responseString, String key, String value) {
        try {
            JSONObject json = (JSONObject) parser.parse(responseString);
            if (json.containsKey(key)) {
                json.put(key, value);
            }
        } catch (ParseException e) {
            String errorMsg = "error occured while parsing the Json string to Json object when setting API status";
            log.error(errorMsg);
        }
    }

    public static void singleApiExport(String consumerCredentials) throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //generating access token
            String  token = ImportExportUtils.getAccessToken(ImportExportConstants.SCOPE_VIEW,consumerCredentials);

            //creating folder to hold exporting APIs
            String destinationFolderPath;
            if(StringUtils.isNotBlank(config.getDestinationPath())){
                destinationFolderPath = config.getDestinationPath();
            }else {
                log.warn("zip folder of expoting API will be created at user's current directory ");
                destinationFolderPath=System.getProperty(ImportExportConstants.USER_DIR);
            }
            String folderName = null;
            if(StringUtils.isBlank(config.getDestinationFolderName())){
                folderName = ImportExportConstants.DEFAULT_FOLDER_NAME;
                log.warn("zip folder will be created with default name ' "+ImportExportConstants.DEFAULT_FOLDER_NAME+" ' ");
            }else folderName = config.getDestinationFolderName();

            zipFileLocation = destinationFolderPath.concat(File.separator+folderName);

            exportAPI(config.getApiName(), config.getApiProvider(),config.getApiVersion(),token);

        } catch (APIExportException e) {
            log.error("Error occurred during token generation, can't continue with valid token");
            System.exit(1);
        } catch (IOException e) {
            String errormsg = "error occurred while exporting API "+config.getApiProvider()+"-"+
                    config.getApiName() +"-"+config.getApiVersion();
            log.error(errormsg,e);
            throw new APIExportException(errormsg,e);
        }

    }
}
