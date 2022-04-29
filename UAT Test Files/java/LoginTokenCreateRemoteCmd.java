package com.code42.auth;

import java.net.URL;

import com.backup42.CpcConstants;
import com.code42.auth.LoginTokenCreateRemoteCmd.LoginTokenRemote;
import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.server.ServerFindWebsiteHostByServerGuidCmd;
import com.code42.server.node.Node;
import com.code42.server.node.NodeFindBySourceAndDestinationCmd;
import com.code42.server.node.ProviderNode;
import com.code42.server.sync.StorageNodeStructureUpdates;
import com.code42.server.sync.SyncFindHierarchyUpdatesCmd;
import com.code42.user.BackupUser;
import com.code42.user.User;
import com.code42.user.UserFindByIdCmd;
import com.google.inject.Inject;

/**
 * Create a single-use token for automatic login to this server; typical use is to generate such a token for inclusion
 * on a URL that is then used to automatically log the already authenticated user into the website, but that may not be
 * the only use.
 */
public class LoginTokenCreateRemoteCmd extends DBCmd<LoginTokenRemote> {

	enum Error {
		ADMIN_PROVIDER_LOGIN_NOT_SUPPORTED
	}

	@Inject
	private IAutoTokenController autoTokenController;

	/* User we wish to create the token for */
	private final int userId;
	private IPermission appLoginPermission;
	private final Long sourceGuid;
	private final Long destinationGuid;

	public LoginTokenCreateRemoteCmd(int userId, long sourceGuid, long targetGuid) {
		this(userId, null, sourceGuid, targetGuid);
	}

	public LoginTokenCreateRemoteCmd(int userId, IPermission appLoginPermission, Long sourceGuid, Long destinationGuid) {
		this.userId = userId;
		this.appLoginPermission = appLoginPermission;
		this.sourceGuid = sourceGuid;
		this.destinationGuid = destinationGuid;
	}

	@Override
	public LoginTokenRemote exec(CoreSession session) throws CommandException {

		/*
		 * Note: We are NOT generating the token for the authenticated subject; we are creating one for the given userId.
		 * However, the subject must have permission to make this request on their behalf.
		 */
		User user = this.runtime.run(new UserFindByIdCmd(this.userId), session);

		// If no login permission was provided use the defaults
		if (this.appLoginPermission == null) {
			this.appLoginPermission = this.serverService.getRequiredLoginPermission(this.userId);
		}

		/*
		 * Check for login permission for the required application.
		 * 
		 * The user we are generating the token for must have the correct login permission as well
		 */
		try {
			this.auth.isAuthorized(session, this.appLoginPermission);
			this.auth.isAuthorized(this.auth.getSession(user), this.appLoginPermission);
		} catch (UnauthorizedException e) {
			// AUTH_MIGRATION: Unauthenticated exception, reason: USER_INVALID
			/*
			 * This is the incorrect error to indicate here... should be a basic AuthorizationException indicating that the
			 * necessary permissions weren't present.
			 */
			throw new UnauthorizedException("User not authorized for this application");
		}

		Node node = this.run(new NodeFindBySourceAndDestinationCmd(this.sourceGuid, this.destinationGuid), session);
		if (node == null) {
			throw new CommandException("Node not found; for sourceGuid=" + this.sourceGuid + " and destinationGuid "
					+ this.destinationGuid);
		}

		SyncFindHierarchyUpdatesCmd.Builder builder = new SyncFindHierarchyUpdatesCmd.Builder((BackupUser) user);
		builder.isProvider(node instanceof ProviderNode);
		builder.hierarchyChangePermitted(true);
		StorageNodeStructureUpdates updates = this.run(builder.build(), session);

		if (node instanceof ProviderNode) {
			if (user.getUserId().intValue() == CpcConstants.Users.ADMIN_ID) {
				throw new CommandException(Error.ADMIN_PROVIDER_LOGIN_NOT_SUPPORTED,
						"The default admin user cannot login to a provider");
			}
			String userUid = user.getUserUid();
			long clusterGuid = this.env.getMyClusterGuid();

			LoginTokenProviderRequestMessage msg = new LoginTokenProviderRequestMessage(userUid, this.sourceGuid,
					this.destinationGuid, clusterGuid, this.appLoginPermission, updates);
			LoginTokenProviderResponseMessage respMsg = this.autoTokenController.send(msg, node);
			if (respMsg == null) {
				throw new CommandException("Unable to request login token from provider; null response");
			}
			if (!respMsg.isSuccess()) {
				throw new CommandException("Error requesting login token from provider", respMsg.errorMessageSet());
			}
			return new LoginTokenRemote(respMsg.getLoginToken(), respMsg.getServerUrl());

		} else {
			LoginTokenRequestMessage msg = new LoginTokenRequestMessage(this.userId, this.appLoginPermission, updates);
			LoginTokenResponseMessage respMsg = this.autoTokenController.send(msg, node);
			if (respMsg == null) {
				throw new CommandException("Unable to request login token; null response");
			}
			if (!respMsg.isSuccess()) {
				throw new CommandException("Error requesting login token", respMsg.errorMessageSet());
			}
			URL url = this.run(new ServerFindWebsiteHostByServerGuidCmd(node.getNodeGuid()), session);
			String serverUrl = url == null ? null : url.toString();
			return new LoginTokenRemote(respMsg.getLoginToken(), serverUrl);
		}
	}

	public static class LoginTokenRemote {

		private String loginToken;
		private String serverUrl;

		private LoginTokenRemote(String loginToken, String serverUrl) {
			this.loginToken = loginToken;
			this.serverUrl = serverUrl;
		}

		public String getLoginToken() {
			return this.loginToken;
		}

		public String getServerUrl() {
			return this.serverUrl;
		}
	}
}
