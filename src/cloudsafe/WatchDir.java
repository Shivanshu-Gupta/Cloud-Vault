package cloudsafe;

/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;

import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.exceptions.LockNotAcquiredException;
import cloudsafe.util.Pair;

public class WatchDir {
	
	private final static Logger logger = LogManager
			.getLogger(WatchDir.class.getName());

	private final WatchService watcher;
	private VaultClientDesktop client;
	private final Map<WatchKey, Path> keys;
	private final boolean recursive;
	private boolean trace = false;

	private boolean terminate = false;
	
	WatchEvent.Kind kindBuffer = null;
	String childBuffer = null;
	WatchEvent.Kind kindBufferPrev = null;
	String childBufferPrev = null;

	String deletedFolder = "";

	ArrayList<String> uploadQueue = new ArrayList<String>();
	ArrayList<String> deleteQueue = new ArrayList<String>();
	

	ArrayList <String> downloadsSyncList = new ArrayList <String>();
	ArrayList <String> deleteSyncList = new ArrayList <String>();	// No need to use it??

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	/**
	 * Register the given directory with the WatchService
	 */
	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE,
				ENTRY_MODIFY);
		if (trace) {
			Path prev = keys.get(key);
			if (prev == null) {
				logger.info("register: %s\n" +  dir);
			} else {
				if (!dir.equals(prev)) {
					logger.info("update: " + prev + " -> " + dir + "\n", prev, dir);
//					System.out.format("update: %s -> %s\n", prev, dir);
					deleteAllFiles(prev, dir);
				}
			}
		}
		keys.put(key, dir);
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	private void registerAll(final Path start) throws IOException {
		// register directory and sub-directories
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir,
					BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Creates a WatchService and registers the given directory
	 */
	WatchDir(Path dir, boolean recursive, VaultClientDesktop client)
			throws IOException {
		this.client = client;
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		this.recursive = recursive;

		if (recursive) {
			logger.info("Scanning " + dir + " ...\n");
			registerAll(dir);
			logger.info("Done.");
		} else {
			register(dir);
		}

		// enable trace after initial registration
		this.trace = true;
	}

	/**
	 * Process all events for keys queued to the watcher
	 * 
	 * @throws Exception
	 */

	private int updateCounter = 0;
	
	class Execution extends TimerTask {
		public void run() {
			if(updateCounter == 0){
				executeUpdate();
			}
			else{
				executeSync();
			}
			updateCounter = (updateCounter + 1) % 2;
		}
	}
	
	Timer timer = new Timer();
	
	void processEvents() {
		timer.schedule(new Execution(), 0, 5000);
		for (;!terminate;) {

			// wait for key to be signalled
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				timer.cancel();
				timer.purge();
				return;
			}

			Path dir = keys.get(key);
			// System.out.println("key : " + key.toString());
			if (dir == null) {
				logger.error("WatchKey not recognized!!");
				continue;
			}
			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind kind = event.kind();

				// TBD - provide example of how OVERFLOW event is handled
				if (kind == OVERFLOW) {
					continue;
				}

				// Context for directory entry event is the file name of entry
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);

				logger.info(event.kind().name() + ": " + child + "\n");

				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if (recursive && (kind == ENTRY_CREATE)) {
					try {
						if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
							registerAll(child);
							addAllFiles(child);
						}
					} catch (IOException x) {
						// ignore to keep sample readable
					}
				}
				String AbsoluteFilePath = child.toAbsolutePath().toString();
				if (Files.isDirectory(child, NOFOLLOW_LINKS)
						&& kind == ENTRY_MODIFY) {

				} else if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
					if (Files.exists(child, NOFOLLOW_LINKS)) {
						if (uploadQueue.contains(AbsoluteFilePath)) {
							logger.info("UPLOADQUEUE - File Already Present : "
											+ AbsoluteFilePath);
						} else {
							uploadQueue.add(AbsoluteFilePath);
							deleteQueue.remove(AbsoluteFilePath);
						}
					} else {
						uploadQueue.remove(AbsoluteFilePath);
						deleteQueue.add(AbsoluteFilePath);
					}

				}

				// Deleted Folder also gets transmitted to cloud
				else // ENTRY_DELETE
				{

					if (uploadQueue.remove(AbsoluteFilePath)) {
						logger.info("UPLOADQUEUE - File Removed : "
								+ AbsoluteFilePath);
						if (!deleteQueue.contains(AbsoluteFilePath))
							deleteQueue.add(AbsoluteFilePath);			
					} else {
						if (!deleteQueue.contains(AbsoluteFilePath))
							deleteQueue.add(AbsoluteFilePath);
					}
				}
				logger.info("Upload Size : " + uploadQueue.size()
						+ "\tDelete Size : " + deleteQueue.size());
			}
			// reset key and remove from set if directory no longer accessible
			boolean valid = key.reset();
			if (!valid) {
				keys.remove(key);

				// all directories are inaccessible
				if (keys.isEmpty()) {
					break;
				}
			}
		}
	}
	// add all the files of newly created folder in uploadqueue
	void addAllFiles(Path start) throws IOException {

		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir,
					BasicFileAttributes attrs) throws IOException {
				uploadQueue.add(dir.toAbsolutePath().toString());
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				if (Files.isHidden(file)) {
					return FileVisitResult.CONTINUE;
				}
				uploadQueue.add(file.toAbsolutePath().toString());
				logger.trace("file added inside walk tree");
				return FileVisitResult.CONTINUE;
			}

		});
	}

	void deleteAllFiles(Path prevDir, Path currentDir) throws IOException {
		Files.walkFileTree(currentDir, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir,
					BasicFileAttributes attrs) throws IOException {
				Path currentParentpath = dir/* .toAbsolutePath() */;
				Path commonpath = currentDir.relativize(currentParentpath);
				Path prevParentpath = prevDir.resolve(commonpath);

				currentParentpath = currentParentpath.toAbsolutePath();
				prevParentpath = prevParentpath.toAbsolutePath();
				// Path prevParentpath = currentParentpath.subpath(0,
				// currentParentpath.getNameCount() -
				// currentDir.getNameCount()).resolve(prevDir);

				String prevpath = prevParentpath.toString();
				logger.info("Current path : " + currentParentpath);
				logger.info("common path : " + commonpath);
				logger.info("Previous path : " + prevParentpath);
				uploadQueue.remove(prevpath);
				if (!deleteQueue.contains(prevpath))
					deleteQueue.add(prevpath);
				logger.trace("dir deleted inside walk tree");
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				if (Files.isHidden(file)) {
					return FileVisitResult.CONTINUE;
				}
				Path currentParentpath = file.getParent()/* .toAbsolutePath() */;
				Path commonpath = currentDir.relativize(currentParentpath);
				Path prevParentpath = prevDir.resolve(commonpath);

				currentParentpath = currentParentpath.toAbsolutePath();
				prevParentpath = prevParentpath.toAbsolutePath();
				// Path prevParentpath = currentParentpath.subpath(0,
				// currentParentpath.getNameCount() -
				// currentDir.getNameCount()).resolve(prevDir);

				String prevpath = prevParentpath.toString() + "\\"
						+ file.getFileName().toString();
				logger.info("Current path : " + currentParentpath);
				logger.info("common path : " + commonpath);
				logger.info("Previous path : " + prevParentpath);
				uploadQueue.remove(prevpath);
				if (!deleteQueue.contains(prevpath))
					deleteQueue.add(prevpath);
				logger.trace("file deleted inside walk tree");
				return FileVisitResult.CONTINUE;
			}

		});
	}
	
	ArrayList<String> uQueue = new ArrayList<String>();
	void executeUpdate() {
		logger.trace("ExecuteUpdate Called");
		
		while (!uploadQueue.isEmpty()) {
			String filepath = uploadQueue.get(0);
			uploadQueue.remove(0);
			if(downloadsSyncList.contains(filepath))
			{
				downloadsSyncList.remove(filepath);
			}
			else
			{
				logger.info("Executing UPLOAD QUEUE : " + filepath);
				uQueue.add(filepath);			
			}
		}
		
		try {
			if(!uQueue.isEmpty()){
				client.upload(uQueue);
				uQueue.clear();
			}
		} catch (LockNotAcquiredException e1) {
			// TODO save this uploadQueue for next time of executeUpload is called 
			e1.printStackTrace();
		}	
		
		
		
		ArrayList<String> dQueue = new ArrayList<String>();
		while (!deleteQueue.isEmpty()) {
			String filepath = deleteQueue.get(0);
			deleteQueue.remove(0);
			if(deleteSyncList.contains(filepath))
			{
				deleteSyncList.remove(filepath);
			}
			else
			{
				logger.info("Executing DELETE QUEUE : " + filepath);
				dQueue.add(filepath);
//				client.delete(filepath);			
			}
		}
		
		try {
			if(!dQueue.isEmpty()) {
				client.delete(dQueue);
			}
		} catch (LockNotAcquiredException e) {
			// TODO save this uploadQueue for next time of executeUpload is called 
			e.printStackTrace();
		}	
		dQueue.clear();
		
	}
	
	void executeSync() {
		downloadsSyncList.clear();  //Not Required Actually
		deleteSyncList.clear();		//Not Required Actually
		logger.trace("ExecuteSync Called");
		try{
			Pair<ArrayList<String>, ArrayList<String>> pairOfList = client.sync();
			if(pairOfList == null)
			{
				return;
			}
			downloadsSyncList = pairOfList.first;
			deleteSyncList = pairOfList.second;
		}
		catch (NullPointerException e){
			e.printStackTrace();
			return;
		}
	}
	
	public void shutdown()
	{
		terminate = true;
		try {
			watcher.close();
		} catch (IOException | ClosedWatchServiceException e) {
//			logger.error(");
		}
		timer.cancel();
		timer.purge();
	}
}