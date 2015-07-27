package lejos.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class RCNavigationControl extends Activity implements SeekBar.OnSeekBarChangeListener, View.OnTouchListener, View.OnClickListener {
	protected static final String TAG = "RCNavigationControl";
	private static final float MAX_RADIUS = 200f;
	private static final String NXT_24 = "NXT24";
	private static final String NXT_24_Address = "00:16:53:08:E8:7F";
	private static final String TINE_WITTLER = "Tine Wittler";
	private static final String Tine_Wittler_Address = "00:16:53:08:EB:BB";
	public static final String ROBOT_STATUS = "Status";
	public static final int ROBOT_STATUS_CODE = 1323644574;
	private RCNavComms communicator;
	private boolean connected;
	private ConnectThread mConnectThread;
	private SeekBar rotation;
	private SeekBar speed;
	private TextView speedTextView;
	private ImageView knob;
	private FrameLayout gamepad;
	private Button nxt24;
	private Button tineWittler;



	private float knobx;
	private float knoby;
	private TextView mMessage;
	private UIMessageHandler mUIMessageHandler;

	/*
	* 0: topSpeed
	* 1: vx
	* 2: vy
	* 3: turningspeed
	 */
	private float[] data = new float[4];

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if(seekBar == rotation) {
			data[3] = (((float) rotation.getProgress() - (float) rotation.getMax() / 2f)) / ((float) rotation.getMax() / 2f);
			communicator.setData(data);
		} else if(seekBar == speed){
			data[0] = (float) seekBar.getProgress();
			speedTextView.setText(Float.toString(data[0]));
			communicator.setData(data);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		if(seekBar == rotation) {
			rotation.setProgress((int) ((float) rotation.getMax() / 2f));
			data[3] = 0f;
			communicator.setData(data);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		knobx = knob.getLeft() + knob.getWidth()/2;
		knoby = knob.getTop() + knob.getHeight()/2;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
			if (v == gamepad) {
					if (event.getAction() == MotionEvent.ACTION_UP) {
						((FrameLayout.LayoutParams) knob.getLayoutParams()).setMargins((int) knobx - knob.getWidth() / 2, (int) knoby - knob.getHeight() / 2, 0, 0);
						knob.requestLayout();
						data[1] = 0f;
						data[2] = 0f;
						communicator.setData(data);
					}
					if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) {
						((FrameLayout.LayoutParams) knob.getLayoutParams()).gravity = Gravity.LEFT + Gravity.TOP;

						float deltaX = event.getX() - knobx;
						float deltaY = event.getY() - knoby;
						float angle = (float) (Math.atan2(deltaY, deltaX)); // In radians

						float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
						if(distance >= MAX_RADIUS){
							double x = knobx + Math.cos(angle) * MAX_RADIUS;
							double y = knoby + Math.sin(angle) * MAX_RADIUS;
							((FrameLayout.LayoutParams) knob.getLayoutParams()).setMargins((int) (x - knob.getWidth() / 2), (int) (y - knob.getHeight() / 2), 0, 0);
						} else {
							((FrameLayout.LayoutParams) knob.getLayoutParams()).setMargins((int) (event.getX() - knob.getWidth() / 2), (int) (event.getY() - knob.getHeight() / 2), 0, 0);
						}
						angle += Math.PI / 2;
						if (angle > 2 * Math.PI)
							angle -= 2 * Math.PI;
						data[1] = (float) Math.sin(angle);
						data[2] = (float) Math.cos(angle);
						Log.d("speed", Float.toString(data[0]));
						Log.d("vx", Float.toString(data[1]));
						Log.d("vy", Float.toString(data[2]));
						knob.requestLayout();
						communicator.setData(data);
					}
				return true;
			}
			return false;

	}

	@Override
	public void onClick(View v) {
		if(v == tineWittler){
			doConnect(TINE_WITTLER, Tine_Wittler_Address);
		}
		if(v == nxt24){
			doConnect(NXT_24, NXT_24_Address);
		}

	}

	private int[] status = {0,0};
	class UIMessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			//Log.d(TAG, "handleMessage");
			switch (msg.what) {

				case LeJOSDroid.MESSAGE:
					//Log.d(TAG, (String) msg.getData().get(LeJOSDroid.MESSAGE_CONTENT));
					mMessage.setText((String) msg.getData().get(LeJOSDroid.MESSAGE_CONTENT));
					break;
				case ROBOT_STATUS_CODE:
					status = msg.getData().getIntArray(ROBOT_STATUS);
					showRobotStatus(status[0], status[1]);
				default:
					super.handleMessage(msg);
			}

		}
	}

	private void showRobotStatus(int status, int speed) {
		speedTextView.setText("Status: " + status + ", Speed: " + speed);
		return;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
        setContentView(R.layout.rc_nav_control);
		Log.d(TAG, "onCreate 0");
		ViewGroup root =(ViewGroup) findViewById(R.id.root);
		rotation = (SeekBar) findViewById(R.id.vl);

		rotation.setOnSeekBarChangeListener(this);
		gamepad = (FrameLayout) findViewById(R.id.gamepad);
		knob = (ImageView) gamepad.findViewById(R.id.knob);
		speed = (SeekBar) findViewById(R.id.speedSlider);
		speed.setOnSeekBarChangeListener(this);
		speedTextView = (TextView) findViewById(R.id.speedTextView);
		gamepad.setOnTouchListener(this);
		mMessage = (TextView) findViewById(R.id.mMessage);
		nxt24 = (Button) findViewById(R.id.nxt24);
		tineWittler = (Button) findViewById(R.id.winetittler);
		nxt24.setOnClickListener(this);
		tineWittler.setOnClickListener(this);
        mUIMessageHandler = new UIMessageHandler();
	}

	private void doConnect(String robotName, String robotAddress) {
		Log.d(TAG, "doConnect");
		mConnectThread = new ConnectThread(robotName, robotAddress);
		mConnectThread.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		try {
			communicator.getConnector().close();
			communicator.end();
			communicator = null;
		} catch (Exception e) {
			Log.e(TAG, "onPause() error closing NXTComm ", e);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		communicator = new RCNavComms(mUIMessageHandler);
	}

	private class ConnectThread extends Thread {

		private String robotName = NXT_24;
		private String address = NXT_24_Address;

		public ConnectThread(String robotName, String address){
			this.robotName = robotName;
			this.address = address;
		}
		@Override
		public void run() {
			Looper.prepare();
			setName("RCNavigationControl ConnectThread");
			sendMessageToUIThread("Connecting ... ");
			if (!communicator.connect(robotName.toString(), address.toString())) {
				sendMessageToUIThread("Connection Failed");
				connected = false;
			} else {
				sendMessageToUIThread("Connected to " + communicator.getConnector().getNXTInfo().name);

				connected = true;
			}
			Looper.loop();
		}

		public void sendMessageToUIThread(String message) {
			//Log.d(TAG,"sendMessageToUIThread: "+message);
			Bundle b = new Bundle();
			b.putString(LeJOSDroid.MESSAGE_CONTENT, message);
			Message message_holder = new Message();
			message_holder.setData(b);
			message_holder.what = LeJOSDroid.MESSAGE;
			mUIMessageHandler.sendMessage(message_holder);
		}

	}

}
