package com.code42.rule;

import org.drools.RuleBase;
import org.drools.rule.Package;
import org.drools.rule.Rule;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.content.IContentService;
import com.code42.core.content.IResource;
import com.code42.core.content.impl.ContentProviderServices;
import com.code42.core.impl.AbstractCmd;
import com.code42.logging.Logger;
import com.code42.logging.LoggerFactory;
import com.code42.rule.RuleFindByContentCmd.MatchedRuleBean;
import com.code42.utils.LangUtils;
import com.google.inject.Inject;

/**
 * Search for a rule of the given name in the provided locations. The first valid location found will be used, so put
 * your default path location last.
 * 
 * Failing to find a rule at all is considered an error.
 */
public class RuleFindByContentCmd extends AbstractCmd<MatchedRuleBean> {

	private final static Logger log = LoggerFactory.getLogger(RuleFindByContentCmd.class.getName());

	private static final String RULES_CONTENT_ROOT = "rules";

	private final String ruleName;
	private final String[] paths;

	@Inject
	IContentService contentService;

	@Inject
	IRuleLoaderService ruleLoaderService;

	public RuleFindByContentCmd(String ruleName, String... paths) {
		super();
		this.ruleName = ruleName;
		this.paths = paths;
	}

	@Override
	public MatchedRuleBean exec(CoreSession session) throws CommandException {
		this.auth.isSysadmin(session);

		final ContentProviderServices customContent = this.contentService.getServiceInstance(RULES_CONTENT_ROOT);

		MatchedRuleBean mr = null;

		search: for (String path : this.paths) {

			final IResource r = customContent.getResourceByName(path);
			if (r == null) {
				continue search;
			}

			// the file is here, but that doesn't mean it has the requested rule
			final RuleBase rb = this.ruleLoaderService.loadRuleBase(r);
			if (this.containsRule(rb, this.ruleName)) {

				mr = new MatchedRuleBean(this.ruleName, rb);
				log.debug("Matched ruleName=" + mr.getRuleName() + " against path=" + path);
				break search;
			}

		}

		if (mr != null) {
			return mr;
		}

		// not finding a rule is an error state; if you configure a rule search then we must find one
		throw new CommandException("Could not find rules file. ruleName=" + this.ruleName + ", paths="
				+ LangUtils.toString(this.paths));
	}

	/**
	 * Returns true if and only if both parameters are not null and the ruleBase contains a rule whose name matches the
	 * provided name. Otherwise false.
	 * 
	 * As NPE safe as possible.
	 * 
	 * @param ruleBase
	 * @param ruleName
	 * @return
	 */
	private boolean containsRule(RuleBase ruleBase, String ruleName) {
		boolean contains = false;
		if (ruleBase == null || ruleName == null) {
			return contains;
		}

		Package[] packages = ruleBase.getPackages();
		if (packages != null) {

			// search all the packages
			for (int i = 0; i < packages.length; i++) {
				Rule[] rules = packages[i].getRules();
				if (rules != null) {

					// search all the rules for one with our name
					for (int j = 0; j < rules.length; j++) {
						if (rules[j].getName().startsWith(ruleName)) {
							contains = true;
							break; // we can stop checking rules
						}
					}

					// did we find it? then we can stop checking packages
					if (contains) {
						break;
					}
				}
			}
		}

		return contains;
	}

	/**
	 * Describes the RuleBase and RuleName that were matched by the search.
	 */
	public static class MatchedRuleBean {

		private String ruleName;
		private RuleBase ruleBase;

		public MatchedRuleBean(String ruleName, RuleBase ruleBase) {
			super();
			this.ruleName = ruleName;
			this.ruleBase = ruleBase;
		}

		public String getRuleName() {
			return this.ruleName;
		}

		public void setRuleName(String ruleName) {
			this.ruleName = ruleName;
		}

		public RuleBase getRuleBase() {
			return this.ruleBase;
		}

		public void setRuleBase(RuleBase ruleBase) {
			this.ruleBase = ruleBase;
		}

	}

}
