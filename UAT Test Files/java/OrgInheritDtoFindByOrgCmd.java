package com.code42.org;

import java.util.List;

import com.backup42.CpcConstants;
import com.code42.core.CommandException;
import com.code42.core.auth.C42PermissionApp;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.auth.impl.IsOrgManageableCmd;
import com.code42.core.impl.CoreBridge;
import com.code42.core.impl.DBCmd;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Find information about the source of inherited settings for an org.
 */
public class OrgInheritDtoFindByOrgCmd extends DBCmd<OrgInheritDto> {

	private final int orgId;

	public OrgInheritDtoFindByOrgCmd(int orgId) {
		this.orgId = orgId;
	}

	@Override
	public OrgInheritDto exec(CoreSession session) throws CommandException {
		if (this.orgId < 1) {
			throw new CommandException("Illegal orgId: {}", this.orgId);
		}

		this.run(new IsOrgManageableCmd(this.orgId, C42PermissionApp.Org.READ), session);

		OrgInheritDto dto = new OrgInheritDto(this.orgId);
		if (this.orgId == CpcConstants.Orgs.ADMIN_ID) {
			// if this is the admin org, there's no inheritance to be found. short-circuit the command.
			return dto;
		}

		boolean inclusive = false; // don't care about this org; find the first parent with settings
		List<BackupOrg> orgTree = CoreBridge.run(new OrgFindAllParentsCmd(this.orgId, inclusive));

		// org (max seats, max bytes)
		{
			Org o = findOrgDeclaringOrg(orgTree, this.orgId);
			dto.setOrgIdProvidingOrg(o == null ? null : o.getOrgId());
			dto.setOrgNameProvidingOrg(o == null ? null : o.getOrgName());
		}

		// destinations
		{
			Org o = findOrgDeclaringDestinations(orgTree, this.orgId);
			dto.setOrgIdProvidingDestinations(o == null ? null : o.getOrgId());
			dto.setOrgNameProvidingDestinations(o == null ? null : o.getOrgName());
		}

		// device defaults
		{
			Org o = findOrgDeclaringDeviceDefaults(orgTree, this.orgId);
			dto.setOrgIdProvidingDeviceDefaults(o == null ? null : o.getOrgId());
			dto.setOrgNameProvidingDeviceDefaults(o == null ? null : o.getOrgName());
		}

		return dto;
	}

	/**
	 * Find the org that declares the max_seats/bytes this org should be using. Return null if none found.
	 */
	private static Org findOrgDeclaringOrg(List<BackupOrg> orgTree, int orgId) {
		return Iterables.find(orgTree, new Predicate<Org>() {

			public boolean apply(Org org) {
				BackupOrg borg = (BackupOrg) org;
				return borg.getMaxSeats() != null || borg.getMaxBytes() != null;
			}
		}, null);
	}

	/**
	 * Find the org that declares the destinations this org should be using. Return null if none found.
	 */
	private static Org findOrgDeclaringDestinations(List<BackupOrg> orgTree, int orgId) {

		return Iterables.find(orgTree, new Predicate<Org>() {

			public boolean apply(Org org) {
				return ((BackupOrg) org).getInheritDestinations() == false;
			}
		}, null);
	}

	/**
	 * Find the org that declares the device defaults this org should be using. Return null if none found.
	 */
	private static Org findOrgDeclaringDeviceDefaults(List<BackupOrg> orgTree, int orgId) {
		return Iterables.find(orgTree, new Predicate<Org>() {

			public boolean apply(Org org) {
				BackupOrg borg = (BackupOrg) org;
				return borg.getCustomConfig();
			}
		}, null);
	}

}
