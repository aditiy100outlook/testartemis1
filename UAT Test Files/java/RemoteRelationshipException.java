package com.code42.server;

import com.code42.activation.exception.PeerResponseException;

/**
 * The client expects us to be a server-type that we are not. We cannot fulfill the relationship that they are asking
 * for. It's not them, it's us.
 * 
 */
public class RemoteRelationshipException extends PeerResponseException {

	private static final long serialVersionUID = -7182716556929205684L;

	public RemoteRelationshipException(String arg0) {
		super(arg0);
	}

	public RemoteRelationshipException(String arg0, Object... arg1) {
		super(arg0, arg1);
	}

	public RemoteRelationshipException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

	public RemoteRelationshipException(String arg0, Throwable arg1, Object[] arg2) {
		super(arg0, arg1, arg2);
	}

	public RemoteRelationshipException(Throwable t) {
		super(t);
	}
}
