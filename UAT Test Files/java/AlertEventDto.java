package com.code42.alert;

import java.util.Date;
import java.util.Map;

import com.code42.alert.AlertEvent.Severity;
import com.code42.alert.AlertEvent.Status;
import com.code42.alert.AlertEvent.Type;

public class AlertEventDto {

	private final AlertEvent event;

	public AlertEventDto(final AlertEvent event) {
		this.event = event;
	}

	public Long getAlertEventId() {
		return this.event.getAlertEventId();
	}

	public String getServer() {
		return this.event.getServerName();
	}

	public Type getAlertEventType() {
		return this.event.getAlertEventType();
	}

	public Status getStatus() {
		return this.event.getStatus();
	}

	public Severity getSeverity() {
		return this.event.getSeverity();
	}

	public String getDescription() {
		return this.event.getDescription();
	}

	public Map<String, Object> getInfoMap() throws Exception {
		return this.event.fromJsonInfoMap();
	}

	public Date getCreationDate() {
		return this.event.getCreationDate();
	}

	public Date getModificationDate() {
		return this.event.getModificationDate();
	}

}
