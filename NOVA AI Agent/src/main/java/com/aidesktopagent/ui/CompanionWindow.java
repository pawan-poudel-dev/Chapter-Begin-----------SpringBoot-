package com.aidesktopagent.ui;

import com.aidesktopagent.avatar.AvatarMood;
import com.aidesktopagent.avatar.RobotAvatar;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Always-on-top Jarvis-style companion — original neon robot "Aether"
 * (friendly cartoon robot; not any protected character design).
 *
 * <p>Voice: click the robot or press Space / the Listen button.
 * Type: enter text in the command field.
 * Gesture: click / hold the robot face (camera hand-gesture is not included yet).
 */
public final class CompanionWindow {

    private final JFrame frame;
    private final RobotFacePanel face;
    private final JLabel statusLabel;
    private final JLabel moodLabel;
    private final JTextArea logArea;
    private final JTextField commandField;
    private final JButton listenButton;
    private final RobotAvatar avatar;
    private final AtomicBoolean listening = new AtomicBoolean(false);

    private Consumer<String> onTextCommand = s -> {};
    private Runnable onListenRequest = () -> {};
    private Runnable onStopListen = () -> {};
    private Runnable onClose = () -> {};

    public CompanionWindow(RobotAvatar avatar) {
        this.avatar = Objects.requireNonNull(avatar);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        frame = new JFrame("Aether Buddy · AI Desktop Companion");
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(360, 520);
        frame.setMinimumSize(new Dimension(300, 420));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(new Color(12, 16, 28));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header
        JLabel title = new JLabel("✦ AETHER BUDDY  ·  talk with me", JLabel.CENTER);
        title.setForeground(new Color(0, 230, 255));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        root.add(title, BorderLayout.NORTH);

        // Center: face + status
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setOpaque(false);
        face = new RobotFacePanel();
        face.setPreferredSize(new Dimension(280, 220));
        face.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestListen();
            }
        });
        center.add(face, BorderLayout.CENTER);

        moodLabel = new JLabel("READY", JLabel.CENTER);
        moodLabel.setForeground(new Color(180, 100, 255));
        moodLabel.setFont(moodLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel = new JLabel("Click the robot or press Space to speak", JLabel.CENTER);
        statusLabel.setForeground(new Color(160, 170, 200));
        JPanel statusBox = new JPanel(new BorderLayout());
        statusBox.setOpaque(false);
        statusBox.add(moodLabel, BorderLayout.NORTH);
        statusBox.add(statusLabel, BorderLayout.SOUTH);
        center.add(statusBox, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        // Bottom: log + command + buttons
        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.setOpaque(false);

        logArea = new JTextArea(6, 28);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(20, 24, 40));
        logArea.setForeground(new Color(220, 230, 255));
        logArea.setCaretColor(Color.WHITE);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(80, 60, 140)));
        south.add(scroll, BorderLayout.CENTER);

        commandField = new JTextField();
        commandField.setBackground(new Color(24, 28, 48));
        commandField.setForeground(Color.WHITE);
        commandField.setCaretColor(Color.CYAN);
        commandField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 200, 255)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        commandField.addActionListener(e -> submitText());
        commandField.registerKeyboardAction(e -> requestListen(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_DOWN_MASK),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        controls.setOpaque(false);
        listenButton = new JButton("🎤 Listen");
        listenButton.setFocusable(false);
        listenButton.addActionListener(e -> requestListen());
        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> submitText());
        JButton hideBtn = new JButton("Hide");
        hideBtn.addActionListener(e -> frame.setState(JFrame.ICONIFIED));
        controls.add(listenButton);
        controls.add(sendBtn);
        controls.add(hideBtn);

        JPanel input = new JPanel(new BorderLayout(4, 4));
        input.setOpaque(false);
        input.add(commandField, BorderLayout.CENTER);
        input.add(controls, BorderLayout.SOUTH);
        south.add(input, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose.run();
            }
        });

        // Space = listen when window focused
        frame.getRootPane().registerKeyboardAction(
                e -> requestListen(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        avatar.addListener(mood -> SwingUtilities.invokeLater(() -> {
            face.setMood(mood);
            moodLabel.setText(mood.name());
            moodLabel.setForeground(colorFor(mood));
            face.repaint();
        }));
        face.setMood(avatar.getMood());

        // Soft pulse animation
        new Timer(80, e -> face.tick()).start();
    }

    public void setOnTextCommand(Consumer<String> handler) {
        this.onTextCommand = handler != null ? handler : s -> {};
    }

    public void setOnListenRequest(Runnable handler) {
        this.onListenRequest = handler != null ? handler : () -> {};
    }

    public void setOnStopListen(Runnable handler) {
        this.onStopListen = handler != null ? handler : () -> {};
    }

    public void setOnClose(Runnable handler) {
        this.onClose = handler != null ? handler : () -> {};
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.toFront();
            commandField.requestFocusInWindow();
        });
    }

    public void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void setListening(boolean on) {
        listening.set(on);
        SwingUtilities.invokeLater(() -> {
            listenButton.setText(on ? "⏹ Stop" : "🎤 Listen");
            face.setPulse(on);
            if (on) {
                avatar.setMood(AvatarMood.LISTENING);
                setStatus("Listening… speak now");
            }
        });
    }

    public void setMood(AvatarMood mood) {
        avatar.setMood(mood);
    }

    public boolean isDisplayable() {
        return frame.isDisplayable() || !frame.isVisible();
    }

    public void dispose() {
        frame.dispose();
    }

    private void submitText() {
        String text = commandField.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        commandField.setText("");
        appendLog("> " + text.trim());
        onTextCommand.accept(text.trim());
    }

    private void requestListen() {
        if (listening.get()) {
            onStopListen.run();
            setListening(false);
        } else {
            onListenRequest.run();
        }
    }

    private static Color colorFor(AvatarMood mood) {
        return switch (mood) {
            case IDLE -> new Color(0, 230, 255);
            case LISTENING -> new Color(255, 64, 255);
            case THINKING -> new Color(180, 100, 255);
            case EXECUTING -> new Color(255, 210, 50);
            case SUCCESS -> new Color(80, 255, 120);
            case ERROR -> new Color(255, 90, 90);
            case SLEEPING -> new Color(160, 170, 200);
        };
    }

    /** Painted neon robot face. */
    static final class RobotFacePanel extends JPanel {
        private AvatarMood mood = AvatarMood.IDLE;
        private boolean pulse;
        private float phase;

        RobotFacePanel() {
            setOpaque(false);
            setToolTipText("Click me to talk (gesture: tap)");
        }

        void setMood(AvatarMood m) {
            this.mood = m;
        }

        void setPulse(boolean p) {
            this.pulse = p;
        }

        void tick() {
            phase += 0.12f;
            if (pulse || mood == AvatarMood.LISTENING || mood == AvatarMood.EXECUTING) {
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2 - 5;

            // Original design: soft blue orb buddy with cyan visor — not any copyrighted character
            Color skin = new Color(70, 160, 255);
            Color accent = colorFor(mood);
            float glow = pulse ? 0.55f + 0.45f * (float) Math.sin(phase) : 0.8f;

            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (35 * glow)));
            g2.fillOval(cx - 100, cy - 100, 200, 200);

            // Head (round)
            g2.setColor(skin);
            g2.fillOval(cx - 72, cy - 85, 144, 140);
            // Belly panel (white oval) — original layout
            g2.setColor(new Color(245, 250, 255));
            g2.fillOval(cx - 40, cy - 5, 80, 70);
            // Visor band
            g2.setColor(new Color(20, 30, 50));
            g2.fillRoundRect(cx - 48, cy - 45, 96, 36, 18, 18);
            // Eyes in visor
            g2.setColor(Color.WHITE);
            if (mood == AvatarMood.SLEEPING) {
                g2.setStroke(new java.awt.BasicStroke(3f));
                g2.setColor(new Color(180, 220, 255));
                g2.drawLine(cx - 28, cy - 27, cx - 12, cy - 27);
                g2.drawLine(cx + 12, cy - 27, cx + 28, cy - 27);
            } else {
                int er = mood == AvatarMood.LISTENING ? 12 : 10;
                g2.fillOval(cx - 30, cy - 27 - er / 2, er, er);
                g2.fillOval(cx + 30 - er, cy - 27 - er / 2, er, er);
                g2.setColor(accent);
                g2.fillOval(cx - 26, cy - 28, 5, 5);
                g2.fillOval(cx + 21, cy - 28, 5, 5);
            }
            // Smile
            g2.setColor(new Color(40, 80, 140));
            g2.setStroke(new java.awt.BasicStroke(2.5f));
            if (mood == AvatarMood.SUCCESS) {
                g2.drawArc(cx - 16, cy + 12, 32, 24, 200, 140);
            } else if (mood == AvatarMood.ERROR) {
                g2.drawArc(cx - 14, cy + 22, 28, 16, 20, 140);
            } else {
                g2.drawArc(cx - 14, cy + 14, 28, 18, 200, 140);
            }
            // Side ear disks (original, not cat ears)
            g2.setColor(skin.darker());
            g2.fillOval(cx - 88, cy - 30, 22, 28);
            g2.fillOval(cx + 66, cy - 30, 22, 28);
            // Antenna tip
            g2.setColor(accent);
            g2.setStroke(new java.awt.BasicStroke(3f));
            g2.drawLine(cx, cy - 85, cx, cy - 108);
            g2.fillOval(cx - 7, cy - 122, 14, 14);

            g2.setColor(new Color(200, 220, 255, 200));
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            String tip = "tap me & speak — I will answer out loud";
            int tw = g2.getFontMetrics().stringWidth(tip);
            g2.drawString(tip, Math.max(4, cx - tw / 2), h - 6);
            g2.dispose();
        }
    }
}
