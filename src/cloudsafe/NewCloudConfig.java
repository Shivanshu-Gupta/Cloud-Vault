package cloudsafe;

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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import cloudsafe.util.Pair;

public class NewCloudConfig extends JPanel implements ActionListener {

	private static final long serialVersionUID = 1L;
	private int removeIndex = -1;
	private ArrayList<JLabel> labelCloud = new ArrayList<JLabel>();
	private ArrayList<JLabel> cloudValue = new ArrayList<JLabel>();
	private ArrayList<JRadioButton> radio = new ArrayList<JRadioButton>();
	private ArrayList<String> cloudIdList = new ArrayList<String>();
	private JLabel noCloudLabel = new JLabel("No Clouds Added");
	
	private JButton addButton = new JButton("Add More");
	private JButton removeButton = new JButton("Remove");

	private String cloudMetadataPath = null;
	
	
	GridBagConstraints constraints = new GridBagConstraints();
	int y = 2;
	ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String,String>>();
	
	File cloudConfigFile = null;
	Properties cloudConfigProps;
	private int cloudcounter;

	@SuppressWarnings("unchecked")
	public NewCloudConfig(String configPath, Setup cloudVaultSetup) {
		try {
			cloudMetadataPath = configPath + "/cloudmetadata.ser";
			FileInputStream fileIn = new FileInputStream(cloudMetadataPath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			cloudMetaData = (ArrayList<Pair<String, String>>) in
					.readObject();
			in.close();
			fileIn.close();
		} catch (IOException x) {
//			logger.error("IOException while adding cloud " + x);
		} catch (ClassNotFoundException cfe) {
			cfe.printStackTrace();
		}
		cloudConfigDraw(cloudVaultSetup);
		
	}
	
	public void cloudConfigDraw(Setup cloudVaultSetup) {

		setLayout(new GridBagLayout());
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(10, 10, 5, 10);
		constraints.anchor = GridBagConstraints.WEST;

		refreshPage();
		
		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					cloudVaultSetup.addCloud();
					saveMetadata(cloudVaultSetup);
					JOptionPane.showMessageDialog(null, "Added");
					clearPage();
					refreshPage();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (removeIndex != -1) {
					cloudVaultSetup.deleteCloud(cloudIdList.get(removeIndex));
					saveMetadata(cloudVaultSetup);
					JOptionPane.showMessageDialog(null, "Removed");
					clearPage();
					refreshPage();
				}
			}
		});
		setVisible(true);

	}


	@Override
	public void actionPerformed(ActionEvent e) {
		removeIndex = Integer.parseInt(e.getActionCommand());
	}

	public void saveMetadata(Setup cloudVaultSetup) {
		// save the meta data
		
		try {
			OutputStream outputStream = new FileOutputStream(cloudVaultSetup.cloudConfigFile);
			cloudVaultSetup.cloudConfigProps.store(outputStream, "host setttings");
			outputStream.close();
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "<html>Error saving cloud configuration: "
					+ "config file not found.<br>"
					+ "</html>");
			e.printStackTrace();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(null, 
					"Error saving properties file: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE);		
		}
		
//		try {
//			FileOutputStream fileOut = new FileOutputStream(
//					cloudVaultSetup.cloudMetadataPath);
//			ObjectOutputStream out = new ObjectOutputStream(fileOut);
//			out.writeObject(cloudVaultSetup.cloudMetaData);
//			out.close();
//			fileOut.close();
////			Files.createDirectories(Paths.get(cloudVaultSetup.vaultPath));
//		} catch (IOException i) {
//			i.printStackTrace();
//		}	
	}

	@SuppressWarnings("unchecked")
	public void refreshPage() {	
//		try {
//			FileInputStream fileIn = new FileInputStream(cloudMetadataPath);
//			ObjectInputStream in = new ObjectInputStream(fileIn);
//			cloudMetaData = (ArrayList<Pair<String, String>>) in
//					.readObject();
//			in.close();
//			fileIn.close();
//		} catch (IOException x) {
////			logger.error("IOException while adding cloud " + x);
//		} catch (ClassNotFoundException cfe) {
//			cfe.printStackTrace();
//		}
		
		Properties defaultProps = new Properties();
		// sets default properties
		defaultProps.setProperty("Number of clouds", "0");	
		cloudConfigProps = new Properties(defaultProps);
		try {
			if(Files.exists(Paths.get(cloudConfigFile.toString()))) {
				InputStream inputStream = new FileInputStream(cloudConfigFile);
				cloudConfigProps.load(inputStream);
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud config file not found.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud settings could not be read.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		
		cloudcounter = Integer.parseInt(cloudConfigProps.getProperty("Number of Clouds"));
		y = 2;
		labelCloud.clear();
		cloudValue.clear();
		cloudIdList.clear();
		for (int i = 0; i < cloudcounter; i++) {
			String cloudID = "cloud" + i;
			if(cloudConfigProps.getProperty(cloudID + ".status").equals("1"))
			{
				int index = labelCloud.size();
				radio.add(new JRadioButton(""));
				labelCloud.add(new JLabel("Cloud " + Integer.toString(index + 1)
						+ " : "));
				cloudValue.add(new JLabel(cloudConfigProps.getProperty(cloudID + ".code")));			
				cloudIdList.add(cloudID);
			}
		}

		if (labelCloud.size() == 0) {
			constraints.gridx = 0;
			constraints.gridy = 2;
			add(noCloudLabel, constraints);
		} 
		else {
			ButtonGroup proxyButtons = new ButtonGroup();

			for (int i = 0; i < labelCloud.size(); i++) {
				constraints.gridy = y++;
				constraints.gridx = 0;
				radio.get(i).setActionCommand(Integer.toString(i+1));
				proxyButtons.add(radio.get(i));
				radio.get(i).addActionListener(this);
				add(radio.get(i), constraints);

				constraints.gridx = 1;
				add(labelCloud.get(i), constraints);

				constraints.gridx = 2;
				add(cloudValue.get(i), constraints);

			}
		}
			
		constraints.gridy = y;
		constraints.gridx = 1;
		constraints.anchor = GridBagConstraints.CENTER;
		add(addButton, constraints);

		constraints.gridy = y;
		constraints.gridx = 2;
		constraints.anchor = GridBagConstraints.CENTER;
		add(removeButton, constraints);

	}
	
	public void clearPage()
	{
		remove(noCloudLabel);
		remove(addButton);
		remove(removeButton);
		for (int i = 0; i < labelCloud.size(); i++) {
			remove(radio.get(i));
			remove(labelCloud.get(i));
			remove(cloudValue.get(i));
		}
	}
	

}