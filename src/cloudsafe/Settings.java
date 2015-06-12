//package cloudsafe;
//
//import java.awt.Dialog;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.util.Properties;
//
//import javax.swing.ButtonGroup;
//import javax.swing.JButton;
//import javax.swing.JDialog;
//import javax.swing.JOptionPane;
//import javax.swing.JRadioButton;
//
//public class Settings implements ActionListener {
//	
//	String vaultConfigPath = "trials/config";
//	private File configFile = null;
//	private Properties configProps;
//	JDialog settings = new JDialog(null, "Settings", Dialog.ModalityType.APPLICATION_MODAL);
//	
//	private ProxyConfig proxyConfig = null;
//	private String useProxy = "no";
//	
//	private JButton buttonSave = new JButton("Save");
//	
//	public Settings(String vaultConfigPath) {
//		this.vaultConfigPath = vaultConfigPath;
//		configFile = new File(vaultConfigPath + "/config.properties");
//
//		JRadioButton noProxyButton = new JRadioButton("No Proxy");
//        noProxyButton.setActionCommand("no");
////        noProxyButton.setSelected(true);
//        
//        JRadioButton useProxyButton = new JRadioButton("Configure Proxy Authentication: ");
//        useProxyButton.setActionCommand("yes");
//        
//        ButtonGroup proxyButtons = new ButtonGroup();
//        proxyButtons.add(noProxyButton);
//        proxyButtons.add(useProxyButton);
//		
//        noProxyButton.addActionListener(this);
//        useProxyButton.addActionListener(this);
//        settings.add(noProxyButton);
//        settings.add(useProxyButton);        
//		settings.add(buttonSave);
//		
//		buttonSave.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent arg0) {
//				try {
//					saveProperties();
//					JOptionPane.showMessageDialog(settings, 
//							"Properties were saved successfully!");
//					
//				} catch (IOException ex) {
//					JOptionPane.showMessageDialog(settings, 
//							"Error saving properties file: " + ex.getMessage());		
//				}
//			}
//		});
//        
//        try {
//			loadProperties();
//		} catch (IOException e) {
//			JOptionPane.showMessageDialog(settings, "The config.properties file does not exist, default properties loaded.");
//		}
//		proxyConfig = new ProxyConfig(configProps);
//		settings.add(proxyConfig);
//        settings.pack();
//        settings.setVisible(true);
//        
//        useProxy = configProps.getProperty("requireproxy");
//        if(useProxy.equals("no")){
//        	noProxyButton.setSelected(true);
//        	proxyConfig.setEnabled(false);
//        } else {
//        	useProxyButton.setSelected(true);
//        	proxyConfig.setEnabled(true);
//        }
//	}
//	
//	public void loadProperties() throws IOException {
//		Properties defaultProps = new Properties();
//		// sets default properties
//		defaultProps.setProperty("requireproxy", "no");
//		defaultProps.setProperty("proxyhost", "");
//		defaultProps.setProperty("proxyport", "");
//		defaultProps.setProperty("proxyuser", "");
//		defaultProps.setProperty("proxypass", "");
//		
//		configProps = new Properties(defaultProps);
//		// loads properties from file
//		InputStream inputStream = new FileInputStream(configFile);
//		configProps.load(inputStream);
//		inputStream.close();
//	}
//	
//	public void saveProperties() throws IOException {
//		configProps = new Properties(proxyConfig.getProperties());
//		configProps.setProperty("requireproxy", useProxy);
//		OutputStream outputStream = new FileOutputStream(configFile);
//		configProps.store(outputStream, "host setttings");
//		outputStream.close();
//	}
//	
//
//	@Override
//	public void actionPerformed(ActionEvent e) {
//		useProxy = e.getActionCommand();
//		if(useProxy.equals("no")){
//			proxyConfig.setEnabled(false);
//		} else {
//			proxyConfig.setEnabled(false);
//		}
//	}
//	
//	
//}
