package com.code42.stats;

import java.util.Map;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.relation.IRelationService;
import com.code42.stats.combiner.AggregateBackupStatsCombiner;
import com.code42.stats.combiner.AggregateBackupStatsSimpleCombiner;
import com.code42.utils.option.Option;
import com.code42.utils.option.Some;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

/**
 * Concrete implementation of {@link AbstractSpaceBoundModelCmd} which returns a specific per-destination sum only. This
 * command builds on the foundation provided by {@link SpaceBoundModelUtils} by providing the business logic of
 * correctly using IRelationService to translate from spaces to GUIDs. It also enforces usage of the correct combiner. <br>
 * <br>
 * Note that this command assumes that your IRelationService instance has full visibility to all space-to-destination
 * mappings. If that's not the case (i.e. if you're passing in a set of mappings from another source) you should
 * probably use the static methods in {@link SpaceBoundModelUtils} directly. <br>
 * <br>
 * Wow, is the name of this command ugly.
 * 
 * @author bmcguire
 */
public class SpaceBoundBackupOrgModelGetDestinationSumCmd extends AbstractCmd<AggregateBackupStats> {

	/* ================= Dependencies ================= */
	private IRelationService relation;

	/* ================= DI injection points ================= */
	@Inject
	public void setRelation(IRelationService relation) {
		this.relation = relation;
	}

	private static AggregateBackupStatsCombiner combiner = new AggregateBackupStatsSimpleCombiner();

	private long destguid;
	private Map<Long, AggregateBackupStats> model;

	public SpaceBoundBackupOrgModelGetDestinationSumCmd(long destguid, Map<Long, AggregateBackupStats> model) {

		this.destguid = destguid;

		if (model == null) {
			throw new IllegalArgumentException("Input model cannot be null");
		}

		this.model = model;
	}

	@Override
	public AggregateBackupStats exec(CoreSession session) throws CommandException {

		final Set<Long> spaceIds = this.relation.getSpacesForDestination(this.destguid);
		Option<AggregateBackupStats> rv = combiner.apply(Maps.filterKeys(this.model, Predicates.in(spaceIds)).values());
		return (rv instanceof Some) ? rv.get() : null;
	}
}
