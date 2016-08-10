package utils;

/**
 * Created by kaveesha on 8/8/16.
 */
public class APIExportException extends Exception{
    public APIExportException(String msg){
        super(msg);
    }
    public APIExportException(String msg , Throwable e){
        super(msg,e);
    }
}
