package net.G03.otpgenerator;


import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    // Declare the views and the Generator thread
    private TextView otpTextView;
    private EditText otpValidityEditText;
    private EditText otpLengthEditText;
    private Button startStopButton;
    private Generator countdown;
    private Switch encodingSwitch;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        if (keyguardManager.isKeyguardSecure()) {
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent("Authentication required", "Please enter your PIN");
            startActivityForResult(intent, 1);
        } else {
            Toast.makeText(this, "No lock screen security found.", Toast.LENGTH_SHORT).show();
        }

        // Initialize the views
        startStopButton = findViewById(R.id.startStopButton);
        otpTextView = findViewById(R.id.otpTextView);
        otpValidityEditText = findViewById(R.id.otpValidityEditText);
        otpLengthEditText = findViewById(R.id.otpLengthEditText);
        encodingSwitch = findViewById(R.id.encodingSwitch);


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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Authentication successful", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                startStopButton.setVisibility(View.GONE);
            }
        }
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
                // use AES encryption with ECB mode and no padding --> will produce the same result as the "server"
                aes = Cipher.getInstance("AES/ECB/NoPadding");
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

                sendOtpToServer(otp); // send the OTP to the server

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
            ByteBuffer otpData = ByteBuffer.allocate(16);
            // Add the UID and the current time to the OTP data
            otpData.put((byte) uid);        // 1 byte
            otpData.put((byte)otpLength);   // 1 byte
            otpData.put((byte) interval);   // 1 byte
            otpData.putLong(now);           // 8 bytes
            // in sum: 1 + 1 + 1 + 8 = 11 bytes

            // Add padding to the OTP data (at least 5 bytes) --> done automatically by PKCS5Padding
            otpData.put(new byte[5]);       // 5 bytes


            byte[] otpBytes = otpData.array();

            try {
                byte[] encrypted = aes.doFinal(otpBytes);
                if (encodingSwitch.isChecked()) {
                    // Use Base64 encoding
                    String base64Otp = Base64.encodeToString(encrypted, Base64.NO_WRAP);

                    return "0|"+uid+"|"+otpLength+"|"+interval+"|"+base64Otp.substring(base64Otp.length() - otpLength-1);
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
                    hexString.insert(0, "1|");


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

    private void sendOtpToServer(String otp) {
        new Thread(() -> {
            try {
                Socket socket = new Socket("10.0.2.2", 12345); // replace "server_ip" with your server's IP address
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println(otp); // send the OTP to the server

                String response = in.readLine(); // read the server's response

                runOnUiThread(() -> {
                    // assuming you have a TextView named serverResponseTextView to display the server's response
                    // TextView serverResponseTextView = findViewById(R.id.serverResponseTextView);
                    // serverResponseTextView.setText(response);

                    // Display server response in a toast
                    Toast.makeText(this, response, Toast.LENGTH_SHORT).show();
                });

                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

}