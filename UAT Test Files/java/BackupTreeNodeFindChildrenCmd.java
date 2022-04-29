package com.code42.config;

import java.util.ArrayList;
import java.util.List;

import com.code42.backup.config.BackupSetConfig;
import com.code42.computer.ComputerSso;
import com.code42.computer.ComputerSsoFindByGuidCmd;
import com.code42.core.BuilderException;
import com.code42.core.CommandException;
import com.code42.core.auth.UnauthorizedException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsRemoteFileSelectionAllowedCmd;
import com.code42.core.backupfile.IBackupFileService;
import com.code42.core.backupfile.impl.BackupFileTree;
import com.code42.core.backupfile.impl.BackupFileTreeNode;
import com.code42.core.impl.DBCmd;
import com.code42.io.path.Path;
import com.code42.utils.LangUtils;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.inject.Inject;

/**
 * Find all the children of the given node for the given client. If no file ID is provided, the root is assumed.
 * 
 * If the client is not connected, it will throw an exception.
 */
public class BackupTreeNodeFindChildrenCmd extends DBCmd<List<BackupFileTreeNode>> {

	@Inject
	private IBackupFileService backupFileService;

	public enum Error {
		CLIENT_TIMEOUT, GUID_INVALID, NODE_NOT_FOUND
	}

	private final Builder data;

	public BackupTreeNodeFindChildrenCmd(Builder builder) {
		this.data = builder;
	}

	@Override
	public List<BackupFileTreeNode> exec(CoreSession session) throws CommandException {
		ComputerSso computer = this.runtime.run(new ComputerSsoFindByGuidCmd(this.data.guid), session);
		if (computer == null) {
			throw new UnauthorizedException("Invalid Guid: " + this.data.guid);
		}

		// Check authorization
		this.run(new IsRemoteFileSelectionAllowedCmd(computer), session);

		long guid = this.data.guid;
		long backupSetId = this.data.backupSetId.get();
		boolean isDirectory = this.data.isDirectory.get();
		String comparePath = this.data.path.get();

		BackupFileTree tree = null;
		if (LangUtils.hasValue(comparePath)) {
			tree = this.backupFileService.getBackupFileTree(guid);
		} else {
			tree = this.backupFileService.initBackupFileTree(guid, backupSetId);
		}

		tree.setShowHidden(true);

		boolean success = false;
		List<BackupFileTreeNode> nodes = new ArrayList<BackupFileTreeNode>();
		if (LangUtils.hasValue(comparePath)) {
			Path path = new Path(comparePath, isDirectory);
			BackupFileTreeNode node = tree.getNode(path);
			if (node == null) {
				throw new CommandException(Error.NODE_NOT_FOUND, "Unable to find the node corresponding to path: " + path);
			}
			success = tree.loadChildren(node);
			node = tree.getNode(node.getComparePath());
			nodes = node.getChildren();

		} else {
			success = tree.init();
			nodes = tree.getRoots();
		}

		if (!success) {
			throw new CommandException(Error.CLIENT_TIMEOUT, "Timeout while retrieving file tree");
		}
		return nodes;
	}

	// /////////////////////
	// BUILDER
	// /////////////////////
	public static class Builder {

		private final long guid;

		// Defaulted options; can be overridden
		private Option<String> path = new Some<String>(null);
		private Option<Long> backupSetId = new Some<Long>(BackupSetConfig.DEFAULT_ID);
		private Option<Boolean> isDirectory = new Some<Boolean>(true);

		public Builder(long guid) {
			this.guid = guid;
		}

		public Builder backupSetId(long backupSetId) {
			this.backupSetId = new Some<Long>(backupSetId);
			return this;
		}

		public Builder path(String path) {
			this.path = new Some<String>(path);
			return this;
		}

		public Builder isDirectory(boolean isDirectory) {
			this.isDirectory = new Some<Boolean>(isDirectory);
			return this;
		}

		private void validate() throws BuilderException {
			if (this.guid <= 0) {
				throw new BuilderException(Error.GUID_INVALID, "Invalid GUID: " + this.guid);
			}
		}

		public BackupTreeNodeFindChildrenCmd build() throws BuilderException {
			this.validate();
			return new BackupTreeNodeFindChildrenCmd(this);
		}
	}

	// /////////////////////
	// HELPER METHODS
	// /////////////////////

	// private List<BackupFileTreeNode> loadChildren(List<BackupFileTreeNode> nodes) throws JSONException {
	// List<BackupFileTreeNode> list = new ArrayList<BackupFileTreeNode>();
	//
	// if (nodes != null) {
	// Collections.sort(nodes);
	// for (BackupFileTreeNode node : nodes) {
	// String parentName = (node.getParent() != null) ? node.getParent().getName() : "/";
	// log.debug("Node loaded: name=" + node.getName() + "; parent=" + parentName + "; state="
	// + node.getState().toString());
	// JSONObject o = createJSONFileObject(node);
	// if ((node.getChildren() != null) && (node.getChildren().size() > 0)) {
	// o.put("children", this.loadChildren(node.getChildren()));
	// }
	// list.add(o);
	// }
	// }
	//
	// return jsonArray;
	// }

}
