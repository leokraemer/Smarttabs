package lejos.android;

import android.app.Activity;
import android.os.AsyncTask;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import CommandHolders.DriveCommand;
import CommandHolders.ICommandHolder;
import CommandHolders.LiftingArmCommand;
import CommandHolders.ResetLiftingArmCommand;

public class RCNavigationControl extends Activity implements SeekBar.OnSeekBarChangeListener, View.OnTouchListener, View.OnClickListener {
    protected static final String TAG = "RCNavigationControl";
    private static final float MAX_RADIUS = 200f;
    private static final String NXT_24 = "NXT24";
    private static final String NXT_24_Address = "00:16:53:08:E8:7F";
    private static final String TINE_WITTLER = "Tine Wittler";
    private static final String Tine_Wittler_Address = "00:16:53:08:EB:BB";
    public static final String ROBOT_STATUS = "status";
    public static final String ROBOT_SPEED = "speed";
    public static final String MOTOR_1_STALLED = "m1stalled";
    public static final String MOTOR_2_STALLED = "m2stalled";
    public static final String MOTOR_3_STALLED = "m3stalled";
    public static final String MOTOR_4_STALLED = "m4stalled";
    public static final String BATTERY_VOLTAGE = "batt";
    public static final String AUX_BATTERY_VOLTAGE = "auxbatt";
    public static final String LINACT_POSITION = "linactpos";
    public static final int ROBOT_STATUS_CODE = 1323644574;
    public static final String LIFTINGARMRESETTING = "liftingarmresetting";
    private RCNavComms communicator;
    private boolean connected;
    private ConnectThread mConnectThread;
    private SeekBar rotation;
    private SeekBar speed;
    private SeekBar linacc;
    private SeekBar linaccspeedseekbar;

    private TextView speedTextView;
    private ImageView knob;
    private FrameLayout gamepad;
    private Button nxt24;
    private Button tineWittler;


