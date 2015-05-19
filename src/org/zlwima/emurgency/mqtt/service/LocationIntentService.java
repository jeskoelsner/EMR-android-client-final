package org.zlwima.emurgency.mqtt.service;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import com.google.android.gms.location.LocationServices;
import static org.zlwima.emurgency.mqtt.MqttApplication.APPLICATION;
import org.zlwima.emurgency.mqtt.android.config.Base;
import org.zlwima.emurgency.mqtt.models.EmrLocation;

public class LocationIntentService extends IntentService {

		public LocationIntentService() {
				super("LocationIntentService");
		}

		@Override
		protected void onHandleIntent(Intent intent) {
				Location location = (Location) intent.getParcelableExtra(LocationServices.FusedLocationApi.KEY_LOCATION_CHANGED);

				if (location != null) {

						EmrLocation newLocation = newLocation(location);
						//APPLICATION.USER.setCurrentLocation(newLocation);
						//APPLICATION.USER.addLocation(newLocation);

						APPLICATION.updateLocation(newLocation);

						if (APPLICATION.missionActivity != null) {
								APPLICATION.missionActivity.updateMarker();
						}
				} else {
						Base.log("Null location changed????");
				}
		}

		private EmrLocation newLocation(Location location) {
				EmrLocation newLocation = new EmrLocation(location.getLatitude(), location.getLongitude());
				newLocation.setAltitude(location.getAltitude());
				newLocation.setTimestamp(location.getTime());
				newLocation.setProvider(location.getProvider());
				return newLocation;
		}

}
