package mit.edu.obmg.tempsensing;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.TwiMaster;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Timestamp;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewStyle.GridStyle;
import com.jjoe64.graphview.LineGraphView;

public class TempSensingMain extends IOIOActivity/* implements OnClickListener */{
	private final String TAG = "TempSensingMain";
	private PowerManager.WakeLock wl;

	// initialize variable data logging
	static BufferedWriter OutputFile_GYR;
	File gpxfile;
	FileWriter gpxwriter;

	// MultiThreading
	private Thread Vibration1;
	Thread thread1 = new Thread(Vibration1);

	// Vibration
	float rate1 = 1000;
	private double valueMultiplier01 = 0;
	float temp1, temp2, temp3;
	int sensorNum_;
	int vibPin = 38;
	DigitalOutput out;

	// Sensor I2C
	private TwiMaster twi;
	double sensortemp;

	// UI
	private TextView _vibRate;
	private TextView tempValue;
	private ToggleButton btnStamper;
	float fahrenheit, celsius;
	private NumberPicker minTemp, maxTemp;
	int minPicker = 0;
	int maxPicker = 50;

	// Graph
	private final Handler mHandler = new Handler();
	private Runnable mTimer2;
	private GraphView graphView;
	private double graph2LastXValue = 5d;
	GraphViewSeries exampleSeries;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_temp_sensing_main);

		_vibRate = (TextView) findViewById(R.id.tempP1);
		tempValue = (TextView) findViewById(R.id.tempF1);
		btnStamper = (ToggleButton) findViewById(R.id.btnTStamp);
		btnStamper.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				 if (isChecked) {
					 try {
							OutputFile_GYR.write("\nStart "
									+ DateFormat.format("hh:mm:ss\t",
											new java.util.Date()).toString());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			        } else {
			        	try {
							OutputFile_GYR.write("\tEnd "
									+ DateFormat.format("hh:mm:ss\n",
											new java.util.Date()).toString());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			        }
				
			}
		});

		/**** DAta Log ****/
		File path = new File(Environment.getExternalStorageDirectory()
				+ "/application/tempSense");
		if (!path.exists()) {
			path.mkdirs();
		}
		path = new File(path.toString() + "/tempSenseLog.txt");
		String final_file = path.toString();

		gpxfile = new File(final_file);

		try {
			gpxwriter = new FileWriter(gpxfile, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		OutputFile_GYR = new BufferedWriter(gpxwriter);

		try {
			OutputFile_GYR.write(DateFormat.format("dd-MM-yyyy hh:mm:ss\n",
							new java.util.Date()).toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/**** DAta Log ****/

		String[] sensorNums = new String[maxPicker + 1];
		for (int i = minPicker; i < sensorNums.length; i++) {
			sensorNums[i] = Integer.toString(i);
		}

		minTemp = (NumberPicker) findViewById(R.id.minTemp);
		minTemp.setMinValue(minPicker);
		minTemp.setMaxValue(maxPicker);
		minTemp.setWrapSelectorWheel(false);
		minTemp.setDisplayedValues(sensorNums);
		minTemp.setValue(minPicker);
		minTemp.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

		maxTemp = (NumberPicker) findViewById(R.id.maxTemp);
		maxTemp.setMinValue(minPicker);
		maxTemp.setMaxValue(maxPicker);
		maxTemp.setWrapSelectorWheel(false);
		maxTemp.setDisplayedValues(sensorNums);
		maxTemp.setValue(maxPicker);
		maxTemp.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

		/**** GRAPH VIEW ****/
		// init example series data
		exampleSeries = new GraphViewSeries(
				new GraphViewData[] { new GraphViewData(0, 0) });

		graphView = new LineGraphView(this // context
				, "TempSensing" // heading
		);
		graphView.addSeries(exampleSeries); // data
		graphView.setViewPort(1, 8);
		graphView.setScalable(true);
		graphView.setScrollable(true);
		graphView.getGraphViewStyle().setGridStyle(GridStyle.VERTICAL);
		graphView.setShowHorizontalLabels(false);
		graphView.setManualYAxisBounds(maxTemp.getValue(), minTemp.getValue());

		LinearLayout layout = (LinearLayout) findViewById(R.id.Graph);
		layout.addView(graphView);
		/**** GRAPH VIEW ****/
	}

	protected void onStart() {
		super.onStart();
		// wl.acquire();
	}

	protected void onStop() {
		super.onStop();

		// close file
		try {
			OutputFile_GYR.write("\nEnd of File "
					+ DateFormat.format("dd-MM-yyyy hh:mm:ss",
							new java.util.Date()).toString());
			OutputFile_GYR.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	class Looper extends BaseIOIOLooper {

		private Vibration vibThread;

		@Override
		protected void setup() throws ConnectionLostException,
				InterruptedException {
			twi = ioio_.openTwiMaster(0, TwiMaster.Rate.RATE_100KHz, true);

			out = ioio_.openDigitalOutput(vibPin, false);

			// checkAddress(twi);

			vibThread = new Vibration(ioio_);
			vibThread.start();
		}

		@Override
		public void loop() throws ConnectionLostException {
			ReadSensor(0x34, twi); // dec 52
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
			}
		}

		@Override
		public void disconnected() {
			Log.i(TAG, "IOIO disconnected, killing workers");
			if (vibThread != null) {
				vibThread.abort();
			}
			try {
				if (vibThread != null) {
					vibThread.join();
				}

				Log.i(TAG, "All workers dead");
			} catch (InterruptedException e) {
				Log.w(TAG, "Interrupted. Some workers may linger.");
			}

		}
	}

	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	// temperature sensors
	public void ReadSensor(int address, TwiMaster port) {

		byte[] request = new byte[] { 0x07 }; // Byte address to ask for sensor
												// data
		byte[] tempdata = new byte[2]; // Byte to save sensor data
		double receivedTemp = 0x0000; // Value after processing sensor data
		double tempFactor = 0.02; // 0.02 degrees per LSB (measurement
									// resolution of the MLX90614)

		try {
			Log.d(TAG, ":| Trying to read " + address);
			port.writeRead(address, false, request, request.length, tempdata,
					tempdata.length);

			receivedTemp = (double) (((tempdata[1] & 0x007f) << 8) + tempdata[0]);
			receivedTemp = (receivedTemp * tempFactor) - 0.01;

			Log.d(TAG, ":) success reading");
			Log.i(TAG, "ReceivedTemp: " + receivedTemp);

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

	private float handleTemp(final double address, double temp)
			throws InterruptedException {
		Log.d(TAG, ":| Handle TEMP Address: " + address);

		celsius = (float) (temp - 273.15);
		Log.i(TAG, "Address: " + address + " C: " + celsius);

		fahrenheit = (float) ((celsius * 1.8) + 32);
		Log.i(TAG, "Address: " + address + " F: " + fahrenheit);

		_vibRate.post(new Runnable() {
			public void run() {
				tempValue.setText("Celsius(3): " + celsius);
			}
		});

		/**** DAta Log ****/
		try {
			OutputFile_GYR.write("\t" + celsius);
		} catch (IOException e) {
			e.printStackTrace();
		}
		/**** DAta Log ****/

		return celsius;
	}

	class Vibration extends Thread {
		private final String TAG = "TempSensingVibThread";
		private DigitalOutput led;

		private IOIO ioio_;
		private boolean run_ = true;
		int vibPin_;
		int threadNum_;
		float inTemp_;

		public Vibration(IOIO ioio) throws InterruptedException {
			ioio_ = ioio;
		}

		@Override
		public void run() {
			Log.d(TAG, "Thread [" + getName() + "] is running.");

			while (run_) {
				try {
					led = ioio_.openDigitalOutput(0, true);
					while (true) {

						inTemp_ = celsius;
						final float rate = map(inTemp_,
								(float) minTemp.getValue(),
								(float) maxTemp.getValue(), (float) 1000,
								(float) 5);

						led.write(true);
						out.write(true);
						sleep((long) 100);
						led.write(false);
						out.write(false);
						sleep((long) rate);

						_vibRate.post(new Runnable() {
							public void run() {
								_vibRate.setText("Rate: " + rate);
							}
						});
					}
				} catch (ConnectionLostException e) {
				} catch (Exception e) {
					Log.e(TAG, "Unexpected exception caught in VibThread", e);
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

	public void checkAddress(TwiMaster port) {
		Log.i(TAG, ":| Checking Address...");
		byte[] request_on = new byte[] { 0x07 };
		byte[] response = new byte[2];
		for (int i = 0; i < 120; i++) {

			try {
				if (port.writeRead(i, false, request_on, request_on.length,
						response, response.length)) {
					Log.i(TAG, ":)  Address " + i + " works!");
				} else {
					Log.i(TAG, ":(  Address " + i + " doesn't work!");
				}
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				Log.i(TAG, ":(  Address " + i
						+ " doesn't work! Connection Lost");
				e.printStackTrace();
			} catch (InterruptedException e) {

				Log.i(TAG, ":(  Address " + i
						+ " doesn't work! Interrupted Exception");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void changeAddress(TwiMaster port, double address) {
		byte[] eraseExisting = new byte[] { 0x2E, 0, 0 };
		byte[] response = new byte[2];
		byte[] newAddress = new byte[] { 0x2E, 90, 0 };

		try {
			port.writeRead(0, false, eraseExisting, eraseExisting.length,
					response, response.length);
			Log.d(TAG, "Erase response 1: " + response[0]);
			Log.d(TAG, "Erase response 2: " + response[1]);

			port.writeRead(0, false, newAddress, newAddress.length, response,
					response.length);
			Log.d(TAG, "Write response 1: " + response[0]);
			Log.d(TAG, "Write response 2: " + response[1]);

		} catch (ConnectionLostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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

	@Override
	protected void onResume() {
		super.onResume();

		/*** Graph ****/
		mTimer2 = new Runnable() {
			@Override
			public void run() {
				graph2LastXValue += 0.1d;
				exampleSeries.appendData(new GraphViewData(graph2LastXValue,
						celsius), true, 80);
				mHandler.postDelayed(this, 200);
				graphView.setManualYAxisBounds(maxTemp.getValue(),
						minTemp.getValue());
			}
		};
		mHandler.postDelayed(mTimer2, 1000);
		/*** Graph ****/

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mTimer2);
	}
}