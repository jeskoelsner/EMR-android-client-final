/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.zlwima.emurgency.mqtt;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings.Secure;
import com.google.common.primitives.Ints;
import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.zlwima.emurgency.mqtt.android.config.Base;

/*
 * An example of how to implement an MQTT client in Android, able to receive push notifications from an MQTT message broker server.
 *
 * Dale Lane (dale.lane@gmail.com) 28 Jan 2011
 *
 * Modified into generic library and improved; Dirk Moors 6 Nov 2012
 */
public class MqttService extends Service implements MqttCallback {

		/**
		 * *********************************************************************
		 */
		/*
		 * CONSTANTS
		 */
		/**
		 * *********************************************************************
		 */
		// something unique to identify your app - used for stuff like accessing
		//   application preferences
		public static final String APP_ID = "org.zlwima.emurgency.mqtt";

		// constants used to notify the Activity UI of received messages
		public static final String MQTT_MSG_RECEIVED_INTENT = "MSGRECVD";
		public static final String MQTT_MSG_RECEIVED_TOPIC = "MSGRECVD_TOPIC";
		public static final String MQTT_MSG_RECEIVED_MSG = "MSGRECVD_MSG";

		// constants used to handle topic subscriptions
		public static String MQTT_SUBSCRIBE_TOPIC_INTENT = "SUBSCR";
		public static String MQTT_SUBSCRIBE_TOPIC = "SUBSCR_TOPIC";

		// constants used to handle topic unsubscriptions
		public static String MQTT_UNSUBSCRIBE_TOPIC_INTENT = "UNSUBSCR";
		public static String MQTT_UNSUBSCRIBE_TOPIC = "UNSUBSCR_TOPIC";

		// constants used to notify the Service of messages to send
		public static final String MQTT_PUBLISH_MSG_INTENT = "SENDMSG";
		public static final String MQTT_PUBLISH_MSG_TOPIC = "SENDMSG_TOPIC";
		public static final String MQTT_PUBLISH_MSG = "SENDMSG_MSG";

		// constants used to tell the Activity UI the connection status
		public static final String MQTT_STATUS_INTENT = "STATUS";
		public static final String MQTT_STATUS_CODE = "STATUS_CODE";
		public static final String MQTT_STATUS_MSG = "STATUS_MSG";

		// constant used internally to schedule the next ping event
		public static final String MQTT_PING_ACTION = "PING";

		// constants used by status bar notifications
		public static final int MQTT_NOTIFICATION_ONGOING = 1;
		public static final int MQTT_NOTIFICATION_UPDATE = 2;

		public void deliveryComplete(IMqttDeliveryToken imdt) {
				//DO NOTHING... but we can echo here though..
		}

		// constants used to define MQTT connection status
		public enum ConnectionStatus {

				INITIAL, // initial status
				CONNECTING, // attempting to connect
				CONNECTED, // connected
				NOTCONNECTED_WAITINGFORINTERNET, // can't connect because the phone
				//     does not have Internet access
				NOTCONNECTED_USERDISCONNECT, // user has explicitly requested
				//     disconnection
				NOTCONNECTED_DATADISABLED, // can't connect because the user
				//     has disabled data access
				NOTCONNECTED_UNKNOWNREASON          // failed to connect for some reason
		}

		// MQTT constants
		public static final int MAX_MQTT_CLIENTID_LENGTH = 22;

		/**
		 * *********************************************************************
		 */
		/*
		 * VARIABLES used to maintain state
		 */
		/**
		 * *********************************************************************
		 */
		// status of MQTT client connection
		private ConnectionStatus connectionStatus = ConnectionStatus.INITIAL;
		private Timestamp connectionStatusChangeTime;

