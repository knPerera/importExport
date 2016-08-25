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

import com.opencsv.CSVReader;
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
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
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
     * generate a archive of exporting API
     * @param apiName name of the API
     * @param provider provider of the API
     * @param version version of the API
     * @param accessToken valid access token
     */
    private static void exportAPI(String destinationLocation,String apiName, String provider, String version,
                                  String accessToken) throws APIExportException {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        //creating a directory to hold API details
        String APIFolderPath = destinationLocation.concat(File.separator + apiName + "-"
                + version);
        ImportExportUtils.createDirectory(APIFolderPath);

        //building the API id
        String apiId = provider + "-" + apiName + "-" + version;
        String responseString;
        try {
            //getting API meta- information
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            String metaInfoUrl = config.getPublisherUrl() + "apis/" + apiId;
            HttpGet request = new HttpGet(metaInfoUrl);
            request.setHeader(javax.ws.rs.core.HttpHeaders.AUTHORIZATION,
                    ImportExportConstants.CONSUMER_KEY_SEGMENT + " " + accessToken);
            CloseableHttpResponse response = client.execute(request);
            if(response.getStatusLine().getStatusCode()== 404){ ////// TODO: 8/24/16 int value constant 
                String message = "API "+ apiId+ "does not exist/ not found ";
                log.warn(message);
                return;
            }
            HttpEntity entity = response.getEntity();
            responseString = EntityUtils.toString(entity, ImportExportConstants.CHARSET);
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving the API meta information";
            log.error(errorMsg,e);
            throw new APIExportException(errorMsg,e);
        }
        //creating directory to hold meta-information
        String metaInfoFolderPath = APIFolderPath.concat(File.separator + "meta-information");
        ImportExportUtils.createDirectory(metaInfoFolderPath);

        //set API status and scope before exporting
        setJsonValues(responseString, ImportExportConstants.STATUS_CONSTANT,
                ImportExportConstants.CREATED);
        setJsonValues(responseString,ImportExportConstants.SCOPE_CONSTANT,null);

        try{
            //writing meta-information
            Object json = mapper.readValue(responseString, Object.class);
            String formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            writeFile(metaInfoFolderPath + File.separator + "api.json", formattedJson);
        } catch (IOException e) {
            String error = "Error occurred while formatting the json string";
            log.error(error,e);
            throw new APIExportException(error,e);
        }

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
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    +" "+accessToken);
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
            OutputStream outputStream = null;
            if (extension != null) {
                //writing image in to the  archive
                try{
                    outputStream = new FileOutputStream(APIFolderpath + File.separator +"icon." +
                            extension);
                    IOUtils.copy(httpEntity.getContent(), outputStream);
                }finally {
                    IOUtils.closeQuietly(outputStream);
                    IOUtils.closeQuietly(imageStream);
                    IOUtils.closeQuietly(inputStream);
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
    private static String getAPIDocumentation(String accessToken, String uuid){
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //REST API call on Get API Documents
            String url = config.getPublisherUrl()+"apis/"+uuid+"/documents";
            CloseableHttpClient client =
                    HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                    //HttpClients.createDefault();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    +" " + accessToken);
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
    private static void exportAPIDocumentation(String uuid,String documentaionSummary,
                                               String accessToken,String archivePath){
        System.out.println("                  access token "+accessToken);
        OutputStream outputStream = null;
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //create directory to hold API documents
        String documentFolderPath = archivePath.concat(File.separator+"docs");
        ImportExportUtils.createDirectory(documentFolderPath);

        try {
            //writing API documents to the zip folder
            Object json = mapper.readValue(documentaionSummary, Object.class);
            String formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            writeFile(documentFolderPath + File.separator + "docs.json", formattedJson);

            //convert documentation summary to json object
            JSONObject jsonObj = (JSONObject) parser.parse(documentaionSummary);
            if(jsonObj.containsKey("list")){
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
                        //REST API call to get document contents
                        String url = config.getPublisherUrl() + "apis/" + uuid + "/documents/" +
                                documentId + "/content";
                        CloseableHttpClient client =
                                HttpClientGenerator.getHttpClient(config.getCheckSSLCertificate());
                        HttpGet request = new HttpGet(url);
                        request.setHeader(HttpHeaders.AUTHORIZATION,
                        request.setHeader(HttpHeaders.AUTHORIZATION,
                                ImportExportConstants.CONSUMER_KEY_SEGMENT + " " + accessToken);
                        HttpResponse response = client.execute(request);
                        System.out.println("Document response isss   "+response.getStatusLine().getStatusCode());////// TODO: 8/25/16  remove
                        HttpEntity entity = response.getEntity();

                        if (ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType)) {
                            //creating directory to hold FILE type content
                            String filetypeFolderPath = documentFolderPath.concat
                                    (File.separator + ImportExportConstants.FILE_DOCUMENT_DIRECTORY);
                            ImportExportUtils.createDirectory(filetypeFolderPath);

                            //writing file type content in to the zip folder
                            String documentContent =
                                    EntityUtils.toString(entity, ImportExportConstants.CHARSET);
                            String localFilePath = filetypeFolderPath + File.separator + document.get
                                    (ImportExportConstants.DOC_NAME);
                            try {
                                outputStream = new FileOutputStream(localFilePath);
                                outputStream.write(documentContent
                                        .getBytes());
                            } finally {
                                try {
                                    assert outputStream != null;
                                    outputStream.close();
                                }catch (IOException e){
                                    log.warn("Error occurred while closing the streams");
                                }
                            }
                        } else {
                            //create directory to hold inline contents
                            String inlineFolderPath = documentFolderPath.concat(File.separator +
                                    ImportExportConstants.INLINE_DOCUMENT_DIRECTORY);
                            ImportExportUtils.createDirectory(inlineFolderPath);

                            //writing inline content in to the zip folder
                            InputStream inlineStream = null;
                            try {
                                inlineStream = entity.getContent();
                                String localFilePath = inlineFolderPath + File.separator +
                                        document.get (ImportExportConstants.DOC_NAME);
                                outputStream = new FileOutputStream(localFilePath);
                                IOUtils.copy(inlineStream, outputStream);
                            } finally {
                                try{
                                    if(inlineStream!=null){
                                        inlineStream.close();
                                    }
                                   if (outputStream!=null) {
                                       outputStream.close();
                                   }
                                }catch (IOException e){
                                    log.warn("Error occure while closing the streams");
                                }
                            }
                        }

                    }
                }
            }
        } catch (ParseException e) {
            log.error("Error occurred while getting API documents");
        } catch (IOException e) {
            log.error("Error occured while writing getting API documents");
        }
    }


    /**
     * Write content to file
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
            String errorMsg="File to be write not found";
            log.error(errorMsg,e);
        } catch (UnsupportedEncodingException e) {
            String errorMsg="Error occurred while writing API details to the Zip";
            log.error(errorMsg,e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while coping API details to the zip";
            log.error(errorMsg,e);
        }finally {
            IOUtils.closeQuietly( writer);
            IOUtils.closeQuietly(fileStream);
        }
    }

    /**
     * set the value been sent to the corresponding key
     * @param responseString response to the export rest call
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
            String errorMsg = "error occurred while setting API status";
            log.error(errorMsg);
        }
    }

    static void singleApiExport(String consumerCredentials) throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        Scanner scanner=new Scanner(System.in, ImportExportConstants.CHARSET);
        if(StringUtils.isBlank(config.getApiName())){
            System.out.print("Enter the name of the API been export : ");
            String name = scanner.next();
            if(StringUtils.isNotBlank(name)){config.setApiName(name);}
        }
        if (StringUtils.isBlank(config.getApiProvider())){
            System.out.print("Enter the provider of the API been export : ");
            String provider = scanner.next();
            if(StringUtils.isNotBlank(provider)){config.setApiProvider(provider);}
        }
        if(StringUtils.isBlank(config.getApiVersion())){
            System.out.print("Enter the version of the API been export : ");
            String version = scanner.next();
            if(StringUtils.isNotBlank(version)){config.setApiVersion(version);}
        }

            //generating access token
            String  token = ImportExportUtils.getAccessToken
                    (ImportExportConstants.EXPORT_SCOPE,consumerCredentials);
            if (StringUtils.isBlank(token)){
                String errorMsg = "Error occured while generating the access token for API Export ";
                log.error(errorMsg);
                throw new APIExportException(errorMsg);
            }
            //exporting the API
            String zipLocation = getDestinationFolder(config);
            exportAPI(zipLocation,config.getApiName(), config.getApiProvider(),config.getApiVersion(),token);
        //archiving the directory
        ArchiveGeneratorUtil.archiveDirectory(zipLocation);
    }
    static void bulkApiExport(String credentials){
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String token = ImportExportUtils.getAccessToken(ImportExportConstants.EXPORT_SCOPE,credentials);
        String csvFile = config.getApiFilePath();
        CSVReader reader = null;
        String archivePath = getDestinationFolder(config);
        try {
            reader = new CSVReader(new FileReader(csvFile));
            String[] line;
            while ((line = reader.readNext()) != null) {
                String apiProvider = line[0];
                String apiName =line[1];
                String apiVersion = line[2];
                //exporting the API
                try {
                    exportAPI(archivePath,apiName,apiProvider,apiVersion,token);
                } catch (APIExportException e) {
                    String errorMsg = "Error occurred while trying to export API "+apiName+"-"+apiVersion;
                    log.warn(errorMsg, e);
                }
            }
            ArchiveGeneratorUtil.archiveDirectory(archivePath);
        } catch (FileNotFoundException e) {
            String errorMsg = "error occurred while reading the API list ";
            log.error(errorMsg, e); ////// TODO: 8/24/16  need more
        } catch (IOException e) {
            String errorMsg = "cannot read the API list";
            log.error(errorMsg,e);
        }
    }
    static String getDestinationFolder(ApiImportExportConfiguration config){
        String destinationFolderPath;
        if(StringUtils.isNotBlank(config.getDestinationPath())){
            destinationFolderPath = config.getDestinationPath();
        }else {
            log.warn("zip folder of exporting API will be created at user's current directory ");
            destinationFolderPath=System.getProperty(ImportExportConstants.USER_DIR);
        }
        String folderName;
        if(StringUtils.isBlank(config.getDestinationFolderName())){
            folderName = ImportExportConstants.DEFAULT_FOLDER_NAME;
            log.warn("zip folder will be created with default name ' "+
                    ImportExportConstants.DEFAULT_FOLDER_NAME+" ' ");
        }else folderName = config.getDestinationFolderName();

        return destinationFolderPath.concat(File.separator+folderName);

    }

}
