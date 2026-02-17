/*
 * Arduino Oscilloscope - Data Acquisition System
 * সব Arduino মডেলের সাথে সামঞ্জস্যপূর্ণ
 * ফিচার: রিয়েল-টাইম ওয়েভফর্ম, ফ্রিকোয়েন্সি মাপা, ভোল্টেজ মাপা
 */

// কনফিগারেশন
#define ANALOG_PIN A0           // ইনপুট সিগন্যাল পিন
#define SAMPLE_RATE 10000       // স্যাম্পলিং রেট (Hz)
#define BUFFER_SIZE 512         // ডাটা বাফার সাইজ
#define VOLTAGE_REF 5.0         // রেফারেন্স ভোল্টেজ

// গ্লোবাল ভেরিয়েবল
uint16_t sampleBuffer[BUFFER_SIZE];
uint16_t bufferIndex = 0;
unsigned long lastSampleTime = 0;
unsigned long sampleInterval;
bool streaming = false;

// ফ্রিকোয়েন্সি মাপার জন্য
unsigned long lastCrossingTime = 0;
unsigned long periodSum = 0;
int periodCount = 0;
int lastValue = 0;
float frequency = 0;

void setup() {
  Serial.begin(115200);  // হাই স্পিড কমিউনিকেশন
  
  // ADC অপটিমাইজেশন (Arduino Uno/Nano এর জন্য)
  #if defined(__AVR__)
    // ADC প্রিস্কেলার সেট করা (দ্রুত রিডিং এর জন্য)
    ADCSRA = (ADCSRA & 0xF8) | 0x04; // প্রিস্কেলার 16 (1MHz ADC clock)
  #endif
  
  sampleInterval = 1000000 / SAMPLE_RATE;
  
  pinMode(ANALOG_PIN, INPUT);
  
  // স্টার্টআপ মেসেজ
  Serial.println("ARDUINO_OSC_READY");
  delay(100);
}

void loop() {
  // সিরিয়াল কমান্ড চেক করা
  if (Serial.available() > 0) {
    char command = Serial.read();
    handleCommand(command);
  }
  
  // স্ট্রিমিং মোডে থাকলে ডাটা সংগ্রহ করা
  if (streaming) {
    acquireData();
  }
}

void handleCommand(char cmd) {
  switch (cmd) {
    case 'S': // Start streaming
      streaming = true;
      bufferIndex = 0;
      Serial.println("CMD:START_OK");
      break;
      
    case 'P': // Pause streaming
      streaming = false;
      Serial.println("CMD:PAUSE_OK");
      break;
      
    case 'R': // Reset
      bufferIndex = 0;
      periodCount = 0;
      periodSum = 0;
      frequency = 0;
      Serial.println("CMD:RESET_OK");
      break;
      
    case 'F': // Get frequency
      sendFrequency();
      break;
      
    case 'V': // Get voltage stats
      sendVoltageStats();
      break;
      
    case 'I': // Get info
      sendDeviceInfo();
      break;
  }
}

void acquireData() {
  unsigned long currentTime = micros();
  
  // স্যাম্পলিং টাইম চেক
  if (currentTime - lastSampleTime >= sampleInterval) {
    lastSampleTime = currentTime;
    
    // ADC রিডিং
    int rawValue = analogRead(ANALOG_PIN);
    
    // ফ্রিকোয়েন্সি ক্যালকুলেশন (জিরো ক্রসিং ডিটেকশন)
    detectZeroCrossing(rawValue);
    
    // বাফারে ডাটা সংরক্ষণ
    sampleBuffer[bufferIndex++] = rawValue;
    
    // বাফার পূর্ণ হলে পাঠানো
    if (bufferIndex >= BUFFER_SIZE) {
      sendDataPacket();
      bufferIndex = 0;
    }
  }
}

void detectZeroCrossing(int currentValue) {
  int threshold = 512; // মিড-পয়েন্ট (5V সিস্টেমে 2.5V)
  
  // পজিটিভ জিরো ক্রসিং ডিটেক্ট করা
  if (lastValue < threshold && currentValue >= threshold) {
    unsigned long currentTime = micros();
    
    if (lastCrossingTime > 0) {
      unsigned long period = currentTime - lastCrossingTime;
      
      // বৈধ পিরিয়ড চেক (20Hz - 20kHz রেঞ্জ)
      if (period > 50 && period < 50000) {
        periodSum += period;
        periodCount++;
        
        // প্রতি 10টি পিরিয়ডে গড় বের করা
        if (periodCount >= 10) {
          float avgPeriod = (float)periodSum / periodCount;
          frequency = 1000000.0 / avgPeriod; // Hz তে কনভার্ট
          periodSum = 0;
          periodCount = 0;
        }
      }
    }
    
    lastCrossingTime = currentTime;
  }
  
  lastValue = currentValue;
}

void sendDataPacket() {
  // ডাটা প্যাকেট ফরম্যাট: "DATA:value1,value2,value3,...\n"
  Serial.print("DATA:");
  
  for (int i = 0; i < BUFFER_SIZE; i++) {
    Serial.print(sampleBuffer[i]);
    if (i < BUFFER_SIZE - 1) {
      Serial.print(",");
    }
  }
  
  Serial.println();
}

void sendFrequency() {
  // ফ্রিকোয়েন্সি ফরম্যাট: "FREQ:123.45\n"
  Serial.print("FREQ:");
  Serial.println(frequency, 2);
}

void sendVoltageStats() {
  // ভোল্টেজ স্ট্যাট ক্যালকুলেশন
  uint16_t minVal = 1023;
  uint16_t maxVal = 0;
  uint32_t sum = 0;
  
  for (int i = 0; i < bufferIndex; i++) {
    uint16_t val = sampleBuffer[i];
    if (val < minVal) minVal = val;
    if (val > maxVal) maxVal = val;
    sum += val;
  }
  
  float minVoltage = (minVal / 1023.0) * VOLTAGE_REF;
  float maxVoltage = (maxVal / 1023.0) * VOLTAGE_REF;
  float avgVoltage = ((sum / bufferIndex) / 1023.0) * VOLTAGE_REF;
  float peakToPeak = maxVoltage - minVoltage;
  
  // ফরম্যাট: "VOLT:min,max,avg,pp\n"
  Serial.print("VOLT:");
  Serial.print(minVoltage, 3);
  Serial.print(",");
  Serial.print(maxVoltage, 3);
  Serial.print(",");
  Serial.print(avgVoltage, 3);
  Serial.print(",");
  Serial.println(peakToPeak, 3);
}

void sendDeviceInfo() {
  Serial.print("INFO:");
  #if defined(__AVR_ATmega328P__)
    Serial.print("UNO/NANO");
  #elif defined(__AVR_ATmega2560__)
    Serial.print("MEGA");
  #elif defined(ESP32)
    Serial.print("ESP32");
  #else
    Serial.print("GENERIC");
  #endif
  Serial.print(",");
  Serial.print(SAMPLE_RATE);
  Serial.print(",");
  Serial.println(BUFFER_SIZE);
}
