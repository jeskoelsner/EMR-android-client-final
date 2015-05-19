package org.zlwima.emurgency.mqtt;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import static org.zlwima.emurgency.mqtt.MqttApplication.APPLICATION;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttMessageHandler;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttMessageReceiver;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttStatusHandler;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttStatusReceiver;
import org.zlwima.emurgency.mqtt.android.DashboardActivity;
import org.zlwima.emurgency.mqtt.android.MissionActivity;
import org.zlwima.emurgency.mqtt.android.config.Base;
import org.zlwima.emurgency.mqtt.android.config.SharedPrefs;
import org.zlwima.emurgency.mqtt.android.ui.ApplicationDialog;
import org.zlwima.emurgency.mqtt.models.EmrCaseData;
import org.zlwima.emurgency.mqtt.models.EmrException;
import org.zlwima.emurgency.mqtt.models.EmrLocation;
import org.zlwima.emurgency.mqtt.models.EmrUser;
import org.zlwima.emurgency.mqtt.service.LocationIntentService;

public class MqttApplication extends Application implements ConnectionCallbacks, OnConnectionFailedListener, MqttStatusHandler, MqttMessageHandler {

		// Update interals and resolution code (mandatory)
		private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
		private final static int UNIQUE_REQUESTCODE = 129919;

		private static final long SLOW_UPDATE_INTERVAL = 1000 * 60 * 30;  //Every 30min.
		private static final long NORMAL_UPDATE_INTERVAL = 1000 * 60 * 10;//Every 10min.
		private static final long FAST_UPDATE_INTERVAL = 1000 * 60 * 1;   //Every 1min.
		private static final long REALTIME_UPDATE_INTERVAL = 1000 * 10;		//Every 10 Seconds

		private static final int MIN_DISTANCE = 5;											//in m
		private static final int MED_DISTANCE = 50;											//in m
		private static final int MAX_DISTANCE = 500;                    //in m

		public final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 991;

		// Singleton: application & mqttclient & user
		private String mqttClientId = null;
		public static MqttApplication APPLICATION = null;

		public EmrUser USER;
		public String DISPATCHER;
		public EmrLocation LASTKNOWN_LOCATION;
		public String DISPLAYED_CASE_ID = "blank";

		public boolean LOGOUT = false;
		private boolean LOGIN_LOCK = false;

		// Notifications / statusbar
		private Intent mNotificationIntent;
		private Notification mNotification;
		private NotificationManager mNotificationManager;
		private MqttMessageReceiver msgReceiver;
		private MqttStatusReceiver statusReceiver;

		// Activity handling
		private Activity mCurrentActivity = null;

		public DashboardActivity dashboardActivity;
		public MissionActivity missionActivity;
		private ApplicationDialog dialogHelper;

		// Shared application data
		private SharedPrefs sharedPrefs;
		public final ArrayList<EmrCaseData> activeCases = new ArrayList<EmrCaseData>();

		// Audio & alarmangement
		private MediaPlayer mMediaPlayer;
		private AudioManager mAudioManager;
		private int defaultVolume;
		private Uri alarmSound;
		private Uri defaultSound;

		// Location management via Play Services
		private FusedLocationProviderApi mLocationClient;
		private GoogleApiClient mGoogleApiClient;
		private LocationRequest mLocationRequest;
		private LocationRequest mLocationRequestLow;
		private LocationRequest mLocationRequestHigh;
		private PendingIntent mPendingIntent;
		private Intent mLocationIntent;

		// Exceptions send via MQTT
		private UncaughtExceptionHandler defaultExceptionHandler;
		private UncaughtExceptionHandler customExceptionHandler = new UncaughtExceptionHandler() {
				// Converting stacktrace to string
				public void uncaughtException(Thread thread, Throwable ex) {

						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						ex.printStackTrace(pw);

						EmrException emr_ex = new EmrException();
						emr_ex.setException(sw.toString());

						sendException(emr_ex);
						defaultExceptionHandler.uncaughtException(thread, ex);
				}

		};

