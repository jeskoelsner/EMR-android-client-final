package org.zlwima.emurgency.mqtt.models;

import com.google.gson.Gson;

public class EmrLocation {
	private String provider;
	private double latitude;
	private double longitude;
	private double altitude;
	private long timestamp;
        
	public EmrLocation( double latitude, double longitude ) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public EmrLocation() {
	}

	/*
	 * GETTERS AND SETTERS
	 */
	public double getLatitude() {
		return latitude;
	}

	public void setLatitude( double latitude ) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude( double longitude ) {
		this.longitude = longitude;
	}

	public double getAltitude() {
		return altitude;
	}

	public void setAltitude( double altitude ) {
		this.altitude = altitude;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider( String provider ) {
		this.provider = provider;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp( long timestamp ) {
		this.timestamp = timestamp;
	}
        
	public String toJson() {
		return new Gson().toJson( this );
	}
        
        @Override
	public String toString() {
            return String.format("(locationData: %d, %d, %d, %s, %f, %f)", latitude, longitude, altitude, provider, timestamp);
	}
        
}
