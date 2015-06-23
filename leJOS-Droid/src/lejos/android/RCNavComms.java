package lejos.android;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import lejos.pc.comm.*;

/**
 * Provides  Bluetooth communications services to RCNavitationControl:<br>
 * 1. connect to NXT
 * 2. send commands  using the Command emum
 * 3. receives robot position
 * @author Roger
 */
public class RCNavComms
{
	Handler mUIMessageHandler;
    private String TAG="RCNavComms";
    private Reader reader = new Reader();

  public RCNavComms(Handler uiMessageHandler)
  {
    mUIMessageHandler = uiMessageHandler;
    Log.d(TAG," RCNavComms sendData");
  }

  /**
   * connects to NXT using Bluetooth
   * @param name of NXT
   * @param address  bluetooth address
   */
  public boolean connect(String name, String address)
  {
      Log.d(TAG," connecting to " + name + " " + address);
    connector = new NXTConnector();

    boolean connected = connector.connectTo(name, address, NXTCommFactory.BLUETOOTH);
    System.out.println(" connect result " + connected);

    if (!connected)
    {
      return connected;
    }
    dataIn = connector.getDataIn();
    dataOut = connector.getDataOut();
    if (dataIn == null)
    {
      connected = false;
      return connected;
    }
      if (!reader.isRunning)
      {
          reader.start();
      }
    return connected;
  }
  

  private long lasttime = 0;
    private float[] data = {0,0,0,0};
    private static final int delay = 100;

  /**
   * used by reader
   */
  private DataInputStream dataIn;
  /**
   * used by send()
   */
  private DataOutputStream dataOut;
  private NXTConnector connector;
  public NXTConnector getConnector() {
	return connector;
}

    public void setData(float[] data) {
        this.data = data;
    }

    class Reader extends Thread
    {
        public boolean reading = false;
        int count = 0;
        boolean isRunning = false;
        public void run()
        {
            setName("RCNavComms read thread");
            isRunning = true;
            while (isRunning)
            {
                if (reading)  //reads one message at a time
                {
                    Log.d(TAG,"reading ");
                    int status = 0;
                    int speed = 0;
                    boolean ok = false;
                    try
                    {
                        status = dataIn.readInt();
                        speed = dataIn.readInt();
                        ok = true;
                        Log.d(TAG,"data  " + status + " " + speed);
                    } catch (IOException e)
                    {
                        Log.d(TAG,"connection lost");
                        count++;
                        isRunning = count < 20;// give up
                        ok = false;
                    }
                    if (ok)
                    {
                        sendDataUIThread(status, speed);
                        reading = false;
                    }
                    try
                    {
                        Thread.sleep(50);
                    } catch (InterruptedException ex)
                    {
                        Log.d(RCNavComms.class.getName(), ex.getMessage());
                    }
                } else {
                    //send data and set to null
                    if( data != null) {
                        try {
                            for (float f : data)  // iterate over the   data   array
                            {
                                dataOut.writeFloat(f);
                            }
                            dataOut.flush();
                            data = null;
                        }catch(IOException e) {
                            Log.e(TAG, " send throws exception  ", e);
                        }
                        reader.reading = true;
                    }
                }
            }
        }//while is running


    }

    public void sendDataUIThread(int status, int speed) {
        int[] data= {status, speed};
        Bundle b = new Bundle();
        b.putIntArray(RCNavigationControl.ROBOT_STATUS, data);
        Message message_holder = new Message();
        message_holder.what = RCNavigationControl.ROBOT_STATUS_CODE;
        message_holder.setData(b);
        mUIMessageHandler.sendMessage(message_holder);
    }

    void end(){
        reader.isRunning=false;
    }
}
