package mit.edu.obmg.tempsensing;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.PwmOutput;
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
import android.widget.SeekBar;
import android.widget.ToggleButton;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class TempSensingMain extends IOIOActivity implements OnClickListener{
	private final String TAG = "TempSensingMain";
	
	//MultiThreading
		private Thread Vibration;
		Thread thread = new Thread(Vibration);
		float rate = 1000;
		DigitalOutput out;
		
	//Sensor I2C
	private TwiMaster twi;
	double sensortemp;
	
	//TempSense
	//final float fahrenheit

	//UI
	private TextView TempCelsius1;
	private TextView TempFahrenheit1;
	private TextView TempCelsius2;
	private TextView TempFahrenheit2;
	private TextView TempCelsius3;
	private TextView TempFahrenheit3;
	private Button Button01Plus, Button01Minus;
	private Button Button02Plus, Button02Minus;
	private Button Button03Plus, Button03Minus;
	private TextView Vol01, Vol02, Vol03;

	//Vibration output
	private PwmOutput mVibrate01, mVibrate02, mVibrate03;
	private int mVibrate_pin01 = 34;
	//private int mVibrate_pin02 = 35;
	private int mVibrate_pin03 = 36;
	private int freq01 = 50;
	private int freq02 = 50;
	private int freq03 = 50;
	private float period1, period2, period3 = 0;
	private double valueMultiplier01 = 0, valueMultiplier02 = 0, valueMultiplier03 = 0;

	/*
	 *  TONES  ==========================================
	 * Start by defining the relationship between 
	 * 
	 *	       note, period, &  frequency. 
	 *	#define  c     3830     261 Hz 
	 *	#define  d     3400     294 Hz 
	 *	#define  e     3038     329 Hz 
	 *	#define  f     2864     349 Hz 
	 *	#define  g     2550     392 Hz 
	 *	#define  a     2272     440 Hz 
	 *	#define  b     2028     493 Hz 
	 *	#define  C     1912     523 Hz 
	 */


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_temp_sensing_main);
		
		//getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		TempCelsius1 = (TextView) findViewById(R.id.tempC1);
		TempFahrenheit1 = (TextView) findViewById(R.id.tempF1);
		
		Button01Plus = (Button) findViewById(R.id.Button01Plus);
		Button01Plus.setOnClickListener(this);
		Button01Minus = (Button) findViewById(R.id.Button01Minus);
		Button01Minus.setOnClickListener(this);
		Vol01 = (TextView) findViewById(R.id.ValueMulti01);
		
		
		TempCelsius2 = (TextView) findViewById(R.id.tempC2);
		TempFahrenheit2 = (TextView) findViewById(R.id.tempF2);

		Button02Plus = (Button) findViewById(R.id.Button02Plus);
		Button02Plus.setOnClickListener(this);
		Button02Minus = (Button) findViewById(R.id.Button02Minus);
		Button02Minus.setOnClickListener(this);
		Vol02 = (TextView) findViewById(R.id.ValueMulti02);
		
		
		TempCelsius3 = (TextView) findViewById(R.id.tempC3);
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
		//mVibrate01.close();
		//mVibrate02.close();
		//mVibrate03.close();
	}

	class Looper extends BaseIOIOLooper {

		@Override
		protected void setup() throws ConnectionLostException {
			twi = ioio_.openTwiMaster(0, TwiMaster.Rate.RATE_100KHz, true);
						
			mVibrate01 = ioio_.openPwmOutput(mVibrate_pin01, freq01);
			//mVibrate02 = ioio_.openPwmOutput(mVibrate_pin02, freq02);
			mVibrate03 = ioio_.openPwmOutput(mVibrate_pin03, freq03);
			//InitSensor(0x00, twi);
			//changeAddress(twi,0x5A);
			//checkAddress(twi);
			
			try {
				Vibration thread_ = new Vibration(ioio_);
				thread_.start();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void loop() throws ConnectionLostException {
			try {				
				ReadSensor(0x34, twi);		//dec 52
				ReadSensor(0x2a, twi);		//dec 42
				ReadSensor(0x5a, twi);		//dec 90
				
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	// temperature sensors
	public void ReadSensor(int address, TwiMaster port) {

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wake Tag");
		wl.acquire();
		
		byte[] request = new byte[] { 0x07 };	//Byte address to ask for sensor data
		byte[] tempdata = new byte[2];			//Byte to save sensor data
		double receivedTemp = 0x0000;			//Value after processing sensor data
		double tempFactor = 0.02;				//0.02 degrees per LSB (measurement resolution of the MLX90614)

		try {
			port.writeRead(address, false, request,request.length,tempdata,tempdata.length);

			receivedTemp = (double)(((tempdata[1] & 0x007f) << 8)+ tempdata[0]);
			receivedTemp = (receivedTemp * tempFactor)-0.01;

			Log.d(TAG, "ReceivedTemp: "+ receivedTemp);

			handleTemp(address, receivedTemp);

		} catch (ConnectionLostException e) {
			// TODO Auto-generated catch block 
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		 wl.release();
	}

	private void handleTemp (double address, double temp){
		//Log.d(TAG, "Address: "+address);

		final float celsius = (float) (temp - 273.15);
		Log.i(TAG, "Address: "+address+" C: "+celsius); 

		final float fahrenheit = (float) ((celsius*1.8) + 32);
		Log.i(TAG, "Address: "+address+" F: "+fahrenheit); 
		
		period1 = (float) //(Math.pow(fahrenheit, valueMultiplier01)+200);
						  	((Math.pow(2, fahrenheit/10))+valueMultiplier01);
		period2 = (float) 	//(Math.pow(fahrenheit, valueMultiplier02));
			  				((Math.pow(2, fahrenheit/10))+valueMultiplier02);
		period3 = (float) 	//(Math.pow(fahrenheit, valueMultiplier03)+50);
			  				((Math.pow(2, fahrenheit/10))+valueMultiplier03);


		switch ((int)address){
		case 90:
			TempCelsius1.post(new Runnable() {
				public void run() {
					TempCelsius1.setText("fahrenheit 1: "+ fahrenheit);
					TempFahrenheit1.setText("Period: "+ String.format("%.2f", period1));
					Vol01.setText("Multiplier: "+ String.format("%.2f", valueMultiplier01));
				}
			});
			try {
				mVibrate01.setPulseWidth(period1);
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		case 42:
			TempCelsius2.post(new Runnable() {
				public void run() {
					TempCelsius2.setText("fahrenheit 2: "+ fahrenheit);
					TempFahrenheit2.setText("Period 2: "+ String.format("%.2f", period2));
					Vol02.setText("Multiplier: "+ String.format("%.2f", valueMultiplier02));
				}
			});
			/*try {
				mVibrate02.setPulseWidth(period2);
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
			break;
		case 52:
			TempCelsius3.post(new Runnable() {
				public void run() {
					TempCelsius3.setText("fahrenheit 3: "+ fahrenheit);
					TempFahrenheit3.setText("Period 3: "+ String.format("%.2f", period3));
					Vol03.setText("Multiplier: "+ String.format("%.2f", valueMultiplier03));
				}
			});
			try {
				mVibrate03.setPulseWidth(period3);
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		}
	}

	class Vibration extends Thread{
    	private DigitalOutput led;
    	
    	private IOIO ioio_;
    	
    	public Vibration(IOIO ioio)throws InterruptedException{
    		ioio_ = ioio;
    	}
    	
    	public void run(){
    		super.run();
			while (true) {
				try {
					led = ioio_.openDigitalOutput(0, true);
					out = ioio_.openDigitalOutput(35, true);
					while (true) {
						rate = period1;
						Log.i (TAG, "Rate= "+ rate);
						
						/*mRateValue.post(new Runnable() {
							public void run() {
								mRateValue.setText("Rate: "+ rate/2);
							}
						});*/
						
						led.write(true);
						out.write(true);
						sleep((long) rate/2);
						led.write(false);
						out.write(false);
						sleep((long) rate/2);
					}
				} catch (ConnectionLostException e) {
				} catch (Exception e) {
					Log.e("HelloIOIOPower", "Unexpected exception caught", e);
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
    }
	
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
				e.printStackTrace();
			} catch (InterruptedException e) {
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
			valueMultiplier01 = valueMultiplier01 + 100;
			break;

		case R.id.Button01Minus:
			valueMultiplier01 = valueMultiplier01 - 100;
			break;
			
		case R.id.Button02Plus:
			valueMultiplier02 = valueMultiplier02 + 100;
			break;

		case R.id.Button02Minus:
			valueMultiplier02 = valueMultiplier02 - 100;
			break;
			
		case R.id.Button03Plus:
			valueMultiplier03 = valueMultiplier03 + 100;
			break;

		case R.id.Button03Minus:
			valueMultiplier03 = valueMultiplier03 - 100;
			break;

		}
	}
}