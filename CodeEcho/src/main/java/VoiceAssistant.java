import com.darkprograms.speech.microphone.Microphone;
import com.darkprograms.speech.recognizer.GSpeechDuplex;
import com.darkprograms.speech.recognizer.GSpeechResponseListener;
import com.darkprograms.speech.recognizer.GoogleResponse;
import net.sourceforge.javaflacencoder.FLACFileWriter;

import javax.swing.*;
import java.awt.*;

public class VoiceAssistant extends JPanel {
    private final App app;
    private Microphone mic;
    private GSpeechDuplex duplex;
    private boolean isListening = false;

    private JButton voiceButton;
    private JLabel statusLabel;

    // Deduplication: store the last command handled
    private String lastCommand = "";

    // Replace with your actual Google Speech API key
    private static final String API_KEY = "Google-API-Key";

    public VoiceAssistant(App app) {
        this.app = app;
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setOpaque(false);

        voiceButton = new JButton("Start Voice Assistant");
        statusLabel = new JLabel("Idle");
        statusLabel.setForeground(new Color(10, 180, 80));

        add(voiceButton);
        add(statusLabel);

        mic = new Microphone(FLACFileWriter.FLAC);
        duplex = new GSpeechDuplex(API_KEY);
        duplex.setLanguage("en");

        voiceButton.addActionListener(e -> {
            if (!isListening) {
                startRecognition();
            } else {
                stopRecognition();
            }
        });
    }

    private void startRecognition() {
        statusLabel.setText("Listening...");
        voiceButton.setText("Stop Voice Assistant");
        isListening = true;
        lastCommand = ""; // Reset deduplication for new session

        duplex.addResponseListener(new GSpeechResponseListener() {
            @Override
            public void onResponse(GoogleResponse gr) {
                String output = gr.getResponse();
                if (output != null && !output.trim().isEmpty()) {
                    statusLabel.setText("Recognized: " + output);
                    handleCommand(output.trim().toLowerCase());
                }
            }
        });

        new Thread(() -> {
            try {
                duplex.recognize(mic.getTargetDataLine(), mic.getAudioFormat());
            } catch (Exception ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }).start();
    }

    private void stopRecognition() {
        mic.close();
        duplex.stopSpeechRecognition();
        statusLabel.setText("Idle");
        voiceButton.setText("🎤 Start Voice Assistant");
        isListening = false;
        lastCommand = ""; // Reset for next session
    }

    private void handleCommand(String command) {
        // Deduplication: only handle if different from last command
        if (command.equals(lastCommand)) {
            return;
        }
        lastCommand = command;

        if (command.contains("open terminal")) {
            SwingUtilities.invokeLater(() -> app.openTerminalButton.doClick());
        } else if (command.contains("delete file")) {
            SwingUtilities.invokeLater(app.projectView::deleteFile);
        } else if (command.contains("save file")) {
            SwingUtilities.invokeLater(app.projectView::saveFile);
        } else if (command.contains("rename file")) {
            SwingUtilities.invokeLater(app.projectView::renameFile);
        } else if (command.contains("new file") || command.contains("create file")) {
            SwingUtilities.invokeLater(() -> app.projectView.createFile(false));
        } else {
            statusLabel.setText("Command not recognized.");
        }
    }
}
