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

    private TextView otpTextView;
    private EditText otpValidityEditText;
    private EditText otpLengthEditText;
    private Button startStopButton;
    private Generator countdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        otpTextView = findViewById(R.id.otpTextView);
        otpValidityEditText = findViewById(R.id.otpValidityEditText);
        otpLengthEditText = findViewById(R.id.otpLengthEditText);
        startStopButton = findViewById(R.id.startStopButton);

        startStopButton.setOnClickListener(v -> {
            if (startStopButton.getText().toString().equals("Start")) {
                int interval = Integer.parseInt(otpValidityEditText.getText().toString());
                countdown = new Generator(interval);
                countdown.start();
                startStopButton.setText("Stop");
            } else {
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
        private byte[] key = "1234567890123456".getBytes(); // 128 bit key
    private int uid = 0; // User ID
    private Cipher aes;

    public Generator(int interval) {
        this.interval = interval;
        run = true;
        try {
            aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    otpTextView.setText(finalOtp);
                }
            });
            try {
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
        run = false;
    }
}
}