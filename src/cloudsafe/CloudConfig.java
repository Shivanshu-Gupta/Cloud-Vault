package cloudsafe;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import cloudsafe.cloud.CloudMeta;


public class CloudConfig extends JPanel implements ActionListener {

	private static final long serialVersionUID = 1L;
	private int removeIndex = -1;
	private ArrayList<JLabel> labelCloud = new ArrayList<JLabel>();
	private ArrayList<JLabel> cloudValue = new ArrayList<JLabel>();
	private ArrayList<JRadioButton> radio = new ArrayList<JRadioButton>();
	private JLabel noCloudLabel = new JLabel("No Clouds Added");
	
	private JButton addButton = new JButton("Add More");
	private JButton removeButton = new JButton("Remove");

	GridBagConstraints constraints = new GridBagConstraints();
	int y = 2;
	
	ArrayList<CloudMeta> cloudMetas = new ArrayList<>();

	public CloudConfig(String configPath, String vaultPath) {
		Setup cloudVaultSetup = new Setup(vaultPath, configPath);
		this.cloudMetas = cloudVaultSetup.cloudMetas;
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
					cloudVaultSetup.saveMetadata();
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
					if(labelCloud.size()>4){
						int choice = JOptionPane.showConfirmDialog(null, "<html>Are you Sure?<br><br>"
								+ "Note: Data uploaded to this cloud will no longer be accessible</html>",
								"",JOptionPane.YES_NO_OPTION,
								JOptionPane.QUESTION_MESSAGE, null);
						if(choice == JOptionPane.YES_OPTION){
							cloudVaultSetup.deleteCloud(removeIndex);
							cloudVaultSetup.saveMetadata();
							
							//TODO : change the file cloud lists as required
							
							JOptionPane.showMessageDialog(null, "Removed : " + removeIndex);
							clearPage();
							refreshPage();
						}
					} else {
						JOptionPane.showMessageDialog(null, "Sorry Cloud Vault needs to atleast 4 cloud services to work properly");
					}
				}
			}
		});
		setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		removeIndex = Integer.parseInt(e.getActionCommand());
	}

	public void refreshPage() {	
		y = 2;
		radio.clear();
		labelCloud.clear();
		cloudValue.clear();
		for (int i = 0; i < cloudMetas.size(); i++) {
			radio.add(new JRadioButton(""));
			labelCloud.add(new JLabel("Cloud " + Integer.toString(i + 1)
					+ " : "));
			cloudValue.add(new JLabel(cloudMetas.get(i).getName()));
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
				radio.get(i).setActionCommand(Integer.toString(i));
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