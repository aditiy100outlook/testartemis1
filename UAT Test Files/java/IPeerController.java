package com.backup42.app.cpc.peer;

import java.util.Collection;
import java.util.List;

import com.backup42.common.AuthorizeRules;
import com.backup42.common.cpc.message.CPCAccountMessage;
import com.backup42.common.cpc.message.CPCAddFriendDestinationRequestMessage;
import com.backup42.common.cpc.message.CPCAddFriendRequestMessage;
import com.backup42.common.cpc.message.CPCAlertMessage;
import com.backup42.common.cpc.message.CPCAuthRulesRequestMessage;
import com.backup42.common.cpc.message.CPCBackupLastConnectedMessage;
import com.backup42.common.cpc.message.CPCCapacityMessage;
import com.backup42.common.cpc.message.CPCChangePasswordMessage;
import com.backup42.common.cpc.message.CPCDeauthorizeRequestMessage;
import com.backup42.common.cpc.message.CPCEstablishNetworkRequestMessage;
import com.backup42.common.cpc.message.CPCGetConfigMessage;
import com.backup42.common.cpc.message.CPCInviteRequestMessage;
import com.backup42.common.cpc.message.CPCLogMessage;
import com.backup42.common.cpc.message.CPCLogMessage2;
import com.backup42.common.cpc.message.CPCLoginMessage;
import com.backup42.common.cpc.message.CPCRemoveFriendMessage;
import com.backup42.common.cpc.message.CPCResetBackupCodeRequestMessage;
import com.backup42.common.cpc.message.CPCRestoreStatusMessage;
import com.backup42.common.cpc.message.CPCSaveChildComputerMessage;
import com.backup42.common.cpc.message.CPCSaveComputerMessage;
import com.backup42.common.cpc.message.CPCSaveFriendMessage;
import com.backup42.common.cpc.message.CPCSecurityKeyTypeRequestMessage;
import com.backup42.common.cpc.message.CPCSendNotificationRequestMessage;
import com.backup42.common.cpc.message.CPCSetupTwitterRequestMessage;
import com.backup42.common.cpc.message.CPCSimpleRequestMessage;
import com.backup42.common.cpc.message.CPCSlaveVersionMessage;
import com.backup42.common.cpc.message.CPCSocialNetworkChangedMessage;
import com.backup42.common.cpc.message.CPCSpecialInviteRequestMessage;
import com.backup42.common.cpc.message.CPCStoreConfigMessage;
import com.backup42.common.cpc.message.CPCTwitterRequestMessage;
import com.backup42.common.cpc.message.CPCUsageSwapDoneMessage;
import com.backup42.common.cpc.message.CPCUsageVersionUpdateMessage;
import com.backup42.common.cpc.message.CPCUserComputerUsagesRequestMessage;
import com.backup42.common.cpc.message.CPCVersionMessage2;
import com.backup42.common.cpc.message.CPCWebLoginKeyRequestMessage;
import com.backup42.common.languageupdate.LanguageUpdateServer;
import com.backup42.common.net.ConnectionDiscoverySuccessEvent;
import com.backup42.common.peer.message.IVersionMessage;
import com.backup42.common.peer.message.SourceBackupConnectedMessage2;
import com.backup42.common.peer.message.TargetBackupConnectedMessage2;
import com.backup42.common.peer.message.VersionMessage;
import com.backup42.computer.LicenseServices;
import com.code42.balance.engine.BulkStatsBroadcast;
import com.code42.computer.Computer;
import com.code42.computer.Config;
import com.code42.messaging.IMessage;
import com.code42.messaging.IMessageReceiverProxyTarget;
import com.code42.messaging.ProtoWrapper;
import com.code42.messaging.Session;
import com.code42.peer.IPeerAgent;
import com.code42.peer.Peer;
import com.code42.peer.RemotePeer;
import com.code42.peer.event.ConnectedEvent;
import com.code42.peer.event.DisconnectedEvent;
import com.code42.peer.event.ProxyMessageEvent;
import com.code42.peer.exception.AgentExistsException;
import com.code42.peer.exception.AgentStartUpException;
import com.code42.peer.exception.PeerException;
import com.code42.peer.message.KeepAliveMessage;
import com.code42.protos.PeerMessages;
import com.code42.social.FriendComputerUsage;
import com.code42.ssoauth.SsoAuthUser;
import com.code42.user.User;

