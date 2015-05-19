package org.zlwima.emurgency.mqtt.models;

import com.google.gson.Gson;

public class EmrException {

		public String exception;

		public String getException() {
				return exception;
		}

		public void setException(String exception) {
				this.exception = exception;
		}

		public String toJson() {
				return new Gson().toJson(this);
		}
}
