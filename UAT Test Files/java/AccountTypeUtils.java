package com.code42.account;

import com.backup42.common.AuthorizeRules;
import com.code42.protos.v1.shared.AuthorityStructure;
import com.google.protobuf.ByteString;

/**
 * Utility methods for converting between types for account-related actions (currently limited to auth rules). Most
 * useful for defining translations between the protobuff types and our Java types/enums.
 * 
 * TODO: Not sure how classes that follow this model will handle versioning of protobuffs once we get beyond v1...
 * 
 * @author bmcguire
 */
public class AccountTypeUtils {

	/* =============================== Basic type conversions =============================== */
	public static AuthorityStructure.AuthorizeRules toProto(AuthorizeRules arg) {

		/* Short-circuit for the null case */
		if (arg == null) {
			return null;
		}

		AuthorityStructure.AuthorizeRules.Builder builder = AuthorityStructure.AuthorizeRules.newBuilder();

		/* TODO: Are all of these fields always defined for incoming AuthorizeRules? Doesn't seem like that's guaranteed... */
		builder.setMinPasswordLength(arg.getMinPasswordLength());
		builder.setHashPassword(arg.isHashPassword());
		builder.setSalt(ByteString.copyFrom(arg.getSalt()));
		builder.setLdap(arg.isLdap());
		builder.setUserNameIsAnEmail(arg.isUsernameIsAnEmail());
		builder.setDeferredAllowed(arg.isDeferredAllowed());
		return builder.build();
	}
}
