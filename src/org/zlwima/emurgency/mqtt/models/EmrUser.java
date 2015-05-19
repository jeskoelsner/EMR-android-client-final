package org.zlwima.emurgency.mqtt.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class EmrUser {

		private String id;
		private String email;
		private String password;
		private int level = -1;
		private String name;
		private String gender;
		private String mobile_number;

		private String zipcode;
		private String city;
		private String street;
		private String phone_number;
		private String house_number;
		private String country;
		private boolean receives_notifications;

		private int birthdate;
		private int created;
		private EmrLocation current_location;
		private EmrLocation staticLocation;
		private List<EmrLocation> locationHistory;
		private List<String> dispatchers_nearby;

		public EmrUser(String email, String password) {
				this.email = email;
				this.id = md5(email);
				this.password = password;
		}

		public String getPhoneNumber() {
				return phone_number;
		}

		public void setPhoneNumber(String phone_number) {
				this.phone_number = phone_number;
		}

		public String getHouseNumber() {
				return house_number;
		}

		public void setHouseNumber(String house_number) {
				this.house_number = house_number;
		}

		public boolean isReceivesNotifications() {
				return receives_notifications;
		}

		public void setReceivesNotifications(boolean receives_notifications) {
				this.receives_notifications = receives_notifications;
		}

		public void addLocation(EmrLocation location) {
				List<EmrLocation> history = getLocationHistory();
				history.add(location);
				setLocationHistory(history);
		}

		public List<EmrLocation> getLocationHistory() {
				return locationHistory;
		}

		public void setLocationHistory(List<EmrLocation> locationHistory) {
				this.locationHistory = locationHistory;
		}

		/*
		 * GETTERS AND SETTERS
		 */
		public String getEmail() {
				return email;
		}

		public void setEmail(String email) {
				this.email = email;
		}

		public String getPassword() {
				return password;
		}

		public void setPassword(String password) {
				this.password = password;
		}

		public String getId() {
				return id;
		}

		public void setId(String id) {
				this.id = id;
		}

		public String getName() {
				return name;
		}

		public void setName(String name) {
				this.name = name;
		}

		public String getGender() {
				return gender;
		}

		public void setGender(String gender) {
				this.gender = gender;
		}

		public String getMobileNumber() {
				return mobile_number;
		}

		public void setMobileNumber(String mobile_number) {
				this.mobile_number = mobile_number;
		}

		public String getStreet() {
				return street;
		}

		public void setStreet(String street) {
				this.street = street;
		}

		public String getCity() {
				return city;
		}

		public void setCity(String city) {
				this.city = city;
		}

		public String getZipcode() {
				return zipcode;
		}

		public void setZipcode(String zipcode) {
				this.zipcode = zipcode;
		}

		public String getCountry() {
				return country;
		}

		public void setCountry(String country) {
				this.country = country;
		}

		public int getBirthdate() {
				return birthdate;
		}

		public void setBirthdate(int birthdate) {
				this.birthdate = birthdate;
		}

		public int getCreated() {
				return created;
		}

		public void setCreated(int created) {
				this.created = created;
		}

		public int getLevel() {
				return level;
		}

		public void setLevel(int level) {
				this.level = level;
		}

		public EmrLocation getCurrentLocation() {
				return current_location;
		}

		public void setCurrentLocation(EmrLocation current_location) {
				this.current_location = current_location;
		}

		public EmrLocation getStaticLocation() {
				return staticLocation;
		}

		public void setStaticLocation(EmrLocation staticLocation) {
				this.staticLocation = staticLocation;
		}

		public List<String> getDispatcher() {
				return dispatchers_nearby;
		}

		public void setDispatcher(ArrayList<String> dispatchers_nearby) {
				this.dispatchers_nearby = dispatchers_nearby;
		}

		public void addDispatcher(String dispatcher_id) {
				this.dispatchers_nearby.add(dispatcher_id);
		}

		public void removeDispatcher(String dispatcher_id) {
				this.dispatchers_nearby.remove(dispatcher_id);
		}

		public String toJson() {
				Gson gson = new GsonBuilder()
						.create();

				return gson.toJson(this);
		}

		@Override
		public String toString() {
				return "[firstName=" + getName()
						+ ", email=" + getEmail()
						+ ", password=" + getPassword()
						+ ", birthdate=" + getBirthdate()
						+ ", gender=" + getGender()
						+ ", mobilePhone=" + getMobileNumber()
						+ ", street=" + getStreet()
						+ ", city=" + getCity()
						+ ", zipcode=" + getZipcode()
						+ ", country=" + getCountry()
						+ ", created=" + getCreated();
		}

		public String md5(String string) {
				try {
						// Create MD5 Hash
						MessageDigest digest = MessageDigest.getInstance("MD5");
						digest.update(string.getBytes());
						byte messageDigest[] = digest.digest();

						// Create Hex String
						StringBuilder hexString = new StringBuilder();
						for (int i = 0; i < messageDigest.length; i++) {
								String h = Integer.toHexString(0xFF & messageDigest[i]);
								while (h.length() < 2) {
										h = "0" + h;
								}
								hexString.append(h);
						}
						return hexString.toString();

				} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
				}
				return "";
		}

}
