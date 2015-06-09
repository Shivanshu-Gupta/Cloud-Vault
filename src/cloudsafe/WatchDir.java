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
import java.nio.file.WatchEvent.Kind;

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
				System.out.format("register: %s\n", dir);
			} else {
				if (!dir.equals(prev)) {
					System.out.format("update: %s -> %s\n", prev, dir);
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
	 * @throws Exception 
	 */
	void processEvents() throws Exception {
		for (;;) {

			// wait for key to be signalled
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey not recognized!!");
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind kind = event.kind();

				// TBD - provide example of how OVERFLOW event is handled
				if (kind == OVERFLOW) {
					System.out.println("OVERFLOW happened in watcher");
					continue;
				}

				// Context for directory entry event is the file name of entry
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);

				// print out event
				System.out.format(".......................%s: %s\n", event
						.kind().name(), child);

				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if (recursive && (kind == ENTRY_CREATE)) {
					try {
						if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
							registerAll(child);
						}
					} catch (IOException x) {
						// ignore to keep sample readbale
					}
				}

				// Directory Created
				if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
					// Folder
					if(kind == ENTRY_DELETE)
					{
						deletedFolder = child.toAbsolutePath().toString();
					}
					continue; // ignore
				}
				
				else {

					System.out.println("DeletedFolder : " + deletedFolder);
					if(child.endsWith("table.ser") || child.endsWith("tablesize.txt"))
					{
						continue;
					}
					if(kind == ENTRY_CREATE)
					{
						try {
							String tempo = child.toAbsolutePath().toString();
							client.upload(tempo);
							System.out.println("-------------------------------Upload Finished : " + tempo);
						} catch (NullPointerException e) {
							// TODO Auto-generated catch block
							continue;
						}
					}
					else if(kind == ENTRY_MODIFY)
					{
						String tempo = child.toAbsolutePath().toString();
						if(deletedFolder.length() == 0)
						{
							client.upload(tempo);
						}
						else  {
							if (tempo.startsWith(deletedFolder)) {
								System.out.println("............Entered Delete");
								client.delete(tempo);
							} else {
								System.out.println("............Entered Upload");
								client.upload(tempo);
								deletedFolder = "";
							}
						}
						System.out.println("-------------------------------Modify Finished : " + tempo);
					}
					else if(kind == ENTRY_DELETE)
					{
						String tempo = child.toAbsolutePath().toString();
						try {
							client.delete(tempo);
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();continue;
						}
						catch (Exception e){
							continue;
						}
						System.out.println("-------------------------------Delete Finished : " + tempo);
					}
					
				}
				
				/*else {
					if (kindBuffer == null) 
					{
						kindBuffer = kind;
						childBuffer = child.toString();
						System.out.println("kindBuffer modified");
					}
					else if (kindBuffer == ENTRY_CREATE)
					{
						if(kind == ENTRY_CREATE)
						{
							if(childBuffer.equals(child.toString()))
							{
								System.out.println("--------------Impossible Event Happened");
							}
							else
							{
								simpleupload(kind,child.toString());
							}
						}
						else if(kind == ENTRY_MODIFY)
						{
							if(childBuffer.equals(child.toString()))
							{
								simpleupload(kind,child.toString());
								kindBuffer = null;
								childBuffer = null;
							}
							else
							{
								simpleupload(kind,child.toString());
							}
						}
						else if(kind == ENTRY_DELETE)
						{
							if(childBuffer.equals(child.toString()))
							{
								kindBuffer = null;
								childBuffer = null;
							}
							else
							{
								simpleupload(kind,child.toString());
							}
						}
					}
					else if (kindBuffer == ENTRY_MODIFY)
					{
						if(kind == ENTRY_CREATE)
						{
							if(childBuffer.equals(child.toString()))
							{
								simpleupload(kind,child.toString());
							}
							else
							{
								simpleupload(kind,child.toString());
							}
						}
						else if(kind == ENTRY_MODIFY)
						{
							if(childBuffer.equals(child.toString()))
							{
								simpleupload(kind,child.toString());
								kindBuffer = null;
								childBuffer = null;								
							}
							else
							{
								simpleupload(kind,child.toString());
							}
						}
						else if(kind == ENTRY_DELETE)
						{
							if(childBuffer.equals(child.toString()))
							{
								simpleupload(kind,child.toString());
							}
							else
							{
								simpleupload(kind,child.toString());
							}
						}
					}
					else if (kindBuffer == ENTRY_DELETE)
					{
						if(kind == ENTRY_CREATE)
						{
							if(childBuffer.equals(child.toString()))
							{
								simpledelete(kind,child.toString());
							}
							else
							{
								simpledelete(kind,child.toString());
							}
						}
						else if(kind == ENTRY_MODIFY)
						{
							if(childBuffer.equals(child.toString()))
							{
								System.out.println("--------------Impossible Event Happened");							
							}
							else
							{
								simpledelete(kind,child.toString());
							}
						}
						else if(kind == ENTRY_DELETE)
						{
							if(childBuffer.equals(child.toString()))
							{
								System.out.println("--------------Impossible Event Happened");
							}
							else
							{
								simpledelete(kind,child.toString());
							}
						}
					}
				}
				
				
				//if this is the last entry
				if(kindBufferPrev == kindBuffer && childBuffer.equals(childBufferPrev))
				{
					if(kindBuffer == ENTRY_CREATE || kindBuffer == ENTRY_MODIFY)
					{
						client.upload(Paths.get(childBuffer).toAbsolutePath().toString());
						System.out.println("Upload Finished : " + childBuffer);
						kindBuffer = null;
						childBuffer = null;
					}
					else if(kindBuffer == ENTRY_DELETE)
					{
						try {
							client.delete(childBuffer);
							System.out.println("Upload Finished : " + childBuffer);
							kindBuffer = null;
							childBuffer = null;
						} catch (FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				
				kindBufferPrev = kindBuffer;
				childBufferPrev = childBuffer;*/
				
				

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
		
	private void simpledelete(WatchEvent.Kind kind, String childString) throws Exception {
		try {
			String tempo = Paths.get(childBuffer).toAbsolutePath().toString();
			client.delete(tempo);
			System.out.println("---------------------------Delete Finished : " + tempo);
			kindBuffer = kind;
			childBuffer = childString;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void simpleupload (WatchEvent.Kind kind, String childString) throws Exception{
		String tempo = Paths.get(childBuffer).toAbsolutePath().toString();
		client.upload(tempo);
		System.out.println("-------------------------------Upload Finished : " + tempo);
		kindBuffer = kind;
		childBuffer = childString;
	}

	static void usage() {
		System.err.println("usage: java WatchDir [-r] dir");
		System.exit(-1);
	}

	/*
	 * public static void main(String[] args) throws IOException { String
	 * targetdir = "test"; // parse arguments boolean recursive = true; //
	 * register directory and process its events Path dir =
	 * Paths.get(targetdir); new WatchDir(dir, recursive).processEvents(); }
	 */
}