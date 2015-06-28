package cloudsafe.exceptions;

public class AuthenticationException extends Exception{

	private static final long serialVersionUID = 1L;
	private String message = null;
	 
    public AuthenticationException() {
        super();
    }
 
    public AuthenticationException(String message) {
        super(message);
        this.message = message;
    }
 
    public AuthenticationException(Throwable cause) {
        super(cause);
        this.message = cause.getMessage();
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