		/**
		 * *********************************************************************
		 */
		/*
		 * VARIABLES used to configure MQTT connection
		 */
		/**
		 * *********************************************************************
		 */
		// taken from preferences
		//    host name of the server we're receiving push notifications from
		private String brokerHostName = "tcp://rabbit.livesapp.io";
		// taken from preferences
		//    topic we want to receive messages about
		//    can include wildcards - e.g.  '#' matches anything
		private List<String> topics = new ArrayList<String>();
		private List<Integer> qoss = new ArrayList<Integer>();

		// defaults - this sample uses very basic defaults for it's interactions
		//   with message brokers
		private int brokerPortNumber = 1883;
		private MqttClientPersistence usePersistence = new MemoryPersistence();
		private boolean cleanStart = false;
		private String username = "bucksbunny_6_emurgency.io";
		private char[] password = "jackrabbit_9_emurgency.io".toCharArray();

		//  how often should the app ping the server to keep the connection alive?
		//
		//   too frequently - and you waste battery life
		//   too infrequently - and you wont notice if you lose your connection
		//                       until the next unsuccessfull attempt to ping
		//
		//   it's a trade-off between how time-sensitive the data is that your
		//      app is handling, vs the acceptable impact on battery life
		//
		//   it is perhaps also worth bearing in mind the network's support for
		//     long running, idle connections. Ideally, to keep a connection open
		//     you want to use a keep alive value that is less than the period of
		//     time after which a network operator will kill an idle connection
		private short keepAliveSeconds = 50;
		//TODO set rabbitmq to 5x longer KeepAlive time

		// This is how the Android client app will identify itself to the
		//  message broker.
		// It has to be unique to the broker - two clients are not permitted to
		//  connect to the same broker using the same client ID.
		private String mqttClientId = null;

		/**
		 * *********************************************************************
		 */
		/*
		 * VARIABLES - other local variables
		 */
		/**
		 * *********************************************************************
		 */
		// connection to the message broker
		private MqttClient mqttClient = null;

		// receiver that notifies the Service when the phone gets data connection
		private NetworkConnectionIntentReceiver netConnReceiver;

		// receiver that wakes the Service up when it's time to ping the server
		private PingSender pingSender;

		private ExecutorService executor;

		/**
		 * *********************************************************************
		 */
		/*
		 * METHODS - core Service lifecycle methods
		 */
		/**
		 * *********************************************************************
		 */
		// see http://developer.android.com/guide/topics/fundamentals.html#lcycles
		@Override
		public void onCreate() {
				super.onCreate();

				// reset status variable to initial state
				changeStatus(ConnectionStatus.INITIAL);

				// create a binder that will let the Activity UI send
				//   commands to the Service
				mBinder = new LocalBinder<MqttService>(this);

				executor = Executors.newFixedThreadPool(2);
		}

		@Override
		public void onStart(final Intent intent, final int startId) {
				// This is the old onStart method that will be called on the pre-2.0
				// platform.  On 2.0 or later we override onStartCommand() so this
				// method will not be called.

				doStart(intent, startId);
		}

		@Override
		public int onStartCommand(final Intent intent, int flags, final int startId) {
				doStart(intent, startId);

				// return START_NOT_STICKY - we want this Service to be left running
				//  unless explicitly stopped, and it's process is killed, we want it to
				//  be restarted
				return START_STICKY;
		}

		private void doStart(final Intent intent, final int startId) {
				initMqttClient();

				executor.submit(new Runnable() {
						@Override
						public void run() {
								try {
										handleStart(intent, startId);
								} catch (Exception ex) {
										Thread t = Thread.currentThread();
										t.getUncaughtExceptionHandler().uncaughtException(t, ex);
								}
						}

				});
		}

