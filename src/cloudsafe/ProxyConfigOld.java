package cloudsafe;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

public class ProxyConfigOld extends JPanel implements ActionListener {
	
	private static final long serialVersionUID = 1L;
	String configPath = "trials/config";
	private File configFile = null;
	private Properties configProps;
	
	private String useProxy = "no";
	
	private JLabel labelHost = new JLabel("Proxy Host: ");
	private JLabel labelPort = new JLabel("Proxy Port: ");
	private JLabel labelUser = new JLabel("Username: ");
	private JLabel labelPass = new JLabel("Password: ");
	
	private JTextField textHost = new JTextField(20);
	private JTextField textPort = new JTextField(20);
	private JTextField textUser = new JTextField(20);
	private JTextField textPass = new JTextField(20);
	
	private JButton buttonSave = new JButton("Save");
	
	public ProxyConfigOld(String configPath) {
		this.configPath = configPath;
		configFile = new File(configPath + "/config.properties");
		
		setLayout(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(10, 10, 5, 10);
		constraints.anchor = GridBagConstraints.WEST;
		
		JRadioButton noProxyButton = new JRadioButton("No Proxy");
        noProxyButton.setActionCommand("no");
        
        JRadioButton useProxyButton = new JRadioButton("Configure Proxy Authentication: ");
        useProxyButton.setActionCommand("yes");
        
        ButtonGroup proxyButtons = new ButtonGroup();
        proxyButtons.add(noProxyButton);
        proxyButtons.add(useProxyButton);
		
        noProxyButton.addActionListener(this);
        useProxyButton.addActionListener(this);
        
        add(noProxyButton);
        
        constraints.gridy = 1;
        add(useProxyButton);
        
        constraints.gridy = 2;
		add(labelHost, constraints);
		
		constraints.gridx = 1;
		add(textHost, constraints);
		
		constraints.gridy = 3;
		constraints.gridx = 0;
		add(labelPort, constraints);
		
		constraints.gridx = 1;
		add(textPort, constraints);

		constraints.gridy = 4;
		constraints.gridx = 0;
		add(labelUser, constraints);
		
		constraints.gridx = 1;
		add(textUser, constraints);

		constraints.gridy = 5;
		constraints.gridx = 0;
		add(labelPass, constraints);
		
		constraints.gridx = 1;
		add(textPass, constraints);
		
		constraints.gridy = 6;
		constraints.gridx = 1;
		constraints.gridwidth = 2;
		constraints.anchor = GridBagConstraints.CENTER;
		add(buttonSave, constraints);
		
		buttonSave.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					saveProperties();
					JOptionPane.showMessageDialog(ProxyConfigOld.this, 
							"Properties were saved successfully!");
					
				} catch (IOException ex) {
					JOptionPane.showMessageDialog(ProxyConfigOld.this, 
							"Error saving properties file: " + ex.getMessage(),
							"Error",
							JOptionPane.ERROR_MESSAGE);		
				}
			}
		});
		
		setVisible(true);
		
		loadProperties();
		textHost.setText(configProps.getProperty("proxyhost"));
		textPort.setText(configProps.getProperty("proxyport"));
		textUser.setText(configProps.getProperty("proxyuser"));
		textPass.setText(configProps.getProperty("proxypass"));
        
		useProxy = configProps.getProperty("requireproxy");
        if(useProxy.equals("no")){
        	noProxyButton.setSelected(true);
        	setEnableFields(false);
        } else {
        	useProxyButton.setSelected(true);
        	setEnableFields(true);
        }
	}
	
	private void loadProperties() {
		Properties defaultProps = new Properties();
		// sets default properties
		defaultProps.setProperty("requireproxy", "no");
		defaultProps.setProperty("proxyhost", "");
		defaultProps.setProperty("proxyport", "");
		defaultProps.setProperty("proxyuser", "");
		defaultProps.setProperty("proxypass", "");
		
		configProps = new Properties(defaultProps);
		
		// loads properties from file
		try {
			if(Files.exists(Paths.get(configFile.toString()))) {
				InputStream inputStream = new FileInputStream(configFile);
				configProps.load(inputStream);
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(this, "<html>Error loading proxy configuration: "
					+ "config file not found.<br>"
					+ "Default properties loaded instead."
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "<html>Error loading proxy configuration: "
					+ "proxy settings could not be read.<br>"
					+ "Default properties loaded instead."
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}
	
	private void saveProperties() throws IOException {
		configProps.setProperty("requireproxy", useProxy);
		configProps.setProperty("proxyhost", textHost.getText());
		configProps.setProperty("proxyport", textPort.getText());
		configProps.setProperty("proxyuser", textUser.getText());
		configProps.setProperty("proxypass", textPass.getText());
		OutputStream outputStream;
		try {
			outputStream = new FileOutputStream(configFile);
			configProps.store(outputStream, "host setttings");
			outputStream.close();
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(this, "<html>Error saving proxy configuration: "
					+ "config file not found.<br>"
					+ "</html>");
			e.printStackTrace();
		}
	}
	
	public Properties getProperties(){
		configProps.setProperty("proxyhost", textHost.getText());
		configProps.setProperty("proxyport", textPort.getText());
		configProps.setProperty("proxyuser", textUser.getText());
		configProps.setProperty("proxypass", textPass.getText());
		return configProps;
	}
	
	public void enableComponents(Container container, boolean enable) {
        Component[] components = container.getComponents();
        for (Component component : components) {
            component.setEnabled(enable);
            if (component instanceof Container) {
                enableComponents((Container)component, enable);
            }
        }
    }
	
	private void setEnableFields(boolean enable){
		labelHost.setEnabled(enable);
		labelPort.setEnabled(enable);
		labelUser.setEnabled(enable);
		labelPass.setEnabled(enable);
		textHost.setEnabled(enable);
		textPort.setEnabled(enable);
		textUser.setEnabled(enable);
		textPass.setEnabled(enable);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		useProxy = e.getActionCommand();
		if(useProxy.equals("no")){
			setEnableFields(false);
		} else {
			setEnableFields(true);
		}
	}
}