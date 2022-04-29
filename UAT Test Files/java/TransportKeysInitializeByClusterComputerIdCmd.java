package com.code42.computer;

import java.security.KeyPair;

import com.backup42.computer.TransportKeyServices;
import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.crypto.RSAKeyUtility;
import com.code42.crypto.X509PublicKey;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.messaging.security.SecurityProvider;

/**
 * Migration of MessagingTransport.initializeTransportKeys() into command structure. The caller must determine the
 * cluster GUID we wish to setup keys for. <br>
 * <br>
 * Note that at the moment Persistence must be initialized in order for this to work! <br>
 * <br>
 * In the future we'll remove the references to TransportKeyServices as well.
 * 
 * @author bmcguire
 */
public class TransportKeysInitializeByClusterComputerIdCmd extends AbstractCmd<KeyPair> {

	private final static Logger log = LoggerFactory.getLogger(TransportKeysInitializeByClusterComputerIdCmd.class);

	private long clusterComputerId;

	public TransportKeysInitializeByClusterComputerIdCmd(long clusterComputerId) {

		this.clusterComputerId = clusterComputerId;
	}

	@Override
	public KeyPair exec(CoreSession session) throws CommandException {

		try {

			TransportKeyServices tks = TransportKeyServices.getInstance();
			KeyPair keyPair = tks.getTransportKeyPair(this.clusterComputerId);
			if (keyPair == null) {
				log.info("Generating new cluster RSA key pair.");
				keyPair = RSAKeyUtility.generateKeyPair(SecurityProvider.Algorithms.KEY_SIZE);
				tks.saveTransportKeyPairForCluster(this.clusterComputerId, keyPair);
			}
			final X509PublicKey publicKey = new X509PublicKey(keyPair.getPublic());
			log.info("RSA Public Key: " + publicKey + ", base64=" + RSAKeyUtility.getBase64Encoded(publicKey));

			return keyPair;
		} catch (Exception e) {

			throw new CommandException("Exception setting transport keys", e);
		}
	}
}