/**
 * A temporary placeholder interface extracted from CPCPeerController. For now this is intended to be a stand-in for
 * CPCPeerController itself within pro_core (and by extension mod_crashplan and mod_shareplan). Eventually most of the
 * functionality here will be migrated to ClientCallback instances.
 * 
 * @author bmcguire
 */
public interface IPeerController {

	/* =============================== Methods inherited from parent classes =============================== */
	/* This method should be removed as soon as possible */
	@Deprecated
	public Peer getPeer();

	@Deprecated
	public RemotePeer getRemotePeer(final long uid);

	/* The following method definitions are required by NodePeerController */
	public boolean isConnected(final long uid);

	public boolean connect(final long guid, final long waitTime, final boolean force);

	public boolean sendMessage(final RemotePeer recipient, final IMessage message);

	public boolean checkVersions(long remoteGuid, IVersionMessage msg, Boolean... otherConditions);

	public void disconnect();

	public boolean disconnect(long guid);

	/* =============================== Methods on CPCPeerController =============================== */
	public void start() throws PeerException;

	public void stop();

	public LanguageUpdateServer getLanguageUpdateServer();

	public void listen() throws PeerException;

	/**
	 * <b>Step 0</b> - startup
	 * <p>
	 * Initialize your agent with a session.
	 * 
	 * @param session
	 * @throws AgentStartUpException
	 */
	public void startUp(final Session session) throws AgentStartUpException;

	/**
	 * Helper for the outside world.
	 */
	public int getPeerCount();

	public void loggedIn(RemotePeer remotePeer);

	public void alertConnected(final String title, final String message, final long expireTimeInMillis);

	public void refreshUpgrades();

	/**
	 * @see com.backup42.social.ISocialComputerNetworkChangeHandler#notifyDestinationUsageChanged(com.backup42.computer.data.Computer,
	 *      com.backup42.social.data.FriendComputerUsage)
	 */
	public void notifyDestinationUsageChanged(Computer sourceComputer, FriendComputerUsage usage);

	/**
	 * @see com.backup42.social.ISocialComputerNetworkChangeHandler#notifySocialNetworkOfChange(int)
	 */
	public void notifySocialNetworkOfChange(int userId);

	/**
	 * @see com.backup42.social.ISocialComputerNetworkChangeHandler#notifyUserOfChange(int)
	 */
	public void notifyUserOfChange(int userId);

	/**
	 * Send a server configuration change to all connected clients
	 * 
	 * @param name
	 * @param webhost
	 */
	public void sendServerConfigChangedMessage(final long clusterServerGuid, final String name, final String webhost);

	/**
	 * Send a social network change to everyone
	 */
	public void sendSocialComputerNetworkChangedToMasterUsers();

	/**
	 * Send a social network changed to all of a User's Computers AND Friends' Computers
	 * 
	 * @param user
	 */
	public void sendSocialComputerNetworkChangedToAll(User user, Long... excludeGuids);

	/**
	 * Send a social network changed to all of the User's Computer(s).
	 * 
	 * @param user
	 */
	public void sendSocialComputerNetworkChangedToUser(final User user, Long... excludeGuids);

	public CPCSocialNetworkChangedMessage getSocialNetworkForGuid(long guid);

	/**
	 * @see com.backup42.social.ISocialComputerNetworkChangeHandler#notifyComputerOfChange(long)
	 */
	public void notifyComputerOfChange(long guid);

	/**
	 * @see com.backup42.computer.IConfigChangeHandler#notifyComputerOfChangedConfig(long,
	 *      com.backup42.computer.data.Config)
	 */
	public void notifyComputerOfChangedConfig(long guid, Config config);

	/**
	 * @see com.backup42.computer.IConfigChangeHandler#notifyComputersOfChangedConfig(java.util.Collection)
	 */
	public void notifyComputersOfChangedConfig(final Collection<Computer> computers);

