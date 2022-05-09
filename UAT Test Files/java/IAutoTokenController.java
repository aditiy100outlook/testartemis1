package com.code42.auth;

import com.code42.messaging.MessageException;
import com.code42.server.node.Node;

public interface IAutoTokenController {

	public DataKeyFindResponseMessage send(DataKeyFindRequestMessage msg);

	public void receiveMessage(DataKeyFindRequestMessage msg) throws MessageException;

	public LoginTokenResponseMessage send(LoginTokenRequestMessage msg, Node node);

	public void receiveMessage(LoginTokenRequestMessage msg) throws MessageException;

	public LoginTokenProviderResponseMessage send(LoginTokenProviderRequestMessage msg, Node providerMasterNode);

	public void receiveMessage(LoginTokenProviderRequestMessage msg) throws MessageException;

}