		synchronized void handleStart(Intent intent, int startId) {
				// before we start - check for a couple of reasons why we should stop

				if (mqttClient == null) {
						// we were unable to define the MQTT client connection, so we stop
						//  immediately - there is nothing that we can do
						stopSelf();
						return;
				}

				if (connectionStatus == ConnectionStatus.NOTCONNECTED_USERDISCONNECT) {
						// When calling startService in multiple activities, onStartCommand()
						// is called when activies are switched. Thus the service would connect
						// automatically even though the user might have requested the disconnect.
						return;
				}

				if (!isBackgroundDataEnabled()) // respect the user's request not to use data!
				{
						// user has disabled background data
						changeStatus(ConnectionStatus.NOTCONNECTED_DATADISABLED);

						// update the app to show that the connection has been disabled
						broadcastServiceStatus("Not connected - background data disabled @ " + getConnectionChangeTimestamp());

						// we have a listener running that will notify us when this preference changes
						return;
				}

				// if the Service was already running and we're already connected - we
				//   don't need to do anything
				if (!isConnected()) {
						// set the status to show we're trying to connect
						changeStatus(ConnectionStatus.CONNECTING);

						// before we attempt to connect - we check if the phone has a
						//  working data connection
						if (isOnline()) {
								// we think we have an Internet connection, so try to connect
								//  to the message broker
								if (connectToBroker()) {
										subscribeToTopics();
								}
						} else {
								// we can't do anything now because we don't have a working
								//  data connection
								changeStatus(ConnectionStatus.NOTCONNECTED_WAITINGFORINTERNET);

								// inform the app that we are not connected
								broadcastServiceStatus("Waiting for network connection @ " + getConnectionChangeTimestamp());
						}
				}

				// changes to the phone's network - such as bouncing between WiFi
				//  and mobile data networks - can break the MQTT connection
				// the MQTT connectionLost can be a bit slow to notice, so we use
				//  Android's inbuilt notification system to be informed of
				//  network changes - so we can reconnect immediately, without
				//  haing to wait for the MQTT timeout
				if (netConnReceiver == null) {
						netConnReceiver = new NetworkConnectionIntentReceiver();
						registerReceiver(netConnReceiver,
								new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
				}

				// creates the intents that are used to wake up the phone when it is
				//  time to ping the server
				if (pingSender == null) {
						pingSender = new PingSender();
						registerReceiver(pingSender, new IntentFilter(MQTT_PING_ACTION));
				}

				if (!handleStartAction(intent)) {
						// the Activity UI has started the MQTT service - this may be starting
						//  the Service new for the first time, or after the Service has been
						//  running for some time (multiple calls to startService don't start
						//  multiple Services, but it does call this method multiple times)
						// if we have been running already, we re-send any stored data
						rebroadcastStatus();
				}
		}

		private boolean handleStartAction(Intent intent) {
				String action = intent.getAction();

				if (action == null) {
						return false;
				}

				if (action.equalsIgnoreCase(MQTT_PUBLISH_MSG_INTENT)) {
						handlePublishMessageIntent(intent);
				}

				if (action.equalsIgnoreCase(MQTT_SUBSCRIBE_TOPIC_INTENT)) {
						handleSubscribeTopicIntent(intent);
				}

				if (action.equalsIgnoreCase(MQTT_SUBSCRIBE_TOPIC_INTENT)) {
						handleUnsubscribeTopicIntent(intent);
				}

				return true;
		}

		@Override
		public void onDestroy() {
				// disconnect immediately
				disconnectFromBroker();

				// inform the app that the app has successfully disconnected
				broadcastServiceStatus("Disconnected @ " + getConnectionChangeTimestamp());

				if (mBinder != null) {
						mBinder.close();
						mBinder = null;
				}

				super.onDestroy();
		}

		/**
		 * *********************************************************************
		 */
		/*
		 * METHODS - broadcasts and notifications
		 */
		/**
		 * *********************************************************************
		 */
		// methods used to notify the Activity UI of something that has happened
		//  so that it can be updated to reflect status and the data received
		//  from the server
		private void broadcastServiceStatus(String statusDescription) {
				// inform the app (for times when the Activity UI is running /
				//   active) of the current MQTT connection status so that it
				//   can update the UI accordingly

				Intent broadcastIntent = new Intent();
				broadcastIntent.setAction(MQTT_STATUS_INTENT);
				broadcastIntent.putExtra(MQTT_STATUS_CODE, connectionStatus.ordinal());
				broadcastIntent.putExtra(MQTT_STATUS_MSG, statusDescription);
				sendBroadcast(broadcastIntent);
		}

		private void broadcastReceivedMessage(String topic, byte[] message) {
				// pass a message received from the MQTT server on to the Activity UI
				//   (for times when it is running / active) so that it can be displayed
				//   in the app GUI

				Intent broadcastIntent = new Intent();
				broadcastIntent.setAction(MQTT_MSG_RECEIVED_INTENT);
				broadcastIntent.putExtra(MQTT_MSG_RECEIVED_TOPIC, topic);
				broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG, message);
				sendBroadcast(broadcastIntent);
		}

