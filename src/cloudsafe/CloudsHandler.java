package cloudsafe;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
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
import cloudsafe.cloud.CloudMeta;
import cloudsafe.cloud.WriteMode;

public class CloudsHandler {
	private final static Logger logger = LogManager
			.getLogger(CloudsHandler.class.getName());

	ArrayList<Cloud> clouds = new ArrayList<>();
	ArrayList<CloudMeta> cloudMetas = new ArrayList<>();

	ArrayList<ConcurrentHashMap<String, byte[]>> cloudUploadQueues = new ArrayList<ConcurrentHashMap<String, byte[]>>();
	ArrayList<Timer> cloudPeriodicUploaders = new ArrayList<>();
	ArrayList<String> cloudQueueFilePaths = new ArrayList<>();

	ExecutorService executor = Executors.newFixedThreadPool(10);

	@SuppressWarnings("unchecked")
	public CloudsHandler(ArrayList<Cloud> clouds, ArrayList<CloudMeta> cloudMetas, String configPath) {
		this.clouds = clouds;
		this.cloudMetas = cloudMetas;
		int idx = 0;
		while (idx < clouds.size()) {
			String cloudQueueFilePath = configPath + "/uploadQueues/"
					+ cloudMetas.get(idx).getId();
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
			timer.schedule(new CloudUploader(idx, uploadQueue), 0, 60000);
			cloudPeriodicUploaders.add(timer);
			idx++;
		}
		executor = Executors.newFixedThreadPool(clouds.size());
		
	}

