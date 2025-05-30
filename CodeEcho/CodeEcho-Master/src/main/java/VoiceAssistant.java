import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.*;

public class VoiceAssistant {
    private final App app;
    private final String credentialsResourceName = "codeecho-461104-ab537fd89f1a.json";

    public VoiceAssistant(App app) {
        this.app = app;
    }

    public void startListening() {
        new Thread(() -> {
            try {
                String command = recognizeCommand();
                if (command == null || command.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(app, "No speech detected (microphone or API issue).", "Voice Assistant", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                command = command.trim().toLowerCase();
                if (command.contains("run")) {
                    app.runCurrentFile();
                } else if (command.contains("delete")) {
                    app.projectView.deleteFile();
                } else if (command.contains("open terminal")) {
                    app.openTerminalButton.doClick();
                } else {
                    JOptionPane.showMessageDialog(app, "Unrecognized command: " + command, "Voice Assistant", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(app, "Voice recognition failed: " + e.getMessage(), "Voice Assistant", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private String recognizeCommand() throws Exception {
        // Load credentials from classpath
        InputStream credentialsStream = getClass().getClassLoader().getResourceAsStream(credentialsResourceName);
        if (credentialsStream == null) {
            throw new FileNotFoundException("Credentials file '" + credentialsResourceName + "' not found in classpath resources.");
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();

        // Configure audio format (16kHz, 16bit, mono, signed, little endian)
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IllegalStateException("Microphone not supported with the required audio format.");
        }
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();

        JOptionPane.showMessageDialog(app,
            "Voice Assistant is listening (7 seconds).\nPlease say: run, save, or open terminal.",
            "Voice Assistant", JOptionPane.INFORMATION_MESSAGE);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long startTime = System.currentTimeMillis();

        // Record for 7 seconds to help catch your speech
        while (System.currentTimeMillis() - startTime < 7000) {
            int count = microphone.read(buffer, 0, buffer.length);
            if (count > 0) {
                out.write(buffer, 0, count);
            }
        }
        microphone.stop();
        microphone.close();
        byte[] audioData = out.toByteArray();

        // DEBUG: Save recorded audio to disk for troubleshooting
        try (FileOutputStream fos = new FileOutputStream("debug-mic-output.raw")) {
            fos.write(audioData);
        } catch (IOException ioe) {
            System.err.println("Failed to save debug audio: " + ioe.getMessage());
        }

        // Send audio to Google Speech-to-Text
        try (SpeechClient speechClient = SpeechClient.create(settings)) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setLanguageCode("en-US")
                    .setSampleRateHertz(16000)
                    .build();
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(audioData))
                    .build();
            RecognizeResponse response = speechClient.recognize(config, audio);

            // DEBUG: Print the response to console
            System.out.println("Google Speech API Response: " + response);

            for (SpeechRecognitionResult result : response.getResultsList()) {
                String transcript = result.getAlternatives(0).getTranscript();
                if (transcript != null && !transcript.trim().isEmpty()) {
                    return transcript;
                }
            }
        }
        return null;
    }
}