		// methods used to notify the user of what has happened for times when
		//  the app Activity UI isn't running
		private void notifyUser(String alert, String title, String body) {
				/*
				 * NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE); Notification notification = new
				 * Notification(R.drawable.ic_launcher, alert, System.currentTimeMillis()); notification.defaults |= Notification.DEFAULT_LIGHTS;
				 * notification.defaults |= Notification.DEFAULT_SOUND; notification.defaults |= Notification.DEFAULT_VIBRATE; notification.flags |=
				 * Notification.FLAG_AUTO_CANCEL; notification.ledARGB = Color.MAGENTA; Intent notificationIntent = new Intent(this,
				 * MQTTNotifier.class); PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
				 * PendingIntent.FLAG_UPDATE_CURRENT); notification.setLatestEventInfo(this, title, body, contentIntent);
				 * nm.notify(MQTT_NOTIFICATION_UPDATE, notification);
				 */
		}

		/**
		 * *********************************************************************
		 */
		/*
		 * METHODS - binding that allows access from the Actitivy
		 */
		/**
		 * *********************************************************************
		 */
		// trying to do local binding while minimizing leaks - code thanks to
		//   Geoff Bruckner - which I found at
		//   http://groups.google.com/group/cw-android/browse_thread/thread/d026cfa71e48039b/c3b41c728fedd0e7?show_docid=c3b41c728fedd0e7
		private LocalBinder<MqttService> mBinder;

		@Override
		public IBinder onBind(Intent intent) {
				return mBinder;
		}

		public class LocalBinder<S> extends Binder {

				private WeakReference<S> mService;

				public LocalBinder(S service) {
						mService = new WeakReference<S>(service);
				}

				public S getService() {
						return mService.get();
				}

				public void close() {
						mService = null;
				}

		}

		//
		// public methods that can be used by Activities that bind to the Service
		//
		public ConnectionStatus getConnectionStatus() {
				return connectionStatus;
		}

		public void rebroadcastStatus() {
				String status = "";

				switch (connectionStatus) {
						case INITIAL:
								status = "Please wait";
								break;
						case CONNECTING:
								status = "Connecting @ " + getConnectionChangeTimestamp();
								break;
						case CONNECTED:
								status = "Connected @ " + getConnectionChangeTimestamp();
								break;
						case NOTCONNECTED_UNKNOWNREASON:
								status = "Not connected - waiting for network connection @ " + getConnectionChangeTimestamp();
								break;
						case NOTCONNECTED_USERDISCONNECT:
								status = "Disconnected @ " + getConnectionChangeTimestamp();
								break;
						case NOTCONNECTED_DATADISABLED:
								status = "Not connected - background data disabled @ " + getConnectionChangeTimestamp();
								break;
						case NOTCONNECTED_WAITINGFORINTERNET:
								status = "Unable to connect @ " + getConnectionChangeTimestamp();
								break;
				}

				// inform the app that the Service has successfully connected
				broadcastServiceStatus(status);
		}

