package net.G03.otpgenerator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    // Declare the views and the Generator thread
    private TextView otpTextView;
    private EditText otpValidityEditText;
    private EditText otpLengthEditText;
    private Button startStopButton;
    private Generator countdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the views
        otpTextView = findViewById(R.id.otpTextView);
        otpValidityEditText = findViewById(R.id.otpValidityEditText);
        otpLengthEditText = findViewById(R.id.otpLengthEditText);
        startStopButton = findViewById(R.id.startStopButton);

        // Set an OnClickListener for the start/stop button
        startStopButton.setOnClickListener(v -> {
            // If the button text is "Start", start the OTP generation
            if (startStopButton.getText().toString().equals("Start")) {
                int interval = Integer.parseInt(otpValidityEditText.getText().toString());
                countdown = new Generator(interval);
                countdown.start();
                startStopButton.setText("Stop");
            } else {
                // If the button text is "Stop", stop the OTP generation
                if (countdown != null) {
                    countdown.kill();
                    countdown = null;
                }
                startStopButton.setText("Start");
            }
        });
    }

    class Generator extends Thread {
        private volatile boolean run = true;
        private int interval;
        // hard coded key for demonstration purposes
        private byte[] key = "1234567890123456".getBytes(); // 128 bit key
        private int uid = 0; // User ID
        private Cipher aes;

        public Generator(int interval) {
            this.interval = interval;
            run = true;
            try {
                // Initialize the AES cipher
                // initial copilot suggestion: use ECB mode, which however is insecure!
                // aes = Cipher.getInstance("AES/ECB/PKCS5Padding");

                // secure alternative: use CBC mode
                aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                SecretKeySpec aesKey = new SecretKeySpec(key, "AES");
                aes.init(Cipher.ENCRYPT_MODE, aesKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void run() {
            while (run) {
                String otp = null;
                otp = generateOTP();
                if (otp == null) return;
                String finalOtp = otp;
                // Update the OTP TextView on the UI thread
                runOnUiThread(() -> otpTextView.setText(finalOtp));
                try {
                    // Sleep for the specified interval
                    this.sleep(1000 * interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private String generateOTP() {
            long now = System.currentTimeMillis() / 1000;
            int otpLength = Integer.parseInt(otpLengthEditText.getText().toString());
            ByteBuffer otpData = ByteBuffer.allocate(otpLength + 3);
            otpData.put((byte) uid);
            otpData.putLong(now);
            byte[] otpBytes = Arrays.copyOfRange(otpData.array(), 0, otpLength + 3);
            try {
                // Encrypt the OTP data and convert it to a hex string
                byte[] encrypted = aes.doFinal(otpBytes);
                StringBuilder hexString = new StringBuilder();
                for (byte b : encrypted) {
                    hexString.append(Integer.toHexString(0xFF & b));
                }
                return hexString.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public void kill() {
            // Stop the OTP generation
            run = false;
        }
    }
}