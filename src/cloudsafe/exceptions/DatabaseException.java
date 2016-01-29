package cloudsafe.exceptions;

public class DatabaseException extends Exception{

	private static final long serialVersionUID = 123432423;
	private String message = null;
	 
    public DatabaseException() {
        super();
    }
 
    public DatabaseException(String message) {
        super(message);
        this.message = message;
    }
 
    public DatabaseException(Throwable cause) {
        super(cause);
        this.message = cause.getMessage();
    }
    
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
 
    @Override
    public String toString() {
        return message;
    }
 
    @Override
    public String getMessage() {
        return message;
    }
}