	/**
	 * @see com.backup42.social.ISocialComputerNetworkChangeHandler#notifyRemovedComputer(long)
	 */
	public void notifyRemovedComputer(long guid);

	/**
	 * Send a license change to a peer
	 * 
	 * @param archive
	 * @see LicenseServices#handleLicenseChangeForUser(User)
	 */
	public void sendLicenseToComputer(final Computer computer, final User user);

	/**
	 * @param guid
	 * @param command
	 * @return
	 */
	public boolean sendServiceCommand(final long guid, final String command);

	public boolean pruneFileHistories(long guid);

	public boolean pruneAllFileHistories();

	/**
	 * This simply opens all BackupSource(s) that are using CPC. This triggers a manifest migration if necessary.
	 * 
	 * @return
	 */
	public boolean migrateManifests();

	public boolean sendAuthRules(Long guid, AuthorizeRules rules);

	public void sendServiceCommandToUser(int userId, String command);

	/**
	 * Send an authorize command to all the computers with a guid found in the given comma-separated list of guids.
	 */
	public void sendAuthorize(String guids, boolean includeConfig);

	public String getSendAuthorizeList();

	public boolean sendCPSGetRootPathsRequestMessage(long guid, Long backupSetId, IMessageReceiverProxyTarget receiver);

	public boolean sendCPSGetChildrenPathsRequestMessage(long guid, long backupSetId, String parentPath,
			IMessageReceiverProxyTarget receiver, Object context);

	public BulkStatsBroadcast getBulkStatsBroadcast();

	public void handleLogCopierDone(String filename, long guid, Long ticket);

	/**
	 * Retrieve the history log from the given client if connected.
	 * 
	 * @param guid
	 * @return the history log data
	 */
	public String retrieveHistoryLogFromClient(long guid, long timeoutMs);

	public void sendLogsToSupport(String filename, long guid, Long ticket, boolean clientLogs);

	/**
	 * Add a message-handling agent to the peer network.
	 * 
	 * @param agent the agent to add
	 * @throws AgentExistsException if something goes wrong
	 */
	public void addPeerAgent(IPeerAgent agent) throws AgentExistsException;

	/**
	 * Add a message-handling agent to the super-peer network.
	 * 
	 * @param agent the agent to add
	 * @throws AgentExistsException if something goes wrong
	 */
	public void addSuperPeerAgent(IPeerAgent agent) throws AgentExistsException;

	/**
	 * Add a message-handling agent to the cluster peer network.
	 * 
	 * @param agent the agent to add
	 * @throws AgentExistsException if something goes wrong
	 */
	public void addClusterPeerAgent(IPeerAgent agent) throws AgentExistsException;

	/**
	 * Equivalent to getRemotePeer(), but for the super-peer network only.
	 * 
	 * @param uid
	 * @return
	 */
	public RemotePeer getRemoteSuperPeer(final long uid);

	public List<RemotePeer> getSuperPeers();

	public void addJoinHandler(IJoinHandler handler);

	public boolean sendSsoAuthFinalize(SsoAuthUser ssoAuthUser);

	/* ================================= Event and message handlers ================================= */
	public void handleEvent(LoginPeerEvent event);

	public void handleEvent(ConnectedEvent event);

	/**
	 * @see com.backup42.common.peer.PeerAgentController#handleEvent(com.code42.peer.event.DisconnectedEvent)
	 */
	public void handleEvent(DisconnectedEvent event);

	/**
	 * Handle Proxy message even
	 * 
	 * @param event
	 */
	public void handleEvent(ProxyMessageEvent event);

	/**
	 * Handle a successful connection discovery event. Store the remote addr:port if changed and then notify others.
	 * 
	 * @param event
	 */
	public void handleEvent(ConnectionDiscoverySuccessEvent event);

	/**
	 * Handle a client connecting in non-authority, PASSIVE mode.
	 * 
	 * @see com.backup42.common.peer.PeerAgentController#receiveMessage(com.backup42.common.peer.message.VersionMessage)
	 */
	public void receiveMessage(final VersionMessage msg);

	/**
	 * Handle a client connecting to us as a storage or provider node.
	 */
	public void receiveMessage(final CPCSlaveVersionMessage msg);

