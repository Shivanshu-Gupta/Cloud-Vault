package cloudsafe;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.http.util.ByteArrayBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.WriteMode;

public class CloudsHandler {
	private final static Logger logger = LogManager
			.getLogger(CloudsHandler.class.getName());

	ArrayList<Cloud> clouds = new ArrayList<Cloud>();

	ArrayList<ConcurrentHashMap<String, byte[]>> cloudUploadQueues = new ArrayList<ConcurrentHashMap<String, byte[]>>();
	ArrayList<Timer> cloudPeriodicUploaders = new ArrayList<Timer>();
	ArrayList<String> cloudQueueFilePaths = new ArrayList<String>();

	ExecutorService executor = Executors.newFixedThreadPool(10);

	@SuppressWarnings("unchecked")
	public CloudsHandler(ArrayList<Cloud> clouds, String configPath) {
		this.clouds = clouds;
		int cloudIdx = 0;
		while (cloudIdx < clouds.size()) {
			String cloudQueueFilePath = configPath + "/uploadQueues/"
					+ clouds.get(cloudIdx).getID();
			cloudQueueFilePaths.add(cloudQueueFilePath);
			ConcurrentHashMap<String, byte[]> uploadQueue = null;
			if (Files.exists(Paths.get(cloudQueueFilePath))) {
				try {
					FileInputStream fileIn = new FileInputStream(
							cloudQueueFilePath);
					ObjectInputStream in = new ObjectInputStream(fileIn);
					uploadQueue = (ConcurrentHashMap<String, byte[]>) in
							.readObject();
					in.close();
					fileIn.close();
				} catch (ClassNotFoundException e) {
					uploadQueue = new ConcurrentHashMap<String, byte[]>();
					e.printStackTrace();
				} catch (IOException e) {
					uploadQueue = new ConcurrentHashMap<String, byte[]>();
					e.printStackTrace();
				}
			} else {
				uploadQueue = new ConcurrentHashMap<String, byte[]>();
			}
			cloudUploadQueues.add(uploadQueue);
			Timer timer = new Timer();
			timer.schedule(new CloudUploader(cloudIdx, uploadQueue), 0, 60000);
			cloudPeriodicUploaders.add(timer);
			cloudIdx++;
		}
		executor = Executors.newFixedThreadPool(clouds.size());
		
	}

