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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

class ImportExportUtils {
    private static final Log log = LogFactory.getLog(ImportExportUtils.class);

    /**
     * Registering the clientApplication
     * @param username user name of the client
     * @param password password of the client
     * @param userInfo UserInfo type object to hold consumer keys
     * @return UserInfo type object
     */
    static UserInfo registerClient(String username, String password, UserInfo userInfo) throws APIExportException {

        String concatUsernamePassword=username+":"+password;
        byte[] encodedBytes = Base64.encodeBase64(concatUsernamePassword.getBytes());
        String encodedCredentials = new String(encodedBytes);

        //payload for registering
        JSONObject jsonObject =  new JSONObject();
        jsonObject.put("clientName", ImportExport.prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
        jsonObject.put("owner",username);
        jsonObject.put("grantType", "password");
        jsonObject.put("saasApp", true);

        //REST API call for registering
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String jsonString;
        try {
            String url=ImportExport.prop.getProperty(ImportExportConstants.REGISTRATION_URL);
            client = HttpClients.createDefault();
            HttpPost request =  new HttpPost(url);
            request.setEntity(new StringEntity(jsonObject.toJSONString(), ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.AUTHORIZATION_KEY_SEGMENT+" "
                    +encodedCredentials);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);

            response = client.execute(request);
            jsonString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(jsonString);
//            userInfo.setConsumerKey((String) jsonObj.get(ImportExportConstants.CLIENT_ID));
//            userInfo.setConsumerSecret((String)jsonObj.get(ImportExportConstants.CLIENT_SECRET));

            //storing encoded Consumer credentials
            String consumerCredentials = jsonObj.get(ImportExportConstants.CLIENT_ID) + ":" +
                    jsonObj.get(ImportExportConstants.CLIENT_SECRET);
            byte[] bytes = Base64.encodeBase64(consumerCredentials.getBytes());
            String encodedConsumerDetails = new String(bytes);
            userInfo.setEncodedConsumerKeys(encodedConsumerDetails);
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
        return userInfo;
    }

    /**
     * retrieve a access token with requested scope
     *
     * @param scope required token scope
     *@param info UserInfo type object
     */

    static String getAccessToken(String scope, UserInfo info) throws UnsupportedEncodingException {

        String url = ImportExport.prop.getProperty(ImportExportConstants.GATEWAY_URL);
        String responseString;

        //mapping payload to a List
        List<NameValuePair> params = new ArrayList<>(4);
        params.add(new BasicNameValuePair("grant_type", ImportExportConstants.DEFAULT_GRANT_TYPE));
        params.add(new BasicNameValuePair("username","admin"));
        params.add(new BasicNameValuePair("password","admin"));
        params.add(new BasicNameValuePair("scope", scope));

        //REST API call for get tokens
        CloseableHttpClient client = HttpClients.createDefault();
        try {
            HttpPost request = new HttpPost(url);
            request.setEntity(new UrlEncodedFormEntity(params, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,ImportExportConstants.AUTHORIZATION_KEY_SEGMENT+" "+
                    info.getEncodedConsumerKeys());
            CloseableHttpResponse response;
            response = client.execute(request);
            responseString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(responseString);
            return ((String)jsonObj.get(ImportExportConstants.ACCESS_TOKEN));
        } catch (IOException e) {
            log.error("error occured while getting access token ");
        } catch (ParseException e) {
           log.error("error occured while getting the raccess token");
        }finally {
            IOUtils.closeQuietly(client);
        }
        return null;
    }

    static void disableCertificateValidation() {

        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }};
        try {
            SSLContext sc = SSLContext.getInstance("TLS");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        } catch (KeyManagementException e) {
            log.info("eoorrrr occure while disabling ssl certificate validation");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.info("eoorrrr occure while disabling ssl certificate validation");
        }


        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);


    }
}
