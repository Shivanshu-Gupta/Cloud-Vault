package cloudsafe;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class Settings extends JPanel {
	
	private static final long serialVersionUID = 1L;
	String vaultConfigPath = "trials/config";
	private File configFile = null;
	private Properties configProps;
	
	private JLabel labelHost = new JLabel("Proxy Host: ");
	private JLabel labelPort = new JLabel("Proxy Port: ");
	private JLabel labelUser = new JLabel("Username: ");
	private JLabel labelPass = new JLabel("Password: ");
	
	private JTextField textHost = new JTextField(20);
	private JTextField textPort = new JTextField(20);
	private JTextField textUser = new JTextField(20);
	private JTextField textPass = new JTextField(20);
	
	private JButton buttonSave = new JButton("Save");
	
	public Settings(String vaultConfigPath) {
		this.vaultConfigPath = vaultConfigPath;
		configFile = new File(vaultConfigPath + "/config.properties");
		
		setLayout(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(10, 10, 5, 10);
		constraints.anchor = GridBagConstraints.WEST;
		
		add(labelHost, constraints);
		
		constraints.gridx = 1;
		add(textHost, constraints);
		
		constraints.gridy = 1;
		constraints.gridx = 0;
		add(labelPort, constraints);
		
		constraints.gridx = 1;
		add(textPort, constraints);

		constraints.gridy = 2;
		constraints.gridx = 0;
		add(labelUser, constraints);
		
		constraints.gridx = 1;
		add(textUser, constraints);

		constraints.gridy = 3;
		constraints.gridx = 0;
		add(labelPass, constraints);
		
		constraints.gridx = 1;
		add(textPass, constraints);
		
		constraints.gridy = 4;
		constraints.gridx = 0;
		constraints.gridwidth = 2;
		constraints.anchor = GridBagConstraints.CENTER;
		add(buttonSave, constraints);
		
		buttonSave.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					saveProperties();
					JOptionPane.showMessageDialog(Settings.this, 
							"Properties were saved successfully!");
					
				} catch (IOException ex) {
					JOptionPane.showMessageDialog(Settings.this, 
							"Error saving properties file: " + ex.getMessage());		
				}
			}
		});
//		addWindowListener(new WindowAdapter() {
//	          public void windowClosing(WindowEvent e) {
//	              notify();
//	          }
//	     });
//		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//		pack();
//		setLocationRelativeTo(null);
		setVisible(true);
		
		try {
			loadProperties();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "The config.properties file does not exist, default properties loaded.");
		}
		textHost.setText(configProps.getProperty("proxyhost"));
		textPort.setText(configProps.getProperty("proxyport"));
		textUser.setText(configProps.getProperty("proxyuser"));
		textPass.setText(configProps.getProperty("proxypass"));
	}
	
	private void addItem(JPanel p, JComponent c, int x, int y, int width, int height, int align) {
	    GridBagConstraints gc = new GridBagConstraints();
	    gc.gridx = x;
	    gc.gridy = y;
	    gc.gridwidth = width;
	    gc.gridheight = height;
	    gc.weightx = 100.0;
	    gc.weighty = 100.0;
	    gc.insets = new Insets(5, 5, 5, 5);
	    gc.anchor = align;
	    gc.fill = GridBagConstraints.NONE;
	    p.add(c, gc);
	}

	private void loadProperties() throws IOException {
		Properties defaultProps = new Properties();
		// sets default properties
		defaultProps.setProperty("requireproxy", "no");
		defaultProps.setProperty("proxyhost", "");
		defaultProps.setProperty("proxyport", "");
		defaultProps.setProperty("proxyuser", "");
		defaultProps.setProperty("proxypass", "");
		
		configProps = new Properties(defaultProps);
		
		// loads properties from file
		InputStream inputStream = new FileInputStream(configFile);
		configProps.load(inputStream);
		inputStream.close();
	}
	
	private void saveProperties() throws IOException {
		configProps.setProperty("requireproxy", "yes");
		configProps.setProperty("proxyhost", textHost.getText());
		configProps.setProperty("proxyport", textPort.getText());
		configProps.setProperty("proxyuser", textUser.getText());
		configProps.setProperty("proxypass", textPass.getText());
		OutputStream outputStream = new FileOutputStream(configFile);
		configProps.store(outputStream, "host setttings");
		outputStream.close();
	}
	
//	public static void main(String[] args) {
//		SwingUtilities.invokeLater(new Runnable() {
//			@Override
//			public void run() {
//				new Settings();
//			}
//		});
//	}
	
	
}