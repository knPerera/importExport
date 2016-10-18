package utils;

/**
 * Created by kaveesha on 9/7/16.
 */
public class UtilException extends Exception {

    UtilException(String msg) {
        super(msg);
    }

    UtilException(String msg, Throwable e) {
        super(msg, e);
    }
}