		public void disconnect() {
				disconnectFromBroker();

				// set status
				changeStatus(ConnectionStatus.NOTCONNECTED_USERDISCONNECT);

				// inform the app that the app has successfully disconnected
				broadcastServiceStatus("Disconnected");
		}

		/**
		 * *********************************************************************
		 */
		/*
		 * METHODS - MQTT methods inherited from MQTT classes
		 */
		/**
		 * *********************************************************************
		 */
		/*
		 * callback - method called when we no longer have a connection to the message broker server
		 */
		public void connectionLost(Throwable t) {
				// we protect against the phone switching off while we're doing this
				//  by requesting a wake lock - we request the minimum possible wake
				//  lock - just enough to keep the CPU running until we've finished
				PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
				WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
				wl.acquire();

				//
				// have we lost our data connection?
				//
				if (isOnline() == false) {
						changeStatus(ConnectionStatus.NOTCONNECTED_WAITINGFORINTERNET);

						// inform the app that we are not connected any more
						broadcastServiceStatus("Connection lost - no network connection");

						//
						// inform the user (for times when the Activity UI isn't running)
						//   that we are no longer able to receive messages
						notifyUser("Connection lost - no network connection",
								"MQTT", "Connection lost - no network connection");

						//
						// wait until the phone has a network connection again, when we
						//  the network connection receiver will fire, and attempt another
						//  connection to the broker
				} else {
						//
						// we are still online
						//   the most likely reason for this connectionLost is that we've
						//   switched from wifi to cell, or vice versa
						//   so we try to reconnect immediately
						//

						changeStatus(ConnectionStatus.NOTCONNECTED_UNKNOWNREASON);

						// inform the app that we are not connected any more, and are
						//   attempting to reconnect
						broadcastServiceStatus("Connection lost - reconnecting...");

						// try to reconnect
						reconnectInNewThread();
				}

				// we're finished - if the phone is switched off, it's okay for the CPU
				//  to sleep now
				wl.release();
		}

		/*
		 * callback - called when we receive a message from the server
		 */
		//public void publishArrived(String topic, byte[] payloadbytes, int qos, boolean retained)
		public void messageArrived(String topic, MqttMessage mm) throws Exception {
				//public void messageArrived(MqttTopic topic, MqttMessage message)
//		throws Exception
//    {
				// we protect against the phone switching off while we're doing this
				//  by requesting a wake lock - we request the minimum possible wake
				//  lock - just enough to keep the CPU running until we've finished
				PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
				WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
				wl.acquire();

				//
				//  I'm assuming that all messages I receive are being sent as strings
				//   this is not an MQTT thing - just me making as assumption about what
				//   data I will be receiving - your app doesn't have to send/receive
				//   strings - anything that can be sent as bytes is valid
				//String messageBody = new String(payloadbytes);
				// inform the app (for times when the Activity UI is running) of the
				//   received message so the app UI can be updated with the new data
				broadcastReceivedMessage(topic, mm.getPayload());

				// receiving this message will have kept the connection alive for us, so
				//  we take advantage of this to postpone the next scheduled ping
				scheduleNextPing();

				// we're finished - if the phone is switched off, it's okay for the CPU
				//  to sleep now
				wl.release();
		}

		/**
		 * *********************************************************************
		 */
		/*
		 * METHODS - wrappers for some of the MQTT methods that we use
		 */
		/**
		 * *********************************************************************
		 */
		/*
		 * Create a client connection object that defines our connection to a message broker server
		 */
		private void initMqttClient() {
				if (mqttClient != null) {
						return;
				}

				try {
						// define the connection to the broker
						mqttClient = new MqttClient(brokerHostName + ":" + brokerPortNumber, getClientId(), usePersistence);
						mqttClient.setCallback(this);
				} catch (MqttException ex) {
						Base.log("Unable to connect");
				}
		}

		private void reconnectInNewThread() {
				new Thread(new Runnable() {
						@Override
						public void run() {
								if (connectToBroker()) {
										//onConnect();
								}
						}

				}).start();
		}

