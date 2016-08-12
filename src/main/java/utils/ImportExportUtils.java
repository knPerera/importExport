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

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

class ImportExportUtils {
    private static final Log log = LogFactory.getLog(ImportExportUtils.class);

    /**
     * Registering the clientApplication
     * @param username user name of the client
     * @param password password of the client
     * @return UserInfo type object
     */
    static String registerClient(String username, String password) throws APIExportException {

        //disableCertificateValidation();
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        String concatUsernamePassword=username+":"+password;
        byte[] encodedBytes = Base64.encodeBase64(concatUsernamePassword.getBytes());
        String encodedCredentials = new String(encodedBytes);

        //payload for registering
        JSONObject jsonObject =  new JSONObject();
        jsonObject.put("clientName",config.getClientName() );
        jsonObject.put("owner",username);
        jsonObject.put("grantType", "password");
        jsonObject.put("saasApp", true);

        //REST API call for registering
       // CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String jsonString;
        String encodedConsumerDetails;
        CloseableHttpClient client = null;
        try {
            String url=config.getDcrUrl();
            client = SSL.getHttpClient(config.getCheckSSLCertificate());
                    //HttpClients.createDefault();
            HttpPost request =  new HttpPost(url);
            request.setEntity(new StringEntity(jsonObject.toJSONString(), ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.AUTHORIZATION_KEY_SEGMENT+" "
                    +encodedCredentials);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);

            response = client.execute(request);
            System.out.println(response.getStatusLine());
            jsonString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(jsonString);
//            userInfo.setConsumerKey((String) jsonObj.get(ImportExportConstants.CLIENT_ID));
//            userInfo.setConsumerSecret((String)jsonObj.get(ImportExportConstants.CLIENT_SECRET));

            //storing encoded Consumer credentials
            String consumerCredentials = jsonObj.get(ImportExportConstants.CLIENT_ID) + ":" +
                    jsonObj.get(ImportExportConstants.CLIENT_SECRET);
            byte[] bytes = Base64.encodeBase64(consumerCredentials.getBytes());
            encodedConsumerDetails = new String(bytes);
        } catch (IOException e) {
            String msg = "Error occured while registering the client";
            log.error(msg,e);
            throw new APIExportException(msg, e);
        } catch (ParseException e) {
            String msg = "error occured while getting consumer key and consumer secret from the response";
            log.error(msg,e);
            throw new APIExportException(msg, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
        return encodedConsumerDetails;
    }

    /**
     * retrieve a access token with requested scope
     *
     * @param scope required token scope
     */

    static String getAccessToken(String scope,String consumerCredentials)throws APIExportException{

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String url = config.getGatewayUrl();
        String responseString;

        //mapping payload to a List
        List<NameValuePair> params = new ArrayList<>(4);
        params.add(new BasicNameValuePair("grant_type", ImportExportConstants.DEFAULT_GRANT_TYPE));
        params.add(new BasicNameValuePair("username","admin"));
        params.add(new BasicNameValuePair("password","admin"));
        params.add(new BasicNameValuePair("scope", scope));

        //REST API call for get tokens
        CloseableHttpClient client = SSL.getHttpClient(config.getCheckSSLCertificate());
        try {
            HttpPost request = new HttpPost(url);
            request.setEntity(new UrlEncodedFormEntity(params, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.AUTHORIZATION_KEY_SEGMENT+" "+
                    consumerCredentials);
            CloseableHttpResponse response;
            response = client.execute(request);
            responseString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(responseString);
            return ((String)jsonObj.get(ImportExportConstants.ACCESS_TOKEN));
        } catch (ParseException e) {
           log.error("error occured while getting the access token");
        } catch (UnsupportedEncodingException e) {
            String errormsg = "error occurred while passing the payload for token generation";
            log.error(errormsg,e);
            throw new APIExportException(errormsg, e);
        } catch (ClientProtocolException e) {
            String errormsg = "error occurred while passing the payload for token generation";
            log.error(errormsg,e);
            throw new APIExportException(errormsg, e);
        } catch (IOException e) {
            String errormsg = "error occurred while generating tokens";
            log.error(errormsg, e);
            throw new APIExportException(errormsg,e);
        } finally {
            IOUtils.closeQuietly(client);
        }
        return null;
    }
    /**
     * reading the configuration file
     */
    private static Properties readProperties(String config) {
        Properties prop;
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
        return prop;
    }

    public static void setDefaultConfigurations(ApiImportExportConfiguration config) {
        Properties prop =readProperties("config.properties");
        config.setDestinationPath(System.getProperty(ImportExportConstants.USER_DIR));
        config.setDestinationFolderName(System.getProperty(ImportExportConstants.DESTINATION_FOLDER));
        config.setCheckSSLCertificate(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.SSL_VALIDATION)));
        config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
        config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
        config.setGatewayUrl(prop.getProperty(ImportExportConstants.GATEWAY_URL));
        config.setClientName(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
        config.setSaasApp(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.IS_SAAS)));
        config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
        config.setApiFilePath(prop.getProperty(ImportExportConstants.API_LIST_FILE));
        config.setTrustStoreUrl(prop.getProperty(ImportExportConstants.TRUST_STORE_URL_PROPERTY));
    }

