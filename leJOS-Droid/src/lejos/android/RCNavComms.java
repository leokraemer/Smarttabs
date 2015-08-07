package lejos.android;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import CommandHolders.GetStausCommand;
import CommandHolders.ICommandHolder;
import CommandHolders.InvalidCommandCode;
import ProtocolWriter.ProtocolReaderWriter;
import ResponseHolders.StatusResponse;
import lejos.pc.comm.*;

/**
 * Provides  Bluetooth communications services to RCNavitationControl:<br>
 * 1. connect to NXT
 * 2. send commands  using the Command emum
 * 3. receives robot position
 *
 * @author Roger
 */
public class RCNavComms {
    Handler mUIMessageHandler;
    private String TAG = "RCNavComms";
    private Communicator communicator = new Communicator();
    private int linaccpower;

    public RCNavComms(Handler uiMessageHandler) {
        mUIMessageHandler = uiMessageHandler;
        Log.d(TAG, " RCNavComms sendData");
    }

     DataInputStream dataIn;
     DataOutputStream dataOut;
    /**
     * connects to NXT using Bluetooth
     *
     * @param name    of NXT
     * @param address bluetooth address
     */
    public boolean connect(String name, String address) {
        Log.d(TAG, " connecting to " + name + " " + address);
        connector = new NXTConnector();

        boolean connected = connector.connectTo(name, address, NXTCommFactory.BLUETOOTH);
        System.out.println(" connect result " + connected);

        if (!connected) {
            return connected;
        }
        dataIn = connector.getDataIn();
        dataOut = connector.getDataOut();
        if (dataIn == null) {
            connected = false;
            return connected;
        }
        if (!communicator.isRunning) {
            communicator.start();
        }
        return connected;
    }


    private float[] data = {0, 0, 0, 0};
    private Float linacc = 0f;
    private ICommandHolder command;

    /**
     * used by send()
     */
    private NXTConnector connector;

    public NXTConnector getConnector() {
        return connector;
    }

    public void sendCommand(ICommandHolder command) {
        synchronized (this) {
           this.command = command;
        }
    }


    class Communicator extends Thread {
        public boolean reading = false;
        int count = 0;
        boolean isRunning = false;
        boolean wait = true;
        long laststatus = System.currentTimeMillis();
        long timeoffset = 100l;

        public void run() {
            setName("RCNavComms read thread");
            isRunning = true;

            while (isRunning) {
                wait = true;
                if (reading)  //reads one message at a time
                {
                    read();
                } else {
                    //send data and set to null
                    synchronized (this) {
                        write();
                    }
                }
                if(wait){
                    try {
                        synchronized (this) {
                            this.wait(50);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if(System.currentTimeMillis() - laststatus > timeoffset){
                    command = new GetStausCommand();
                }
            }
        }//while is running

        private void write() {
            try {
                ProtocolReaderWriter.writeCommandToDataoutAndFlush(command, dataOut);
            } catch (IOException e) {
                e.printStackTrace();
            }
            command = new InvalidCommandCode();
            communicator.reading = true;
            wait = false;
        }

        private void read() {
            Log.d(TAG, "reading ");

            try {
                sendDataUIThread(ProtocolReaderWriter.readResponseFromDataIn(dataIn));
                reading = false;
                wait = false;
                laststatus = System.currentTimeMillis();
            } catch (IOException e) {
                Log.d(TAG, "connection lost");
                count++;
                isRunning = count < 20;// give up
            }
        }
    }

    public void sendDataUIThread(StatusResponse status) {
        Bundle b = new Bundle();
        b.putInt(RCNavigationControl.ROBOT_STATUS, status.getCode().ordinal());
        b.putFloat(RCNavigationControl.ROBOT_SPEED,  status.getSpeed());
        b.putBoolean(RCNavigationControl.MOTOR_1_STALLED,  status.isMotor1stalled());
        b.putBoolean(RCNavigationControl.MOTOR_2_STALLED,   status.isMotor2stalled());
        b.putBoolean(RCNavigationControl.MOTOR_3_STALLED,   status.isMotor3stalled());
        b.putBoolean(RCNavigationControl.MOTOR_4_STALLED,   status.isMotor4stalled());
        b.putBoolean(RCNavigationControl.LIFTINGARMRESETTING,   status.isLiftingarmresetting());
        b.putInt(RCNavigationControl.LINACT_POSITION, status.getLinactTachoCount());
        b.putFloat(RCNavigationControl.BATTERY_VOLTAGE, status.getBatteryVoltage());
        b.putFloat(RCNavigationControl.AUX_BATTERY_VOLTAGE, status.getAuxBatteryVoltage());
        Message message_holder = new Message();
        message_holder.what = RCNavigationControl.ROBOT_STATUS_CODE;
        message_holder.setData(b);
        mUIMessageHandler.sendMessage(message_holder);
    }

    void end() {
        communicator.isRunning = false;
    }
}
