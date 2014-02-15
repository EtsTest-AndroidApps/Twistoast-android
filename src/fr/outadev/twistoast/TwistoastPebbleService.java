package fr.outadev.twistoast;

import com.getpebble.android.kit.PebbleKit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class TwistoastPebbleService extends Service {
	
	private TwistoastPebbleReceiver receiver;

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("TwistoastPebbleService", "initialized pebble listener");
		
		receiver = new TwistoastPebbleReceiver();
		PebbleKit.registerReceivedDataHandler(this, receiver);

		return START_REDELIVER_INTENT;
	}

}