package cloudsafe;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class CloudConfig extends JPanel implements ActionListener {

	private static final long serialVersionUID = 1L;
	private int removeIndex = -1;
	private ArrayList<JLabel> labelCloud = new ArrayList<JLabel>();
	private ArrayList<JLabel> cloudValue = new ArrayList<JLabel>();
	private ArrayList<JRadioButton> radio = new ArrayList<JRadioButton>();

	private JButton addButton = new JButton("Add More");
	private JButton removeButton = new JButton("Remove");

	GridBagConstraints constraints = new GridBagConstraints();
	int y = 2;

	public CloudConfig(Setup cloudVaultSetup) {

		setLayout(new GridBagLayout());
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(10, 10, 5, 10);
		constraints.anchor = GridBagConstraints.WEST;

		refreshPage(cloudVaultSetup);

		constraints.gridy = y;
		constraints.gridx = 1;
		constraints.anchor = GridBagConstraints.CENTER;
		add(addButton, constraints);
		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				cloudVaultSetup.addCloud();
				saveMetadata(cloudVaultSetup);
				clearPage();
				refreshPage(cloudVaultSetup);
			}
		});

		constraints.gridy = y;
		constraints.gridx = 2;
		constraints.anchor = GridBagConstraints.CENTER;
		add(removeButton, constraints);
		removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (removeIndex != -1) {
					cloudVaultSetup.deleteCloud(removeIndex);
					saveMetadata(cloudVaultSetup);
					clearPage();
					refreshPage(cloudVaultSetup);
				}
			}
		});
		setVisible(true);

	}

	public void addEntry(String cloudName) {
		int index = labelCloud.size();
		labelCloud.add(new JLabel("Cloud " + Integer.toString(index) + " : "));
		cloudValue.add(new JLabel(cloudName));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		removeIndex = Integer.parseInt(e.getActionCommand());
		// removeButton.setText("Remove " + cloudValue.get(index).getText());
	}

	public void saveMetadata(Setup cloudVaultSetup) {
		// save the meta data
		try {
			FileOutputStream fileOut = new FileOutputStream(
					cloudVaultSetup.cloudMetadataPath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(cloudVaultSetup.cloudMetaData);
			out.close();
			fileOut.close();
			Files.createDirectories(Paths.get(cloudVaultSetup.vaultPath));
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

	public void refreshPage(Setup cloudVaultSetup) {
		y = 2;
		labelCloud.clear();
		cloudValue.clear();
		for (int i = 0; i < cloudVaultSetup.cloudMetaData.size(); i++) {
			int index = labelCloud.size();
			radio.add(new JRadioButton(""));
			labelCloud.add(new JLabel("Cloud " + Integer.toString(index)
					+ " : "));
			cloudValue.add(new JLabel(
					cloudVaultSetup.cloudMetaData.get(i).first));
		}

		if (labelCloud.size() == 0) {
			constraints.gridx = 0;
			constraints.gridy = 2;
			add(new JLabel("No Clouds Added"), constraints);
		} else {
			ButtonGroup proxyButtons = new ButtonGroup();

			for (int i = 0; i < labelCloud.size(); i++) {
				constraints.gridy = y++;
				constraints.gridx = 0;
				radio.get(i).setActionCommand(Integer.toString(i) + 1);
				proxyButtons.add(radio.get(i));
				radio.get(i).addActionListener(this);
				add(radio.get(i), constraints);

				constraints.gridx = 1;
				add(labelCloud.get(i), constraints);

				constraints.gridx = 2;
				add(cloudValue.get(i), constraints);

			}
		}
	}
	
	public void clearPage()
	{
		for (int i = 0; i < labelCloud.size(); i++) {
			remove(radio.get(i));
			remove(labelCloud.get(i));
			remove(cloudValue.get(i));
		}
	}
}