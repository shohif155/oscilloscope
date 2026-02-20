package com.example.arduinooscilloscope;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.example.arduinooscilloscope.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbSerialDevice serialPort;
    private UsbDeviceConnection connection;

    private ProfessionalOscilloscopeView oscilloscopeView;
    private ImageButton btnPlayPause;
    private CardView btnDemo, btnArduino, btnAccel;
    private CardView btnTriggerAuto, btnTriggerNorm, btnTriggerSingle;
    private TextView tvSampleRate, tvTimebase, tvFrequency, tvVoltage;

    private StringBuilder dataBuffer = new StringBuilder();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Handler demoHandler = new Handler(Looper.getMainLooper());
    private Runnable demoRunnable;
    private int demoPhase = 0;

    private boolean isStreaming = true;
    private boolean isConnected = false;

    private enum InputSource { DEMO, ARDUINO, ACCEL }
    private InputSource currentSource = InputSource.DEMO;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (dev != null) {
                            device = dev;
                            openSerialPort();
                        }
                    } else {
                        showToast("USB পারমিশন অস্বীকার করা হয়েছে");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                showToast("Arduino সংযুক্ত হয়েছে");
                connectToArduino();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                showToast("Arduino বিচ্ছিন্ন হয়েছে");
                closeSerialPort();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        initializeViews();
        registerUSBReceiver();
        setupListeners();
        startDemoMode();
    }

    private void registerUSBReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);
    }

    private void initializeViews() {
        oscilloscopeView = findViewById(R.id.oscilloscopeView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnDemo = findViewById(R.id.btnDemo);
        btnArduino = findViewById(R.id.btnArduino);
        btnAccel = findViewById(R.id.btnAccel);
        btnTriggerAuto = findViewById(R.id.btnTriggerAuto);
        btnTriggerNorm = findViewById(R.id.btnTriggerNorm);
        btnTriggerSingle = findViewById(R.id.btnTriggerSingle);
        tvSampleRate = findViewById(R.id.tvSampleRate);
        tvTimebase = findViewById(R.id.tvTimebase);
        tvFrequency = findViewById(R.id.tvFrequency);
        tvVoltage = findViewById(R.id.tvVoltage);
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> {
            isStreaming = !isStreaming;
            if (isStreaming) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                if (currentSource == InputSource.DEMO) startDemoMode();
                else if (isConnected) sendCommand("S");
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                stopDemoMode();
                if (isConnected) sendCommand("P");
            }
        });

        btnDemo.setOnClickListener(v -> {
            currentSource = InputSource.DEMO;
            closeSerialPort();
            startDemoMode();
            showToast("Demo মোড চালু");
        });

        btnArduino.setOnClickListener(v -> {
            currentSource = InputSource.ARDUINO;
            stopDemoMode();
            connectToArduino();
        });

        if (btnAccel != null) {
            btnAccel.setOnClickListener(v -> showToast("Accelerometer মোড"));
        }

        if (btnTriggerAuto != null) btnTriggerAuto.setOnClickListener(v -> showToast("Auto Trigger"));
        if (btnTriggerNorm != null) btnTriggerNorm.setOnClickListener(v -> showToast("Normal Trigger"));
        if (btnTriggerSingle != null) btnTriggerSingle.setOnClickListener(v -> showToast("Single Trigger"));
    }

    private void startDemoMode() {
        isStreaming = true;
        demoRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStreaming && currentSource == InputSource.DEMO) {
                    float[] waveform = new float[512];
                    for (int i = 0; i < 512; i++) {
                        double angle = (demoPhase + i) * 2 * Math.PI / 50.0;
                        waveform[i] = (float)(2.5 + 2.0 * Math.sin(angle));
                    }
                    demoPhase = (demoPhase + 5) % 512;
                    oscilloscopeView.updateWaveform(waveform);
                    if (tvFrequency != null) tvFrequency.setText("1.00 kHz");
                    if (tvVoltage != null) tvVoltage.setText("Vpp: 4.00V");
                    if (tvSampleRate != null) tvSampleRate.setText("10 kS/s");
                    if (tvTimebase != null) tvTimebase.setText("50 ms");
                    demoHandler.postDelayed(this, 50);
                }
            }
        };
        demoHandler.post(demoRunnable);
    }

    private void stopDemoMode() {
        if (demoRunnable != null) demoHandler.removeCallbacks(demoRunnable);
    }

    private void connectToArduino() {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices.isEmpty()) {
            showToast("কোনো USB ডিভাইস পাওয়া যায়নি");
            return;
        }
        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            UsbDevice dev = entry.getValue();
            int vid = dev.getVendorId();
            if (vid == 0x2341 || vid == 0x1A86 || vid == 0x0403 || vid == 0x10C4) {
                device = dev;
                if (usbManager.hasPermission(device)) {
                    openSerialPort();
                } else {
                    PendingIntent pi = PendingIntent.getBroadcast(
                        this, 0,
                        new Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE
                    );
                    usbManager.requestPermission(device, pi);
                    showToast("USB পারমিশন চাওয়া হচ্ছে...");
                }
                return;
            }
        }
        showToast("Arduino পাওয়া যায়নি (VID মিলছে না)");
    }

    private void openSerialPort() {
        connection = usbManager.openDevice(device);
        if (connection == null) { showToast("ডিভাইস খুলতে ব্যর্থ"); return; }

        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialPort != null && serialPort.open()) {
            serialPort.setBaudRate(115200);
            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            serialPort.read(data -> {
                String received = new String(data, StandardCharsets.UTF_8);
                processData(received);
            });
            isConnected = true;
            showToast("Arduino সংযুক্ত! ✓");
            sendCommand("S");
        } else {
            showToast("Serial port খুলতে ব্যর্থ");
        }
    }

    private void closeSerialPort() {
        if (serialPort != null) { serialPort.close(); serialPort = null; }
        if (connection != null) { connection.close(); connection = null; }
        isConnected = false;
    }

    private void processData(String data) {
        dataBuffer.append(data);
        String buf = dataBuffer.toString();
        int idx;
        while ((idx = buf.indexOf('\n')) != -1) {
            String line = buf.substring(0, idx).trim();
            buf = buf.substring(idx + 1);
            parseLine(line);
        }
        dataBuffer = new StringBuilder(buf);
    }

    private void parseLine(String line) {
        if (line.startsWith("DATA:")) {
            try {
                String[] vals = line.substring(5).split(",");
                float[] wf = new float[vals.length];
                for (int i = 0; i < vals.length; i++)
                    wf[i] = Integer.parseInt(vals[i].trim()) / 1023.0f * 5.0f;
                uiHandler.post(() -> oscilloscopeView.updateWaveform(wf));
            } catch (Exception e) { e.printStackTrace(); }
        } else if (line.startsWith("FREQ:")) {
            String f = line.substring(5);
            uiHandler.post(() -> { if (tvFrequency != null) tvFrequency.setText(f + " Hz"); });
        } else if (line.startsWith("VOLT:")) {
            String v = line.substring(5);
            uiHandler.post(() -> { if (tvVoltage != null) tvVoltage.setText("Vpp: " + v + "V"); });
        }
    }

    private void sendCommand(String cmd) {
        if (serialPort != null && isConnected)
            serialPort.write(cmd.getBytes(StandardCharsets.UTF_8));
    }

    private void showToast(String msg) {
        uiHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDemoMode();
        closeSerialPort();
        try { unregisterReceiver(usbReceiver); } catch (Exception e) { e.printStackTrace(); }
    }
}
