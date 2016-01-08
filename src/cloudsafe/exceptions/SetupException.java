package cloudsafe.exceptions;

public class SetupException extends Exception{

	private static final long serialVersionUID = 0;
	private String message = null;
	private Throwable cause = null;
	 
    public SetupException() {
        super();
    }
 
    public SetupException(String message) {
        super(message);
        this.message = message;
    }
 
    public SetupException(Throwable cause) {
        super(cause);
        this.message = cause.getMessage();
        this.cause = cause;
    }
    
    public SetupException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
        this.cause = cause;
    }
 
    @Override
    public String toString() {
        return message;
    }
 
    @Override
    public String getMessage() {
        return message;
    }
    
    @Override
    public Throwable getCause() {
        return cause;
    }
    
    public static class UserInterruptedSetup extends SetupException {
		private static final long serialVersionUID = 1L;
    	public UserInterruptedSetup(String message) {
    		super(message);
    	}
    	
    }
    
    public static class NetworkError extends SetupException {
		private static final long serialVersionUID = 1L;
    	
    	public NetworkError(Throwable cause) {
    		super(cause);
            //get the message and cause from the parent SetupException
    	}
    }
    
    public static final class DbError extends SetupException {
		private static final long serialVersionUID = 1L;
    	public DbError(String message) {
    		super(message);
    	}
    	
    	public DbError(Throwable cause) {
    		super(cause);
    	}
    	
        public DbError(String message, Throwable cause) {
            super(message, cause);
            //get the message and cause from the parent SetupException
        }
    }
}