	public boolean acquireLock() {
		boolean lockAcquired = true;
		String lockFile = "tablelock";
		int cloudID = 0;
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		for (int i = 0; i < clouds.size(); i++, cloudID++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				if (cloud.searchFile("tablelock")) {
					logger.trace("Deleting lock in cloud " + i);
					Future<Void> future = executor.submit(new Deleter(i, lockFile));
					results.add(future);
				} else {
					releaseLock();
					lockAcquired = false;
					break;
				}
			}
		}
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (InterruptedException|ExecutionException e) {
				e.printStackTrace();
			}
		}
		logger.trace("lock could not be acquired");
		if(!lockAcquired) {
			byte[] lock = {};
			for(int i=0; i<cloudID; i++){
				Cloud cloud = clouds.get(i);
				if (cloud.isAvailable()) {
					logger.trace("recreating lock in cloud " + i);
					Future<Void> future = executor.submit(new Uploader(i, lock, lockFile, WriteMode.OVERWRITE));
					results.add(future);
				}
			}
		}
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (InterruptedException|ExecutionException e) {
				e.printStackTrace();
			}
		}
		return lockAcquired;
	}

	public void releaseLock() {
		byte[] lock = {};
		String lockFile = "tablelock";
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				Future<Void> future = executor.submit(new Uploader(i, lock, lockFile, WriteMode.OVERWRITE));
				results.add(future);
			}
		}
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (InterruptedException|ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	public void uploadFile(ArrayList<ByteArrayBuffer> dataArrays,
			String fileID, WriteMode mode) throws IOException {
//		Pattern p = Pattern.compile(".*table.ser.*");
//		boolean isTable = p.matcher(fileID).matches();
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()) {
				Future<Void> future = executor.submit(new Uploader(i,
						dataArrays.get(i).toByteArray(), fileID,
						WriteMode.OVERWRITE));
				results.add(future);
			} else {
				cloudUploadQueues.get(i).put(fileID,
						dataArrays.get(i).toByteArray());
				writeQueueToFile(i);
			}
		}
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	public void uploadFile(String path, String fileID, WriteMode mode)
			throws IOException {
//		Pattern p = Pattern.compile(".*tablemeta.txt.*");
//		boolean isTablemeta = p.matcher(fileID).matches();
		byte[] filedata = Files.readAllBytes(Paths.get(path));
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()) {
				Future<Void> future = executor.submit(new Uploader(i,
						filedata, fileID, WriteMode.OVERWRITE));
				results.add(future);
			} else {
				cloudUploadQueues.get(i).put(fileID, filedata);
				writeQueueToFile(i);
			}
		}
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	private void writeQueueToFile(int i) {
		try {
			FileOutputStream fileOut = new FileOutputStream(
					cloudQueueFilePaths.get(i));
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(cloudUploadQueues.get(i));
			out.flush();
			out.close();
			fileOut.flush();
			fileOut.close();
		} catch (IOException e) {
			logger.error("Unable to write cloud upload queue to file for cloud " + i, e);
		}
	}
	
	public ArrayList<byte[]> downloadFile(String fileID) throws IOException {
		ArrayList<byte[]> filedatas = new ArrayList<byte[]>();
		ArrayList<Future<byte[]>> results = new ArrayList<Future<byte[]>>();
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()
					&& cloud.searchFile(fileID)) {
				Future<byte[]> future = executor.submit(new Downloader(i,
						fileID));
				results.add(future);
			}
		}
		for (Future<byte[]> result : results) {
			try {
				filedatas.add((byte[]) result.get());
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		return filedatas;
	}

	public void downloadFile(String path, String fileID) throws IOException {
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()
					&& cloud.searchFile(fileID)) {
				try {
					cloud.downloadFile(path, fileID);
					break;
				} catch (IOException e) {
					logger.error("Exception while downloading file from cloud. ", e);
					// e.printStackTrace();
				}
			}
		}
	}

	public void deleteFile(String fileID) {
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloud.searchFile(fileID)) {
				Future<Void> future = executor.submit(new Deleter(i, fileID));
				results.add(future);
			}
		}
		for (Future<Void> result : results) {
			try {
				result.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean checkIfNewUser() {
		boolean newUser = true;
		for (int i = 0; i < clouds.size(); i++) {
			if (clouds.get(i).searchFile("table.ser_0")) {
				logger.trace("Found table.ser");
				newUser = false;
				break;
			}
		}
		return newUser;
	}

	class Uploader implements Callable<Void> {
		Cloud cloud;
		int cloudID;
		byte[] data;
		String fileID;
		WriteMode mode;

		public Uploader(int cloudID, byte[] data, String fileID, WriteMode mode) {
			super();
			this.cloud = clouds.get(cloudID);
			this.cloudID = cloudID;
			this.data = data;
			this.fileID = fileID;
			this.mode = mode;
		}

		@Override
		public Void call() throws Exception {
			try {
				logger.trace("Uploading to cloud" + cloudID);
				cloud.uploadFile(data, fileID, mode);
			} catch (IOException e) {
				throw e;
			} catch (Exception e1) {
				cloudUploadQueues.get(cloudID).put(fileID, data);
			}
			return null;
		}
	}

	class Downloader implements Callable<byte[]> {
		private int cloudID;
		private Cloud cloud;
		private String fileID;

		public Downloader(int cloudID, String fileID) {
			super();
			this.cloudID = cloudID;
			this.cloud = clouds.get(cloudID);
			this.fileID = fileID;
		}

		@Override
		public byte[] call() throws Exception {
			logger.trace("Downloading from cloud" + cloudID);
			return cloud.downloadFile(fileID);
		}
	}

	class Deleter implements Callable<Void> {
		private int cloudID;
		private Cloud cloud;
		private String fileID;

		public Deleter(int cloudID, String fileID) {
			super();
			this.cloudID = cloudID;
			this.cloud = clouds.get(cloudID);
			this.fileID = fileID;
		}

		@Override
		public Void call() throws Exception {
			logger.trace("Deleting in cloud" + cloudID);
			cloud.deleteFile(fileID);
			return null;
		}
	}

	private class CloudUploader extends TimerTask {
		int cloudID;
		Cloud cloud;
		ConcurrentHashMap<String, byte[]> uploadQueue;

		public CloudUploader(int cloudID,
				ConcurrentHashMap<String, byte[]> uploadQueue) {
			this.cloudID = cloudID;
			this.cloud = clouds.get(cloudID);
			this.uploadQueue = uploadQueue;
		}

		public void run() {
			logger.trace("starting periodic upload for cloud" + cloudID);
			if(cloud.isAvailable() && !uploadQueue.isEmpty()){
				Set<String> fileNames = uploadQueue.keySet();
				try {
					for (String fileName : fileNames) {
						cloud.uploadFile(uploadQueue.get(fileName), fileName,
								WriteMode.OVERWRITE);
						uploadQueue.remove(fileName);
					}
				} catch (IOException e) {
					logger.error("error in periodic upload to cloud. ", e);
					e.printStackTrace();
				}
				writeQueueToFile(cloudID);
			}
		}
	}

}
