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
import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class CloudConfig extends JPanel implements ActionListener {
	
	private static final long serialVersionUID = 1L;
	
	private ArrayList<JLabel> labelCloud = new ArrayList<JLabel>();
	private ArrayList<JLabel> cloudValue = new ArrayList<JLabel>();
	
	private JButton addButton = new JButton("Add More");
	private JButton removeButton = new JButton("Remove");
//	private JLabel labelcloud1 = new JLabel("Cloud1: ");
//	private JLabel labelcloud2 = new JLabel("Cloud2: ");
//	private JLabel labelcloud3 = new JLabel("Cloud3: ");
//	private JLabel labelcloud4 = new JLabel("Cloud4: ");
	
	public CloudConfig() {
		
		setLayout(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(10, 10, 5, 10);
		constraints.anchor = GridBagConstraints.WEST;
    
		if(labelCloud.size() == 0)
		{
			constraints.gridx = 0;
	        constraints.gridy = 2;
			add(new JLabel("No Clouds Added"), constraints);
		}
		else
		{
	        ButtonGroup proxyButtons = new ButtonGroup();
	        
			int y = 2;
			for(int i=0; i<labelCloud.size(); i++)
			{
				 constraints.gridy = y++;
				 constraints.gridx = 0;
				 JRadioButton radio = new JRadioButton("");
				 radio.setActionCommand(Integer.toString(i));
				 proxyButtons.add(radio);
				 radio.addActionListener(this);
				 add(radio,constraints);
				 
				 constraints.gridx = 1;
				 add(labelCloud.get(i), constraints);
					
				 constraints.gridx = 2;
				 add(cloudValue.get(i), constraints);

			}
			
			constraints.gridy = y;
			constraints.gridx = 1;
//			constraints.gridwidth = 2;
			constraints.anchor = GridBagConstraints.CENTER;
			add(addButton, constraints);
			
			constraints.gridy = y;
			constraints.gridx = 2;
//			constraints.gridwidth = 2;
			constraints.anchor = GridBagConstraints.CENTER;
			add(removeButton, constraints);
		}

		setVisible(true);
        
	}
	
	public void addEntry(String cloudName)
	{
		int index = labelCloud.size();
		labelCloud.add(new JLabel("Cloud " + Integer.toString(index) + " : "));
		cloudValue.add(new JLabel(cloudName));
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		int index = Integer.parseInt(e.getActionCommand());
//		removeButton.setText("Remove " + cloudValue.get(index).getText());
	}
}