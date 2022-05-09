package com.code42.radius;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.code42.core.BuilderException;
import com.code42.utils.LangUtils;
import com.code42.utils.Pair;
import com.code42.utils.option.None;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;

/**
 * Builds the input data and the RadiusServerUpdate command. This takes the place of a big long constructor.
 * 
 * Note that within validate() below we're only doing null checks on builder methods which take a reference type and not
 * those that take a value type (i.e. a primitive). Even though an integer will get autoboxed into an Integer within the
 * Option type and that Integer could (in theory) be null there's no way for a null integer primitive to be passed into
 * the actual method call... and as such there's no way the Integer in the corresponding Option type could be null in
 * practice. Keep this in mind if you change the method signature on any of the builder methods below.
 */
public abstract class RadiusServerBuilder<S, T> {

	public static enum Error {
		MISSING_SERVER_NAME, //
		MISSING_ADDRESS, //
		MISSING_SHARED_SECRET, //
		MISSING_ATTRIBUTES, //
		INVALID_ATTRIBUTE //
	}

	public Option<String> radiusServerName = None.getInstance();
	public Option<String> address = None.getInstance();
	public Option<String> sharedSecret = None.getInstance();
	public Option<String> attributeString = None.getInstance();
	public Option<Integer> timeoutSeconds = None.getInstance();
	public Collection<RadiusAttribute> attributes = new HashSet<RadiusAttribute>();

	public RadiusServerBuilder() {
	}

	public S radiusServerName(String name) {
		this.radiusServerName = new Some<String>(name);
		return (S) this;
	}

	public S address(String url) {
		this.address = new Some<String>(url);
		return (S) this;
	}

	public S sharedSecret(String sharedSecret) {
		this.sharedSecret = new Some<String>(sharedSecret);
		return (S) this;
	}

	/**
	 * @param attributes - example: name1=value1 \n name2=value2
	 * @return
	 */
	public S attributes(String attributes) {
		this.attributeString = new Some<String>(attributes);
		return (S) this;
	}

	public S timeoutSeconds(int seconds) {
		this.timeoutSeconds = new Some<Integer>(seconds);
		return (S) this;
	}

	public void validate() throws BuilderException {

		if (LangUtils.hasValue(this.attributeString)) {
			Pair<Set<RadiusAttribute>, List<String>> pair = RadiusServerDto.parseAttributes(this.attributeString.get());
			List<String> errors = pair.getTwo();
			if (errors.size() > 0) {
				throw new BuilderException(Error.INVALID_ATTRIBUTE, "Invalid attributes: "
						+ LangUtils.toString(errors, "[", ", ", "]"));
			}
			this.attributes = pair.getOne();
			// Rebuild the attribute string.
			this.attributeString = new Some(RadiusServerDto.stringifyAttributes(this.attributes));

			// Make sure we have either a NAS-Identifier or a NAS-IP-Address attribute
			boolean missing = true;
			for (RadiusAttribute ra : this.attributes) {
				if (ra.getType() == RadiusAttributeType.NASIdentifier || ra.getType() == RadiusAttributeType.NASIPAddress) {
					missing = false;
				}
			}
			if (missing) {
				throw new BuilderException(Error.MISSING_ATTRIBUTES,
						"One of these attributes is required: NAS-IP-Address or NAS-Identifier");
			}
		}

		if (!(this.radiusServerName instanceof None) && !LangUtils.hasValue(this.radiusServerName.get())) {
			throw new BuilderException(Error.MISSING_SERVER_NAME, "radiusServerName cannot be null");
		}

		if (!(this.address instanceof None) && !LangUtils.hasValue(this.address.get())) {
			throw new BuilderException(Error.MISSING_ADDRESS, "address cannot be null");
		}

		if (!(this.sharedSecret instanceof None) && !LangUtils.hasValue(this.sharedSecret.get())) {
			throw new BuilderException(Error.MISSING_SHARED_SECRET, "shared secret cannot be null");
		}

		if (!(this.attributeString instanceof None) && (this.attributes == null || this.attributes.isEmpty())) {
			throw new BuilderException(Error.MISSING_ATTRIBUTES, "attributes cannot be empty");
		}

		if (!(this.timeoutSeconds instanceof None) && this.timeoutSeconds.get() < 1) {
			throw new BuilderException("timeoutSeconds must be greater than zero");
		}

	}

	public abstract T build() throws BuilderException;

}