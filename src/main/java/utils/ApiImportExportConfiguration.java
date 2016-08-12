package utils;

import java.util.Properties;

/**
 * Created by kaveesha on 8/9/16.
 */
public class ApiImportExportConfiguration {

    private String apiName;
    private String apiVersion;
    private String apiProvider;
    private String apiFilePath ;
    private String destinationPath;
    private String destinationFolderName;
    private String log4JFilePath;
    private boolean checkSSLCertificate;
    private String dcrUrl;
    private String gatewayUrl;
    private String publisherUrl;
    private String clientName;
    private boolean isSaasApp;
    private String trustStoreUrl;


    private static ApiImportExportConfiguration instance;

    public static ApiImportExportConfiguration getInstance() {
        if (instance == null) {
            instance = new ApiImportExportConfiguration();
        }
        return instance;
    }


    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiVersion(String version){this.apiVersion=version;}

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiProvider(String provider){this.apiProvider=provider;}

    public String getApiProvider() {
        return apiProvider;
    }

    public void setApiFilePath(String path){this.apiFilePath=path;}

    public String getApiFilePath() {
        return apiFilePath;
    }

    public void setDestinationPath(String path){this.destinationPath=path;}

    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationFolderName(String name){this.destinationFolderName=name;}

    public String getDestinationFolderName(){return destinationFolderName;}

    public void setLog4JFilePath(String path){this.log4JFilePath=path;}

    public String getLog4JFilePath() {
        return log4JFilePath;
    }

    public void setCheckSSLCertificate(boolean value){this.checkSSLCertificate=value;}

    public boolean getCheckSSLCertificate(){return this.checkSSLCertificate;}

    public void setDcrUrl(String url){this.dcrUrl=url;}

    public String getDcrUrl() {
        return dcrUrl;
    }

    public void setGatewayUrl(String url){this.gatewayUrl=url;}

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setPublisherUrl(String url){this.publisherUrl=url;}

    public String getPublisherUrl() {
        return publisherUrl;
    }

    public void setClientName(String name){this.clientName=name;}

    public String getClientName() {
        return clientName;
    }

    public void setSaasApp(boolean saasApp){this.isSaasApp=saasApp;}

    public boolean getIsSaasApp() {
        return isSaasApp;
    }

    public void setTrustStoreUrl(String url){this.trustStoreUrl=url;}

    public String getTrustStoreUrl(){return  trustStoreUrl;}

    public void importFromProperties(Properties properties) {

    }


}
