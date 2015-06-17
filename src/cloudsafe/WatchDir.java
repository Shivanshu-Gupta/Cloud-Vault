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

/**
 * Example to watch a directory (or tree) for changes to files.
 */

public class WatchDir {

	private final WatchService watcher;
	private VaultClientDesktop client;
	private final Map<WatchKey, Path> keys;
	private final boolean recursive;
	private boolean trace = false;

	WatchEvent.Kind kindBuffer = null;
	String childBuffer = null;
	WatchEvent.Kind kindBufferPrev = null;
	String childBufferPrev = null;

	String deletedFolder = "";

    ArrayList <String> uploadQueue = new ArrayList <String>();
    ArrayList <String> deleteQueue = new ArrayList <String>();
    
	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	/**
	 * Register the given directory with the WatchService
	 */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                    deleteAllFiles(prev,dir);
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
			System.out.format("Scanning %s ...\n", dir);
			registerAll(dir);
			System.out.println("Done.");
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
	
	void processEvents() {
    	class Execution extends TimerTask {
    	    public void run() {
    	       executeUpdate();
    	    }
    	 }
    	
    	Timer timer = new Timer();
    	 timer.schedule(new Execution(), 0, 5000);
    	 
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
            	
            	//System.out.println("Waiting for take()");
                key = watcher.take();
        
                //System.out.println("Wait finished");
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
           // System.out.println("key : " + key.toString());
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }
            int counter = 0;
            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                System.out.format("..........%s: %s\n", event.kind().name(), child);

                
                
                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                            addAllFiles(child);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    }
                }
                String AbsoluteFilePath = child.toAbsolutePath().toString();
                if(Files.isDirectory(child,NOFOLLOW_LINKS) && kind == ENTRY_MODIFY)
                {
                	
                }
                else if(kind == ENTRY_CREATE || kind == ENTRY_MODIFY)
                {
                	if(Files.exists(child, NOFOLLOW_LINKS))
                	{
                    	if(uploadQueue.contains(AbsoluteFilePath))
                    	{
                    		System.out.println("UPLOADQUEUE - File Already Present : " + AbsoluteFilePath);
                    	}
                    	else
                    	{
                    		uploadQueue.add(AbsoluteFilePath);
                    		deleteQueue.remove(AbsoluteFilePath);
                    	}
                	}
                	else
                	{
                    	uploadQueue.remove(AbsoluteFilePath);
                    	deleteQueue.add(AbsoluteFilePath);
                	}

                }
                
                //Deleted Folder also gets transmitted to cloud
                else		//ENTRY_DELETE
                {
                	
                	if(uploadQueue.remove(AbsoluteFilePath))
                	{
                		System.out.println("UPLOADQUEUE - File Removed : " + AbsoluteFilePath);
                	}
                	else
                	{
                		if(!deleteQueue.contains(AbsoluteFilePath))
                			deleteQueue.add(AbsoluteFilePath);
                	}                	
                }
                	System.out.println("Upload Size : " + uploadQueue.size() + "\tDelete Size : " + deleteQueue.size());
            }
//            System.out.println("pollevents loop exited, count : " + counter);
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

	static void usage() {
		System.err.println("usage: java WatchDir [-r] dir");
		System.exit(-1);
	}
	
	  //add all the files of newly created folder in uploadqueue
    void addAllFiles(Path start) throws IOException
    {
        
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {

        	@Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        		uploadQueue.add(dir.toAbsolutePath().toString());
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isHidden(file)) {
                    return FileVisitResult.CONTINUE;
                }
                uploadQueue.add(file.toAbsolutePath().toString());
                System.out.println("file added inside walk tree");
                return FileVisitResult.CONTINUE;
            }
            
            
        });
    }
    
    void deleteAllFiles(Path prevDir,Path currentDir) throws IOException
    {
        Files.walkFileTree(currentDir, new SimpleFileVisitor<Path>() {

        	@Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        		Path currentParentpath = dir/*.toAbsolutePath()*/;
                Path commonpath = currentDir.relativize(currentParentpath);
                Path prevParentpath = prevDir.resolve(commonpath);
                
                currentParentpath = currentParentpath.toAbsolutePath();
                prevParentpath = prevParentpath.toAbsolutePath();
              //  Path prevParentpath = currentParentpath.subpath(0, currentParentpath.getNameCount() - currentDir.getNameCount()).resolve(prevDir);
                
                String prevpath = prevParentpath.toString();
                System.out.println("Current path : " + currentParentpath);
                System.out.println("common path : " + commonpath);
                System.out.println("Previous path : " + prevParentpath);
                uploadQueue.remove(prevpath);
                if(!deleteQueue.contains(prevpath))
                	deleteQueue.add(prevpath);
                System.out.println("dir deleted inside walk tree");
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isHidden(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path currentParentpath = file.getParent()/*.toAbsolutePath()*/;
                Path commonpath = currentDir.relativize(currentParentpath);
                Path prevParentpath = prevDir.resolve(commonpath);
                
                currentParentpath = currentParentpath.toAbsolutePath();
                prevParentpath = prevParentpath.toAbsolutePath();
              //  Path prevParentpath = currentParentpath.subpath(0, currentParentpath.getNameCount() - currentDir.getNameCount()).resolve(prevDir);
                
                String prevpath = prevParentpath.toString() + "\\" + file.getFileName().toString();
                System.out.println("Current path : " + currentParentpath);
                System.out.println("common path : " + commonpath);
                System.out.println("Previous path : " + prevParentpath);
                uploadQueue.remove(prevpath);
                if(!deleteQueue.contains(prevpath))
                	deleteQueue.add(prevpath);
                System.out.println("file deleted inside walk tree");
                return FileVisitResult.CONTINUE;
            }
            
            
        });
    }
    
    void executeUpdate()
    {
    	while(!uploadQueue.isEmpty())
    	{
    		String filepath = uploadQueue.get(0);
    		uploadQueue.remove(0);
    		System.out.println("Executing UPLOAD QUEUE : " + filepath);
            try {
				client.upload(filepath);
			} catch (NoSuchFileException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	while(!deleteQueue.isEmpty())
    	{
    		String filepath = deleteQueue.get(0);
    		deleteQueue.remove(0);
    		System.out.println("Executing DELETE QUEUE : " + filepath);
            try {
				client.delete(filepath);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
}