    private ImageView stalled1;
    private ImageView stalled2;
    private ImageView stalled3;
    private ImageView stalled4;
    private ImageView liftingarmresetting;
    private TextView battery;
    private TextView aux_battery;
    private TextView linacctext;


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
    private Button resetLiftingarm;
    public int linaccspeed = 0;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        switch (seekBar.getId()) {
            case R.id.turn:
                data[3] = (((float) progress - (float) rotation.getMax() / 2f)) / ((float) rotation.getMax() / 2f);
                communicator.sendCommand(new DriveCommand().update(data[0], data[1], data[2], data[3]));
                break;
            case R.id.speedSlider:
                data[0] = (float) progress;
                speedTextView.setText(Float.toString(data[0]));
                communicator.sendCommand(new DriveCommand().update(data[0], data[1], data[2], data[3]));
                break;
            case R.id.linaccSlider:
                communicator.sendCommand(new LiftingArmCommand().update((float) progress, linaccspeed));
                break;
            case R.id.linaccspeedslider:
                linaccspeed = progress;
                //communicator.sendCommand(ICommandHolder.Command.LIFTINGARM.ordinal(), (float) progress);
                break;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == rotation) {
            rotation.setProgress((int) ((float) rotation.getMax() / 2f));
            data[3] = 0f;
            communicator.sendCommand(new DriveCommand().update(data[0], data[1], data[2], data[3]));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        knobx = knob.getLeft() + knob.getWidth() / 2;
        knoby = knob.getTop() + knob.getHeight() / 2;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v == gamepad) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                ((FrameLayout.LayoutParams) knob.getLayoutParams()).setMargins((int) knobx - knob.getWidth() / 2, (int) knoby - knob.getHeight() / 2, 0, 0);
                knob.requestLayout();
                data[1] = 0f;
                data[2] = 0f;
                communicator.sendCommand(new DriveCommand().update(data[0], data[1], data[2], data[3]));
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_DOWN) {
                ((FrameLayout.LayoutParams) knob.getLayoutParams()).gravity = Gravity.LEFT + Gravity.TOP;

                float deltaX = event.getX() - knobx;
                float deltaY = event.getY() - knoby;
                float angle = (float) (Math.atan2(deltaY, deltaX)); // In radians

                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                if (distance >= MAX_RADIUS) {
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
                communicator.sendCommand(new DriveCommand().update(data[0], data[1], data[2], data[3]));
            }
            return true;
        }
        return false;

    }

    @Override
    public void onClick(View v) {
        if (v == tineWittler) {
            doConnect(TINE_WITTLER, Tine_Wittler_Address);
        }
        if (v == nxt24) {
            doConnect(NXT_24, NXT_24_Address);
        }
        if (v == resetLiftingarm)
            communicator.sendCommand(new ResetLiftingArmCommand());

    }

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
                    Bundle b = msg.getData();
                    showRobotStatus(b.getInt(RCNavigationControl.ROBOT_STATUS)
                            , b.getFloat(RCNavigationControl.ROBOT_SPEED)
                            , b.getBoolean(RCNavigationControl.MOTOR_1_STALLED)
                            , b.getBoolean(RCNavigationControl.MOTOR_2_STALLED)
                            , b.getBoolean(RCNavigationControl.MOTOR_3_STALLED)
                            , b.getBoolean(RCNavigationControl.MOTOR_4_STALLED)
                            , b.getBoolean(RCNavigationControl.LIFTINGARMRESETTING)
                            , b.getInt(RCNavigationControl.LINACT_POSITION)
                            , b.getFloat(RCNavigationControl.BATTERY_VOLTAGE)
                            , b.getFloat(RCNavigationControl.AUX_BATTERY_VOLTAGE)
                    );
                default:
                    super.handleMessage(msg);
            }

        }
    }

    private void showRobotStatus(int status, float speed, boolean motor1stalled, boolean motor2stalled, boolean motor3stalled, boolean motor4stalled, boolean liftingarmresetting, int linactpos, float battvoltage, float auxbattvoltage) {
        speedTextView.setText("Status: " + status + ", Speed: " + speed);
        linacctext.setText("Linar actuator: " + linactpos);
        if (motor1stalled)
            stalled1.setImageResource(R.drawable.redlight);
        else
            stalled1.setImageResource(R.drawable.greenlight);
        if (motor2stalled)
            stalled2.setImageResource(R.drawable.redlight);
        else
            stalled2.setImageResource(R.drawable.greenlight);
        if (motor3stalled)
            stalled3.setImageResource(R.drawable.redlight);
        else
            stalled3.setImageResource(R.drawable.greenlight);
        if (motor4stalled)
            stalled4.setImageResource(R.drawable.redlight);
        else
            stalled4.setImageResource(R.drawable.greenlight);
        if (liftingarmresetting)
            this.liftingarmresetting.setImageResource(R.drawable.redlight);
        else
            this.liftingarmresetting.setImageResource(R.drawable.greenlight);
        battery.setText("Main Battery: " + battvoltage + "V");
        aux_battery.setText("Auxiliary Battery: " + auxbattvoltage + "V");
        return;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.rc_nav_control);
        Log.d(TAG, "onCreate 0");
        ViewGroup root = (ViewGroup) findViewById(R.id.root);
        rotation = (SeekBar) findViewById(R.id.turn);

        rotation.setOnSeekBarChangeListener(this);
        gamepad = (FrameLayout) findViewById(R.id.gamepad);
        knob = (ImageView) gamepad.findViewById(R.id.knob);
        linacc = (SeekBar) findViewById(R.id.linaccSlider);
        linacc.setOnSeekBarChangeListener(this);
        linaccspeedseekbar = (SeekBar) findViewById(R.id.linaccspeedslider);
        linaccspeedseekbar.setOnSeekBarChangeListener(this);

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

        resetLiftingarm = (Button) findViewById(R.id.resetliftingarm);
        resetLiftingarm.setOnClickListener(this);

        stalled1 = (ImageView) findViewById(R.id.stalled1);
        stalled2 = (ImageView) findViewById(R.id.stalled2);
        stalled3 = (ImageView) findViewById(R.id.stalled3);
        stalled4 = (ImageView) findViewById(R.id.stalled4);
        liftingarmresetting = (ImageView) findViewById(R.id.liftingarmreset);

        linacctext = (TextView) findViewById(R.id.linactext);
        battery = (TextView) findViewById(R.id.batteryVoltage);
        aux_battery = (TextView) findViewById(R.id.auxBatteryVoltage);


        info = (TextView) findViewById(R.id.info);

        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();


        editTextAddress = (EditText) findViewById(R.id.ip);
        editTextAddress.setText(getIpAddress());
        editTextPort = (EditText) findViewById(R.id.port);
        buttonConnect = (Button) findViewById(R.id.connect);

        buttonConnect.setOnClickListener(buttonConnectOnClickListener);
    }

    TextView info;
    String message = "";
    ServerSocket serverSocket;


    private void doConnect(String robotName, String robotAddress) {
        Log.d(TAG, "doConnect");
        mConnectThread = new ConnectThread(robotName, robotAddress);
        mConnectThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
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

        public ConnectThread(String robotName, String address) {
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


    private class SocketServerThread extends Thread {

        static final int SocketServerPORT = 8080;
        int count = 0;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SocketServerPORT);
                RCNavigationControl.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        info.setText("I'm waiting here: "
                                + serverSocket.getLocalPort());
                    }
                });

                while (true) {
                    Socket socket = serverSocket.accept();
                    count++;
                    message += "#" + count + " from " + socket.getInetAddress()
                            + ":" + socket.getPort() + "\n";

                    RCNavigationControl.this.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            msg.setText(message);
                        }
                    });

                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(
                            socket, count);
                    socketServerReplyThread.run();

                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }


    private class SocketServerReplyThread extends Thread {

        private Socket hostThreadSocket;
        int cnt;

        SocketServerReplyThread(Socket socket, int c) {
            hostThreadSocket = socket;
            cnt = c;
        }

        @Override
        public void run() {
            OutputStream outputStream;
            String msgReply = "Hello from Android, you are #" + cnt;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(msgReply);
                printStream.close();

                message += "replayed: " + msgReply + "\n";

                RCNavigationControl.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        msg.setText(message);
                    }
                });

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                message += "Something wrong! " + e.toString() + "\n";
            }

            RCNavigationControl.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    msg.setText(message);
                }
            });
        }

    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "SiteLocalAddress: "
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }

    ////////////////////Client Code


    TextView textResponse;
    EditText editTextAddress, editTextPort;
    Button buttonConnect;


    View.OnClickListener buttonConnectOnClickListener =
            new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    MyClientTask myClientTask = new MyClientTask(
                            editTextAddress.getText().toString(),
                            Integer.parseInt(editTextPort.getText().toString()));
                    myClientTask.execute();
                }
            };

    public class MyClientTask extends AsyncTask<Void, Void, Void> {

        String dstAddress;
        int dstPort;
        String response = "";

        MyClientTask(String addr, int port) {
            dstAddress = addr;
            dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg0) {

            Socket socket = null;

            try {
                socket = new Socket(dstAddress, dstPort);

                ByteArrayOutputStream byteArrayOutputStream =
                        new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];

                int bytesRead;
                InputStream inputStream = socket.getInputStream();

    /*
     * notice:
     * inputStream.read() will block if no data return
     */
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    response += byteArrayOutputStream.toString("UTF-8");
                }

            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "UnknownHostException: " + e.toString();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "IOException: " + e.toString();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            textResponse.setText(response);
            super.onPostExecute(result);
        }

    }

}