		/*
		 * (Re-)connect to the message broker
		 */
		private boolean connectToBroker() {
				try {
						MqttConnectOptions options = new MqttConnectOptions();
						options.setCleanSession(cleanStart);
						options.setKeepAliveInterval(keepAliveSeconds);
						options.setUserName(username);
						options.setPassword(password);

						// try to connect
						mqttClient.connect(options);

						// we are connected
						changeStatus(ConnectionStatus.CONNECTED);

						// inform the app that the app has successfully connected
						broadcastServiceStatus("Connected @ " + getConnectionChangeTimestamp());

						// we need to wake up the phone's CPU frequently enough so that the
						//  keep alive messages can be sent
						// we schedule the first one of these now
						scheduleNextPing();

						return true;
				} catch (MqttException e) {
						// something went wrong!
						changeStatus(ConnectionStatus.NOTCONNECTED_UNKNOWNREASON);

						//
						// inform the app that we failed to connect so that it can update
						//  the UI accordingly
						broadcastServiceStatus("Unable to connect @ " + getConnectionChangeTimestamp());

						//
						// inform the user (for times when the Activity UI isn't running)
						//   that we failed to connect
						notifyUser("Unable to connect", "MQTT", "Unable to connect - will retry later");

						// if something has failed, we wait for one keep-alive period before
						//   trying again
						// in a real implementation, you would probably want to keep count
						//  of how many times you attempt this, and stop trying after a
						//  certain number, or length of time - rather than keep trying
						//  forever.
						// a failure is often an intermittent network issue, however, so
						//  some limited retry is a good idea
						scheduleNextPing();

						return false;
				}
		}

		public void subscribeToTopic(String topic, int qos) {
				if (!isConnected()) {
						// quick sanity check - don't try and subscribe if we
						//  don't have a connection
						Base.log("Unable to subscribe as we are not connected");
				} else {
						try {
								mqttClient.subscribe(topic, qos);
								topics.add(topic);
								qoss.add(qos);
						} catch (MqttException ex) {
								Base.log("Unable to subscribe to topic via mqttClient");
						}
				}
		}

		public void unsubscribeFromTopic(String topic) {
				if (!isConnected()) {
						// quick sanity check - don't try and subscribe if we
						//  don't have a connection
						Base.log("Unable to subscribe as we are not connected");
				} else {
						try {
								mqttClient.unsubscribe(topic);
						} catch (MqttException ex) {
								Base.log("Unable to subscribe to topic via mqttClient");
						} finally {
								topics.remove(topic);
						}
				}
		}

		/*
		 * Send a request to the message broker to be sent messages published with the specified topic names. Wildcards are allowed.
		 */
		private void subscribeToTopics() {

				boolean subscribed = false;

				if (!isConnected()) {
						// quick sanity check - don't try and subscribe if we
						//  don't have a connection
						Base.log("Unable to subscribe as we are not connected - subscribeToTopics()");
				} else {
						try {
								mqttClient.subscribe(topics.toArray((new String[topics.size()])), Ints.toArray(qoss));
								subscribed = true;
						} catch (IllegalArgumentException e) {
								Base.log("subscribe failed - illegal argument: " + e);
						} catch (MqttException ex) {
								Base.log("MqttException on subscribe: " + ex);
						}
				}

				if (subscribed == false) {
						//
						// inform the app of the failure to subscribe so that the UI can
						//  display an error
						broadcastServiceStatus("Unable to subscribe @ " + getConnectionChangeTimestamp());

						//
						// inform the user (for times when the Activity UI isn't running)
						notifyUser("Unable to subscribe", "MQTT", "Unable to subscribe");
				}
		}

