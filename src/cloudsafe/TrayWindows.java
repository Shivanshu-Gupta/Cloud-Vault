/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
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
 *   - Neither the name of Oracle or the names of its
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

package cloudsafe;  
/*
 * TrayIconDemo.java
 */

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.*;

public class TrayWindows {
	
	static JTabbedPane settings = new JTabbedPane();
	ProxyConfig proxySettings;
	CloudConfig cloudSettings;
	
    TrayWindows(String configPath, Setup cloudVaultSetup) {
    	
    	

		proxySettings = new ProxyConfig(configPath);
		settings.addTab("Proxy Settings", null, proxySettings,
				"Proxy Settings");
		cloudSettings = new CloudConfig(configPath,
				cloudVaultSetup);
		settings.addTab("Clouds", null, cloudSettings, "Clouds");
		
        /* Use an appropriate Look and Feel */
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            //UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (UnsupportedLookAndFeelException ex) {
            ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        } catch (InstantiationException ex) {
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        /* Turn off metal's use of bold fonts */
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        //Schedule a job for the event-dispatching thread:
        //adding TrayIcon.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
    
    private static void createAndShowGUI() {
        //Check the SystemTray support
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();
        Path filePath = Paths.get("icons/CloudVault1.png");
        final TrayIcon trayIcon = 
                new TrayIcon(createImage(filePath.toString(), "CloudVault"));
        final SystemTray tray = SystemTray.getSystemTray();
        
        // Create a popup menu components
//        MenuItem aboutItem = new MenuItem("About");
        MenuItem settingItem = new MenuItem("Settings");
        MenuItem exitItem = new MenuItem("Exit");
        
        //Add components to popup menu
//        popup.add(aboutItem);
//        popup.addSeparator();
        popup.add(settingItem);
        popup.add(exitItem);
        
        trayIcon.setPopupMenu(popup);
        trayIcon.setImageAutoSize(true);
        
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
            return;
        }
        
        trayIcon.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null,
                        "This dialog box is run from System Tray");
            }
        });
        
//        aboutItem.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                JOptionPane.showMessageDialog(null,
//                        "This dialog box is run from the About menu item");
//            }
//        });
        
        settingItem.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		JOptionPane.showMessageDialog(null, settings, "Settings", JOptionPane.PLAIN_MESSAGE);
        		return;
        	}
        });
        
        exitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tray.remove(trayIcon);
                System.exit(0);
            }
        });
    }
    
    //Obtain the image URL
	protected static Image createImage(String path, String description) {
		return (new ImageIcon(path,description)).getImage();
    }
}