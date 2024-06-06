package net.G03.otpgenerator;

import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
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
    private Switch encodingSwitch; // Add this line


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the views
        otpTextView = findViewById(R.id.otpTextView);
        otpValidityEditText = findViewById(R.id.otpValidityEditText);
        otpLengthEditText = findViewById(R.id.otpLengthEditText);
        startStopButton = findViewById(R.id.startStopButton);
        encodingSwitch = findViewById(R.id.encodingSwitch); // Add this line

        // Set an OnClickListener for the start/stop button
        startStopButton.setOnClickListener(v -> {
            // If the button text is "Start", start the OTP generation
            if (startStopButton.getText().toString().equals("Start")) {
                int interval;
                int otpLength;
                // Check if the EditText fields are empty
                if (otpValidityEditText.getText().toString().isEmpty()) {
                    interval = 45; // Default value
                } else {
                    interval = Integer.parseInt(otpValidityEditText.getText().toString());
                }
                if (otpLengthEditText.getText().toString().isEmpty()) {
                    otpLength = 6; // Default value
                } else {
                    otpLength = Integer.parseInt(otpLengthEditText.getText().toString());
                }
                countdown = new Generator(interval, otpLength);
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
        private int otpLength; // OTP length

        // hard coded key for demonstration purposes
        private byte[] key = "1234567890123456".getBytes(); // 128 bit key
        private int uid = 0; // User ID
        private Cipher aes;

        public Generator(int interval, int otpLength) {
            this.interval = interval;
            this.otpLength = otpLength; // Set the OTP length

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
            // Create a ByteBuffer to store the OTP data
            ByteBuffer otpData = ByteBuffer.allocate(otpLength + 9);
            // Add the UID and the current time to the OTP data
            otpData.put((byte) uid);
            otpData.putLong(now);
            otpData.put((byte) interval);
            // Add the OTP length to the OTP data
            byte[] otpBytes = Arrays.copyOfRange(otpData.array(), 0, otpLength + 9); // Adjust the range
            try {
                byte[] encrypted = aes.doFinal(otpBytes);
                if (encodingSwitch.isChecked()) {
                    // Use Base64 encoding
                    String base64Otp = Base64.encodeToString(encrypted, Base64.DEFAULT);

                    return uid+"|"+otpLength+"|"+interval+"|"+base64Otp.substring(base64Otp.length() - otpLength-1);
                } else {
                    // Use hexadecimal encoding
                    StringBuilder hexString = new StringBuilder();
                    for (int i = encrypted.length - 1; i >= encrypted.length - otpLength; i--) {
                        hexString.append(Integer.toHexString(0xFF & encrypted[i]));
                    }
                    hexString.reverse();

                    hexString.insert(0, "|");
                    hexString.insert(0, interval);
                    hexString.insert(0, "|");
                    hexString.insert(0, otpLength);
                    hexString.insert(0, "|");
                    hexString.insert(0, uid);


                    // String format: uid|otpLength|interval|otp

//                    for (byte b : encrypted) {
//                        hexString.append(Integer.toHexString(0xFF & b));
//                    }
                    String otpString = hexString.toString();
                    return otpString;
                }

                // TODO: Implement Base58 encoding istead of Base64
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