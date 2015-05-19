package org.zlwima.emurgency.mqtt.models;

import com.google.gson.Gson;

public class EmrVolunteer {
	private String email;
	private String clientId;
	private EmrLocation location;
        
        public EmrVolunteer() {
            //defaults
	}

	/*
	 * GETTERS AND SETTERS
	 */	
	public String getEmail() {
		return email;
	}

	public void setEmail( String email ) {
		this.email = email;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId( String clientId ) {
		this.clientId = clientId;
	}

	public EmrLocation getLocation() {
		return location;
	}

	public void setLocation( EmrLocation location ) {
		this.location = location;
	}

	public String toJson() {
		return new Gson().toJson( this );
	}
}
