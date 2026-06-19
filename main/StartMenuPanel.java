package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class StartMenuPanel extends JPanel {
    private static final String SERIF = "Serif";

    public StartMenuPanel(Runnable onPlayCampaign, Runnable onPlaySandbox, Runnable onCreateMap, Runnable onSettings,
            int coins) {
        setLayout(new BorderLayout());
        setBackground(new Color(55, 40, 28));
        setPreferredSize(new Dimension(900, 640));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(80, 120, 80, 120));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("rts name idk", SwingConstants.CENTER);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
        titleLabel.setForeground(new Color(235, 218, 180));
        titleLabel.setFont(new Font(SERIF, Font.BOLD, 34));
        JLabel subtitleLabel = new JLabel("rts game lol", SwingConstants.CENTER);
        subtitleLabel.setAlignmentX(CENTER_ALIGNMENT);
        subtitleLabel.setForeground(new Color(210, 192, 152));
        subtitleLabel.setFont(new Font(SERIF, Font.PLAIN, 18));
        JLabel coinsLabel = new JLabel("Coins: " + coins, SwingConstants.CENTER);
        coinsLabel.setAlignmentX(CENTER_ALIGNMENT);
        coinsLabel.setForeground(new Color(236, 210, 116));
        coinsLabel.setFont(new Font(SERIF, Font.BOLD, 20));
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 0, 18));
        buttonPanel.setOpaque(false);
        buttonPanel.setMaximumSize(new Dimension(320, 290));
        JButton playCampaignButton = createMenuButton("Play Campaign", onPlayCampaign);
        JButton playSandboxButton = createMenuButton("Play Sandbox", onPlaySandbox);
        JButton createMapButton = createMenuButton("Create Map", onCreateMap);
        JButton settingsButton = createMenuButton("Settings", onSettings);
        buttonPanel.add(playCampaignButton);
        buttonPanel.add(playSandboxButton);
        buttonPanel.add(createMapButton);
        buttonPanel.add(settingsButton);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(subtitleLabel);
        content.add(Box.createVerticalStrut(20));
        content.add(coinsLabel);
        content.add(Box.createVerticalStrut(28));
        content.add(buttonPanel);
        add(content, BorderLayout.CENTER);
    }

    private JButton createMenuButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setFont(new Font(SERIF, Font.BOLD, 20));
        button.setBackground(new Color(130, 92, 54));
        button.setForeground(new Color(248, 236, 208));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(70, 45, 26), 2),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        button.addActionListener(event -> action.run());
        return button;
    }
}