		/*
		 * Terminates a connection to the message broker.
		 */
		private void disconnectFromBroker() {
				// if we've been waiting for an Internet connection, this can be
				//  cancelled - we don't need to be told when we're connected now
				try {
						if (netConnReceiver != null) {
								unregisterReceiver(netConnReceiver);
								netConnReceiver = null;
						}

						if (pingSender != null) {
								unregisterReceiver(pingSender);
								pingSender = null;
						}
				} catch (Exception eee) {
						// probably because we hadn't registered it
						Base.log("unregister failed: " + eee);
				}

				try {
						if (mqttClient != null && mqttClient.isConnected()) {
								mqttClient.disconnect();
						}
				} catch (MqttException e) {
						Base.log("disconnect failed - persistence exception: " + e);
				} finally {
						mqttClient = null;
				}

				// we can now remove the ongoing notification that warns users that
				//  there was a long-running ongoing service running
				NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				nm.cancelAll();
		}


		/*
		 * Schedule the next time that you want the phone to wake up and ping the message broker server
		 */
		private void scheduleNextPing() {
				// When the phone is off, the CPU may be stopped. This means that our
				//   code may stop running.
				// When connecting to the message broker, we specify a 'keep alive'
				//   period - a period after which, if the client has not contacted
				//   the server, even if just with a ping, the connection is considered
				//   broken.
				// To make sure the CPU is woken at least once during each keep alive
				//   period, we schedule a wake up to manually ping the server
				//   thereby keeping the long-running connection open
				// Normally when using this Java MQTT client library, this ping would be
				//   handled for us.
				// Note that this may be called multiple times before the next scheduled
				//   ping has fired. This is good - the previously scheduled one will be
				//   cancelled in favour of this one.
				// This means if something else happens during the keep alive period,
				//   (e.g. we receive an MQTT message), then we start a new keep alive
				//   period, postponing the next ping.

				PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
						new Intent(MQTT_PING_ACTION),
						PendingIntent.FLAG_UPDATE_CURRENT);

				// in case it takes us a little while to do this, we try and do it
				//  shortly before the keep alive period expires
				// it means we're pinging slightly more frequently than necessary
				Calendar wakeUpTime = Calendar.getInstance();
				wakeUpTime.add(Calendar.SECOND, keepAliveSeconds);

				AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
				aMgr.set(AlarmManager.RTC_WAKEUP,
						wakeUpTime.getTimeInMillis(),
						pendingIntent);
		}

		/**
		 * *********************************************************************
		 */
		/*
		 * METHODS - internal utility methods
		 */
		/**
		 * *********************************************************************
		 */
		private boolean isConnected() {
				return ((mqttClient != null) && (mqttClient.isConnected() == true));
		}

		@SuppressWarnings("deprecation")
		private boolean isBackgroundDataEnabled() {
				ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

				//Only on pre-ICS platforms, backgroundDataSettings API exists
				if (Build.VERSION.SDK_INT < (Build.VERSION_CODES.GINGERBREAD_MR1 + 3)) {
						return cm.getBackgroundDataSetting();
				}

				//On ICS platform and higher, define BackgroundDataSetting by checking if
				//phone is online
				return isOnline();
		}

		private boolean isOnline() {
				ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
				NetworkInfo netInfo = cm.getActiveNetworkInfo();
				return netInfo != null && netInfo.isAvailable() && netInfo.isConnected();
		}

		private String getClientId() {
				// generate a unique client id if we haven't done so before, otherwise
				//   re-use the one we already have

				if (mqttClientId == null) {
						String android_id = Secure.getString(getContentResolver(),
								Secure.ANDROID_ID);
						mqttClientId = android_id;

						// truncate - MQTT spec doesn't allow client ids longer than 23 chars
						if (mqttClientId.length() > MAX_MQTT_CLIENTID_LENGTH) {
								mqttClientId = mqttClientId.substring(0, MAX_MQTT_CLIENTID_LENGTH);
						}
				}

				return mqttClientId;
		}

		private void changeStatus(ConnectionStatus newStatus) {
				connectionStatus = newStatus;
				connectionStatusChangeTime = new Timestamp(new Date().getTime());
		}

		private String getConnectionChangeTimestamp() {
				return connectionStatusChangeTime.toString();
		}

		private void handlePublishMessageIntent(Intent intent) {

				if (!isOnline() || !isConnected()) {
						Base.log("handlePublishMessageIntent: connection checked! online && connected");
						return;
				}

				byte[] payload = intent.getByteArrayExtra(MQTT_PUBLISH_MSG);
				String topic = intent.getStringExtra(MQTT_PUBLISH_MSG_TOPIC);

				try {
						mqttClient.publish(topic, new MqttMessage(payload));
				} catch (MqttException e) {
						Base.log(e.getMessage());
				}
		}

		private void handleSubscribeTopicIntent(Intent intent) {

				if (!isOnline() || !isConnected()) {
						Base.log("handleSubscribeTopicIntent: connection checked! online && connected");
						return;
				}

				String topic = intent.getStringExtra(MQTT_SUBSCRIBE_TOPIC);

				try {
						mqttClient.subscribe(topic, 1);
						topics.add(topic);
						qoss.add(1);
				} catch (MqttException e) {
						Base.log(e.getMessage());
				}
		}

		private void handleUnsubscribeTopicIntent(Intent intent) {

				if (!isOnline() || !isConnected()) {
						Base.log("handleSubscribeTopicIntent: connection checked! online && connected");
						return;
				}

				String topic = intent.getStringExtra(MQTT_UNSUBSCRIBE_TOPIC);

				if (topics.contains(topic)) {
						try {
								mqttClient.unsubscribe(topic);
								topics.remove(topic);
								qoss.remove(1);
						} catch (MqttException e) {
								Base.log(e.getMessage());
						}
				}
		}

		/*
		 * Called in response to a change in network connection - after losing a connection to the server, this allows us to wait until we have
		 * a usable data connection again
		 */
		private class NetworkConnectionIntentReceiver extends BroadcastReceiver {

				@Override
				public void onReceive(Context ctx, Intent intent) {
						// we protect against the phone switching off while we're doing this
						//  by requesting a wake lock - we request the minimum possible wake
						//  lock - just enough to keep the CPU running until we've finished
						PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
						WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
						wl.acquire();

						if (isOnline() && !isConnected()) {
								doStart(null, -1);
						}

						// we're finished - if the phone is switched off, it's okay for the CPU
						//  to sleep now
						wl.release();
				}

		}

		/*
		 * Used to implement a keep-alive protocol at this Service level - it sends a PING message to the server, then schedules another ping
		 * after an interval defined by keepAliveSeconds
		 */
		public class PingSender extends BroadcastReceiver {

				@Override
				public void onReceive(Context context, Intent intent) {
						// Note that we don't need a wake lock for this method (even though
						//  it's important that the phone doesn't switch off while we're
						//  doing this).
						// According to the docs, "Alarm Manager holds a CPU wake lock as
						//  long as the alarm receiver's onReceive() method is executing.
						//  This guarantees that the phone will not sleep until you have
						//  finished handling the broadcast."
						// This is good enough for our needs.

						if (isOnline() && !isConnected()) {
								doStart(null, -1);
						} else if (!isOnline()) {
								//TODO LOAD
						} else {
								try {
										mqttClient.publish("/", new MqttMessage("ping".getBytes()));
								} catch (MqttException e) {
										// if something goes wrong, it should result in connectionLost
										//  being called, so we will handle it there
										Base.log("ping failed - MQTT exception");

										// assume the client connection is broken - trash it
										try {
												mqttClient.disconnect();
										} catch (MqttPersistenceException e1) {
												Base.log("disconnect failed - persistence exception");
										} catch (MqttException e2) {
												Base.log("disconnect failed - mqtt exception");
										}

										// reconnect
										Base.log("onReceive: MqttException=" + e);
										doStart(null, -1);
								}
						}

						// start the next keep alive period
						scheduleNextPing();
				}

		}

}
