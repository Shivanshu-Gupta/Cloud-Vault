package cloudsafe.client;

import java.io.FileNotFoundException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.Main;
import cloudsafe.VaultClient;

public class ClientHandler implements Runnable {
	private final static Logger logger = LogManager.getLogger(Main.class
			.getName());
	VaultClient client;
//	private ConcurrentHashMap<UploadTask, Date> uploadRequests;
//	private ConcurrentHashMap<DownloadTask, Date> downloadRequests;
//	private ConcurrentHashMap<DeleteTask, Date> deleteRequests;
	private BlockingQueue<ClientTask> tasks;
	AtomicBoolean tableChanged;

	public ClientHandler(VaultClient client,
//			ConcurrentHashMap<UploadTask, Date> uploadRequests,
//			ConcurrentHashMap<DownloadTask, Date> downloadRequests,
//			ConcurrentHashMap<DeleteTask, Date> deleteRequests,
			BlockingQueue<ClientTask> tasks, AtomicBoolean tableChanged) {
		this.client = client;
//		this.uploadRequests = uploadRequests;
//		this.downloadRequests = downloadRequests;
//		this.deleteRequests = deleteRequests;
		this.tasks = tasks;
		this.tableChanged = tableChanged;
	}

	@Override
	public void run() {
		ClientTask task;
		while (true) {
			try {
				task = tasks.take();
				if (task.getType().equals("upload")) {
					UploadTask uploadTask = (UploadTask) task;
//					if (uploadRequests.containsKey(uploadTask) && 
//							uploadRequests.get(uploadTask).equals(uploadTask.getTimestamp())) {
						client.upload(uploadTask.getLocalFilePath(),
								uploadTask.getUploadPath());
						tableChanged.set(true);
//						uploadRequests.remove(uploadTask, uploadTask.getTimestamp());
//					}
				} else if (task.getType().equals("download")) {
					DownloadTask downloadTask = (DownloadTask)task;
//					if (downloadRequests.containsKey(downloadTask)&& 
//							downloadRequests.get(downloadTask).equals(downloadTask.getTimestamp())) {
						try {
							client.download(downloadTask.getCloudFilePath());
						} catch (FileNotFoundException e) {
							//TODO show the user an error
							logger.error("file couldn't be downloaded: " + e);
						}
//						downloadRequests.remove(downloadTask, downloadTask.getTimestamp());
//					}
				} else if (task.getType().equals("delete")) {
					DeleteTask deleteTask = (DeleteTask)task;
//					if (deleteRequests.containsKey(deleteTask)&& 
//							deleteRequests.get(deleteTask).equals(deleteTask.getTimestamp())) {
						try {
							client.delete(deleteTask.getCloudFilePath());
							tableChanged.set(true);
						} catch (FileNotFoundException e) {
							//TODO show the user an error
							logger.error("file couldn't be deleted: " + e);
						}
//						deleteRequests.remove(deleteTask, deleteTask.getTimestamp());
//					}
				}
			} catch (InterruptedException e) {
				logger.error("handler thread interrupted" + e);
				e.printStackTrace();
			}

		}
	}

}