		private final DialogInterface.OnClickListener okDialogListener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
						switch (id) {
								case DialogInterface.BUTTON_POSITIVE:
										dialog.dismiss();
										break;
						}
				}

		};

		private final DialogInterface.OnClickListener downloadDialogListener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
						switch (id) {
								case DialogInterface.BUTTON_POSITIVE:
										dialog.dismiss();
										Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.maps"));
										intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
										startActivity(intent);
										break;
								case DialogInterface.BUTTON_NEGATIVE:
										dialog.dismiss();
										break;
						}
				}

		};

		//TODO
		public void onConnectionSuspended(int i) {
				Base.log("MqttApplication: onConnectionSuspended[" + i + "]");
		}

		public void onSuccess(IMqttToken imt) {
				Base.log("MqttApplication: onSuccess");
		}

		public void onFailure(IMqttToken imt, Throwable thrwbl) {
				Base.log("MqttApplication: onFailure - ex: " + thrwbl.getMessage());
		}

		public void connectionLost(Throwable thrwbl) {
				Base.log("MqttApplication: connectionLost - ex: " + thrwbl.getMessage());
		}

		public void deliveryComplete(IMqttDeliveryToken imdt) {
				Base.log("MqttApplication: deliveryComplete");
		}

		public void handleStatus(MqttService.ConnectionStatus status, String reason) {
				Base.log("handleStatus() MqttApplication: " + status.name() + " - " + reason);
		}

		class StopTask extends TimerTask {

				@Override
				public void run() {
						stopSound();
				}

		}

		public void setSound(boolean setDefault) {
				try {
						mMediaPlayer.setDataSource(this, setDefault ? defaultSound : alarmSound);
				} catch (IOException ex) {
						Base.log("Setting sound not possible");
				}
		}

		public void playSound(int volume, int length, boolean setDefault, boolean vibrate) {
				int flag = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
				flag |= vibrate ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL;
				flag |= AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE;

				mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) * volume / 100, flag);
				try {
						if (!mMediaPlayer.isPlaying()) {
								setSound(setDefault);
								mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
								mMediaPlayer.setLooping(true);
								mMediaPlayer.prepare();
								mMediaPlayer.start();

								Timer timer = new Timer("timer", true);
								timer.schedule(new StopTask(), length * 1000);
						}

				} catch (IOException e) {
						Base.log("Playing sound not possible");
				}
		}

		public void stopSound() {
				mMediaPlayer.stop();
				mMediaPlayer.reset();
				mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, defaultVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
		}

		public void releasePlayer() {
				mMediaPlayer.release();
		}

		public Activity getCurrentActivity() {
				return mCurrentActivity;
		}

		public void setCurrentActivity(Activity mCurrentActivity) {
				this.mCurrentActivity = mCurrentActivity;
		}

		public void checkPlayServices(Activity activity) {
				if (APPLICATION.getCurrentActivity() != null) {
						int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
						if (resultCode != ConnectionResult.SUCCESS) {
								if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
										//TODO LOCK LOGIN
										GooglePlayServicesUtil.getErrorDialog(resultCode, activity, PLAY_SERVICES_RESOLUTION_REQUEST).show();
								} else {
										//TODO figure out what then?!
								}
								LOGIN_LOCK = true;
						} else if (!isGoogleMapsInstalled()) {
								dialogHelper = new ApplicationDialog(activity);
								dialogHelper.messageDialog(R.string.dialog_nomaps_title, R.string.dialog_nomaps_message, ApplicationDialog.MESSAGE_CHOICE, downloadDialogListener).create().show();
								LOGIN_LOCK = true;
						} else {
								LOGIN_LOCK = false;
						}
				}
		}

		public boolean isGoogleMapsInstalled() {
				try {
						ApplicationInfo info = getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0);
						return true;
				} catch (PackageManager.NameNotFoundException e) {
						return false;
				}
		}

		public boolean loginLocked() {
				return LOGIN_LOCK;
		}

		protected synchronized void buildGoogleApiClient() {
				mGoogleApiClient = new GoogleApiClient.Builder(this)
						.addConnectionCallbacks(this)
						.addOnConnectionFailedListener(this)
						.addApi(LocationServices.API)
						.build();
				mGoogleApiClient.connect();
		}

		@Override
		public void onCreate() {
				super.onCreate();
				Base.log("***************** ONCREATE APPLICATION ********************");
				APPLICATION = this;

				bindStatusReceiver();
				bindMessageReceiver();

				MqttServiceDelegate.startService(this);

				defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
				Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler);

				sharedPrefs = new SharedPrefs(getApplicationContext());
				mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

				mMediaPlayer = new MediaPlayer();
				mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				alarmSound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alarm);
				defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); //TODO default Notification sound!
				defaultVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);

				TelephonyManager telMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

				//Location service
				mLocationRequestLow = LocationRequest.create();
				if (telMgr.getSimState() == TelephonyManager.SIM_STATE_ABSENT) {
						Toast.makeText(this, getString(R.string.bubble_no_sim), Toast.LENGTH_LONG).show();
						mLocationRequestLow.setPriority(LocationRequest.PRIORITY_LOW_POWER);
				} else {
						mLocationRequestLow.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
				}
				mLocationRequestLow.setFastestInterval(FAST_UPDATE_INTERVAL);
				mLocationRequestLow.setInterval(NORMAL_UPDATE_INTERVAL);
				mLocationRequestLow.setSmallestDisplacement(MED_DISTANCE);

				mLocationRequestHigh = LocationRequest.create();
				mLocationRequestHigh.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
				mLocationRequestHigh.setFastestInterval(REALTIME_UPDATE_INTERVAL);
				mLocationRequestHigh.setInterval(FAST_UPDATE_INTERVAL);
				mLocationRequestHigh.setExpirationDuration(NORMAL_UPDATE_INTERVAL);
				mLocationRequestHigh.setSmallestDisplacement(MIN_DISTANCE);

				mLocationRequest = mLocationRequestLow;
				mLocationClient = LocationServices.FusedLocationApi;

				mLocationIntent = new Intent(this, LocationIntentService.class);
				mPendingIntent = PendingIntent.getService(this, UNIQUE_REQUESTCODE, mLocationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				buildGoogleApiClient();
		}

		public void startLocationUpdates() {
				if (mGoogleApiClient.isConnected() && servicesConnected()) {
						mLocationClient.requestLocationUpdates(mGoogleApiClient, mLocationRequest, mPendingIntent);
				} else {
						mGoogleApiClient.connect();
				}
		}

		public void stopLocationUpdates() {
				if (mGoogleApiClient != null && mPendingIntent != null) {
						mLocationClient.removeLocationUpdates(mGoogleApiClient, mPendingIntent);
				}
		}

		public void clearLocationUpdates() {
				if (mGoogleApiClient.isConnected()) {
						mGoogleApiClient.disconnect();
				}
				mPendingIntent.cancel();
		}

		public void restartLocationUpdates() {
				stopLocationUpdates();
				startLocationUpdates();
		}

		public void changeLocationUpdates(boolean high) {
				stopLocationUpdates();

				mLocationRequest = (high ? mLocationRequestHigh : mLocationRequestLow);

				startLocationUpdates();
		}

		private boolean servicesConnected() {
				// Check that Google Play services is available
				int resultCode
						= GooglePlayServicesUtil.
						isGooglePlayServicesAvailable(this);
				// If Google Play services is available
				if (ConnectionResult.SUCCESS == resultCode) {
						Base.log("Google Play Services: servicesConnected() -> AVAILABLE");
						return true;
				} else {
						Base.log("Google Play Services: servicesConnected() -> UNAVAILABLE");
						return false;
				}
		}

		public void closeConnection() {
				Base.log("CLEANING UP (MqttApplication)");
				LOGOUT = true;
				MqttServiceDelegate.stopService(getApplicationContext());
				mNotificationManager.cancelAll();
		}

		// MQTT spec does not allow client ids longer than 23 chars
		public String getAndroidClientId() {
				if (mqttClientId == null) {
						String android_id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
						if (android_id.length() > 12) {
								android_id = android_id.substring(0, 12);
						}
						mqttClientId = android_id;
				}
				Base.log("getAndroidClientId(): " + mqttClientId);
				return mqttClientId;
		}

		public void startMissionActivity(EmrCaseData caseData) {
				Base.log("*** startMissionActivity() ***");
				Intent missionIntent = new Intent();
				missionIntent.setClass(getApplicationContext(), MissionActivity.class);
				missionIntent.putExtra("CASEID", caseData.getCaseId());
				missionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(missionIntent);
		}

		@Override
		public void handleMessage(String topic, byte[] payload) {
				// client only listens to server/{id}/# so we are interested in the last topic
				Base.log("handleMessage() MqttApplication: topic=" + topic + ", message=" + new String(payload));
				String subTopic = topic.substring(topic.lastIndexOf("/"));
				String payloadString = new String(payload);

				/*
				 * PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); PowerManager.WakeLock wakeLock =
				 * pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "caseDataUpdate"); wakeLock.acquire();
				 */
				if (subTopic.equals("/updateCase")) {
						EmrCaseData receivedCaseData = new Gson().fromJson(payloadString, EmrCaseData.class);
						receivedCaseData.setCaseArrivedOnClientTimeMillis(SystemClock.elapsedRealtime() - receivedCaseData.getCaseRunningTimeMillis());

						// check if this case is new to the android client
						if (!hasCase(receivedCaseData.getCaseId())) {
								playSound(sharedPrefs.getVolume(), sharedPrefs.getSoundLength(), sharedPrefs.getSoundOption() == 2, sharedPrefs.getSoundOption() == 3);
								dashboardActivity.addCaseToAdapter(receivedCaseData);
						} else {
								dashboardActivity.updateCaseInAdapter(receivedCaseData);
						}
						startMissionActivity(receivedCaseData);

				} else if (subTopic.equals("/closeCase")) {
						EmrCaseData receivedCaseData = new Gson().fromJson(payloadString, EmrCaseData.class);

						if (hasCase(receivedCaseData.getCaseId())) {
								dashboardActivity.removeRunningCase(receivedCaseData.getCaseId());
						}
				} else {
						//HANDLE LOGOUT HERE!
						USER = null;
						// serverMessages are mainly handled in LoginActivity, but a realtime UserStatusUpdate arrives only here
				}

				//wakeLock.release();
		}

		public void updateNotification(boolean connected, boolean logout) {
				mNotification = new NotificationCompat.Builder(this)
						.setContentTitle(getString(R.string.full_app_name))
						.setContentIntent(PendingIntent.getActivity(this, 0, mNotificationIntent, 0))
						.setContentText(getString(connected ? R.string.notification_available : R.string.notification_unavailable))
						.setSmallIcon(connected ? R.drawable.icon_notification_color : R.drawable.icon_notification_grey)
						.build();
				//.getNotification();
				mNotification.flags |= Notification.FLAG_ONGOING_EVENT;

				if (!LOGOUT) {
						mNotificationManager.cancel(Base.TAG, 255);
						mNotificationManager.notify(Base.TAG, 255, mNotification);
				}
		}

		public void addCase(EmrCaseData caseData) {
				if (!hasCase(caseData.getCaseId())) {
						activeCases.add(null);
				}
		}

		public boolean hasCase(String caseId) {
				return (getCaseById(caseId) != null);
		}

		public EmrCaseData getCaseById(String caseId) {
				for (EmrCaseData oneCase : activeCases) {
						if (oneCase.getCaseId().equals(caseId)) {
								return oneCase;
						}
				}
				return null;
		}

		public void submitLastLocation() {
				Location lastLocation = mLocationClient.getLastLocation(mGoogleApiClient);
				EmrLocation emrLocation = new EmrLocation(lastLocation.getLatitude(), lastLocation.getLongitude());
				emrLocation.setTimestamp(lastLocation.getTime());
				updateLocation(emrLocation);
		}

		public void onConnected(Bundle dataBundle) {
				Location lastLocation = mLocationClient.getLastLocation(mGoogleApiClient);

				if (lastLocation != null && lastLocation.getLatitude() != 0.0 && lastLocation.getLongitude() != 0.0) {
						EmrLocation location = new EmrLocation();
						location.setLatitude(lastLocation.getLatitude());
						location.setLongitude(lastLocation.getLongitude());
						location.setTimestamp(lastLocation.getTime());

						LASTKNOWN_LOCATION = location;

						Base.log("lastLocation:" + lastLocation.getLatitude() + "/" + lastLocation.getLongitude());
				}

		}

		public void onDisconnected() {
				Base.log("Google Play Service: onDisconnected()");
				dialogHelper.messageDialog(R.string.dialog_resolution_error_title, R.string.dialog_resolution_error_message, ApplicationDialog.MESSAGE_OK, okDialogListener).show();
		}

		public void onConnectionFailed(ConnectionResult connectionResult) {
				/*
				 * Google Play services can resolve some errors it detects. If the error has a resolution, try sending an Intent to start a Google
				 * Play services activity that can resolve error.
				 */
				Base.log("Google Play Service: onConnectionFailed()");
				if (connectionResult.hasResolution()) {
            //try {
						// Start an Activity that tries to resolve the error

                //TODO WEIRDLY HACKY
						//connectionResult.startResolutionForResult(dashboardActivity, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
						 * Thrown if Google Play services canceled the original PendingIntent
						 */
						// } catch (IntentSender.SendIntentException e) {
						dialogHelper.messageDialog(R.string.dialog_resolution_error_title, connectionResult.getResolution().toString(), ApplicationDialog.MESSAGE_OK, okDialogListener).show();
						//}
				} else {
						/*
						 * If no resolution is available, display a dialog to the user with the error.
						 */
						dialogHelper.messageDialog(R.string.dialog_resolution_error_title, R.string.dialog_resolution_notfound_message, ApplicationDialog.MESSAGE_OK, okDialogListener).show();
				}
		}

		public void bindMessageReceiver() {
				msgReceiver = new MqttMessageReceiver();
				msgReceiver.registerHandler(this);
				registerReceiver(msgReceiver, new IntentFilter(MqttService.MQTT_MSG_RECEIVED_INTENT));
		}

		private void bindStatusReceiver() {
				statusReceiver = new MqttStatusReceiver();
				statusReceiver.registerHandler(this);
				registerReceiver(statusReceiver, new IntentFilter(MqttService.MQTT_STATUS_INTENT));
		}

		public void subscribeCases() {

		}

		public void unsubscribeCases() {

		}

		public void subscribe(String topic) {
				MqttServiceDelegate.subscribe(this, topic);
		}

		public void unsubscribe(String topic) {
				MqttServiceDelegate.unsubscribe(this, topic);
		}

		// client only listens to server/{id}/# so we tell the server our {id} in the loginRequest
		public void login(EmrUser loginUser) {
				String topic = "/api/login";
				MqttServiceDelegate.publish(this, topic, loginUser.toJson().getBytes());
		}

		public void registration(EmrUser registerUser) {
				String topic = "/api/register";
				MqttServiceDelegate.publish(this, topic, registerUser.toJson().getBytes());
		}

		public void logout() {
				String topic = "/client/" + APPLICATION.USER.getId() + "/" + APPLICATION.DISPATCHER + "/logout";
				MqttServiceDelegate.publish(this, topic, APPLICATION.USER.toJson().getBytes());
		}

		public void acceptCase() {
				String topic = "/client/" + APPLICATION.USER.getId() + "/" + APPLICATION.DISPATCHER + "/acceptCase";
				MqttServiceDelegate.publish(this, topic, ("{ 'id': '" + DISPLAYED_CASE_ID + "'}").getBytes());
		}

		public void updateLocation(EmrLocation location) {
				String topic = "/client/" + APPLICATION.USER.getId() + "/" + APPLICATION.USER.getDispatcher().get(0) + "/updateLocation";
				if (location == null) {
						MqttServiceDelegate.publish(this, topic, APPLICATION.USER.toJson().getBytes());
				} else {
						MqttServiceDelegate.publish(this, topic, location.toJson().getBytes());

				}
		}

		public void arrivedAtCase(String caseId) {
				String topic = "/client/" + APPLICATION.USER.getId() + "/" + APPLICATION.DISPATCHER + "/arrivedAtCase";
				MqttServiceDelegate.publish(this, topic, ("{ 'id': '" + caseId + "'}").getBytes());
		}

		public void sendException(EmrException exception) {
				String topic = "/client/" + APPLICATION.USER.getId() + "/" + APPLICATION.DISPATCHER + "/exception";
				MqttServiceDelegate.publish(this, topic, exception.toJson().getBytes());
		}

}