	/**
	 * Handle a client exchanging a version
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCVersionMessage2 msg);

	public void receiveMessage(final KeepAliveMessage message);

	/**
	 * The source is telling us that their initialized backup layer is talking to ours and they would like to actually
	 * start backing up. We need to connect our end of the backup and then authorize the source.
	 */
	public void receiveMessage(final SourceBackupConnectedMessage2 msg);

	/**
	 * Informs us that our Target has a backup connection ready to go. We are CPC, so this is impossible.
	 */
	public void receiveMessage(final TargetBackupConnectedMessage2 msg);

	/**
	 * Login the specified user, with an optional registration component.
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCLoginMessage msg);

	/**
	 * Change the user's password.
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCChangePasswordMessage msg);

	/**
	 * Legacy support
	 * 
	 * @param logMsg
	 */
	public void receiveMessage(final CPCLogMessage logMsg);

	/**
	 * Accept messages from the client that we log without modifications.
	 * 
	 * @param logMsg
	 */
	public void receiveMessage(final CPCLogMessage2 logMsg);

	/*
	 * Returns a list of peerIds and connection status (true == connected, false == disconnected) for the given
	 * requestMessage.sourceId guid.
	 */
	public void receiveMessage(final ProtoWrapper protoWrapper,
			final PeerMessages.CPCPeerConnectionStatusRequestMessage requestMessage);

	/**
	 * Save info for a *REMOTE* computer network friend. This adjusts the friend's attributes, whether or not my computer
	 * is offered to the friend (and offered bytes) and what target computers my computer is using.
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCSaveFriendMessage msg);

	/**
	 * Save info for a *REMOTE* computer.
	 */
	public void receiveMessage(final CPCSaveComputerMessage msg);

	/**
	 * Save a child computer.
	 */
	public void receiveMessage(final CPCSaveChildComputerMessage msg);

	/**
	 * Break a friendship
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCRemoveFriendMessage msg);

	/**
	 * Receive new account settings.
	 * 
	 * At the moment this message only allows for the activation of an anonymous license.
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCAccountMessage msg);

	public void receiveMessage(final CPCAlertMessage msg);

	public void receiveMessage(final CPCCapacityMessage msg);

	/**
	 * Retrieve the service config for a computer.
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCGetConfigMessage msg);

	/**
	 * Store the service config for a computer.
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCStoreConfigMessage msg);

	public void receiveMessage(final CPCRestoreStatusMessage msg);

	public void receiveMessage(final CPCBackupLastConnectedMessage msg);

	/**
	 * FCU Swap done
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCUsageSwapDoneMessage msg);

	/**
	 * FCU Version update
	 * 
	 * @param msg
	 */
	public void receiveMessage(final CPCUsageVersionUpdateMessage msg);

	public void receiveMessage(final CPCAuthRulesRequestMessage msg);

	/**
	 * Gives the client a temporary 5 minute web login key for auto login to web site.
	 * <p>
	 * Device config "serviceUI.autoLogin" must be true to get an auto login token.
	 */
	public void receiveMessage(CPCWebLoginKeyRequestMessage msg);

	public void receiveMessage(CPCSecurityKeyTypeRequestMessage msg);

	public void receiveMessage(CPCAddFriendDestinationRequestMessage msg);

	public void receiveMessage(CPCAddFriendRequestMessage msg);

	public void receiveMessage(CPCResetBackupCodeRequestMessage msg);

	public void receiveMessage(CPCEstablishNetworkRequestMessage msg);

	public void receiveMessage(CPCInviteRequestMessage msg);

	public void receiveMessage(CPCSpecialInviteRequestMessage msg);

	public void receiveMessage(CPCSendNotificationRequestMessage msg);

	public void receiveMessage(CPCSetupTwitterRequestMessage msg);

	public void receiveMessage(CPCSimpleRequestMessage msg);

	public void receiveMessage(CPCTwitterRequestMessage msg);

	public void receiveMessage(CPCDeauthorizeRequestMessage msg);

	public void receiveMessage(CPCUserComputerUsagesRequestMessage msg);
}