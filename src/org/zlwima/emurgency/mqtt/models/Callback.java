package org.zlwima.emurgency.mqtt.models;

import java.util.ArrayList;

public class Callback {

		public Error error;

		public class Error {

				public String type;
				public String message;
		}

		public User user;

		public class User {

				public int level;
				public String id;
				public String email;
				public String password;
				public ArrayList<String> dispatchers_nearby;
		}

}
