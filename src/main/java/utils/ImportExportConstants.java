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

import java.io.File;

public class ImportExportConstants {

    //system dependent default path separator character, represented as a string
    public static final String DIRECTORY_SEPARATOR = File.separator;
    //System independent file separator for zip files
    public static final char ZIP_FILE_SEPARATOR = '/';
    //character encoding type
    public static final String CHARSET= "UTF-8";
    //length of the name of the temporary directory
    public static final int TEMP_FILENAME_LENGTH = 5;

    //OAuth constants
    public static final String SCOPE_VIEW = "apim:api_view";
    public static final String AUTHORIZATION_KEY_SEGMENT = "Basic";
    public static final String CONSUMER_KEY_SEGMENT = "Bearer";
    public static final String DEFAULT_GRANT_TYPE= "password";
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String ACCESS_TOKEN = "access_token";

    //name of the inline file type
    public static final String INLINE_DOC_TYPE = "INLINE";

    //name of the physical file type
    public static final String FILE_DOC_TYPE = "FILE";

    //location of the documents JSON file
    public static final String DOCUMENT_FILE_LOCATION = DIRECTORY_SEPARATOR + "docs" + DIRECTORY_SEPARATOR +
            "docs.json";
    //location of the fileType content
    public static final String FILE_DOCUMENT_DIRECTORY = "FileContents";

    //location of the documentation
    public static final String DOCUMENT_DIRECTORY = "docs";

    //location of the inline type content
    public static final String INLINE_DOCUMENT_DIRECTORY = "InlineContents";



    //configuration constants
    public static final String TRUST_STORE_URL_PROPERTY ="trust.store.url";
    public static final String TRUST_STORE_PASSWORD_PROPERTY ="trust.store.password";
    public static final String PUBLISHER_URL = "publisher.url.segment";
    public static final String GATEWAY_URL ="gateway.url.segment";
    public static final String REGISTRATION_URL="client.registration.url";
    public static final String CLIENT_NAME_PROPERTY="client.name";
    public static final String SSL_VALIDATION="ssl.certificate.validation";
    public static final String IS_SAAS="is.saas.app";

    //REST API constants
    public static final String CREATED = "CREATED";
    public static final String THUMBNAIL = "thumbnailUri";
    public static final String CONTENT_JSON = "application/json";

    //system property constants
    public static final String USER_DIR = "user.dir";
    public static final String SSL_TRUSTSTORE = "javax.net.ssl.trustStore";
    public static final String SSL_PASSWORD = "javax.net.ssl.trustStorePassword";
    public static final String API_NAME = "name";
    public static final String API_VERSION = "version";
    public static final String API_PROVIDER = "provider";
    public static final String API_LIST = "list";
    public static final String CONFIG_FILE ="config";

    //directory constants

    //json constants
    public static final String STATUS_CONSTANT = "status";
    public static final String SCOPE_CONSTANT = "scope";
    public static final String SWAGGER="apiDefinition";
    public static final String UUID = "id";
    public static final String DOC_LIST = "list";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String DOC_ID = "documentId";
    public static final String DOC_NAME = "name";

}
