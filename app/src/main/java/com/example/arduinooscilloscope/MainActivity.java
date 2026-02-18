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
import android.view.View;
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

/**
 * Arduino Oscilloscope - Professional UI
 * প্রফেশনাল Oscilloscope ইন্টারফেস
 */
public class MainActivity extends AppCompatActivity {

    // USB Communication
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbSerialDevice serialPort;
    private UsbDeviceConnection connection;
    
    // UI Components
    private ProfessionalOscilloscopeView oscilloscopeView;
    private ImageButton btnPlayPause;
    private CardView btnDemo, btnArduino, btnAccel;
    private TextView tvSampleRate, tvTimebase, tvFrequency, tvVoltage;
    private CardView btnTriggerAuto, btnTriggerNorm, btnTriggerSingle;
    
    // Data Processing
    private StringBuilder dataBuffer = new StringBuilder();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isStreaming = false;
    private boolean isConnected = false;
    
    // Input Source
    private enum InputSource { DEMO, ARDUINO, ACCEL }
    private InputSource currentSource = InputSource.DEMO;
    
    // Trigger Mode
    private enum TriggerMode { AUTO, NORM, SINGLE }
    private TriggerMode triggerMode = TriggerMode.AUTO;
    
    // USB Permission
    private static final String ACTION_USB_PERMISSION = "com.example.arduinooscilloscope.USB_PERMISSION";
    
