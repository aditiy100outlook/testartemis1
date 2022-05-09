package com.code42.auth;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.backup42.CpcConstants;
import com.code42.core.CommandException;
import com.code42.core.auth.Authenticator;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.DBCmd;
import com.code42.core.ldap.LDAPSpec;
import com.code42.core.radius.RADIUSSpec;
import com.code42.ldap.LdapServer;
import com.code42.ldap.LdapServerFindAllByOrgCmd;
import com.code42.org.IOrg;
import com.code42.org.OrgSso;
import com.code42.org.OrgSsoFindByOrgIdCmd;
import com.code42.radius.RadiusServerDto;
import com.code42.radius.RadiusServerFindAllByOrgCmd;
import com.code42.server.cluster.ClusterFindMyMasterClusterQuery;
import com.code42.server.cluster.MasterCluster;
import com.code42.user.RadiusServer;
import com.google.inject.Inject;

/**
 * Generate an ordered list of authenticators for an org. This list should be used when attempting to find directory
 * entries for users within this org. <br>
 * <br>
 * Note that this command returns a list of Authenticators yet in practice we nearly always [1] use only a single
 * Authenticator per org. The role of this command is to determine which Authenticators could be used to authenticate
 * users for a given org; if callers wish to use only one of those entries they are free to take the first item in the
 * list, throw an error if more than one value is returned, etc. It's not the responsibility of this command to impose
 * those constraints; all we do is figure out which Authenticators an org might use based on a set of rules. <br>
 * <br>
 * [1] - The only case known to violate this constraint is slave server authentication. When dealing with an LDAP org
 * slaves can currently ask the master for authentication and, if that fails, try the LDAP server themselves. If the
 * slave is outside the internal network this is unlikely to work (since most organizations won't expose directory
 * services to the outside world) but if the slave is internal it is a reasonable failover.
 */
public class AuthenticatorFindByOrgCmd extends DBCmd<List<Authenticator>> {

	/* ================= Dependencies ================= */
	private com.code42.core.auth.AuthenticatorFactory factory;

	/* ================= DI injection points ================= */
	@Inject
	public void setFactory(com.code42.core.auth.AuthenticatorFactory factory) {
		this.factory = factory;
	}

	private Integer orgId;
	private OrgSso org;

	public AuthenticatorFindByOrgCmd(IOrg org) {
		this(org.getOrgId());
	}

	public AuthenticatorFindByOrgCmd(int orgId) {
		this.orgId = orgId;
	}

	public AuthenticatorFindByOrgCmd(OrgSso org) {
		this.org = org;
	}

	@Override
	public List<Authenticator> exec(CoreSession session) throws CommandException {

		List<Authenticator> rv = new LinkedList<Authenticator>();

		/* Does this server think it's a master? */
		boolean masterServer = this.env.isMaster();

		if (this.org == null) {
			if (this.orgId == null) {
				throw new CommandException("Either orgId or org is required by the constructors");
			}
			this.org = this.run(new OrgSsoFindByOrgIdCmd(this.orgId), session);
		}

		/* If we're a master and the org in question has no master GUID it's one of ours; we're authoritative for it */
		boolean masterOrg = (this.org.getMasterGuid() == null) && masterServer;

		/*
		 * If we're a master and the org in question has a master GUID then we're providing it to someone else... and we'll
		 * need to ask them about authentication decisions.
		 */
		boolean providerOrg = (this.org.getMasterGuid() != null) && masterServer;

		/*
		 * If you're not either of the above then we're dealing with a slave org. Both null and non-null cases of an orgs
		 * master GUID are covered above so this test reduces down to not being a master.
		 */
		boolean slaveOrg = !masterServer;

		/* A basic short-circuit; if you're dealing with the admin org you get local only */
		if (this.org.getOrgId().equals(CpcConstants.Orgs.ADMIN_ID)) {
			rv.add(this.factory.getLocalAuthenticator());
			return rv;
		}

		/* Slaves and providers should always check with the master first */
		if (providerOrg) {
			rv.add(this.factory.getMasterServerAuthenticator(this.org.getMasterGuid()));
		}
		/* Only provider orgs have a master guid; slave orgs have to retrieve the MasterCluster to get the id */
		if (slaveOrg) {
			MasterCluster masterCluster = this.db.find(new ClusterFindMyMasterClusterQuery(this.env.getMyClusterId()));
			rv.add(this.factory.getMasterServerAuthenticator(masterCluster.getClusterGuid()));
		}

		/*
		 * Are we dealing with a user in an LDAP-enabled org?
		 */
		List<LdapServer> ldapServers = this.run(new LdapServerFindAllByOrgCmd(this.org.getOrgId()), session);
		boolean ldapOrg = ldapServers != null;

		/*
		 * Masters and slaves can try LDAP, but there's no reason for a provider to do so.
		 */
		if ((masterOrg || slaveOrg) && ldapOrg) {

			for (LdapServer ls : ldapServers) {
				LDAPSpec spec = new LDAPSpec.Builder().ldapServer(ls).build();
				rv.add(this.factory.getLDAPAuthenticator(spec, ls.getTimeoutSeconds(), TimeUnit.SECONDS));
			}
		}

		/*
		 * Are we dealing with a user in a RADIUS-enabled org?
		 */
		List<RadiusServer> radiusServers = this.run(new RadiusServerFindAllByOrgCmd(this.org.getOrgId()), session);
		boolean radiusOrg = radiusServers != null;

		/*
		 * Masters and slaves can try RADIUS, but there's no reason for a provider to do so.
		 */
		if ((masterOrg || slaveOrg) && radiusOrg) {

			for (RadiusServer rs : radiusServers) {
				RadiusServerDto dto = new RadiusServerDto(rs); // This is needed for decrypting the shared secret
				RADIUSSpec spec = new RADIUSSpec(dto.getAddress(), dto.getSharedSecret(), dto.getAttributes());
				rv.add(this.factory.getRADIUSAuthenticator(spec, rs.getTimeoutSeconds(), TimeUnit.SECONDS));
			}
		}

		/*
		 * If the org isn't an LDAP org we need to add a local authenticator for all three cases. For master servers this
		 * equates to a local org, for slaves it's the same thing (where the data is provided via a synch with the master)
		 * and in the provider case it's a local (i.e. non-hosted) org.
		 */
		if (!ldapOrg && !radiusOrg) {
			rv.add(this.factory.getLocalAuthenticator());
		}

		return rv;
	}
}
