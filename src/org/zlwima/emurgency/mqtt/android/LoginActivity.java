package org.zlwima.emurgency.mqtt.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.google.gson.Gson;
import static org.zlwima.emurgency.mqtt.MqttApplication.APPLICATION;
import org.zlwima.emurgency.mqtt.MqttService;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttMessageHandler;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttMessageReceiver;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttStatusHandler;
import org.zlwima.emurgency.mqtt.MqttServiceDelegate.MqttStatusReceiver;
import org.zlwima.emurgency.mqtt.R;
import org.zlwima.emurgency.mqtt.android.config.Base;
import org.zlwima.emurgency.mqtt.android.config.SharedPrefs;
import org.zlwima.emurgency.mqtt.android.ui.ApplicationDialog;
import org.zlwima.emurgency.mqtt.android.ui.FontHelper;
import org.zlwima.emurgency.mqtt.models.Callback;
import org.zlwima.emurgency.mqtt.models.EmrUser;

public class LoginActivity extends Activity implements OnClickListener, MqttMessageHandler, MqttStatusHandler {

		private MqttMessageReceiver msgReceiver;
		private MqttStatusReceiver statusReceiver;
		private Button buttonLogin;
		private Button buttonRegister;
		private CheckBox checkSave;
		private TextView inputEmail;
		private TextView inputPassword;

		private SharedPrefs sharedPrefs;
		private EmrUser loginUser;

		private ProgressDialog loadingDialog;
		private ApplicationDialog dialog;

		private final String CALLBACK_URL = "/api/login/callback";

		private final DialogInterface.OnClickListener exitApplicationDialogListener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
						switch (id) {
								case DialogInterface.BUTTON_POSITIVE:
										dialog.dismiss();
										APPLICATION.closeConnection();
										finish();
										break;
								case DialogInterface.BUTTON_NEGATIVE:
										dialog.dismiss();
										break;
						}
				}
		};

		private final DialogInterface.OnClickListener loginFailedDialogListener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
						switch (id) {
								case DialogInterface.BUTTON_NEUTRAL:
										dialog.dismiss();
										break;
						}
				}
		};

		@Override
		public void onCreate(Bundle savedInstanceState) {
				super.onCreate(savedInstanceState);

				setContentView(R.layout.screen_login);

				// Globally add 'droid sans' font since different fonts break layout...
				FontHelper fHelper = new FontHelper(this);
				fHelper.applyCustomFont((RelativeLayout) findViewById(R.id.loginRoot));

				// Hold instance of logged in user form data
				sharedPrefs = new SharedPrefs(getApplicationContext());

				inputEmail = (TextView) findViewById(R.id.formEmail);
				inputPassword = (TextView) findViewById(R.id.formPassword);
				checkSave = (CheckBox) findViewById(R.id.formCheckSave);
				buttonLogin = (Button) findViewById(R.id.buttonLogin);
				buttonRegister = (Button) findViewById(R.id.buttonRegister);

				// Autofill form
				checkSave.setChecked(sharedPrefs.getAutoFill());
				inputEmail.setText(sharedPrefs.getEmail());

				buttonLogin.setOnClickListener(this);
				buttonRegister.setOnClickListener(this);

				dialog = new ApplicationDialog(this);
				loadingDialog = dialog.progressDialog(R.string.login_loading_title, R.string.login_loading_message);

				/*
				 * Bind mqtt service to message & status events
				 */
				bindStatusReceiver();
				bindMessageReceiver();

				APPLICATION.subscribe(CALLBACK_URL);
		}

		@Override
		public void onBackPressed() {
				dialog.messageDialog(R.string.dialog_quit_title, R.string.dialog_quit_message, ApplicationDialog.MESSAGE_CHOICE, exitApplicationDialogListener).show();
		}

		public void onClick(View view) {
				Base.log("LOGINACTIVITY onLick() " + view.getId());
				if (view.getId() == R.id.buttonLogin) {
						if (inputEmail.getText().toString().trim().equals("")) {
								return;
						}
						loadingDialog.show();

						/*
						 * Submit login data and wait for response
						 */
						loginUser = new EmrUser(inputEmail.getText().toString(), inputPassword.getText().toString());
						APPLICATION.login(loginUser);
				} else if (view.getId() == R.id.buttonRegister) {
						Intent intent = new Intent(getApplicationContext(), RegistrationActivity.class);
						startActivity(intent);
						finish();
				}
		}

		@Override
		protected void onDestroy() {
				Base.log("onDestroy (LoginActivity)");
				/*
				 * We don't need to listen to callbacks and messages/status events
				 */
				APPLICATION.unsubscribe(CALLBACK_URL);
				unbindMessageReceiver();
				unbindStatusReceiver();
				super.onDestroy();
		}

		private void bindMessageReceiver() {
				msgReceiver = new MqttServiceDelegate.MqttMessageReceiver();
				msgReceiver.registerHandler(this);
				registerReceiver(msgReceiver, new IntentFilter(MqttService.MQTT_MSG_RECEIVED_INTENT));
		}

		private void unbindMessageReceiver() {
				if (msgReceiver != null) {
						msgReceiver.unregisterHandler(this);
						unregisterReceiver(msgReceiver);
						msgReceiver = null;
				}
		}

		private void bindStatusReceiver() {
				statusReceiver = new MqttServiceDelegate.MqttStatusReceiver();
				statusReceiver.registerHandler(this);
				registerReceiver(statusReceiver, new IntentFilter(MqttService.MQTT_STATUS_INTENT));
		}

		private void unbindStatusReceiver() {
				if (statusReceiver != null) {
						statusReceiver.unregisterHandler(this);
						unregisterReceiver(statusReceiver);
						statusReceiver = null;
				}
		}

		@Override
		public void handleMessage(String topic, byte[] payload) {
				String response = new String(payload);

				if (topic.equals(CALLBACK_URL)) {
						loadingDialog.dismiss();

						Callback callback = new Gson().fromJson(response, Callback.class);
						if (callback.user != null) {
								if (checkSave.isChecked()) {
										sharedPrefs.setEmail(callback.user.email);
								}
								sharedPrefs.setAutoFill(checkSave.isChecked());

								APPLICATION.USER = new EmrUser(callback.user.email, inputPassword.getText().toString());
								APPLICATION.USER.setLevel(callback.user.level);
								APPLICATION.USER.setDispatcher(callback.user.dispatchers_nearby);

								APPLICATION.DISPATCHER = callback.user.dispatchers_nearby.get(0);

								Intent intent = new Intent(this, DashboardActivity.class);
								startActivity(intent);
								finish();
						} else if (callback.error != null) {
								dialog.messageDialog(R.string.login_error, R.string.login_invalid_message, ApplicationDialog.MESSAGE_NEUTRAL, loginFailedDialogListener).show();
						}
				}

		}

		@Override
		public void handleStatus(MqttService.ConnectionStatus status, String reason) {
				buttonLogin.setEnabled(status == MqttService.ConnectionStatus.CONNECTED);
		}

}
