package utils;

/**
 * Created by kaveesha on 8/22/16.
 */
public class APIImportException extends Exception {
    public APIImportException(String msg){
        super(msg);
    }
    public APIImportException(String msg , Throwable e){
        super(msg,e);
    }
}
