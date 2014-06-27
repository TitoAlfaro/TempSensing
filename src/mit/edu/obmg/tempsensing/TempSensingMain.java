package mit.edu.obmg.tempsensing;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.TwiMaster;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class TempSensingMain extends IOIOActivity implements OnClickListener{
	private final String TAG = "TempSensingMain";
	private PowerManager.WakeLock wl;
	
	//MultiThreading
	private Thread Vibration1, Vibration2, Vibration3;
	Thread thread1 = new Thread(Vibration1);
	Thread thread2 = new Thread(Vibration2);
	Thread thread3 = new Thread(Vibration3);
	
	//Vibration
	float rate1 = 1000;
	float rate2 = 1000;
	float rate3 = 1000;
	private double valueMultiplier01 = 0, valueMultiplier02 = 0, valueMultiplier03 = 0;
	float temp1, temp2, temp3;
	int[] vibPin = {38, 39, 40};
	int vibPin1 = 38;
	int vibPin2 = 39;
	int vibPin3 = 40;
		
	//Sensor I2C
	private TwiMaster twi;
	double sensortemp;
	

	//UI
	private TextView TempPeriod1;
	private TextView TempFahrenheit1;
	private TextView TempPeriod2;
	private TextView TempFahrenheit2;
	private TextView TempPeriod3;
	private TextView TempFahrenheit3;
	private Button Button01Plus, Button01Minus;
	private Button Button02Plus, Button02Minus;
	private Button Button03Plus, Button03Minus;
	private TextView Vol01, Vol02, Vol03;
	float fahrenheit, celsius;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_temp_sensing_main);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, TAG);
				
		TempPeriod1 = (TextView) findViewById(R.id.tempP1);
		TempFahrenheit1 = (TextView) findViewById(R.id.tempF1);
		
		Button01Plus = (Button) findViewById(R.id.Button01Plus);
		Button01Plus.setOnClickListener(this);
		Button01Minus = (Button) findViewById(R.id.Button01Minus);
		Button01Minus.setOnClickListener(this);
		Vol01 = (TextView) findViewById(R.id.ValueMulti01);
		
		
		TempPeriod2 = (TextView) findViewById(R.id.tempP2);
		TempFahrenheit2 = (TextView) findViewById(R.id.tempF2);

		Button02Plus = (Button) findViewById(R.id.Button02Plus);
		Button02Plus.setOnClickListener(this);
		Button02Minus = (Button) findViewById(R.id.Button02Minus);
		Button02Minus.setOnClickListener(this);
		Vol02 = (TextView) findViewById(R.id.ValueMulti02);
		
		
		TempPeriod3 = (TextView) findViewById(R.id.tempP3);
		TempFahrenheit3 = (TextView) findViewById(R.id.tempF3);

		Button03Plus = (Button) findViewById(R.id.Button03Plus);
		Button03Plus.setOnClickListener(this);
		Button03Minus = (Button) findViewById(R.id.Button03Minus);
		Button03Minus.setOnClickListener(this);
		Vol03 = (TextView) findViewById(R.id.ValueMulti03);
	}

	protected void onStart(){
		super.onStart();
	}
	
	protected void onStop(){
		super.onStop();
	}

	class Looper extends BaseIOIOLooper {
		
		private Vibration[] vibThread_ = new Vibration[3];

		@Override
		protected void setup() throws ConnectionLostException, InterruptedException {
			wl.acquire();
			twi = ioio_.openTwiMaster(0, TwiMaster.Rate.RATE_100KHz, true);
															
				for (int i = 0; i < 3; ++i) {
					vibThread_[i] = new Vibration(ioio_, vibPin[i], i);
					vibThread_[i].start();
				}
		}

		@Override
		public void loop() throws ConnectionLostException {
			ReadSensor(0x34, twi);		//dec 52
			ReadSensor(0x2a, twi);		//dec 42
			ReadSensor(0x5a, twi);		//dec 90
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
				
		}
		
		@Override
		public void disconnected() {
			Log.i(TAG, "IOIO disconnected, killing workers");
			for (int i = 0; i < vibThread_.length; ++i) {
				if (vibThread_[i] != null) {
					vibThread_[i].abort();
				}
			}
			try {
				for (int i = 0; i < vibThread_.length; ++i) {
					if (vibThread_[i] != null) {
						vibThread_[i].join();
					}
				}
				
				Log.i(TAG, "All workers dead");
			} catch (InterruptedException e) {
				Log.w(TAG, "Interrupted. Some workers may linger.");
			}
		
			wl.release();
		}
	}

	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	// temperature sensors
	public void ReadSensor(int address, TwiMaster port) {

		byte[] request = new byte[] { 0x07 };	//Byte address to ask for sensor data
		byte[] tempdata = new byte[2];			//Byte to save sensor data
		double receivedTemp = 0x0000;			//Value after processing sensor data
		double tempFactor = 0.02;				//0.02 degrees per LSB (measurement resolution of the MLX90614)

		try {
			Log.d(TAG, ":| Trying to read");
			port.writeRead(address, false, request,request.length,tempdata,tempdata.length);

			receivedTemp = (double)(((tempdata[1] & 0x007f) << 8)+ tempdata[0]);
			receivedTemp = (receivedTemp * tempFactor)-0.01;

			Log.d(TAG, ":) success reading");
			Log.i(TAG, "ReceivedTemp: "+ receivedTemp);

			handleTemp(address, receivedTemp);

		} catch (ConnectionLostException e) {
			Log.d(TAG, ":( read ConnLost");
			// TODO Auto-generated catch block 
			e.printStackTrace();
		} catch (InterruptedException e) {
			Log.d(TAG, ":( read InterrExcept");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private void handleTemp (double address, double temp) throws InterruptedException{
		Log.d(TAG, ":| Handle TEMP Address: "+address);

		celsius = (float) (temp - 273.15);
		Log.i(TAG, "Address: "+address+" C: "+celsius); 

		fahrenheit = (float) ((celsius*1.8) + 32);
		Log.i(TAG, "Address: "+address+" F: "+fahrenheit); 
		
		switch ((int)address){
		case 90:
			temp1 = celsius;
			
			TempPeriod1.post(new Runnable() {
				public void run() {
					TempPeriod1.setText("Fahrenheit"+ fahrenheit);
					TempFahrenheit1.setText("Celsius: "+ celsius);
					Vol01.setText("Multiplier: "+ String.format("%.2f", valueMultiplier01));
				}
			});
			break;
			
		case 42:
			temp2 = celsius;

			TempPeriod2.post(new Runnable() {
				public void run() {
					TempPeriod2.setText("Fahrenheit"+ fahrenheit);
					TempFahrenheit2.setText("Celsius: "+ celsius);
					Vol02.setText("Multiplier: "+ String.format("%.2f", valueMultiplier02));
				}
			});			
			break;
			
		case 52:
			temp3 = celsius;

			TempPeriod3.post(new Runnable() {
				public void run() {
					TempPeriod3.setText("Faherenheit: "+ fahrenheit);
					TempFahrenheit3.setText("Celsius: "+ celsius);
					Vol03.setText("Multiplier: "+ String.format("%.2f", valueMultiplier03));
				}
			});
			break;
		}
	}
	
	class Vibration extends Thread {

		private IOIO ioio_;
		private boolean run_ = true;
		int vibPin_;
		DigitalOutput out;
		int threadNum_;
		float inTemp_;

		public Vibration(IOIO ioio, int vibPin, int threadNum) throws InterruptedException {
			ioio_ = ioio;
			vibPin_ = vibPin;
			threadNum_ = threadNum;			
		}
		
		

		@Override
		public void run() {
			
			Log.d(TAG, "Thread [" + getName() + "] is running.");
			while (run_) {
				try {
					out = ioio_.openDigitalOutput(vibPin_, false);
					while (true) {

						switch  (threadNum_){
						case 0:
							inTemp_ = temp1;
							break;
							
						case 1:
							inTemp_ = temp2;
							break;
							
						case 2:
							inTemp_ = temp3;
							break;
						}
						
						float rate = map(inTemp_, 
								(float) 10, // minSensor.getValue(),
								(float) 50, // maxSensor.getValue(),
								(float) 1000, 
								(float) 10);

						out.write(true);
						sleep((long) 50);
						out.write(false);
						sleep((int) rate);
						Log.i(TAG, "Vibration [" + getName() + "] Rate: " + rate); 

					}
				} catch (ConnectionLostException e) {
				} catch (Exception e) {
					Log.e(TAG, "Unexpected exception caught", e);
					ioio_.disconnect();
					break;
				} finally {
					try {
						ioio_.waitForDisconnect();
					} catch (InterruptedException e) {
					}
				}
			}
		}
		
		public void abort() {
			run_ = false;
			interrupt();
		}
	}

//	class Vibration2 extends Thread {
//
//		private IOIO ioio_;
//
//		public Vibration2(IOIO ioio) throws InterruptedException {
//			ioio_ = ioio;
//		}
//
//		public void run() {
//			super.run();
//			while (true) {
//				try {
//					// led = ioio_.openDigitalOutput(0, true);
//					out = ioio_.openDigitalOutput(vibPin2, false);
//					while (true) {
//
//						float rate = map(temp2, 
//								(float) 10, // minSensor.getValue(),
//								(float) 50, // maxSensor.getValue(),
//								(float) 1000, 
//								(float) 5);
//
//						// led.write(false);
//						out.write(true);
//						sleep((long) 50);
//						// led.write(true);
//						out.write(false);
//						sleep((long) rate);
//						Log.i(TAG, "Vibration 2 Rate: " + rate); 
//
//					}
//				} catch (ConnectionLostException e) {
//				} catch (Exception e) {
//					Log.e(TAG, "Unexpected exception caught", e);
//					ioio_.disconnect();
//					break;
//				} finally {
//					try {
//						ioio_.waitForDisconnect();
//					} catch (InterruptedException e) {
//					}
//				}
//			}
//		}
//	}
//
//	class Vibration3 extends Thread {
//
//		private IOIO ioio_;
//
//		public Vibration3(IOIO ioio) throws InterruptedException {
//			ioio_ = ioio;
//		}
//
//		public void run() {
//			super.run();
//			while (true) {
//				try {
//					//led = ioio_.openDigitalOutput(0, true);
//					out = ioio_.openDigitalOutput(vibPin3, false);
//					while (true) {
//
//						float rate = map(temp3, 
//								(float) 10, // minSensor.getValue(),
//								(float) 50, // maxSensor.getValue(),
//								(float) 1000, 
//								(float) 5);
//
//						//led.write(false);
//						out.write(true);
//						sleep((long) 50);
//						//led.write(true);
//						out.write(false);
//						sleep((long) rate);
//						Log.i(TAG, "Vibration 3 Rate: " + rate); 
//
//					}
//				} catch (ConnectionLostException e) {
//				} catch (Exception e) {
//					Log.e(TAG, "Unexpected exception caught", e);
//					ioio_.disconnect();
//					break;
//				} finally {
//					try {
//						ioio_.waitForDisconnect();
//					} catch (InterruptedException e) {
//					}
//				}
//			}
//		}
//	}
	
	public void checkAddress(TwiMaster port){
		Log.i(TAG, ":| Checking Address...");
		byte[] request_on = new byte[] { 0x07 };
		byte[] response = new byte[2];
		for(int i=0; i<120; i++){

			try {
				if( port.writeRead(i, false, request_on,request_on.length,response,response.length)){
					Log.i(TAG, ":)  Address "+ i+ " works!");
				}else{
					Log.i(TAG, ":(  Address "+ i+ " doesn't work!");
				}
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				Log.i(TAG, ":(  Address "+ i+ " doesn't work! Connection Lost");
				e.printStackTrace();
			} catch (InterruptedException e) {

				Log.i(TAG, ":(  Address "+ i+ " doesn't work! Interrupted Exception");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void changeAddress(TwiMaster port, double address){
		byte[] eraseExisting = new byte[] {0x2E, 0,0 };
		byte[] response = new byte[2];
		byte[] newAddress = new byte [] { 0x2E, 90, 0};

		try {
			port.writeRead(0, false, eraseExisting,eraseExisting.length,response,response.length);
			Log.d( TAG, "Erase response 1: "+response[0]);
			Log.d( TAG, "Erase response 2: "+response[1]);

			port.writeRead(0, false, newAddress,newAddress.length,response,response.length);
			Log.d( TAG, "Write response 1: "+response[0]);
			Log.d( TAG, "Write response 2: "+response[1]);

		} catch (ConnectionLostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.Button01Plus:
			valueMultiplier01 += 100;
			break;

		case R.id.Button01Minus:
			valueMultiplier01 -= 100;
			break;
			
		case R.id.Button02Plus:
			valueMultiplier02 += 100;
			break;

		case R.id.Button02Minus:
			valueMultiplier02 -= 100;
			break;
			
		case R.id.Button03Plus:
			valueMultiplier03 += 100;
			break;

		case R.id.Button03Minus:
			valueMultiplier03 -= 100;
			break;

		}
	}
	
	float map(float x, float in_min, float in_max, float out_min, float out_max) {
		if (x < in_min)
			return out_min;
		else if (x > in_max)
			return out_max;
		else
			return (x - in_min) * (out_max - out_min) / (in_max - in_min)
					+ out_min;
	}
	
}

	