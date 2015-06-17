package cloudsafe.exceptions;

public class LockNotAcquiredException extends Exception {
	
	private static final long serialVersionUID = 1L;
	private String message = null;
	 
    public LockNotAcquiredException() {
        super();
    }
 
    public LockNotAcquiredException(String message) {
        super(message);
        this.message = message;
    }
 
    public LockNotAcquiredException(Throwable cause) {
        super(cause);
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