	public boolean acquireLock() {
		boolean lockAcquired = true;
		String lockFile = "tablelock";
		int cloudIdx = 0;
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		for (int i = 0; i < clouds.size(); i++, cloudIdx++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				if (cloud.searchFile("tablelock")) {
					logger.trace("Deleting lock in cloud " + i);
					Future<Void> future = executor.submit(new Deleter(i, lockFile));
					results.add(future);
				} else {
					// redundant
//					releaseLock();
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
		results.clear();
		if(!lockAcquired) {
			logger.trace("lock could not be acquired");
			byte[] lock = {};
			// recreate lock in all those clouds where it got deleted.
			for(int i=0; i<cloudIdx; i++){
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

	public String[] uploadFile(ArrayList<ByteArrayBuffer> dataArrays,
			String fileID, WriteMode mode) throws IOException {
//		Pattern p = Pattern.compile(".*table.ser.*");
//		boolean isTable = p.matcher(fileID).matches();
		HashSet<String> cloudsUsed = new HashSet<>();
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		ArrayList<CloudMeta> cloudsBeingUsed = new ArrayList<>();
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			CloudMeta cloudMeta = cloudMetas.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()) {
				Future<Void> future = executor.submit(new Uploader(i,
						dataArrays.get(i).toByteArray(), fileID,
						WriteMode.OVERWRITE));
				cloudsBeingUsed.add(cloudMeta);
				results.add(future);
			} else {
				cloudUploadQueues.get(i).put(fileID,
						dataArrays.get(i).toByteArray());
				writeQueueToFile(i);
				
				// TODO : Do Something Better:
				// AS OF NOW I AM ALSO ADDING ALL THE CLOUDS CONFIGURED 
				// EVEN IF THEY ARE NOT USED RIGHT AWAY
				cloudsUsed.add(cloudMeta.getGenericName());
			}
		}
		for(int i=0; i<results.size(); i++) {
			try {
				Future<Void> result = results.get(i);
				result.get();
				cloudsUsed.add(cloudsBeingUsed.get(i).getGenericName());
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}			
		}
		logger.debug("Number of clouds used: " + cloudsUsed.size());
        return cloudsUsed.toArray(new String[cloudsUsed.size()]);
	}

	public String[] uploadFile(String path, String fileID, WriteMode mode)
			throws IOException {
//		Pattern p = Pattern.compile(".*tablemeta.txt.*");
//		boolean isTablemeta = p.matcher(fileID).matches();
		HashSet<String> cloudsUsed = new HashSet<>();
		ArrayList<Future<Void>> results = new ArrayList<Future<Void>>();
		ArrayList<CloudMeta> cloudsBeingUsed = new ArrayList<>();
		byte[] filedata = Files.readAllBytes(Paths.get(path));
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			CloudMeta cloudMeta = cloudMetas.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()) {
				Future<Void> future = executor.submit(new Uploader(i,
						filedata, fileID, WriteMode.OVERWRITE));
				cloudsBeingUsed.add(cloudMeta);
				results.add(future);
			} else {
				cloudUploadQueues.get(i).put(fileID, filedata);
				writeQueueToFile(i);
				// TODO : Do Something Better:
				// AS OF NOW I AM ALSO ADDING ALL THE CLOUDS CONFIGURED 
				// EVEN IF THEY ARE NOT USED RIGHT AWAY
				cloudsUsed.add(cloudMeta.getGenericName());
			}
		}
		for(int i=0; i<results.size(); i++) {
			try {
				Future<Void> result = results.get(i);
				result.get();
				cloudsUsed.add(cloudsBeingUsed.get(i).getGenericName());
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}			
		}
		logger.debug("Number of clouds used: " + cloudsUsed.size());
        return cloudsUsed.toArray(new String[cloudsUsed.size()]);
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
	
	public ArrayList<byte[]> downloadFile(String fileID) {
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
		boolean downloaded = false;
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloudUploadQueues.get(i).isEmpty()
					&& cloud.searchFile(fileID)) {
				try {
					cloud.downloadFile(path, fileID);
					downloaded = true;
					break;
				} catch (IOException e) {
					logger.error("Error downloading file from cloud : " + fileID, e);
				}
			}
		}
		if(!downloaded) {
			throw new IOException();
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
		int cloudIdx;
		byte[] data;
		String fileID;
		WriteMode mode;

		public Uploader(int cloudIdx, byte[] data, String fileID, WriteMode mode) {
			super();
			this.cloud = clouds.get(cloudIdx);
			this.cloudIdx = cloudIdx;
			this.data = data;
			this.fileID = fileID;
			this.mode = mode;
		}

		@Override
		public Void call() throws Exception {
			try {
				logger.trace("Uploading to cloud" + cloudIdx);
				cloud.uploadFile(data, fileID, mode);
			}catch (Exception e) {
				logger.error("error while uploading " + fileID, e);
				cloudUploadQueues.get(cloudIdx).put(fileID, data);
				throw e;
			}
			return null;
		}
	}

	class Downloader implements Callable<byte[]> {
		private int cloudIdx;
		private Cloud cloud;
		private String fileID;

		public Downloader(int cloudIdx, String fileID) {
			super();
			this.cloudIdx = cloudIdx;
			this.cloud = clouds.get(cloudIdx);
			this.fileID = fileID;
		}

		@Override
		public byte[] call() throws Exception {
			logger.trace("Downloading from cloud" + cloudIdx);
			return cloud.downloadFile(fileID);
		}
	}

	class Deleter implements Callable<Void> {
		private int cloudIdx;
		private Cloud cloud;
		private String fileID;

		public Deleter(int cloudIdx, String fileID) {
			super();
			this.cloudIdx = cloudIdx;
			this.cloud = clouds.get(cloudIdx);
			this.fileID = fileID;
		}

		@Override
		public Void call() throws Exception {
			logger.trace("Deleting in cloud" + cloudIdx);
			cloud.deleteFile(fileID);
			return null;
		}
	}

	private class CloudUploader extends TimerTask {
		int cloudIdx;
		Cloud cloud;
		ConcurrentHashMap<String, byte[]> uploadQueue;

		public CloudUploader(int cloudIdx,
				ConcurrentHashMap<String, byte[]> uploadQueue) {
			this.cloudIdx = cloudIdx;
			this.cloud = clouds.get(cloudIdx);
			this.uploadQueue = uploadQueue;
		}

		public void run() {
			logger.trace("starting periodic upload for cloud" + cloudIdx);
			if(cloud.isAvailable() && !uploadQueue.isEmpty()){
				Set<String> fileNames = uploadQueue.keySet();
				for (String fileName : fileNames) {
					if (cloud.isAvailable()) {
						try {
							cloud.uploadFile(uploadQueue.get(fileName),
									fileName, WriteMode.OVERWRITE);
							uploadQueue.remove(fileName);
						} catch (IOException e) {
							logger.error("error while uploading " + fileName, e);
						}
					} else {
						break;
					}
				}
				writeQueueToFile(cloudIdx);
			}
		}
	}

	public void shutdown() {
		for(Timer periodicUploader : cloudPeriodicUploaders) {
			periodicUploader.cancel();
		}
		executor.shutdown();
	}

}