    // Demo waveform generator
    private Handler demoHandler = new Handler(Looper.getMainLooper());
    private Runnable demoRunnable;
    private int demoPhase = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupUSB();
        setupListeners();
        startDemoMode(); // Start in demo mode
    }
    
    private void initializeViews() {
        oscilloscopeView = findViewById(R.id.oscilloscopeView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnDemo = findViewById(R.id.btnDemo);
        btnArduino = findViewById(R.id.btnArduino);
        btnAccel = findViewById(R.id.btnAccel);
        
        tvSampleRate = findViewById(R.id.tvSampleRate);
        tvTimebase = findViewById(R.id.tvTimebase);
        tvFrequency = findViewById(R.id.tvFrequency);
        tvVoltage = findViewById(R.id.tvVoltage);
        
        btnTriggerAuto = findViewById(R.id.btnTriggerAuto);
        btnTriggerNorm = findViewById(R.id.btnTriggerNorm);
        btnTriggerSingle = findViewById(R.id.btnTriggerSingle);
        
        // Initial state
        updateSourceSelection(InputSource.DEMO);
        updateTriggerSelection(TriggerMode.AUTO);
        isStreaming = true;
    }
    
    private void setupUSB() {
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);
    }
    
    private void setupListeners() {
        // Play/Pause button
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        
        // Input source buttons
        btnDemo.setOnClickListener(v -> switchToSource(InputSource.DEMO));
        btnArduino.setOnClickListener(v -> switchToSource(InputSource.ARDUINO));
        btnAccel.setOnClickListener(v -> switchToSource(InputSource.ACCEL));
        
        // Trigger mode buttons
        btnTriggerAuto.setOnClickListener(v -> setTriggerMode(TriggerMode.AUTO));
        btnTriggerNorm.setOnClickListener(v -> setTriggerMode(TriggerMode.NORM));
        btnTriggerSingle.setOnClickListener(v -> setTriggerMode(TriggerMode.SINGLE));
    }
    
    private void togglePlayPause() {
        isStreaming = !isStreaming;
        
        if (isStreaming) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            if (currentSource == InputSource.DEMO) {
                startDemoMode();
            } else if (currentSource == InputSource.ARDUINO) {
                sendCommand("S");
            }
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            if (currentSource == InputSource.DEMO) {
                stopDemoMode();
            } else if (currentSource == InputSource.ARDUINO) {
                sendCommand("P");
            }
        }
    }
    
    private void switchToSource(InputSource source) {
        stopDemoMode();
        
        currentSource = source;
        updateSourceSelection(source);
        
        switch (source) {
            case DEMO:
                if (isStreaming) {
                    startDemoMode();
                }
                showToast("ডেমো মোড চালু");
                break;
                
            case ARDUINO:
                if (!isConnected) {
                    connectToArduino();
                } else if (isStreaming) {
                    sendCommand("S");
                }
                showToast("Arduino মোড");
                break;
                
            case ACCEL:
                showToast("Accelerometer মোড (শীঘ্রই আসছে)");
                break;
        }
    }
    
    private void updateSourceSelection(InputSource source) {
        // Reset all
        btnDemo.setCardBackgroundColor(getResources().getColor(android.R.color.transparent));
        btnArduino.setCardBackgroundColor(getResources().getColor(android.R.color.transparent));
        btnAccel.setCardBackgroundColor(getResources().getColor(android.R.color.transparent));
        
        // Highlight selected
        int highlightColor = getResources().getColor(android.R.color.holo_green_dark);
        switch (source) {
            case DEMO:
                btnDemo.setCardBackgroundColor(highlightColor);
                break;
            case ARDUINO:
                btnArduino.setCardBackgroundColor(highlightColor);
                break;
            case ACCEL:
                btnAccel.setCardBackgroundColor(highlightColor);
                break;
        }
    }
    
    private void setTriggerMode(TriggerMode mode) {
        triggerMode = mode;
        updateTriggerSelection(mode);
        oscilloscopeView.setTriggerMode(mode.ordinal());
    }
    
    private void updateTriggerSelection(TriggerMode mode) {
        int normalColor = getResources().getColor(android.R.color.darker_gray);
        int highlightColor = getResources().getColor(android.R.color.holo_blue_dark);
        
        btnTriggerAuto.setCardBackgroundColor(normalColor);
        btnTriggerNorm.setCardBackgroundColor(normalColor);
        btnTriggerSingle.setCardBackgroundColor(normalColor);
        
        switch (mode) {
            case AUTO:
                btnTriggerAuto.setCardBackgroundColor(highlightColor);
                break;
            case NORM:
                btnTriggerNorm.setCardBackgroundColor(highlightColor);
                break;
            case SINGLE:
                btnTriggerSingle.setCardBackgroundColor(highlightColor);
                break;
        }
    }
    
    // ==================== DEMO MODE ====================
    
    private void startDemoMode() {
        demoRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStreaming && currentSource == InputSource.DEMO) {
                    generateDemoWaveform();
                    demoHandler.postDelayed(this, 50); // 20 FPS
                }
            }
        };
        demoHandler.post(demoRunnable);
    }
    
    private void stopDemoMode() {
        if (demoRunnable != null) {
            demoHandler.removeCallbacks(demoRunnable);
        }
    }
    
    private void generateDemoWaveform() {
        float[] waveform = new float[512];
        
        for (int i = 0; i < 512; i++) {
            // Generate sine wave
            double angle = (demoPhase + i) * 2 * Math.PI / 50.0;
            waveform[i] = (float) (2.5 + 2.0 * Math.sin(angle));
        }
        
        demoPhase = (demoPhase + 5) % 512;
        
        oscilloscopeView.updateWaveform(waveform);
        
        // Update measurements
        tvFrequency.setText("1.00 kHz");
        tvVoltage.setText("Vpp: 4.00V");
        tvSampleRate.setText("10 kS/s");
        tvTimebase.setText("50 ms");
    }
    
    // ==================== ARDUINO CONNECTION ====================
    
    private void connectToArduino() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        
        if (usbDevices.isEmpty()) {
            showToast("কোনো USB ডিভাইস খুঁজে পাওয়া যায়নি");
            return;
        }
        
        for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
            device = entry.getValue();
            int vendorId = device.getVendorId();
            
            if (vendorId == 0x2341 || vendorId == 0x1A86 || vendorId == 0x0403) {
                requestUSBPermission();
                return;
            }
        }
        
        showToast("Arduino ডিভাইস খুঁজে পাওয়া যায়নি");
    }
    
    private void requestUSBPermission() {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
            this, 0, new Intent(ACTION_USB_PERMISSION), 
            PendingIntent.FLAG_IMMUTABLE
        );
        usbManager.requestPermission(device, permissionIntent);
    }
    
    private void openSerialPort() {
        connection = usbManager.openDevice(device);
        
        if (connection == null) {
            showToast("ডিভাইস খুলতে ব্যর্থ");
            return;
        }
        
        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
        
        if (serialPort != null) {
            if (serialPort.open()) {
                serialPort.setBaudRate(115200);
                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                
                serialPort.read(mCallback);
                
                isConnected = true;
                showToast("Arduino সংযুক্ত হয়েছে");
                
                if (isStreaming) {
                    sendCommand("S");
                }
            }
        }
    }
    
    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            String receivedData = new String(data, StandardCharsets.UTF_8);
            processReceivedData(receivedData);
        }
    };
    
    private void processReceivedData(String data) {
        dataBuffer.append(data);
        
        String bufferContent = dataBuffer.toString();
        int newlineIndex;
        
        while ((newlineIndex = bufferContent.indexOf('\n')) != -1) {
            String line = bufferContent.substring(0, newlineIndex).trim();
            bufferContent = bufferContent.substring(newlineIndex + 1);
            
            parseLine(line);
        }
        
        dataBuffer = new StringBuilder(bufferContent);
    }
    
    private void parseLine(String line) {
        if (line.startsWith("DATA:")) {
            String dataStr = line.substring(5);
            parseWaveformData(dataStr);
        } else if (line.startsWith("FREQ:")) {
            String freqStr = line.substring(5);
            updateFrequency(freqStr);
        } else if (line.startsWith("VOLT:")) {
            String voltStr = line.substring(5);
            updateVoltage(voltStr);
        }
    }
    
    private void parseWaveformData(String dataStr) {
        try {
            String[] values = dataStr.split(",");
            float[] waveform = new float[values.length];
            
            for (int i = 0; i < values.length; i++) {
                int rawValue = Integer.parseInt(values[i].trim());
                waveform[i] = (rawValue / 1023.0f) * 5.0f;
            }
            
            uiHandler.post(() -> oscilloscopeView.updateWaveform(waveform));
            
            requestMeasurements();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void updateFrequency(String freqStr) {
        try {
            float frequency = Float.parseFloat(freqStr);
            uiHandler.post(() -> {
                if (frequency > 0) {
                    tvFrequency.setText(String.format("%.2f Hz", frequency));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void updateVoltage(String voltStr) {
        try {
            String[] values = voltStr.split(",");
            float pp = Float.parseFloat(values[3]);
            
            uiHandler.post(() -> {
                tvVoltage.setText(String.format("Vpp: %.2fV", pp));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private int measurementCounter = 0;
    private void requestMeasurements() {
        measurementCounter++;
        if (measurementCounter >= 5) {
            sendCommand("F");
            sendCommand("V");
            measurementCounter = 0;
        }
    }
    
    private void sendCommand(String command) {
        if (serialPort != null && isConnected) {
            serialPort.write(command.getBytes());
        }
    }
    
    // USB BroadcastReceiver
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            openSerialPort();
                        }
                    } else {
                        showToast("USB পারমিশন পাওয়া যায়নি");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                closeSerialPort();
                isConnected = false;
            }
        }
    };
    
    private void closeSerialPort() {
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }
    
    private void showToast(String message) {
        uiHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        closeSerialPort();
        stopDemoMode();
    }
}
