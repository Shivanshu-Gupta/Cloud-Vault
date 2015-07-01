package cloudsafe;  

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TrayWindows {
	private static CountDownLatch restart = null;	
	private static JTabbedPane settings = new JTabbedPane();
	
    TrayWindows(String configPath, String vaultPath, CountDownLatch restart) {
    	TrayWindows.restart = restart;
    	ProxyConfig proxySettings = new ProxyConfig(configPath);
		settings.addTab("Proxy Settings", null, proxySettings,
				"Proxy Settings");
		CloudConfig cloudSettings = new CloudConfig(configPath, vaultPath);
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
        	CountDownLatch res = restart;
        	public void actionPerformed(ActionEvent e) {
        		JOptionPane.showMessageDialog(null, settings, "Settings", JOptionPane.PLAIN_MESSAGE);
        		res.countDown();
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