    public static void setUserConfigurations(String configFile,ApiImportExportConfiguration config){
        Properties prop = readProperties(configFile);
        config.setApiName(prop.getProperty("api.name"));
        config.setApiVersion(prop.getProperty("api.version"));
        config.setApiProvider(prop.getProperty("api.provider"));
        config.setDestinationPath(prop.getProperty(ImportExportConstants.ZIP_DESTINATION));
        config.setDestinationFolderName(System.getProperty(ImportExportConstants.DESTINATION_FOLDER));
        config.setCheckSSLCertificate(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.SSL_VALIDATION)));
        config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
        config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
        config.setGatewayUrl(prop.getProperty(ImportExportConstants.GATEWAY_URL));
        config.setClientName(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
        config.setSaasApp(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.IS_SAAS)));
        config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
        config.setApiFilePath(prop.getProperty(ImportExportConstants.API_LIST_FILE));
        config.setTrustStoreUrl(prop.getProperty(ImportExportConstants.TRUST_STORE_URL_PROPERTY));

    }

    /**
     * setting SSL Cert
     */
    public static void setSSlCert(){
        System.out.println("indise setting SERT");
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String trustStore=null;
        Scanner sc = new Scanner(System.in);
        if(StringUtils.isBlank(config.getTrustStoreUrl())){
            System.out.print("Enter the trust store url : ");
            trustStore= sc.next();
        }else {
            trustStore=config.getTrustStoreUrl();
        }
        System.out.print("Enter trust store password : ");
        char[] trustStorePassword = sc.next().toCharArray();

        if (StringUtils.isNotBlank(trustStore) && StringUtils.isNotBlank(String.valueOf(trustStorePassword))) {
            System.setProperty(ImportExportConstants.SSL_TRUSTSTORE, trustStore);
            System.setProperty(ImportExportConstants.SSL_PASSWORD, String.valueOf(trustStorePassword));
        }
    }

    public static void registerOAuthApplication(String username, String password) throws APIExportException {
        String usernamePw = username + ":" + password;
        String auth=null;
        try {
            byte[] encodedBytes = Base64.encodeBase64(usernamePw.getBytes("UTF-8"));
            auth = new String(encodedBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            String err ="Couldnt get the authorization headers";
        }

        String dcrEndpointURL = "https://localhost:9443/client-registration/v0.10/register";
        try {
            URL obj = new URL(dcrEndpointURL);
            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

            String applicationRequestBody = "{\n" +
                    "\"clientName\": \"" + "rest_publisher" + "\",\n" +
                    "\"tokenScope\": \"Production\",\n" +
                    "\"owner\": \"" + username + "\",\n" +
                    "\"grantType\": \"password refresh_token\",\n" +
                    "\"saasApp\": true\n" +
                    "}";

            String basicAuth = "Basic "+auth;
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setRequestProperty("Authorization",basicAuth);
            OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream(), "UTF-8");
            writer.write(applicationRequestBody);
            writer.close();

            int responseCode = con.getResponseCode();
            System.out.println("@@@@@@@@@@@@@@@@@@@@@  Response Code : " + responseCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuffer jsonString = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
            con.disconnect();
            System.out.println("Resiult of registration issssssssss   "+jsonString.toString());
        } catch (Exception e) {
            String err= "Error occured while registering the client ";
            log.error(err,e);
            throw new APIExportException(err,e);
        }


//        Map<String, String> dcrRequestHeaders = new HashMap<String, String>();
//        Map<String, String> dataMap = new HashMap<String, String>();
//
//
//        try {
//            String usernamePw = username + ":" + password;
//            //Basic Auth header is used for only to get token
//            byte[] encodedBytes = Base64.encodeBase64(usernamePw.getBytes("UTF-8"));
//            dcrRequestHeaders.put("Authorization", "Basic " + new String(encodedBytes, "UTF-8"));
//            //Set content type as its mandatory
//            dcrRequestHeaders.put("Content-Type", "application/json");
//            doPost(new URL(dcrEndpointURL), applicationRequestBody, dcrRequestHeaders);
//
//        } catch (MalformedURLException e) {
//            String msg = "Error in register method";
//            log.info(msg, e);
//            throw new APIExportException(msg,e);
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }finally {
//
//        }


    }

    private static void doPost(URL endpoint, String postBody, Map<String, String> headers) throws APIExportException {

        HttpURLConnection urlConnection = null;

        try {
            urlConnection = (HttpURLConnection)endpoint.openConnection();

            urlConnection.setRequestMethod("POST");

            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setUseCaches(false);
            urlConnection.setAllowUserInteraction(false);
            Iterator e = headers.entrySet().iterator();

            while(e.hasNext()) {
                Map.Entry sb = (Map.Entry)e.next();
                urlConnection.setRequestProperty((String)sb.getKey(), (String)sb.getValue());
            }

            OutputStream e1 = urlConnection.getOutputStream();

            try {
                OutputStreamWriter sb1 = new OutputStreamWriter(e1, "UTF-8");
                sb1.write(postBody);
                sb1.close();
            } catch (IOException var32) {
                throw new Exception("IOException while posting data " , (Throwable) e);
            } finally {
                if(e1 != null) {
                    e1.close();
                }

            }

            StringBuilder sb2 = new StringBuilder();
            BufferedReader rd = null;

            try {
                rd = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), Charset.defaultCharset()));

                String itr;
                while((itr = rd.readLine()) != null) {
                    sb2.append(itr);
                }
            } catch (FileNotFoundException var35) {
                ;
            } finally {
                if(rd != null) {
                    rd.close();
                }

            }

            Iterator itr1 = urlConnection.getHeaderFields().keySet().iterator();
            HashMap responseHeaders = new HashMap();

            while(itr1.hasNext()) {
                String key = (String)itr1.next();
                if(key != null) {
                    responseHeaders.put(key, urlConnection.getHeaderField(key));
                }
            }

            //HTTPResponse res = new HTTPResponse(sb2.toString(), urlConnection.getResponseCode(), responseHeaders);

        } catch (Exception e) {
            String error = "error occur while calling dcr endpoint ";
            log.error(error,e);
            throw new APIExportException(error,e);
        }


    }
}
